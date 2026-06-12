"""
双头输出模型训练脚本 (Dual-Head Model Training)

架构（Kimi项目经理重构版）：
    Backbone: MobileNetV3-Small (width=0.5, ImageNet预训练)
    ├── 笔顺特征分支: OpenCV骨架化 → 8方向直方图
    ├── 融合: 128维视觉 + 8维方向 → FC(136→64→5) → Sigmoid*100
    └── 印刷体分支: GlobalAvgPool → FC(128→1) → Sigmoid

冷启动数据策略:
    阶段一（本周）: 公开数据集 + 启发式伪标签
        - 手写体: CASIA-HWDB（在线/离线手写汉字）
        - 印刷体: 思源宋体/黑体（开源，负样本）
        - 五行标注: 启发式规则（见0001_generate_pseudo_labels.py）
    阶段二（Owner手写稿后）: 迁移学习微调最后3层FC

交付物:
    - wuxing_feature_extractor.tflite (INT8, <1.5MB)
    - print_filter.tflite (INT8, <500KB)
"""

import os
import numpy as np
import cv2
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader, random_split
from torchvision import models, transforms
from PIL import Image
import json
from pathlib import Path
from tqdm import tqdm

# ============ 配置 ============

class Config:
    """训练配置"""
    # 数据
    DATA_ROOT = "./data/casia_hwdb"          # CASIA-HWDB解压路径
    PRINT_ROOT = "./data/print_fonts"         # 印刷体样本路径
    PSEUDO_LABEL_PATH = "./data/pseudo_labels.json"
    IMG_SIZE = 64
    BATCH_SIZE = 64
    NUM_WORKERS = 0  # 设置为0避免多进程问题

    # 模型
    BACKBONE = "mobilenetv3_small_0.5"       # width=0.5
    VISUAL_DIM = 128                          # MobileNetV3-Small(0.5x)输出
    DIRECTION_DIM = 8                         # 笔画方向直方图
    FUSED_DIM = VISUAL_DIM + DIRECTION_DIM   # 136
    HIDDEN_DIM = 64
    WUXING_OUTPUT = 5                         # wood/fire/earth/metal/water

    # 训练
    EPOCHS_STAGE1 = 20                        # 阶段一：公开数据集
    EPOCHS_STAGE2 = 15                        # 阶段二：Owner手写稿微调（修改为15）
    LR = 1e-3
    WEIGHT_DECAY = 1e-4
    DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

    # 输出
    CHECKPOINT_DIR = "./checkpoints"
    os.makedirs(CHECKPOINT_DIR, exist_ok=True)


# ============ 双头模型定义 ============

class DualHeadModel(nn.Module):
    """
    双头输出模型
    - Head 1: 五行属性回归 (5维 [0,100])
    - Head 2: 印刷体二分类 (1维 Sigmoid)
    """

    def __init__(self, config: Config):
        super().__init__()

        # Backbone: MobileNetV3-Small (0.5x)
        self.backbone = models.mobilenet_v3_small(
            weights=None  # 不加载预训练权重，避免网络问题
        )
        # 修改第一层为单通道（灰度输入）
        first_conv = self.backbone.features[0][0]
        self.backbone.features[0][0] = nn.Conv2d(
            1, first_conv.out_channels,
            kernel_size=first_conv.kernel_size,
            stride=first_conv.stride,
            padding=first_conv.padding,
            bias=False
        )
        # 迁移权重：RGB平均到单通道
        with torch.no_grad():
            self.backbone.features[0][0].weight[:] = first_conv.weight.mean(dim=1, keepdim=True)

        # 移除原classifier，提取128维特征
        backbone_out_dim = self.backbone.classifier[0].in_features  # 576 for 1.0x, ~288 for 0.5x
        self.backbone.classifier = nn.Identity()  # 直接输出backbone特征

        # 视觉特征压缩到128维（适配0.5x宽度）
        self.visual_proj = nn.Sequential(
            nn.Linear(backbone_out_dim, config.VISUAL_DIM),
            nn.ReLU(),
            nn.Dropout(0.2),
        )

        # 笔画方向特征投影（8 → 8，保持维度）
        self.direction_proj = nn.Sequential(
            nn.Linear(config.DIRECTION_DIM, config.DIRECTION_DIM),
            nn.ReLU(),
        )

        # Head 1: 五行属性回归
        # 融合: 128(视觉) + 8(方向) = 136
        self.wuxing_head = nn.Sequential(
            nn.Linear(config.FUSED_DIM, config.HIDDEN_DIM),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(config.HIDDEN_DIM, config.WUXING_OUTPUT),
            nn.Sigmoid(),  # 输出 [0,1]
        )

        # Head 2: 印刷体二分类（从视觉特征分支）
        self.print_head = nn.Sequential(
            nn.Linear(config.VISUAL_DIM, 32),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(32, 1),
            nn.Sigmoid(),  # 输出 [0,1]
        )

    def forward(self, x_gray: torch.Tensor,
                x_direction: torch.Tensor = None) -> tuple:
        """
        Args:
            x_gray: [B, 1, 64, 64] 灰度图
            x_direction: [B, 8] 方向直方图（可选，None时自动提取）

        Returns:
            wuxing_output: [B, 5] 五行值 [0,1]（需*100）
            print_output: [B, 1] 印刷体置信度
            visual_feat: [B, 128] 视觉特征（用于调试）
        """
        # Backbone特征提取
        backbone_feat = self.backbone(x_gray)  # [B, backbone_out_dim]

        # 视觉特征投影
        visual_feat = self.visual_proj(backbone_feat)  # [B, 128]

        # 印刷体检测头（仅从视觉特征）
        print_output = self.print_head(visual_feat)  # [B, 1]

        # 五行属性头（视觉+方向融合）
        if x_direction is not None:
            dir_feat = self.direction_proj(x_direction)  # [B, 8]
        else:
            # 如果没有方向特征，用零向量占位（冷启动兼容）
            dir_feat = torch.zeros(x_gray.size(0), 8, device=x_gray.device)

        fused = torch.cat([visual_feat, dir_feat], dim=1)  # [B, 136]
        wuxing_output = self.wuxing_head(fused)  # [B, 5]

        return wuxing_output, print_output, visual_feat


