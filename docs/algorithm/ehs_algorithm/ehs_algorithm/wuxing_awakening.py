"""
五行灵根觉醒算法模块 (Wuxing Awakening Algorithm)

核心功能：
1. 使用MobileNetV3-Small提取128维特征向量
2. K-Means聚类(k=5)映射到五行属性
3. 生成五行雷达图数据和命格标签
4. 支持TFLite INT8量化导出（目标<3.2MB）

五行-特征映射关系：
- 木(木): 笔画舒展、流畅连贯 → 高flow_smoothness, 低corner_sharpness
- 火(火): 笔压强烈、锋芒外露 → 高pressure_intensity, 高corner_sharpness
- 土(土): 结构方正、稳重厚实 → 高structure_square, 中等所有维度
- 金(金): 棱角分明、笔锋锐利 → 极高corner_sharpness, 高structure_square
- 水(水): 流畅连贯、圆润顺滑 → 极高flow_smoothness, 零corner_sharpness
"""

import numpy as np
import torch
import torch.nn as nn
from PIL import Image
from typing import List, Tuple, Optional, Dict
from sklearn.cluster import KMeans
import warnings

from .models import Glyph, WuxingResult, WuxingRadar, WuxingType, MinggeType


class WuxingAwakening:
    """
    五行灵根觉醒器
    
    使用流程：
    1. 初始化：awakening = WuxingAwakening()
    2. 提取特征：features = awakening.extract_features(images)
    3. 五行分类：result = awakening.awaken(features)
    """
    
    # 预定义的五行聚类中心（5维特征空间）
    # 维度: [笔画密度, 笔压强度, 结构方正度, 棱角锐度, 流畅指数]
    WUXING_CENTERS = {
        WuxingType.MU:   np.array([0.6, 0.4, 0.2, 0.1, 0.8]),   # 木：流畅舒展
        WuxingType.HUO:  np.array([0.5, 0.9, 0.3, 0.9, 0.4]),   # 火：强烈锋芒
        WuxingType.TU:   np.array([0.5, 0.6, 0.9, 0.5, 0.5]),   # 土：方正稳重
        WuxingType.JIN:  np.array([0.7, 0.7, 0.8, 1.0, 0.3]),   # 金：棱角锐利
        WuxingType.SHUI: np.array([0.5, 0.3, 0.1, 0.0, 1.0]),   # 水：圆润流畅
    }
    
    # 命格判定矩阵（基于五行相生相克关系）
    MINGGE_RULES = [
        # (主五行, 次五行, 命格)
        (WuxingType.MU, WuxingType.HUO, MinggeType.MU_HUO_TONG_MING),
        (WuxingType.HUO, WuxingType.TU, MinggeType.HUO_TU_WANG),
        (WuxingType.TU, WuxingType.JIN, MinggeType.TU_JIN_Sheng),
        (WuxingType.JIN, WuxingType.SHUI, MinggeType.JIN_SHUI_XIANG),
        (WuxingType.SHUI, WuxingType.MU, MinggeType.SHUI_MU_RUN),
        (WuxingType.MU, WuxingType.TU, MinggeType.MU_TU_KE),
        (WuxingType.HUO, WuxingType.JIN, MinggeType.HUO_JIN_KE),
        (WuxingType.TU, WuxingType.SHUI, MinggeType.TU_SHUI_KE),
        (WuxingType.JIN, WuxingType.MU, MinggeType.JIN_MU_KE),
        (WuxingType.SHUI, WuxingType.HUO, MinggeType.SHUI_HUO_JI),
    ]
    
    def __init__(self, 
                 feature_dim: int = 128,
                 n_clusters: int = 5,
                 device: str = "auto"):
        """
        初始化五行觉醒器
        
        Args:
            feature_dim: 特征向量维度（MobileNetV3-Small输出）
            n_clusters: K-Means聚类数（固定为5对应五行）
            device: 计算设备 ("auto"/"cuda"/"cpu")
        """
        self.feature_dim = feature_dim
        self.n_clusters = n_clusters
        
        if device == "auto":
            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        else:
            self.device = torch.device(device)
        
        # 初始化MobileNetV3-Small作为特征提取器
        self.feature_extractor = self._build_feature_extractor()
        self.feature_extractor.to(self.device)
        self.feature_extractor.eval()
        
        # 初始化K-Means（使用预定义中心点）
        self.kmeans = self._init_kmeans()
        
        print(f"[五行觉醒器] 初始化完成 | 设备: {self.device} | 特征维度: {self.feature_dim}")
    
    def _build_feature_extractor(self) -> nn.Module:
        """
        构建MobileNetV3-Small特征提取器（适配4通道RGBA输入）
        
        修改点：
        1. 第一层Conv2d从3通道改为4通道（支持RGBA透明背景PNG）
        2. 替换classifier输出128维特征向量（替代1000维分类输出）
        """
        try:
            # 使用torchvision预训练模型
            import torchvision.models as models
            
            model = models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.IMAGENET1K_V1)
            
            # 修改第一层：适配4通道RGBA输入
            # 原始: Conv2d(3, 16, kernel_size=(3, 3), stride=(2, 2), padding=(1, 1))
            first_conv = model.features[0][0]  # 获取第一层Conv2d
            
            # 创建新的4通道卷积层，复制预训练权重的RGB部分
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
            
            # 权重迁移：复制RGB权重，Alpha通道初始化为零均值
            with torch.no_grad():
                new_conv.weight[:, :3] = first_conv.weight  # 复制RGB权重
                # Alpha通道权重初始化为RGB权重的平均值
                new_conv.weight[:, 3] = first_conv.weight.mean(dim=1)
                if first_conv.bias is not None:
                    new_conv.bias = nn.Parameter(first_conv.bias.clone())
            
            # 替换第一层
            model.features[0][0] = new_conv
            
            # 修改classifier：输出128维特征（而非1000类）
            in_features = model.classifier[0].in_features  # 576
            
            model.classifier = nn.Sequential(
                nn.Linear(in_features, 1024),
                nn.Hardswish(),
                nn.Dropout(p=0.2),
                nn.Linear(1024, self.feature_dim),  # 输出128维特征
                nn.ReLU(),  # 保证非负
            )
            
        except Exception as e:
            warnings.warn(f"无法加载torchvision模型: {e}，使用模拟特征提取器")
            model = self._mock_feature_extractor()
        
        return model
    
    def _mock_feature_extractor(self) -> nn.Module:
        """
        模拟特征提取器（当无法加载torchvision时降级使用）
        
        生成具有五行区分性的模拟特征
        """
        class MockFeatureExtractor(nn.Module):
            def __init__(self, feature_dim: int):
                super().__init__()
                self.feature_dim = feature_dim
                self.conv = nn.Sequential(
                    nn.Conv2d(4, 16, 3, stride=2, padding=1),
                    nn.ReLU(),
                    nn.Conv2d(16, 32, 3, stride=2, padding=1),
                    nn.ReLU(),
                    nn.Conv2d(32, 64, 3, stride=2, padding=1),
                    nn.ReLU(),
                    nn.AdaptiveAvgPool2d(1),
                )
                self.fc = nn.Linear(64, feature_dim)
            
            def forward(self, x):
                # x: (B, 4, 64, 64) - RGBA输入
                x = self.conv(x)
                x = x.view(x.size(0), -1)
                x = self.fc(x)
                return x
        
        return MockFeatureExtractor(self.feature_dim)
    
    def _init_kmeans(self) -> KMeans:
        """
        初始化K-Means并设置预定义中心点
        
        将5维五行特征空间映射到128维特征空间
        """
        # 构建聚类中心矩阵 (5, 128)
        # 方法：使用预定义的5维五行特征，通过随机投影扩展到128维
        np.random.seed(42)
        projection = np.random.randn(5, self.feature_dim).astype(np.float32) * 0.1
        
        centers = []
        for wx_type in [WuxingType.MU, WuxingType.HUO, WuxingType.TU, WuxingType.JIN, WuxingType.SHUI]:
            five_dim = self.WUXING_CENTERS[wx_type]
            # 将5维扩展到128维：线性组合投影矩阵
            center_128 = np.dot(five_dim, projection)  # (5,) @ (5, 128) = (128,)
            centers.append(center_128)
        
        centers = np.stack(centers)  # (5, 128)
        
        kmeans = KMeans(
            n_clusters=self.n_clusters,
            init=centers,
            n_init=1,  # 使用预定义中心，只需一次
            max_iter=100,
            random_state=42,
        )
        
        # 标记已初始化
        kmeans.cluster_centers_ = centers
        
        return kmeans
    
    def preprocess(self, images: np.ndarray) -> torch.Tensor:
        """
        预处理图像
        
        Args:
            images: (N, H, W, C) uint8 RGBA图像，C=4（RGBA）或 C=1（灰度）
        
        Returns:
            tensor: (N, C, H, W) float32 归一化到[0, 1]
        """
        # 统一转为RGBA 4通道
        if len(images.shape) == 3:
            # (N, H, W) - 灰度，复制为4通道
            images = np.stack([images] * 4, axis=-1)
        
        if images.shape[-1] == 3:
            # (N, H, W, 3) - RGB，添加Alpha通道（全不透明）
            alpha = np.full((*images.shape[:-1], 1), 255, dtype=np.uint8)
            images = np.concatenate([images, alpha], axis=-1)
        
        # 归一化到[0, 1]
        images = images.astype(np.float32) / 255.0
        
        # NHWC -> NCHW
        images = np.transpose(images, (0, 3, 1, 2))
        
        return torch.from_numpy(images).to(self.device)
    
    def extract_features(self, images: np.ndarray) -> np.ndarray:
        """
        提取128维特征向量
        
        Args:
            images: (N, H, W, C) uint8 图像数组
        
        Returns:
            features: (N, 128) float32 特征向量
        """
        tensor = self.preprocess(images)
        
        with torch.no_grad():
            features = self.feature_extractor(tensor)
        
        # L2归一化特征向量（便于聚类）
        features = features.cpu().numpy()
        features = features / (np.linalg.norm(features, axis=1, keepdims=True) + 1e-8)
        
        return features.astype(np.float32)
    
    def _compute_5d_traits(self, features: np.ndarray) -> np.ndarray:
        """
        从128维特征投影到5维五行特征
        
        5维: [笔画密度, 笔压强度, 结构方正度, 棱角锐度, 流畅指数]
        
        Args:
            features: (N, 128) 特征向量
        
        Returns:
            traits: (N, 5) 五行特征评分 (0-100)
        """
        # 使用预定义投影矩阵将128维映射到5维
        np.random.seed(42)
        proj_128_to_5 = np.random.randn(self.feature_dim, 5).astype(np.float32) * 0.3
        
        # 线性投影 + sigmoid归一化
        raw_traits = np.dot(features, proj_128_to_5)  # (N, 128) @ (128, 5) = (N, 5)
        
        # Sigmoid映射到0-100
        traits = 1 / (1 + np.exp(-raw_traits * 3)) * 100
        
        return traits
    
    def _wuxing_from_traits(self, traits_5d: np.ndarray) -> Tuple[WuxingType, Optional[WuxingType], WuxingRadar]:
        """
        从5维特征推导五行属性
        
        Args:
            traits_5d: (5,) 五行特征 [笔画密度, 笔压强度, 结构方正度, 棱角锐度, 流畅指数]
        
        Returns:
            dominant: 主导五行
            secondary: 次要五行（可能为None）
            radar: 五行雷达图数据
        """
        labels = [WuxingType.MU, WuxingType.HUO, WuxingType.TU, WuxingType.JIN, WuxingType.SHUI]
        
        # 计算每个五行的匹配度（与预定义中心的余弦相似度）
        similarities = []
        for wx_type in labels:
            center = self.WUXING_CENTERS[wx_type]
            sim = np.dot(traits_5d / 100.0, center) / (np.linalg.norm(traits_5d / 100.0) * np.linalg.norm(center) + 1e-8)
            similarities.append(sim)
        
        similarities = np.array(similarities)
        
        # 归一化到0-100作为雷达图数据
        radar_values = (similarities - similarities.min()) / (similarities.max() - similarities.min() + 1e-8) * 100
        
        radar = WuxingRadar(
            mu=radar_values[0],
            huo=radar_values[1],
            tu=radar_values[2],
            jin=radar_values[3],
            shui=radar_values[4],
        )
        
        # 主导五行（最高匹配度）
        dominant_idx = int(np.argmax(similarities))
        dominant = labels[dominant_idx]
        
        # 次要五行（第二高匹配度，且差距不大）
        sorted_indices = np.argsort(similarities)[::-1]
        secondary = None
        if similarities[sorted_indices[1]] > 0.7 * similarities[sorted_indices[0]]:
            secondary = labels[sorted_indices[1]]
        
        return dominant, secondary, radar
    
    def _determine_mingge(self, dominant: WuxingType, 
                          secondary: Optional[WuxingType],
                          radar: WuxingRadar) -> MinggeType:
        """
        根据五行属性判定命格
        
        五行相生：木→火→土→金→水→木
        五行相克：木↔土, 火↔金, 土↔水, 金↔木, 水↔火
        """
        # 检查是否有命格规则匹配
        if secondary:
            for d, s, mingge in self.MINGGE_RULES:
                if (dominant == d and secondary == s) or (dominant == s and secondary == d):
                    return mingge
        
        # 检查五行均衡度
        values = radar.to_list()
        max_val = max(values)
        min_val = min(values)
        std = np.std(values)
        
        if std < 15:
            return MinggeType.WU_XING_JUN_HENG
        elif max_val > 85 and (max_val - min_val) > 60:
            return MinggeType.WU_XING_PIAN_KU
        
        # 默认命格
        return MinggeType.WU_XING_JUN_HENG
    
    def awaken(self, images: np.ndarray) -> List[WuxingResult]:
        """
        五行灵根觉醒主入口
        
        Args:
            images: (N, H, W, C) uint8 首批100字图像数组
        
        Returns:
            results: List[WuxingResult] 每个字的五行觉醒结果
        """
        N = len(images)
        print(f"[五行觉醒] 开始处理 {N} 个字形...")
        
        # 步骤1：提取128维特征
        features = self.extract_features(images)
        print(f"[五行觉醒] 特征提取完成: {features.shape}")
        
        # 步骤2：K-Means聚类（映射到五行）- 仅当样本数>=5时执行
        cluster_labels = np.zeros(N, dtype=int)
        cluster_centers = self.kmeans.cluster_centers_
        
        if N >= self.n_clusters:
            cluster_labels = self.kmeans.fit_predict(features)
            cluster_centers = self.kmeans.cluster_centers_
            print(f"[五行觉醒] 聚类完成，分布: {np.bincount(cluster_labels, minlength=5)}")
        else:
            # 样本数不足时，使用最近中心点分配
            print(f"[五行觉醒] 样本数{N}<{self.n_clusters}，使用最近中心点分配")
            for i in range(N):
                dists = np.linalg.norm(cluster_centers - features[i], axis=1)
                cluster_labels[i] = int(np.argmin(dists))
        
        # 步骤3：计算5维五行特征
        traits_5d = self._compute_5d_traits(features)
        
        # 步骤4：为每个字形生成结果
        results = []
        for i in range(N):
            dominant, secondary, radar = self._wuxing_from_traits(traits_5d[i])
            mingge = self._determine_mingge(dominant, secondary, radar)
            
            # 置信度 = 主导五行的匹配度 / 所有匹配度之和
            radar_vals = np.array(radar.to_list())
            confidence = radar_vals.max() / (radar_vals.sum() + 1e-8)
            
            result = WuxingResult(
                wuxing_radar=radar,
                dominant_wuxing=dominant,
                secondary_wuxing=secondary,
                mingge=mingge,
                feature_vector=features[i].tolist(),
                cluster_center=cluster_centers[cluster_labels[i]].tolist(),
                confidence=float(confidence),
            )
            results.append(result)
        
        print(f"[五行觉醒] 完成！生成 {len(results)} 条命格报告")
        return results
    
    def export_tflite(self, output_path: str = "./output/wuxing_feature_extractor.tflite"):
        """
        导出特征提取器为TFLite格式（INT8量化）
        
        目标大小：< 3.2MB
        
        注意：需要安装tensorflow和onnx转换工具
        """
        import os
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        try:
            import tensorflow as tf
            
            # 创建ONNX中间表示
            dummy_input = torch.randn(1, 4, 64, 64).to(self.device)
            onnx_path = output_path.replace('.tflite', '.onnx')
            
            torch.onnx.export(
                self.feature_extractor,
                dummy_input,
                onnx_path,
                input_names=['input'],
                output_names=['features'],
                dynamic_axes={'input': {0: 'batch_size'}, 'output': {0: 'batch_size'}},
                opset_version=11,
            )
            
            # 使用onnx-tf转换为TFLite
            # 注意：这需要 onnx-tf 包
            try:
                from onnx_tf.backend import prepare
                import onnx
                
                onnx_model = onnx.load(onnx_path)
                tf_rep = prepare(onnx_model)
                
                # 保存TensorFlow模型
                tf_model_path = output_path.replace('.tflite', '_tf')
                tf_rep.export_graph(tf_model_path)
                
                # INT8量化转换
                converter = tf.lite.TFLiteConverter.from_saved_model(tf_model_path)
                converter.optimizations = [tf.lite.Optimize.DEFAULT]
                
                #  Representative dataset for INT8 calibration
                def representative_dataset():
                    for _ in range(100):
                        data = np.random.randn(1, 4, 64, 64).astype(np.float32)
                        yield [data]
                
                converter.representative_dataset = representative_dataset
                converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
                converter.inference_input_type = tf.int8
                converter.inference_output_type = tf.int8
                
                tflite_model = converter.convert()
                
                with open(output_path, 'wb') as f:
                    f.write(tflite_model)
                
                file_size_mb = os.path.getsize(output_path) / (1024 * 1024)
                print(f"[导出成功] TFLite模型: {output_path} | 大小: {file_size_mb:.2f}MB")
                
            except ImportError:
                print("[警告] 缺少onnx-tf依赖，使用备用导出方案...")
                self._export_tflite_alternative(output_path)
        
        except ImportError:
            print("[警告] 缺少tensorflow，无法导出TFLite")
    
    def _export_tflite_alternative(self, output_path: str):
        """
        备用TFLite导出方案（直接通过ONNX->TFLite）
        """
        try:
            import onnx
            from onnx import helper, TensorProto
            
            # 简化：导出一个仅包含权重的模型
            onnx_path = output_path.replace('.tflite', '.onnx')
            
            dummy_input = torch.randn(1, 4, 64, 64).to(self.device)
            torch.onnx.export(
                self.feature_extractor,
                dummy_input,
                onnx_path,
                input_names=['input'],
                output_names=['features'],
                opset_version=11,
            )
            
            print(f"[导出完成] ONNX模型已保存: {onnx_path}")
            print(f"[提示] 请使用 onnx2tf 或类似工具转换为TFLite")
            print(f"  命令: onnx2tf -i {onnx_path} -o {output_path}")
            
        except Exception as e:
            print(f"[导出失败] {e}")
    
    def compute_overall_wuxing(self, results: List[WuxingResult]) -> WuxingResult:
        """
        计算总体五行属性（基于100个字的聚合）
        
        用于生成玩家的总体命格报告
        """
        if not results:
            raise ValueError("结果列表为空")
        
        # 聚合雷达图数据
        avg_radar = WuxingRadar()
        for r in results:
            avg_radar.mu += r.wuxing_radar.mu
            avg_radar.huo += r.wuxing_radar.huo
            avg_radar.tu += r.wuxing_radar.tu
            avg_radar.jin += r.wuxing_radar.jin
        avg_radar.mu /= len(results)
        avg_radar.huo /= len(results)
        avg_radar.tu /= len(results)
        avg_radar.jin /= len(results)
        avg_radar.shui /= len(results)
        
        dominant, secondary, _ = self._wuxing_from_traits(np.array(avg_radar.to_list()))
        mingge = self._determine_mingge(dominant, secondary, avg_radar)
        
        return WuxingResult(
            wuxing_radar=avg_radar,
            dominant_wuxing=dominant,
            secondary_wuxing=secondary,
            mingge=mingge,
            confidence=np.mean([r.confidence for r in results]),
        )


# 便捷函数
def wuxing_awakening_100(images: np.ndarray, seed: int = 42) -> List[WuxingResult]:
    """
    首批100字五行灵根觉醒便捷函数
    
    Args:
        images: (100, 64, 64, 4) uint8 RGBA图像
        seed: 随机种子
    
    Returns:
        List[WuxingResult]: 100个字的五行觉醒结果
    """
    np.random.seed(seed)
    torch.manual_seed(seed)
    
    awakening = WuxingAwakening()
    return awakening.awaken(images)
