"""
印刷体过滤算法模块 (Print Filter)

核心功能：
- 二分类：手写体(1) vs 印刷体(0)
- 基于MobileNetV3-Small微调
- 训练数据：手写体(CASIA-HWDB) + 印刷体(字体生成)
- 输出置信度，阈值0.5
- 支持TFLite INT8量化导出（目标<3.2MB）

端侧部署说明：
- 模型输入: (1, 64, 64, 4) uint8 RGBA
- 模型输出: (1, 1) float32 置信度
- 阈值: 0.5（>0.5为手写体，<=0.5为印刷体）
"""

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader, TensorDataset
from typing import Tuple, Optional, List
import os
import warnings

from .models import Glyph, PrintFilterResult


class PrintFilter(nn.Module):
    """
    印刷体过滤模型 (MobileNetV3-Small + 二分类头)
    
    架构：
    1. MobileNetV3-Small backbone (预训练ImageNet)
    2. Global Average Pooling
    3. FC层 -> 1维输出 (Sigmoid)
    """
    
    def __init__(self, dropout: float = 0.3):
        super().__init__()
        
        try:
            import torchvision.models as models
            
            # 加载MobileNetV3-Small预训练权重
            self.backbone = models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.IMAGENET1K_V1)
            
            # 修改第一层：适配4通道RGBA输入
            first_conv = self.backbone.features[0][0]
            
            new_conv = nn.Conv2d(
                in_channels=4,  # RGBA
                out_channels=first_conv.out_channels,
                kernel_size=first_conv.kernel_size,
                stride=first_conv.stride,
                padding=first_conv.padding,
                bias=first_conv.bias is not None,
                groups=first_conv.groups,
                dilation=first_conv.dilation,
            )
            
            # 权重迁移：RGB权重复制，Alpha初始化为RGB均值
            with torch.no_grad():
                new_conv.weight[:, :3] = first_conv.weight
                new_conv.weight[:, 3] = first_conv.weight.mean(dim=1)
                if first_conv.bias is not None:
                    new_conv.bias = nn.Parameter(first_conv.bias.clone())
            
            self.backbone.features[0][0] = new_conv
            
            # 获取特征维度
            in_features = self.backbone.classifier[0].in_features  # 576
            
            # 替换classifier为二分类头
            self.backbone.classifier = nn.Sequential(
                nn.Linear(in_features, 256),
                nn.Hardswish(),
                nn.Dropout(p=dropout),
                nn.Linear(256, 1),
                nn.Sigmoid(),  # 输出0-1概率
            )
            
        except Exception as e:
            warnings.warn(f"无法加载torchvision: {e}，使用模拟backbone")
            self.backbone = self._mock_backbone()
    
    def _mock_backbone(self) -> nn.Module:
        """模拟backbone（降级方案）"""
        class MockBackbone(nn.Module):
            def __init__(self):
                super().__init__()
                self.features = nn.Sequential(
                    nn.Conv2d(4, 16, 3, stride=2, padding=1),
                    nn.BatchNorm2d(16),
                    nn.ReLU(),
                    nn.Conv2d(16, 32, 3, stride=2, padding=1),
                    nn.BatchNorm2d(32),
                    nn.ReLU(),
                    nn.Conv2d(32, 64, 3, stride=2, padding=1),
                    nn.BatchNorm2d(64),
                    nn.ReLU(),
                    nn.Conv2d(64, 128, 3, stride=2, padding=1),
                    nn.BatchNorm2d(128),
                    nn.ReLU(),
                    nn.AdaptiveAvgPool2d(1),
                )
                self.classifier = nn.Sequential(
                    nn.Linear(128, 64),
                    nn.ReLU(),
                    nn.Dropout(0.3),
                    nn.Linear(64, 1),
                    nn.Sigmoid(),
                )
            
            def forward(self, x):
                x = self.features(x)
                x = x.view(x.size(0), -1)
                x = self.classifier(x)
                return x
        
        return MockBackbone()
    
    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        前向传播
        
        Args:
            x: (B, 4, 64, 64) RGBA输入
        
        Returns:
            (B, 1) 置信度 (0-1)
        """
        return self.backbone(x)


class PrintFilterTrainer:
    """
    印刷体过滤模型训练器
    
    训练流程：
    1. 准备数据：手写体(正样本, label=1) + 印刷体(负样本, label=0)
    2. 训练模型
    3. 评估验证
    4. 导出TFLite
    """
    
    def __init__(self, 
                 model: Optional[PrintFilter] = None,
                 device: str = "auto",
                 learning_rate: float = 1e-4,
                 batch_size: int = 32):
        """
        初始化训练器
        
        Args:
            model: PrintFilter模型（None则新建）
            device: 计算设备
            learning_rate: 学习率
            batch_size: 批大小
        """
        if device == "auto":
            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        else:
            self.device = torch.device(device)
        
        self.model = model or PrintFilter()
        self.model.to(self.device)
        
        self.learning_rate = learning_rate
        self.batch_size = batch_size
        self.criterion = nn.BCELoss()
        self.optimizer = optim.Adam(self.model.parameters(), lr=learning_rate)
        
        print(f"[印刷体过滤训练器] 初始化完成 | 设备: {self.device} | LR: {learning_rate}")
    
    def prepare_data(self, 
                     handwritten_images: np.ndarray,
                     printed_images: np.ndarray,
                     val_split: float = 0.2) -> Tuple[DataLoader, DataLoader]:
        """
        准备训练数据
        
        Args:
            handwritten_images: (N, H, W, C) 手写体图像
            printed_images: (M, H, W, C) 印刷体图像
            val_split: 验证集比例
        
        Returns:
            train_loader, val_loader: DataLoader
        """
        # 预处理图像
        X_hand = self._preprocess_images(handwritten_images)
        X_print = self._preprocess_images(printed_images)
        
        # 合并数据集
        X = np.concatenate([X_hand, X_print], axis=0)
        y = np.concatenate([
            np.ones(len(X_hand)),      # 手写体 = 1
            np.zeros(len(X_print))      # 印刷体 = 0
        ], axis=0)
        
        # 划分训练/验证集
        n_total = len(X)
        n_val = int(n_total * val_split)
        indices = np.random.permutation(n_total)
        
        train_idx = indices[n_val:]
        val_idx = indices[:n_val]
        
        # 创建TensorDataset
        X_train = torch.FloatTensor(X[train_idx])
        y_train = torch.FloatTensor(y[train_idx]).unsqueeze(1)
        X_val = torch.FloatTensor(X[val_idx])
        y_val = torch.FloatTensor(y[val_idx]).unsqueeze(1)
        
        train_dataset = TensorDataset(X_train, y_train)
        val_dataset = TensorDataset(X_val, y_val)
        
        train_loader = DataLoader(train_dataset, batch_size=self.batch_size, shuffle=True)
        val_loader = DataLoader(val_dataset, batch_size=self.batch_size)
        
        print(f"[数据准备] 训练集: {len(train_dataset)} | 验证集: {len(val_dataset)}")
        print(f"            手写体: {len(X_hand)} | 印刷体: {len(X_print)}")
        
        return train_loader, val_loader
    
    def _preprocess_images(self, images: np.ndarray) -> np.ndarray:
        """
        预处理图像
        
        输入: (N, H, W, C) uint8
        输出: (N, C, H, W) float32 [0, 1]
        """
        # 确保RGBA 4通道
        if len(images.shape) == 3:
            images = np.stack([images] * 4, axis=-1)
        if images.shape[-1] == 3:
            alpha = np.full((*images.shape[:-1], 1), 255, dtype=np.uint8)
            images = np.concatenate([images, alpha], axis=-1)
        
        # 归一化
        images = images.astype(np.float32) / 255.0
        
        # NHWC -> NCHW
        images = np.transpose(images, (0, 3, 1, 2))
        
        return images
    
    def train(self, train_loader: DataLoader, 
              val_loader: DataLoader,
              epochs: int = 10,
              patience: int = 3) -> dict:
        """
        训练模型
        
        Args:
            train_loader: 训练数据
            val_loader: 验证数据
            epochs: 训练轮数
            patience: 早停耐心值
        
        Returns:
            history: 训练历史
        """
        history = {"train_loss": [], "val_loss": [], "val_acc": []}
        best_val_loss = float('inf')
        patience_counter = 0
        
        print(f"[训练开始] Epochs: {epochs} | Patience: {patience}")
        
        for epoch in range(epochs):
            # 训练阶段
            self.model.train()
            train_losses = []
            
            for batch_x, batch_y in train_loader:
                batch_x = batch_x.to(self.device)
                batch_y = batch_y.to(self.device)
                
                self.optimizer.zero_grad()
                outputs = self.model(batch_x)
                loss = self.criterion(outputs, batch_y)
                loss.backward()
                self.optimizer.step()
                
                train_losses.append(loss.item())
            
            avg_train_loss = np.mean(train_losses)
            
            # 验证阶段
            self.model.eval()
            val_losses = []
            val_correct = 0
            val_total = 0
            
            with torch.no_grad():
                for batch_x, batch_y in val_loader:
                    batch_x = batch_x.to(self.device)
                    batch_y = batch_y.to(self.device)
                    
                    outputs = self.model(batch_x)
                    loss = self.criterion(outputs, batch_y)
                    val_losses.append(loss.item())
                    
                    # 计算准确率
                    predicted = (outputs > 0.5).float()
                    val_correct += (predicted == batch_y).sum().item()
                    val_total += batch_y.size(0)
            
            avg_val_loss = np.mean(val_losses)
            val_acc = val_correct / val_total
            
            history["train_loss"].append(avg_train_loss)
            history["val_loss"].append(avg_val_loss)
            history["val_acc"].append(val_acc)
            
            print(f"  Epoch [{epoch+1}/{epochs}] "
                  f"Train Loss: {avg_train_loss:.4f} | "
                  f"Val Loss: {avg_val_loss:.4f} | "
                  f"Val Acc: {val_acc:.4f}")
            
            # 早停检查
            if avg_val_loss < best_val_loss:
                best_val_loss = avg_val_loss
                patience_counter = 0
                # 保存最佳模型
                self.best_state = self.model.state_dict().copy()
            else:
                patience_counter += 1
                if patience_counter >= patience:
                    print(f"[早停] 验证损失不再下降，在第 {epoch+1} 轮停止")
                    break
        
        # 恢复最佳模型
        if hasattr(self, 'best_state'):
            self.model.load_state_dict(self.best_state)
        
        return history
    
    def predict(self, images: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
        """
        预测图像是否为手写体
        
        Args:
            images: (N, H, W, C) uint8 图像
        
        Returns:
            predictions: (N,) bool数组，True=手写体
            confidences: (N,) float置信度
        """
        X = self._preprocess_images(images)
        X_tensor = torch.FloatTensor(X).to(self.device)
        
        self.model.eval()
        with torch.no_grad():
            outputs = self.model(X_tensor)
        
        confidences = outputs.cpu().numpy().flatten()
        predictions = confidences > 0.5
        
        return predictions, confidences
    
    def save_model(self, path: str):
        """保存PyTorch模型"""
        os.makedirs(os.path.dirname(path) or '.', exist_ok=True)
        torch.save({
            'model_state_dict': self.model.state_dict(),
            'optimizer_state_dict': self.optimizer.state_dict(),
        }, path)
        print(f"[模型保存] {path}")
    
    def load_model(self, path: str):
        """加载PyTorch模型"""
        checkpoint = torch.load(path, map_location=self.device)
        self.model.load_state_dict(checkpoint['model_state_dict'])
        print(f"[模型加载] {path}")


class PrintFilterInference:
    """
    印刷体过滤推理器（部署时使用）
    
    轻量级封装，用于端侧推理
    """
    
    def __init__(self, model_path: Optional[str] = None, device: str = "cpu"):
        """
        初始化推理器
        
        Args:
            model_path: 模型路径（None则新建未训练模型）
            device: 推理设备
        """
        self.device = torch.device(device)
        self.model = PrintFilter()
        
        if model_path and os.path.exists(model_path):
            checkpoint = torch.load(model_path, map_location=device)
            self.model.load_state_dict(checkpoint['model_state_dict'])
        
        self.model.to(self.device)
        self.model.eval()
    
    def classify(self, glyph: Glyph, threshold: float = 0.5) -> PrintFilterResult:
        """
        分类单个字形
        
        Args:
            glyph: 字形对象
            threshold: 分类阈值
        
        Returns:
            PrintFilterResult: 分类结果
        """
        if glyph.image is None:
            raise ValueError("字形对象缺少图像数据")
        
        # 预处理
        img = glyph.image
        if len(img.shape) == 2:
            img = np.stack([img] * 4, axis=-1)
        if img.shape[-1] == 3:
            alpha = np.full((*img.shape[:-1], 1), 255, dtype=np.uint8)
            img = np.concatenate([img, alpha], axis=-1)
        
        img = img.astype(np.float32) / 255.0
        img = np.transpose(img, (2, 0, 1))  # HWC -> CHW
        img = np.expand_dims(img, axis=0)    # 添加batch维度
        
        tensor = torch.FloatTensor(img).to(self.device)
        
        # 推理
        with torch.no_grad():
            output = self.model(tensor)
        
        confidence = float(output.cpu().numpy().flatten()[0])
        is_handwritten = confidence > threshold
        
        return PrintFilterResult(
            is_handwritten=is_handwritten,
            confidence=confidence,
            raw_score=confidence,
            glyph_id=glyph.glyph_id,
        )
    
    def classify_batch(self, images: np.ndarray, threshold: float = 0.5) -> List[PrintFilterResult]:
        """
        批量分类
        
        Args:
            images: (N, H, W, C) uint8 图像数组
            threshold: 分类阈值
        
        Returns:
            List[PrintFilterResult] 分类结果列表
        """
        # 预处理
        X = images.astype(np.float32) / 255.0
        if X.shape[-1] == 3:
            alpha = np.full((*X.shape[:-1], 1), 1.0)
            X = np.concatenate([X, alpha], axis=-1)
        X = np.transpose(X, (0, 3, 1, 2))
        
        tensor = torch.FloatTensor(X).to(self.device)
        
        with torch.no_grad():
            outputs = self.model(tensor)
        
        confidences = outputs.cpu().numpy().flatten()
        
        results = []
        for conf in confidences:
            results.append(PrintFilterResult(
                is_handwritten=conf > threshold,
                confidence=float(conf),
                raw_score=float(conf),
            ))
        
        return results


# TFLite导出工具
def export_print_filter_tflite(pytorch_model: PrintFilter,
                                output_path: str = "./output/print_filter.tflite"):
    """
    导出印刷体过滤模型为TFLite（INT8量化）
    
    目标大小：< 3.2MB
    
    Args:
        pytorch_model: 训练好的PyTorch模型
        output_path: TFLite输出路径
    """
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    try:
        import tensorflow as tf
        
        # 导出ONNX
        dummy_input = torch.randn(1, 4, 64, 64)
        onnx_path = output_path.replace('.tflite', '.onnx')
        
        torch.onnx.export(
            pytorch_model,
            dummy_input,
            onnx_path,
            input_names=['input'],
            output_names=['confidence'],
            dynamic_axes={'input': {0: 'batch_size'}},
            opset_version=11,
        )
        
        # 转换为TFLite（INT8量化）
        try:
            from onnx_tf.backend import prepare
            import onnx
            
            onnx_model = onnx.load(onnx_path)
            tf_rep = prepare(onnx_model)
            
            tf_model_path = output_path.replace('.tflite', '_tf')
            tf_rep.export_graph(tf_model_path)
            
            converter = tf.lite.TFLiteConverter.from_saved_model(tf_model_path)
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            
            def representative_dataset():
                for _ in range(100):
                    data = np.random.randn(1, 4, 64, 64).astype(np.float32)
                    yield [data]
            
            converter.representative_dataset = representative_dataset
            converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
            converter.inference_input_type = tf.int8
            converter.inference_output_type = tf.float32
            
            tflite_model = converter.convert()
            
            with open(output_path, 'wb') as f:
                f.write(tflite_model)
            
            file_size_mb = os.path.getsize(output_path) / (1024 * 1024)
            print(f"[TFLite导出成功] {output_path} | 大小: {file_size_mb:.2f}MB")
            
        except ImportError:
            print("[提示] 安装 onnx-tf 后运行: onnx2tf -i {} -o {}".format(onnx_path, output_path))
    
    except ImportError:
        print("[警告] 缺少tensorflow，跳过TFLite导出")