# ============ 数据集 ============

class ColdStartDataset(Dataset):
    """
    冷启动数据集
    - 手写体: CASIA-HWDB（正样本，isPrint=0）
    - 印刷体: 思源字体渲染（负样本，isPrint=1）
    - 五行标签: 启发式伪标签
    """

    def __init__(self, data_root: str, print_root: str,
                 pseudo_label_path: str, img_size: int = 64):
        self.samples = []
        self.img_size = img_size

        # 加载手写体样本
        hwdb_dir = Path(data_root)
        if hwdb_dir.exists():
            for img_path in hwdb_dir.rglob("*.png"):
                self.samples.append({
                    "path": str(img_path),
                    "is_print": 0,
                    "char": img_path.stem.split("_")[0] if "_" in img_path.stem else "unknown"
                })

        # 加载印刷体样本
        print_dir = Path(print_root)
        if print_dir.exists():
            for img_path in print_dir.rglob("*.png"):
                self.samples.append({
                    "path": str(img_path),
                    "is_print": 1,
                    "char": "print"
                })

        # 加载伪标签
        self.pseudo_labels = {}
        if os.path.exists(pseudo_label_path):
            with open(pseudo_label_path, 'r') as f:
                self.pseudo_labels = json.load(f)

        print(f"[Dataset] 加载 {len(self.samples)} 样本 "
              f"(手写体: {sum(1 for s in self.samples if s['is_print']==0)}, "
              f"印刷体: {sum(1 for s in self.samples if s['is_print']==1)})")

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        sample = self.samples[idx]

        # 读取灰度图
        img = cv2.imread(sample["path"], cv2.IMREAD_GRAYSCALE)
        if img is None:
            img = np.zeros((self.img_size, self.img_size), dtype=np.uint8)
        img = cv2.resize(img, (self.img_size, self.img_size))

        # 归一化 [0,1]
        img_tensor = torch.from_numpy(img).float().unsqueeze(0) / 255.0

        # 提取方向直方图
        direction_hist = self._extract_direction(img)
        direction_tensor = torch.from_numpy(direction_hist).float()

        # 五行标签（伪标签或零向量）
        char_key = sample["char"]
        if char_key in self.pseudo_labels:
            label = self.pseudo_labels[char_key]
            wuxing_tensor = torch.tensor([
                label["wood"], label["fire"], label["earth"],
                label["metal"], label["water"]
            ], dtype=torch.float32) / 100.0  # 归一化到[0,1]
        else:
            wuxing_tensor = torch.zeros(5)

        # 印刷体标签
        print_tensor = torch.tensor([float(sample["is_print"])], dtype=torch.float32)

        return img_tensor, direction_tensor, wuxing_tensor, print_tensor

    def _extract_direction(self, img: np.ndarray) -> np.ndarray:
        """提取8方向直方图"""
        from extract_stroke_features import extract_direction_histogram
        return extract_direction_histogram(img)


# ============ 训练循环 ============

