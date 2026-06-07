# 蚯蚓.手书修仙传 (EHS) - 本地TTF字体生成流水线

## 项目简介

个人笔迹数字孪生修仙游戏的字体生成组件。将已确认的单字PNG图像通过轮廓提取，打包为标准TTF字体文件。

**当前实现**: MVP版本（基础字形打包，暂不支持多版本随机替换和字距调整）

## 技术栈

| 组件 | 用途 | 版本 |
|------|------|------|
| OpenCV | PNG轮廓提取（替代Potrace矢量化） | 4.12+ |
| fontTools | TTF字体构建 | 4.58+ |
| Pillow | 图像处理 / Mock数据生成 | 11.2+ |

## 项目结构

```
font_pipeline/
  src/
    font_pipeline.py      # 主脚本（完整流水线 + 单元测试）
  tests/
    （集成在font_pipeline.py中，通过unittest运行）
  output/
    MyHandwriting.ttf           # 生成的TTF字体文件
    MyHandwriting_metadata.json # 字体元数据JSON
  README.md               # 本文档
```

## 快速开始

### 方式1：Mock模式（快速测试，无需输入数据）

```bash
cd src
python3 font_pipeline.py
```

自动执行：
1. 生成10个测试汉字PNG（透明背景）
2. 提取字形轮廓
3. 构建TTF字体
4. 生成元数据JSON
5. 运行7项单元测试

### 方式2：真实数据模式

```bash
# 准备输入目录结构：
# input/
#   glyphs/               # 单字PNG文件（透明背景）
#     U+4E00_一.png
#     U+4E8C_二.png
#     ...
#   metadata.json         # 字库元数据

python3 font_pipeline.py -i /path/to/input -o /path/to/output -n "MyFont"
```

### 命令行参数

```
选项:
  -i, --input     输入目录（包含 glyphs/ 子目录和 metadata.json）
  -o, --output    输出目录（默认: ./output）
  -n, --name      字体名称（默认: MyHandwriting）
  -v, --version   字体版本（默认: 1.0.0）
  --mock          使用Mock数据运行测试模式
```

## 输入格式

### 单字PNG文件

- 格式：PNG，RGBA透明背景
- 命名：`{Unicode}_{字符}.png`
- 示例：`U+4E00_一.png`, `U+4E8C_二.png`
- 图像中Alpha通道 > 128 的像素视为有效字形区域

### 元数据JSON

```json
{
  "glyphs": [
    {
      "unicode": "U+4E00",
      "char": "一",
      "wuxing": "水",
      "version": "v1.0",
      "filename": "U+4E00_一.png"
    }
  ]
}
```

## 输出格式

### TTF字体文件

- 标准TrueType格式（glyf表）
- UPM: 1000
- 包含12个标准表：cmap, glyf, head, hhea, hmtx, loca, maxp, name, OS/2, post, GlyphOrder
- 字形ID分配：0=.notdef, 1+ = 汉字（按metadata.json顺序）

### 字体元数据JSON（Agent-D接口）

```json
{
  "font_name": "MyHandwriting",
  "version": "1.0.0",
  "generated_at": "2026-06-07T12:00:00",
  "units_per_em": 1000,
  "glyph_count": 10,
  "glyphs": [
    {"glyph_id": 1, "unicode": "U+4E00", "char": "一", "wuxing": "水", "version": "v1.0", "source_file": "U+4E00_一.png"}
  ],
  "wuxing_stats": {"金": 2, "木": 2, "水": 2, "火": 2, "土": 2},
  "pipeline_version": "1.0.0"
}
```

## Agent-D 调用接口

```python
from font_pipeline import FontPipeline, FontPipelineResult

# 真实数据模式
pipeline = FontPipeline(Path("./input"), Path("./output"))
result: FontPipelineResult = pipeline.run("MyHandwriting")

print(result.ttf_path)      # /absolute/path/to/MyHandwriting.ttf
print(result.meta_path)     # /absolute/path/to/MyHandwriting_metadata.json
print(result.glyph_count)   # 10

# Mock快速测试模式
result = FontPipeline.run_with_mock(Path("./output"), "TestFont")
```

## 单元测试

7项测试覆盖完整流水线：

| 编号 | 测试内容 | 说明 |
|------|---------|------|
| TC01 | Mock数据生成 | 10个PNG + JSON，五行全覆盖 |
| TC02 | 轮廓提取 | 每个PNG至少提取1个有效轮廓 |
| TC03 | TTF构建与读取 | fontTools可正确解析，包含所有必需表 |
| TC04 | 字符映射正确性 | cmap表包含全部10个Unicode映射 |
| TC05 | 字体元数据JSON | 完整字形表 + 五行统计 |
| TC06 | 字形数量一致性 | TTF内字形数与元数据一致 |
| TC07 | 字形非空性 | 汉字字形包含实际轮廓数据 |

运行测试：
```bash
python3 -c "from font_pipeline import run_tests; run_tests()"
```

## 流水线处理流程

```
输入: 字库目录
  |
  v
[步骤1] 读取 metadata.json  --> GlyphMetadata[]
  |
  v
[步骤2] 轮廓提取 (OpenCV)
  - PNG → Alpha通道 → 二值化
  - findContours → 多边形近似
  - 坐标转换: 图像坐标系 → 字体坐标系(UPM=1000)
  |
  v
[步骤3] TTF构建 (fontTools FontBuilder)
  - 创建 glyf 表（TrueType轮廓）
  - 创建 cmap 表（Unicode映射）
  - 设置 head/hhea/OS/2/name/post/maxp 表
  |
  v
[步骤4] 元数据生成
  - 字形映射表（glyph_id → Unicode/五行/版本）
  - 五行统计
  - 输出 JSON
  |
  v
输出: MyHandwriting.ttf + MyHandwriting_metadata.json
```

## 设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 矢量化方案 | OpenCV轮廓提取 | 纯Python实现，无需外部Potrace二进制 |
| 字体表格式 | glyf（TrueType轮廓） | 标准格式，兼容性最好 |
| UPM | 1000 | 行业惯例，整数运算友好 |
| 轮廓近似 | approxPolyDP(ε=1.0) | 平衡精度与文件大小 |
| 字距调整 | 暂不实现 | MVP简化 |
| 多版本替换 | 暂不实现 | MVP简化 |

## 依赖安装

```bash
pip install opencv-python-headless fonttools pillow
```

## 版本历史

- v1.0.0 (2026-06-07): MVP版本，基础字形打包，10字测试通过
