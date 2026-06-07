# 蚯蚓.手书修仙传 - 核心算法模块

> **Eruca HandScript: Cultivation Saga - Core Algorithm Module**

## 项目概述

本文档定义了《蚯蚓.手书修仙传》MVP阶段三大核心算法模块的实现：

| 模块 | 功能 | 输入 | 输出 |
|------|------|------|------|
| **五行灵根觉醒** | 首批100字特征提取与五行分类 | 100张64×64手写单字PNG | 五行雷达图 + 命格标签 |
| **金字招牌检测** | 高稳定性字形触发检测 | Glyph对象（含元数据） | Boolean + 等级 |
| **印刷体过滤** | 手写体 vs 印刷体二分类 | 单字图片 | 置信度 + 分类结果 |

---

## 技术栈

- **Python**: 3.9+
- **PyTorch**: 2.0+ (MobileNetV3-Small)
- **scikit-learn**: K-Means聚类
- **Pillow/OpenCV**: 图像预处理
- **TensorFlow**: TFLite INT8量化导出（可选）

---

## 快速开始

### 安装依赖

```bash
# 基础安装（推理）
pip install -r requirements.txt

# 完整安装（含TFLite导出）
pip install -r requirements.txt
pip install tensorflow onnx onnx-tf
```

### 运行演示

```bash
# 运行所有算法演示
python main.py

# 运行指定任务
python main.py --task 1   # 仅五行灵根觉醒
python main.py --task 2   # 仅金字招牌检测
python main.py --task 3   # 仅印刷体过滤

# 详细输出 + 保存样本
python main.py --verbose --save-samples
```

### 运行单元测试

```bash
# 运行全部测试
python -m pytest tests/ -v

# 运行指定模块测试
python -m pytest tests/test_wuxing.py -v
python -m pytest tests/test_golden_sign.py -v
python -m pytest tests/test_print_filter.py -v
```

---

## 模块1：五行灵根觉醒

### 算法说明

```
输入: 100张 64×64 RGBA 手写单字图片
  ↓
预处理: 归一化 → NHWC转NCHW
  ↓
MobileNetV3-Small: 提取128维特征向量 (ImageNet预训练)
  ↓
L2归一化: 特征向量单位化
  ↓
K-Means聚类(k=5): 映射到五行
  ↓
5维特征投影: [笔画密度, 笔压强度, 结构方正度, 棱角锐度, 流畅指数]
  ↓
五行匹配: 与预定义中心计算余弦相似度
  ↓
输出: 五行雷达图(0-100) + 命格标签 + 置信度
```

### 五行-特征映射

| 五行 | 笔画密度 | 笔压强度 | 结构方正度 | 棱角锐度 | 流畅指数 | 典型特征 |
|------|----------|----------|------------|----------|----------|----------|
| 木 | 0.6 | 0.4 | 0.2 | 0.1 | 0.8 | 流畅舒展 |
| 火 | 0.5 | 0.9 | 0.3 | 0.9 | 0.4 | 强烈锋芒 |
| 土 | 0.5 | 0.6 | 0.9 | 0.5 | 0.5 | 方正稳重 |
| 金 | 0.7 | 0.7 | 0.8 | 1.0 | 0.3 | 棱角锐利 |
| 水 | 0.5 | 0.3 | 0.1 | 0.0 | 1.0 | 圆润流畅 |

### 命格体系

基于五行相生相克关系：
- **相生**: 木→火→土→金→水→木
- **相克**: 木↔土, 火↔金, 土↔水, 金↔木, 水↔火

```python
from ehs_algorithm.mock_data import MockDataGenerator
from ehs_algorithm.wuxing_awakening import WuxingAwakening

# 生成100字Mock数据
generator = MockDataGenerator(seed=42)
images, chars = generator.generate_batch(count=100)

# 五行觉醒
awakening = WuxingAwakening()
results = awakening.awaken(images)

# 查看结果
for r in results[:5]:
    print(f"主导: {r.dominant_wuxing.value}, 命格: {r.mingge.value}")
    print(f"雷达: {r.wuxing_radar.to_list()}")

# 总体五行
overall = awakening.compute_overall_wuxing(results)
print(f"总体命格: {overall.mingge.value}")
```

### 输出JSON Schema

```json
{
  "wuxing_radar": {
    "木": 78.5,
    "火": 45.2,
    "土": 32.1,
    "金": 15.6,
    "水": 89.3
  },
  "dominant_wuxing": "水",
  "secondary_wuxing": "木",
  "mingge": "水木润泽",
  "confidence": 0.35
}
```

---

## 模块2：金字招牌检测

### 判定逻辑

```
触发条件（三围同时达标）：
  1. 出场率 >= 5次    (occurrenceCount >= 5)
  2. 稳定性 >= 0.95   (stabilityScore >= 0.95)
  3. 用户精修过        (isUserVerified == true)

等级计算：
  Level 3 (传说): occurrence>=20 且 stability>=0.99
  Level 2 (稀有): occurrence>=10 且 stability>=0.97
  Level 1 (普通): occurrence>=5  且 stability>=0.95
```

### 使用示例

```python
from ehs_algorithm.golden_sign import GoldenSignDetector
from ehs_algorithm.models import Glyph

# 创建字形
glyph = Glyph(
    char="道",
    image=None,
    glyph_id="glyph_001",
    occurrence_count=10,
    stability_score=0.97,
    is_user_verified=True,
)

# 检测
detector = GoldenSignDetector()
result = detector.check(glyph)

print(f"触发: {result.is_triggered}")     # True
print(f"等级: {result.level}")             # 2 (稀有)
print(f"详情: {result.criteria_check}")   # {'occurrence_sufficient': True, ...}
```