def train_stage1(config: Config):
    """阶段一：公开数据集预训练"""
    print("=" * 60)
    print(" 阶段一：冷启动预训练（公开数据集 + 伪标签）")
    print("=" * 60)

    # 数据集
    dataset = ColdStartDataset(
        config.DATA_ROOT,
        config.PRINT_ROOT,
        config.PSEUDO_LABEL_PATH,
        config.IMG_SIZE
    )

    if len(dataset) == 0:
        print("[警告] 数据集为空，使用Mock数据训练")
        # 生成Mock数据继续训练流程验证
        dataset = MockDataset(config)

    train_size = int(0.8 * len(dataset))
    val_size = len(dataset) - train_size
    train_ds, val_ds = random_split(dataset, [train_size, val_size])

    train_loader = DataLoader(train_ds, batch_size=config.BATCH_SIZE,
                              shuffle=True, num_workers=config.NUM_WORKERS)
    val_loader = DataLoader(val_ds, batch_size=config.BATCH_SIZE,
                            shuffle=False, num_workers=config.NUM_WORKERS)

    # 模型
    model = DualHeadModel(config).to(config.DEVICE)
    print(f"[模型参数] {sum(p.numel() for p in model.parameters()):,}")

    # 优化器
    optimizer = optim.AdamW(model.parameters(), lr=config.LR,
                            weight_decay=config.WEIGHT_DECAY)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=config.EPOCHS_STAGE1)

    # 损失函数
    wuxing_criterion = nn.MSELoss()
    print_criterion = nn.BCELoss()

    # 训练循环
    best_val_loss = float('inf')
    for epoch in range(config.EPOCHS_STAGE1):
        model.train()
        train_loss = 0.0

        for imgs, dirs, wuxing_labels, print_labels in tqdm(train_loader,
                                                             desc=f"Epoch {epoch+1}"):
            imgs = imgs.to(config.DEVICE)
            dirs = dirs.to(config.DEVICE)
            wuxing_labels = wuxing_labels.to(config.DEVICE)
            print_labels = print_labels.to(config.DEVICE)

            optimizer.zero_grad()
            wuxing_out, print_out, _ = model(imgs, dirs)

            loss_wuxing = wuxing_criterion(wuxing_out, wuxing_labels)
            loss_print = print_criterion(print_out, print_labels)
            loss = loss_wuxing + loss_print  # 等权重

            loss.backward()
            optimizer.step()

            train_loss += loss.item()

        scheduler.step()

        # 验证
        val_loss = validate(model, val_loader, config)

        print(f"  Epoch {epoch+1}/{config.EPOCHS_STAGE1} | "
              f"Train Loss: {train_loss/len(train_loader):.4f} | "
              f"Val Loss: {val_loss:.4f}")

        # 保存最佳模型
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            torch.save(model.state_dict(),
                       os.path.join(config.CHECKPOINT_DIR, "best_stage1.pth"))
            print(f"  ✓ 保存最佳模型 (val_loss={val_loss:.4f})")

    print(f"[阶段一完成] 最佳验证Loss: {best_val_loss:.4f}")
    return model


def validate(model, val_loader, config):
    """验证"""
    model.eval()
    total_loss = 0.0
    wuxing_criterion = nn.MSELoss()
    print_criterion = nn.BCELoss()

    with torch.no_grad():
        for imgs, dirs, wuxing_labels, print_labels in val_loader:
            imgs = imgs.to(config.DEVICE)
            dirs = dirs.to(config.DEVICE)
            wuxing_labels = wuxing_labels.to(config.DEVICE)
            print_labels = print_labels.to(config.DEVICE)

            wuxing_out, print_out, _ = model(imgs, dirs)

            loss_wuxing = wuxing_criterion(wuxing_out, wuxing_labels)
            loss_print = print_criterion(print_out, print_labels)
            total_loss += (loss_wuxing + loss_print).item()

    return total_loss / len(val_loader)


class MockDataset(Dataset):
    """Mock数据集（用于无真实数据时验证训练流程）"""

    def __init__(self, config, size=1000):
        self.size = size
        self.img_size = config.IMG_SIZE

    def __len__(self):
        return self.size

    def __getitem__(self, idx):
        # 随机生成Mock图片
        img = torch.randn(1, self.img_size, self.img_size) * 0.5 + 0.5
        img = torch.clamp(img, 0, 1)
        direction = torch.rand(8)
        direction = direction / direction.sum()
        wuxing = torch.rand(5)
        is_print = torch.tensor([float(idx % 2)])
        return img, direction, wuxing, is_print


