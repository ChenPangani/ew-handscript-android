#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
蚯蚓.手书修仙传 (EHS) - 本地TTF字体生成流水线
Local Font Generation Pipeline

功能：将已确认的单字PNG通过轮廓提取后，打包为TTF字体文件
作者：Agent-B（后端工程师）
版本：1.0.0
日期：2026-06-07

技术栈：
  - OpenCV: PNG位图轮廓提取
  - fontTools (FontBuilder): TTF字体构建
  - Pillow: 图像处理和Mock数据生成

接口（Agent-D调用）：
  >>> pipeline = FontPipeline(input_dir, output_dir)
  >>> ttf_path, meta_path = pipeline.run("MyHandwriting")
"""

import os
import sys
import json
import random
import argparse
import tempfile
import unittest
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont


# ============ 常量配置 ============
UPM = 1000               # Units Per Em（字体坐标系单位）
ASCENT = 800             # 上升高度
DESCENT = -200           # 下降深度
LINE_GAP = 100           # 行间距
IMG_SIZE = 512           # Mock图像尺寸


# ============ 数据结构 ============

@dataclass
class GlyphMetadata:
    """单字元数据 - 对应元数据JSON中的每个字形记录"""
    unicode: str      # Unicode码点，如 "U+4E00"
    char: str         # 实际字符，如 "一"
    wuxing: str       # 五行属性：金/木/水/火/土
    version: str      # 版本标签，如 "v1.0"
    filename: str     # 源PNG文件名，如 "U+4E00_一.png"
    width: int = 512  # 图像宽度
    height: int = 512 # 图像高度


@dataclass
class FontPipelineResult:
    """流水线输出结果 - Agent-D接口返回格式"""
    ttf_path: str     # TTF字体文件绝对路径
    meta_path: str    # 字体元数据JSON绝对路径
    font_name: str    # 字体名称
    glyph_count: int  # 包含的字形数量
    version: str      # 字体版本


# ============ Mock数据生成器 ============

class MockDataGenerator:
    """
    Mock数据生成器
    
    生成10个测试汉字PNG（透明背景）和对应的metadata.json，
    用于在没有真实字库数据时测试流水线。
    
    五行覆盖：
      水：一、六    火：二、七    木：三、八    金：四、九    土：五、十
    """

    # (unicode, 字符, 五行)
    TEST_CHARS: List[Tuple[str, str, str]] = [
        ("U+4E00", "一", "水"),
        ("U+4E8C", "二", "火"),
        ("U+4E09", "三", "木"),
        ("U+56DB", "四", "金"),
        ("U+4E94", "五", "土"),
        ("U+516D", "六", "水"),
        ("U+4E03", "七", "火"),
        ("U+516B", "八", "木"),
        ("U+4E5D", "九", "金"),
        ("U+5341", "十", "土"),
    ]

    def __init__(self, output_dir: Path, img_size: int = IMG_SIZE):
        self.output_dir = output_dir
        self.img_size = img_size
        self.glyph_dir = output_dir / "glyphs"

    def generate(self) -> Tuple[List[Path], Path]:
        """
        生成Mock数据
        
        返回:
            (png路径列表, 元数据JSON路径)
        """
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.glyph_dir.mkdir(parents=True, exist_ok=True)

        # 加载系统中文字体
        font = self._load_font()

        metadata = {
            "project": "蚯蚓手书修仙传 (EHS)",
            "version": "1.0.0",
            "generated_at": "2026-06-07",
            "generator": "MockDataGenerator",
            "glyphs": [],
        }
        png_paths: List[Path] = []

        for unicode_str, char, wuxing in self.TEST_CHARS:
            png_path = self._draw_glyph(unicode_str, char, wuxing, font)
            png_paths.append(png_path)

            metadata["glyphs"].append({
                "unicode": unicode_str,
                "char": char,
                "wuxing": wuxing,
                "version": "v1.0",
                "filename": png_path.name,
                "width": self.img_size,
                "height": self.img_size,
            })

        # 保存元数据JSON
        meta_path = self.output_dir / "metadata.json"
        with open(meta_path, 'w', encoding='utf-8') as f:
            json.dump(metadata, f, ensure_ascii=False, indent=2)

        print(f"[Mock] 生成完成: {len(png_paths)}个PNG + metadata.json")
        return png_paths, meta_path

    def _load_font(self) -> ImageFont.FreeTypeFont:
        """加载系统中文字体"""
        font_candidates = [
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
            "/System/Library/Fonts/PingFang.ttc",  # macOS
            "C:/Windows/Fonts/msyh.ttc",           # Windows
        ]
        for fp in font_candidates:
            if Path(fp).exists():
                try:
                    return ImageFont.truetype(fp, int(self.img_size * 0.7))
                except Exception:
                    continue
        print("[警告] 未找到中文字体，使用默认字体")
        return ImageFont.load_default()

    def _draw_glyph(self, unicode_str: str, char: str, wuxing: str,
                    font: ImageFont.FreeTypeFont) -> Path:
        """绘制单个汉字为PNG透明背景图像"""
        # 创建透明背景
        img = Image.new('RGBA', (self.img_size, self.img_size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)

        # 获取文字边界框
        bbox = draw.textbbox((0, 0), char, font=font)
        text_w = bbox[2] - bbox[0]
        text_h = bbox[3] - bbox[1]

        # 居中 + 模拟手写随机偏移
        x = (self.img_size - text_w) // 2 - bbox[0]
        y = (self.img_size - text_h) // 2 - bbox[1]
        jitter_x = random.randint(-10, 10)
        jitter_y = random.randint(-10, 10)

        # 根据五行属性调整墨色（模拟不同"灵力"效果）
        ink_colors = {
            "金": (30, 25, 20, 255),   # 深褐
            "木": (20, 40, 20, 255),   # 墨绿
            "水": (20, 25, 40, 255),   # 墨蓝
            "火": (45, 20, 15, 255),   # 暗红
            "土": (40, 35, 25, 255),   # 土黄
        }
        ink = ink_colors.get(wuxing, (20, 20, 20, 255))

        draw.text((x + jitter_x, y + jitter_y), char, font=font, fill=ink)

        # 保存
        filename = f"{unicode_str}_{char}.png"
        png_path = self.glyph_dir / filename
        img.save(png_path, "PNG")
        return png_path


# ============ 轮廓提取器 ============

class GlyphContourExtractor:
    """
    字形轮廓提取器
    
    使用OpenCV从透明背景PNG中提取字形轮廓点，
    转换为字体坐标系（原点在左下，Y轴向上）。
    
    替代Potrace矢量化：纯Python实现，无需外部二进制依赖
    """

    def __init__(self, upm: int = UPM):
        self.upm = upm

    def extract(self, png_path: Path) -> Tuple[List[np.ndarray], int, int]:
        """
        从PNG提取字形轮廓
        
        参数:
            png_path: 单字PNG路径（透明背景）
        
        返回:
            (contours列表, glyph_width, glyph_height)
            contours: 每个元素是 Nx2 的numpy数组，包含轮廓点坐标
                      坐标系：字体坐标系（左下原点，Y轴向上）
        """
        # 读取RGBA图像
        img = Image.open(png_path).convert('RGBA')
        arr = np.array(img)

        # 提取Alpha通道作为掩码
        alpha = arr[:, :, 3]

        # 二值化：Alpha > 128 视为有墨水
        _, binary = cv2.threshold(alpha, 128, 255, cv2.THRESH_BINARY)

        # 查找轮廓（支持内部空洞）
        contours, hierarchy = cv2.findContours(
            binary, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE
        )

        if len(contours) == 0:
            return [], self.upm, self.upm

        # 图像尺寸
        img_h, img_w = binary.shape

        # 计算所有点的边界框，用于归一化
        all_pts = np.vstack([c.reshape(-1, 2) for c in contours])
        min_x, min_y = all_pts.min(axis=0)
        max_x, max_y = all_pts.max(axis=0)

        content_w = max(1, max_x - min_x)  # 避免除零
        content_h = max(1, max_y - min_y)

        # 计算缩放因子（留10%边距）
        margin = self.upm * 0.1
        scale = (self.upm - 2 * margin) / max(content_w, content_h)

        # 计算偏移量（居中）
        offset_x = (self.upm - content_w * scale) / 2 - min_x * scale
        offset_y = (self.upm - content_h * scale) / 2 - min_y * scale

        # 转换轮廓到字体坐标系
        transformed = []
        for contour in contours:
            pts = contour.reshape(-1, 2).astype(np.float64)

            # 缩放 + 平移
            pts[:, 0] = pts[:, 0] * scale + offset_x
            pts[:, 1] = self.upm - (pts[:, 1] * scale + offset_y)  # Y轴翻转

            # 多边形近似（减少点数，epsilon=1.0字体单位）
            epsilon = 1.0
            approx = cv2.approxPolyDP(pts.astype(np.float32), epsilon, True)

            if len(approx) >= 3:
                transformed.append(approx.reshape(-1, 2))

        return transformed, self.upm, self.upm


# ============ TTF字体构建器 ============

class TTFFontBuilder:
    """
    TTF字体构建器
    
    使用fontTools FontBuilder将提取的轮廓打包为标准TTF字体文件。
    
    生成的字体包含：
      - glyf表：TrueType轮廓字形
      - cmap表：Unicode到字形的映射
      - head/hhea/maxp/OS/2/post/name表：标准字体元数据
    """

    def __init__(self, upm: int = UPM, ascent: int = ASCENT, descent: int = DESCENT):
        self.upm = upm
        self.ascent = ascent
        self.descent = descent

    def build(self, glyph_data_list: List[Tuple[int, List[np.ndarray], str]],
              font_name: str, output_path: Path) -> Path:
        """
        构建TTF字体
        
        参数:
            glyph_data_list: [(unicode_int, contours, char_name), ...]
            font_name: 字体家族名称
            output_path: 输出TTF路径
        
        返回:
            输出TTF文件路径
        """
        fb = FontBuilder(self.upm, isTTF=True)

        # ---- 字形数据 ----
        glyphs = {}
        glyphOrder = ['.notdef']
        cmap = {}

        # .notdef：矩形占位符
        pen = TTGlyphPen(None)
        pen.moveTo((50, 50))
        pen.lineTo((950, 50))
        pen.lineTo((950, 950))
        pen.lineTo((50, 950))
        pen.closePath()
        glyphs['.notdef'] = pen.glyph()

        # 用户字形
        for unicode_int, contours, char_name in glyph_data_list:
            glyph_name = f"uni{unicode_int:04X}"
            glyphOrder.append(glyph_name)

            pen = TTGlyphPen(None)
            has_contour = False

            for contour_pts in contours:
                if len(contour_pts) < 3:
                    continue
                pen.moveTo((float(contour_pts[0][0]), float(contour_pts[0][1])))
                for pt in contour_pts[1:]:
                    pen.lineTo((float(pt[0]), float(pt[1])))
                pen.closePath()
                has_contour = True

            if not has_contour:
                # 空字形退化为矩形
                pen.moveTo((50, 50))
                pen.lineTo((950, 50))
                pen.lineTo((950, 950))
                pen.lineTo((50, 950))
                pen.closePath()

            glyphs[glyph_name] = pen.glyph()
            cmap[unicode_int] = glyph_name

        # 水平度量（全角宽度）
        hmetrics = {name: (self.upm, 0) for name in glyphOrder}

        # ---- 设置FontBuilder表 ----
        fb.setupGlyphOrder(glyphOrder)
        fb.setupGlyf(glyphs)
        fb.setupHorizontalMetrics(hmetrics)
        fb.setupCharacterMap(cmap)

        # name表
        fb.setupNameTable({
            'familyName': font_name,
            'styleName': 'Regular',
            'fullName': f"{font_name} Regular",
            'psName': f"{font_name.replace(' ', '')}-Regular",
            'version': "Version 1.0",
            'copyright': "蚯蚓手书修仙传 (EHS Project)",
            'description': "本地手写体字体生成流水线输出",
            'vendorURL': "https://github.com/ew-handscript-android",
        })

        # OS/2表
        fb.setupOS2(
            version=4,
            sTypoAscender=self.ascent,
            sTypoDescender=self.descent,
            sTypoLineGap=LINE_GAP,
            usWinAscent=self.ascent,
            usWinDescent=-self.descent,
            ulUnicodeRange1=0x80000000,   # CJK基本区块
            ulCodePageRange1=0x00020000,  # 中文代码页
            achVendID="EHS ",
        )

        fb.setupPost(italicAngle=0, underlinePosition=-100, underlineThickness=50)
        fb.setupHead(fontRevision=1.0, flags=0x000B)
        fb.setupHorizontalHeader(ascent=self.ascent, descent=self.descent, lineGap=LINE_GAP)

        fb.save(str(output_path))
        return output_path


# ============ 元数据管理器 ============

class FontMetadataManager:
    """
    字体元数据管理器
    
    负责：
      1. 读取字库metadata.json
      2. 生成字体级元数据JSON（字符映射表、版本信息、五行统计）
      3. （可选）将五行属性注入TTF name表自定义记录
    """

    def __init__(self, upm: int = UPM):
        self.upm = upm

    def load_glyph_metadata(self, meta_path: Path) -> List[GlyphMetadata]:
        """读取字库metadata.json，返回GlyphMetadata列表"""
        with open(meta_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        return [GlyphMetadata(**g) for g in data.get("glyphs", [])]

    def build_font_metadata(self, glyphs: List[GlyphMetadata],
                           font_name: str,
                           version: str = "1.0.0") -> Dict:
        """
        构建字体级元数据字典
        
        输出格式（Agent-D接口）：
        {
          "font_name": "MyHandwriting",
          "version": "1.0.0",
          "generated_at": "2026-06-07T12:00:00",
          "units_per_em": 1000,
          "glyph_count": 10,
          "glyphs": [
            {"unicode": "U+4E00", "char": "一", "wuxing": "水", "glyph_id": 1, ...}
          ],
          "wuxing_stats": {"金": 2, "木": 2, "水": 2, "火": 2, "土": 2}
        }
        """
        from datetime import datetime

        # 五行统计
        wuxing_stats: Dict[str, int] = {}
        for g in glyphs:
            wuxing_stats[g.wuxing] = wuxing_stats.get(g.wuxing, 0) + 1

        # 字形映射表（glyph_id从1开始，0是.notdef）
        glyph_table = []
        for idx, g in enumerate(glyphs, start=1):
            glyph_table.append({
                "glyph_id": idx,
                "unicode": g.unicode,
                "char": g.char,
                "wuxing": g.wuxing,
                "version": g.version,
                "source_file": g.filename,
            })

        return {
            "font_name": font_name,
            "version": version,
            "generated_at": datetime.now().isoformat(),
            "units_per_em": self.upm,
            "glyph_count": len(glyphs),
            "glyphs": glyph_table,
            "wuxing_stats": wuxing_stats,
            "pipeline_version": "1.0.0",
        }

    def save(self, metadata: Dict, output_path: Path) -> Path:
        """保存元数据JSON到文件"""
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(metadata, f, ensure_ascii=False, indent=2)
        return output_path


# ============ 主流水线（Agent-D接口） ============

class FontPipeline:
    """
    字体生成主流水线
    
    整合：轮廓提取 -> TTF构建 -> 元数据生成
    
    使用方式：
      # 方式1：真实数据
      pipeline = FontPipeline(Path("./input"), Path("./output"))
      result = pipeline.run("MyHandwriting")
      print(result.ttf_path, result.meta_path)

      # 方式2：Mock数据快速测试
      result = FontPipeline.run_with_mock(Path("./output"), "TestFont")
    """

    def __init__(self, input_dir: Path, output_dir: Path):
        """
        参数:
            input_dir: 输入目录，包含 glyphs/ 子目录和 metadata.json
            output_dir: 输出目录
        """
        self.input_dir = input_dir
        self.output_dir = output_dir
        self.glyph_dir = input_dir / "glyphs"
        self.meta_path = input_dir / "metadata.json"

        # 子模块
        self.extractor = GlyphContourExtractor()
        self.builder = TTFFontBuilder()
        self.meta_mgr = FontMetadataManager()

    def run(self, font_name: str = "MyHandwriting",
            version: str = "1.0.0") -> FontPipelineResult:
        """
        执行完整流水线
        
        参数:
            font_name: 输出字体名称
            version: 字体版本号
        
        返回:
            FontPipelineResult: 包含TTF路径和元数据JSON路径
        """
        print(f"\n{'='*50}")
        print(f"[EHS] 字体生成流水线启动")
        print(f"{'='*50}")
        print(f"输入目录: {self.input_dir}")
        print(f"输出目录: {self.output_dir}")
        print(f"字体名称: {font_name}")

        self.output_dir.mkdir(parents=True, exist_ok=True)

        # ---- 步骤1: 读取元数据 ----
        print("\n[步骤1/4] 读取字库元数据...")
        if not self.meta_path.exists():
            raise FileNotFoundError(f"元数据文件不存在: {self.meta_path}")
        glyphs_meta = self.meta_mgr.load_glyph_metadata(self.meta_path)
        print(f"  加载 {len(glyphs_meta)} 个字形元数据")

        # ---- 步骤2: 轮廓提取 ----
        print("\n[步骤2/4] 提取字形轮廓...")
        glyph_data = []
        for meta in glyphs_meta:
            png_path = self.glyph_dir / meta.filename
            if not png_path.exists():
                print(f"  [跳过] 文件不存在: {png_path}")
                continue

            contours, _, _ = self.extractor.extract(png_path)
            u = int(meta.unicode[2:], 16)
            glyph_data.append((u, contours, meta.char))
            print(f"  ✓ {meta.char} (U+{u:04X}, {meta.wuxing}) - {len(contours)}个轮廓")

        # ---- 步骤3: 构建TTF ----
        print(f"\n[步骤3/4] 构建TTF字体...")
        ttf_path = self.output_dir / f"{font_name}.ttf"
        self.builder.build(glyph_data, font_name, ttf_path)
        print(f"  ✓ TTF: {ttf_path}")

        # ---- 步骤4: 生成字体元数据JSON ----
        print(f"\n[步骤4/4] 生成字体元数据...")
        font_meta = self.meta_mgr.build_font_metadata(
            glyphs_meta, font_name, version
        )
        meta_output = self.output_dir / f"{font_name}_metadata.json"
        self.meta_mgr.save(font_meta, meta_output)
        print(f"  ✓ JSON: {meta_output}")

        # ---- 完成 ----
        result = FontPipelineResult(
            ttf_path=str(ttf_path.absolute()),
            meta_path=str(meta_output.absolute()),
            font_name=font_name,
            glyph_count=len(glyph_data),
            version=version,
        )

        print(f"\n{'='*50}")
        print(f"[EHS] 流水线完成!")
        print(f"  TTF文件: {result.ttf_path}")
        print(f"  元数据: {result.meta_path}")
        print(f"  字形数: {result.glyph_count}")
        print(f"{'='*50}")

        return result

    @classmethod
    def run_with_mock(cls, output_dir: Path, font_name: str = "TestFont",
                      version: str = "1.0.0") -> FontPipelineResult:
        """
        使用Mock数据运行完整流水线（快速测试入口）
        
        参数:
            output_dir: 输出目录
            font_name: 字体名称
            version: 版本号
        
        返回:
            FontPipelineResult
        """
        # 创建临时输入目录
        with tempfile.TemporaryDirectory() as tmpdir:
            input_dir = Path(tmpdir) / "input"

            # 生成Mock数据
            mock_gen = MockDataGenerator(input_dir)
            mock_gen.generate()

            # 运行流水线
            pipeline = cls(input_dir, output_dir)
            return pipeline.run(font_name, version)


# ============ 命令行入口 ============

def main():
    """命令行入口"""
    parser = argparse.ArgumentParser(
        description="蚯蚓手书修仙传 - 本地TTF字体生成流水线"
    )
    parser.add_argument("-i", "--input", type=str, default="",
                        help="输入目录（包含glyphs/和metadata.json）")
    parser.add_argument("-o", "--output", type=str, default="./output",
                        help="输出目录（默认: ./output）")
    parser.add_argument("-n", "--name", type=str, default="MyHandwriting",
                        help="字体名称（默认: MyHandwriting）")
    parser.add_argument("-v", "--version", type=str, default="1.0.0",
                        help="字体版本（默认: 1.0.0）")
    parser.add_argument("--mock", action="store_true",
                        help="使用Mock数据运行（测试模式）")

    args = parser.parse_args()

    if args.mock:
        # Mock模式
        result = FontPipeline.run_with_mock(
            Path(args.output), args.name, args.version
        )
    else:
        # 真实数据模式
        if not args.input:
            parser.print_help()
            sys.exit(1)
        pipeline = FontPipeline(Path(args.input), Path(args.output))
        result = pipeline.run(args.name, args.version)

    # 输出JSON结果（供Agent-D解析）
    print("\n" + json.dumps({
        "ttf_path": result.ttf_path,
        "meta_path": result.meta_path,
        "font_name": result.font_name,
        "glyph_count": result.glyph_count,
        "version": result.version,
    }, ensure_ascii=False))


# ============ 单元测试 ============

class TestFontPipeline(unittest.TestCase):
    """
    字体流水线单元测试
    
    测试覆盖：
      1. Mock数据生成（10个PNG + metadata.json）
      2. TTF文件可读性和完整性
      3. 字符映射（cmap表）正确性
      4. 元数据JSON完整性
      5. 轮廓提取非空性
    """

    @classmethod
    def setUpClass(cls):
        """测试前置：创建临时目录和Mock数据"""
        cls.temp_dir = Path(tempfile.mkdtemp())
        cls.input_dir = cls.temp_dir / "input"
        cls.output_dir = cls.temp_dir / "output"

        # 生成Mock数据
        mock_gen = MockDataGenerator(cls.input_dir)
        cls.mock_pngs, cls.mock_meta = mock_gen.generate()

    @classmethod
    def tearDownClass(cls):
        """测试后置：清理临时目录"""
        import shutil
        shutil.rmtree(cls.temp_dir, ignore_errors=True)

    # ---- 测试用例 ----

    def test_01_mock_data_generated(self):
        """TC01: Mock数据生成 - 应产生10个PNG和1个JSON"""
        self.assertEqual(len(self.mock_pngs), 10)
        self.assertTrue(self.mock_meta.exists())

        with open(self.mock_meta, 'r', encoding='utf-8') as f:
            data = json.load(f)
        self.assertEqual(len(data['glyphs']), 10)

        # 验证五行全覆盖
        wuxing_set = {g['wuxing'] for g in data['glyphs']}
        self.assertEqual(wuxing_set, {'金', '木', '水', '火', '土'})

    def test_02_contour_extraction(self):
        """TC02: 轮廓提取 - 每个PNG应提取到至少1个轮廓"""
        extractor = GlyphContourExtractor()

        for png_path in self.mock_pngs[:3]:  # 抽查3个
            contours, w, h = extractor.extract(png_path)
            self.assertGreater(len(contours), 0,
                               f"{png_path.name} 未提取到轮廓")
            for c in contours:
                self.assertGreaterEqual(len(c), 3,
                                        "轮廓至少应有3个点")

    def test_03_ttf_build_and_read(self):
        """TC03: TTF构建与读取 - 文件应可被fontTools正确解析"""
        # 执行流水线
        result = FontPipeline.run_with_mock(self.output_dir, "UnitTestFont")

        # 验证文件存在
        ttf_path = Path(result.ttf_path)
        self.assertTrue(ttf_path.exists(), "TTF文件应存在")
        self.assertGreater(ttf_path.stat().st_size, 1000,
                           "TTF文件应大于1KB")

        # 验证TTF可读
        font = TTFont(str(ttf_path))

        # 基本表检查
        required_tables = {'cmap', 'glyf', 'head', 'hhea', 'hmtx',
                           'loca', 'maxp', 'name', 'OS/2', 'post'}
        self.assertTrue(required_tables.issubset(set(font.keys())),
                        "TTF应包含所有必需表")

        font.close()

    def test_04_cmap_correctness(self):
        """TC04: 字符映射正确性 - cmap应包含所有10个汉字"""
        result = FontPipeline.run_with_mock(self.output_dir, "CmapTestFont")

        font = TTFont(str(result.ttf_path))
        cmap = font['cmap']

        # 期望的Unicode映射
        expected_chars = {
            0x4E00: '一', 0x4E8C: '二', 0x4E09: '三',
            0x56DB: '四', 0x4E94: '五', 0x516D: '六',
            0x4E03: '七', 0x516B: '八', 0x4E5D: '九',
            0x5341: '十',
        }

        for table in cmap.tables:
            for code, name in table.cmap.items():
                if code in expected_chars:
                    expected_name = f"uni{code:04X}"
                    self.assertEqual(name, expected_name,
                                     f"U+{code:04X} 应映射到 {expected_name}")

        font.close()

    def test_05_font_metadata_json(self):
        """TC05: 字体元数据JSON - 应包含完整字形映射和五行统计"""
        result = FontPipeline.run_with_mock(self.output_dir, "MetaTestFont")

        meta_path = Path(result.meta_path)
        self.assertTrue(meta_path.exists())

        with open(meta_path, 'r', encoding='utf-8') as f:
            meta = json.load(f)

        # 基本字段
        self.assertEqual(meta['font_name'], 'MetaTestFont')
        self.assertEqual(meta['version'], '1.0.0')
        self.assertEqual(meta['glyph_count'], 10)
        self.assertEqual(meta['units_per_em'], 1000)

        # 字形表
        self.assertEqual(len(meta['glyphs']), 10)
        for g in meta['glyphs']:
            self.assertIn('glyph_id', g)
            self.assertIn('unicode', g)
            self.assertIn('char', g)
            self.assertIn('wuxing', g)

        # 五行统计
        self.assertIn('wuxing_stats', meta)
        stats = meta['wuxing_stats']
        self.assertEqual(sum(stats.values()), 10)

    def test_06_glyph_count_matches(self):
        """TC06: 字形数量一致性 - TTF内字形数应与元数据一致"""
        result = FontPipeline.run_with_mock(self.output_dir, "CountTestFont")

        font = TTFont(str(result.ttf_path))
        glyf = font['glyf']

        # .notdef + 10个字形 = 11
        self.assertEqual(len(glyf.glyphOrder), 11)
        self.assertEqual(result.glyph_count, 10)

        font.close()

    def test_07_glyph_non_empty(self):
        """TC07: 字形非空性 - 汉字字形应有实际轮廓数据"""
        result = FontPipeline.run_with_mock(self.output_dir, "NonEmptyFont")

        font = TTFont(str(result.ttf_path))
        glyf = font['glyf']

        # 检查"一"字形（uni4E00）
        glyph = glyf['uni4E00']
        self.assertIsNotNone(glyph)
        self.assertNotEqual(glyph.numberOfContours, 0,
                            "uni4E00应有轮廓数据")

        font.close()


# ============ 测试入口 ============

def run_tests():
    """运行单元测试"""
    loader = unittest.TestLoader()
    suite = loader.loadTestsFromTestCase(TestFontPipeline)
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    return result.wasSuccessful()


if __name__ == "__main__":
    # 如果直接运行脚本且不带参数，执行Mock模式
    if len(sys.argv) == 1:
        print("=" * 60)
        print("蚯蚓.手书修仙传 - 本地TTF字体生成流水线")
        print("=" * 60)
        print("\n默认执行Mock模式 + 单元测试...\n")

        # 运行流水线
        output = Path("/mnt/agents/output/font_pipeline/output")
        result = FontPipeline.run_with_mock(output, "MyHandwriting")

        print("\n" + "=" * 60)
        print("Agent-D 接口输出:")
        print("=" * 60)
        print(f"TTF路径: {result.ttf_path}")
        print(f"JSON路径: {result.meta_path}")

        # 运行单元测试
        print("\n" + "=" * 60)
        print("运行单元测试...")
        print("=" * 60)
        success = run_tests()

        sys.exit(0 if success else 1)
    else:
        main()