### 边界测试覆盖

| 场景 | 出场 | 稳定 | 精修 | 期望结果 |
|------|------|------|------|----------|
| 三围达标 | 5 | 0.95 | true | 触发(L1) |
| 出场不足 | 4 | 0.95 | true | 不触发 |
| 稳定不足 | 5 | 0.94 | true | 不触发 |
| 未精修 | 5 | 0.95 | false | 不触发 |
| 传说级 | 25 | 0.995 | true | 触发(L3) |

### 输出JSON Schema

```json
{
  "is_triggered": true,
  "criteria_check": {
    "occurrence_sufficient": true,
    "stability_sufficient": true,
    "user_verified": true
  },
  "level": 2,
  "glyph_id": "glyph_001"
}
```

---

## 模块3：印刷体过滤

### 模型架构

```
输入: 64×64×4 (RGBA)
  ↓
MobileNetV3-Small (ImageNet预训练)
  - Conv2d + BN + HardSwish
  - Inverted Residual Blocks
  - SE注意力模块
  ↓
Global Average Pooling
  ↓
FC(576→1024) + HardSwish + Dropout
  ↓
FC(1024→1) + Sigmoid
  ↓
输出: 置信度 (0.0 ~ 1.0)
```

### 训练流程

```python
from ehs_algorithm.mock_data import MockDataGenerator
from ehs_algorithm.print_filter import PrintFilterTrainer

# 生成训练数据
generator = MockDataGenerator(seed=42)
hand_images, _ = generator.generate_batch(count=100, handwritten=True)
print_images, _ = generator.generate_batch(count=100, handwritten=False)

# 训练
trainer = PrintFilterTrainer(learning_rate=1e-4)
train_loader, val_loader = trainer.prepare_data(hand_images, print_images)
history = trainer.train(train_loader, val_loader, epochs=10)

# 预测
predictions, confidences = trainer.predict(test_images)
```

### 推理接口

```python
from ehs_algorithm.print_filter import PrintFilterInference
from ehs_algorithm.models import Glyph

# 加载模型
inference = PrintFilterInference(model_path="./output/print_filter_model.pth")

# 单字推理
glyph = Glyph(char="道", image=image_array, glyph_id="g001")
result = inference.classify(glyph, threshold=0.5)

print(f"手写体: {result.is_handwritten}")  # True/False
print(f"置信度: {result.confidence:.4f}")
```

### 输出JSON Schema

```json
{
  "is_handwritten": true,
  "confidence": 0.9234,
  "raw_score": 0.9234,
  "glyph_id": "g001"
}
```

---

## Agent-D 接口规范

### 输入数据格式

```python
# Glyph对象（Agent-D传入）
glyph = Glyph(
    char="道",                    # 汉字字符
    image=np.ndarray(...),        # 字形图像 (H×W×C), uint8
    glyph_id="uuid",              # 唯一ID
    occurrence_count=5,           # 出场次数
    stability_score=0.95,         # 稳定性
    is_user_verified=True,        # 是否精修
    user_id="user_001",           # 用户ID
    timestamp=1699123456.0,       # 时间戳
)
```

### 输出数据格式

三大模块统一返回dataclass对象，可通过 `.to_dict()` 转为JSON：

| 模块 | 返回类型 | 核心字段 |
|------|----------|----------|
| 五行灵根觉醒 | `List[WuxingResult]` | wuxing_radar, dominant_wuxing, mingge |
| 金字招牌检测 | `GoldenSignResult` | is_triggered, level, criteria_check |
| 印刷体过滤 | `PrintFilterResult` | is_handwritten, confidence |

---

## TFLite导出

### 目标规格

| 指标 | 要求 |
|------|------|
| 输入格式 | INT8, (1, 4, 64, 64) |
| 输出格式 | FLOAT32, (1, 128) 或 (1, 1) |
| 模型大小 | < 3.2MB |
| 推理延迟 | < 50ms (麒麟990) |

### 导出命令

```bash
# 任务1：五行特征提取器
python -c "from ehs_algorithm.wuxing_awakening import WuxingAwakening; \
           w = WuxingAwakening(); w.export_tflite('./output/wuxing.tflite')"

# 任务3：印刷体过滤器
python -c "from ehs_algorithm.print_filter import *; \
           export_print_filter_tflite(PrintFilter(), './output/print_filter.tflite')"
```

---

## 项目结构

```
ehs_algorithm/
├── ehs_algorithm/          # 核心算法包
│   ├── __init__.py
│   ├── models.py           # 数据模型与JSON Schema
│   ├── mock_data.py        # Mock数据生成器
│   ├── wuxing_awakening.py # 五行灵根觉醒
│   ├── golden_sign.py      # 金字招牌检测
│   └── print_filter.py     # 印刷体过滤
├── tests/                  # 单元测试
│   ├── test_wuxing.py
│   ├── test_golden_sign.py
│   └── test_print_filter.py
├── main.py                 # 演示入口
├── requirements.txt        # 依赖包
├── setup.py               # 安装配置
└── README.md              # 本文档
```

---

## 版本记录

| 版本 | 日期 | 变更 |
|------|------|------|
| 0.1.0 | 2026-06-07 | MVP初版：三大核心算法模块 |

---

*蚯蚓.手书修仙传 - 让每个人的字迹都能修仙*