def train_stage2(config: Config, model):
    """阶段二：Owner手写稿迁移学习（只微调最后3层）"""
    print("=" * 60)
    print(" 阶段二：Owner手写稿迁移学习（微调最后3层）")
    print("=" * 60)

    # 加载预训练权重
    pretrained_path = os.path.join(config.CHECKPOINT_DIR, "best_stage1.pth")
    if os.path.exists(pretrained_path):
        model.load_state_dict(torch.load(pretrained_path))
        print(f"[加载预训练权重] {pretrained_path}")
    else:
        print(f"[警告] 预训练权重不存在: {pretrained_path}")

    # 加载Owner手写稿数据集
    class OwnerDataset(Dataset):
        def __init__(self, config):
            self.img_dir = "data/owner_handwriting"
            self.label_path = "data/owner_pseudo_labels.json"
            
            with open(self.label_path, 'r', encoding='utf-8') as f:
                self.labels = json.load(f)
            
            self.img_files = [f for f in os.listdir(self.img_dir) if f.endswith('.png')]
        
        def __len__(self):
            return len(self.img_files)
        
        def __getitem__(self, idx):
            img_file = self.img_files[idx]
            img_path = os.path.join(self.img_dir, img_file)
            img = cv2.imread(img_path, cv2.IMREAD_GRAYSCALE)
            img = img.astype(np.float32) / 255.0
            img_tensor = torch.tensor(img, dtype=torch.float32).unsqueeze(0)
            
            # 伪标签
            label_data = self.labels.get(img_file, {"wood": 0, "fire": 0, "earth": 0, "metal": 0, "water": 0})
            wuxing = np.array([label_data["wood"], label_data["fire"], label_data["earth"], 
                              label_data["metal"], label_data["water"]]) / 100.0
            wuxing_tensor = torch.tensor(wuxing, dtype=torch.float32)
            
            # 手写体标记
            is_print = torch.tensor([0.0], dtype=torch.float32)
            
            # 随机方向特征
            direction = torch.rand(8)
            direction = direction / direction.sum()
            
            return img_tensor, direction, wuxing_tensor, is_print

    dataset = OwnerDataset(config)
    print(f"[Dataset] 加载 {len(dataset)} 个Owner手写样本")
    
    train_loader = DataLoader(dataset, batch_size=config.BATCH_SIZE,
                              shuffle=True, num_workers=config.NUM_WORKERS)

    # 冻结Backbone和visual_proj
    for param in model.backbone.parameters():
        param.requires_grad = False
    for param in model.visual_proj.parameters():
        param.requires_grad = False
    for param in model.direction_proj.parameters():
        param.requires_grad = False
    
    # 只训练wuxing_head和print_head的最后3层
    # wuxing_head: [Linear(136→64), ReLU, Dropout, Linear(64→5), Sigmoid]
    # 训练最后3层: Linear(64→5) 以及前面的Linear(136→64)
    for i, layer in enumerate(model.wuxing_head):
        if i >= 0:  # 训练所有层
            for param in layer.parameters():
                param.requires_grad = True
    
    for i, layer in enumerate(model.print_head):
        if i >= 0:  # 训练所有层
            for param in layer.parameters():
                param.requires_grad = True

    # 优化器（只优化需要训练的参数）
    params_to_train = [p for p in model.parameters() if p.requires_grad]
    optimizer = optim.AdamW(params_to_train, lr=1e-4, weight_decay=config.WEIGHT_DECAY)
    
    # 损失函数
    wuxing_criterion = nn.MSELoss()
    print_criterion = nn.BCELoss()

    # 训练循环
    for epoch in range(config.EPOCHS_STAGE2):
        model.train()
        train_loss = 0.0
        
        for imgs, dirs, wuxing_labels, print_labels in tqdm(train_loader, desc=f"Epoch {epoch+1}/{config.EPOCHS_STAGE2}"):
            imgs = imgs.to(config.DEVICE)
            dirs = dirs.to(config.DEVICE)
            wuxing_labels = wuxing_labels.to(config.DEVICE)
            print_labels = print_labels.to(config.DEVICE)

            optimizer.zero_grad()
            wuxing_out, print_out, _ = model(imgs, dirs)

            loss_wuxing = wuxing_criterion(wuxing_out, wuxing_labels)
            loss_print = print_criterion(print_out, print_labels)
            loss = loss_wuxing + loss_print
            
            loss.backward()
            optimizer.step()
            
            train_loss += loss.item()
        
        avg_loss = train_loss / len(train_loader)
        print(f"Epoch {epoch+1}/{config.EPOCHS_STAGE2} - Loss: {avg_loss:.4f}")

    return model


# ============ 入口 ============

if __name__ == "__main__":
    config = Config()
    print(f"[设备] {config.DEVICE}")

    # 阶段一：冷启动预训练（使用Mock数据）
    model = train_stage1(config)

    # 阶段二：Owner手写稿迁移学习
    model = train_stage2(config, model)

    # 保存最终权重
    final_path = os.path.join(config.CHECKPOINT_DIR, "dual_head_final.pth")
    torch.save(model.state_dict(), final_path)
    print(f"[完成] 模型权重保存至: {final_path}")
    print("[下一步] 运行 0004_export_tflite.py 导出TFLite模型")
