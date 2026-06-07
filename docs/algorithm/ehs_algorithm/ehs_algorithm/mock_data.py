"""
Mock数据生成器
模拟手写单字图片生成，用于算法测试和演示

技术说明：
- 使用PIL生成模拟手写体字形图片
- 模拟不同五行特征：笔画密度/笔压强度/结构方正度/棱角锐度/流畅指数
- 透明背景PNG，默认64×64像素
"""

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from typing import Tuple, Optional, List
import random
import os


class MockDataGenerator:
    """
    Mock手写数据生成器
    
    用途：
    1. 算法开发阶段替代真实数据
    2. 单元测试的确定性输入
    3. CI/CD流水线自动化测试
    """
    
    # 常用汉字表（100个）
    COMMON_CHARS = list("的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会"
                        "可主发年动同工也能下过子说产种面而方后多定行学法所民得经十三之进着等部度家电力里如水化高自二理起小物现实加")
    
    def __init__(self, seed: int = 42, image_size: int = 64):
        """
        初始化生成器
        
        Args:
            seed: 随机种子，保证可复现性
            image_size: 输出图片尺寸（正方形）
        """
        self.seed = seed
        self.image_size = image_size
        self.rng = np.random.RandomState(seed)
        random.seed(seed)
        
        # 尝试加载系统字体用于模拟印刷体
        self.font_paths = self._discover_fonts()
        
    def _discover_fonts(self) -> List[str]:
        """发现系统中文字体路径"""
        candidates = [
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",  # Linux
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",  # Linux
            "/System/Library/Fonts/PingFang.ttc",  # macOS
            "C:\\Windows\\Fonts\\msyh.ttc",  # Windows
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",  # Fallback
        ]
        return [p for p in candidates if os.path.exists(p)]
    
    def _create_rgba_image(self, size: int) -> Tuple[Image.Image, ImageDraw.Draw]:
        """创建透明RGBA画布"""
        img = Image.new('RGBA', (size, size), (255, 255, 255, 0))
        draw = ImageDraw.Draw(img)
        return img, draw
    
    def _add_handwriting_noise(self, img: Image.Image, 
                               stroke_width_var: float = 0.5,
                               wobble_amp: float = 1.2) -> Image.Image:
        """
        添加手写噪声（模拟真实手写的不规则性）
        
        Args:
            img: 输入图像
            stroke_width_var: 笔画粗细变化幅度
            wobble_amp: 笔画抖动幅度
        """
        # 轻微模糊模拟墨水扩散
        img = img.filter(ImageFilter.GaussianBlur(radius=0.3))
        
        # 转换为numpy添加噪声
        arr = np.array(img).astype(np.float32)
        
        # 边缘抖动噪声（模拟手抖）
        noise = np.random.randn(*arr.shape[:2]) * wobble_amp
        arr[:, :, 3] = np.clip(arr[:, :, 3] + noise * 10, 0, 255)
        
        # 灰度噪声（模拟笔迹深浅不均）
        gray_noise = np.random.randn(*arr.shape[:2]) * 15
        for i in range(3):
            arr[:, :, i] = np.clip(arr[:, :, i] + gray_noise, 0, 255)
        
        return Image.fromarray(arr.astype(np.uint8), 'RGBA')
    
    def generate_handwritten_char(self, 
                                   char: Optional[str] = None,
                                   wuxing_bias: Optional[str] = None) -> np.ndarray:
        """
        生成模拟手写单字图片
        
        Args:
            char: 指定字符（随机选择一个常用字）
            wuxing_bias: 五行偏向（"木"/"火"/"土"/"金"/"水"），影响生成风格
        
        Returns:
            np.ndarray: RGBA图像 (image_size × image_size × 4), uint8
        """
        if char is None:
            char = random.choice(self.COMMON_CHARS)
        
        img, draw = self._create_rgba_image(self.image_size)
        
        # 根据五行偏向调整生成参数
        params = self._get_wuxing_params(wuxing_bias)
        
        # 随机旋转角度（模拟手写倾斜）
        rotation = random.uniform(-params['max_rotation'], params['max_rotation'])
        
        # 笔画颜色（墨黑色，带透明度变化）
        ink_alpha = random.randint(180, 240)
        ink_color = (20, 20, 20, ink_alpha)
        
        # 绘制模拟笔画（使用几何形状组合模拟汉字结构）
        self._draw_simulated_strokes(draw, self.image_size, ink_color, params)
        
        # 旋转图像
        img = img.rotate(rotation, expand=False, fillcolor=(255, 255, 255, 0))
        
        # 裁剪回原始尺寸
        if img.size != (self.image_size, self.image_size):
            img = img.crop((
                (img.width - self.image_size) // 2,
                (img.height - self.image_size) // 2,
                (img.width + self.image_size) // 2,
                (img.height + self.image_size) // 2
            ))
        
        # 添加手写噪声
        img = self._add_handwriting_noise(img, 
                                          stroke_width_var=params['width_var'],
                                          wobble_amp=params['wobble_amp'])
        
        return np.array(img)
    
    def _get_wuxing_params(self, wuxing_bias: Optional[str]) -> dict:
        """
        根据五行偏向获取生成参数
        
        五行特征映射：
        - 木：笔画舒展流畅，线条较长，有一定弧度
        - 火：笔压强烈，笔画粗细变化大，锋芒毕露
        - 土：结构方正，横平竖直，稳重厚实
        - 金：棱角分明，折笔锐利，起收笔干脆
        - 水：流畅连贯，圆润顺滑，连带自然
        """
        base = {
            'max_rotation': 8,
            'width_var': 0.5,
            'wobble_amp': 1.0,
            'stroke_count': random.randint(3, 8),
            'corner_sharpness': 0.5,    # 0=圆润, 1=尖锐
            'structure_square': 0.5,     # 0=舒展, 1=方正
            'pressure_intensity': 0.5,   # 0=轻柔, 1=强烈
            'flow_smoothness': 0.5,      # 0=生涩, 1=流畅
        }
        
        if wuxing_bias == "木":
            base.update({
                'max_rotation': 12,
                'wobble_amp': 1.5,
                'stroke_count': random.randint(5, 10),
                'corner_sharpness': 0.2,
                'structure_square': 0.2,
                'pressure_intensity': 0.4,
                'flow_smoothness': 0.8,
            })
        elif wuxing_bias == "火":
            base.update({
                'max_rotation': 15,
                'width_var': 1.5,
                'wobble_amp': 2.0,
                'stroke_count': random.randint(4, 7),
                'corner_sharpness': 0.9,
                'structure_square': 0.3,
                'pressure_intensity': 0.9,
                'flow_smoothness': 0.4,
            })
        elif wuxing_bias == "土":
            base.update({
                'max_rotation': 3,
                'width_var': 0.3,
                'wobble_amp': 0.5,
                'stroke_count': random.randint(3, 6),
                'corner_sharpness': 0.5,
                'structure_square': 0.9,
                'pressure_intensity': 0.7,
                'flow_smoothness': 0.5,
            })
        elif wuxing_bias == "金":
            base.update({
                'max_rotation': 5,
                'width_var': 0.8,
                'wobble_amp': 0.8,
                'stroke_count': random.randint(4, 8),
                'corner_sharpness': 1.0,
                'structure_square': 0.8,
                'pressure_intensity': 0.8,
                'flow_smoothness': 0.3,
            })
        elif wuxing_bias == "水":
            base.update({
                'max_rotation': 10,
                'width_var': 0.4,
                'wobble_amp': 1.2,
                'stroke_count': random.randint(4, 9),
                'corner_sharpness': 0.0,
                'structure_square': 0.1,
                'pressure_intensity': 0.3,
                'flow_smoothness': 1.0,
            })
        
        return base
    
    def _draw_simulated_strokes(self, draw: ImageDraw.Draw, size: int, 
                                color: Tuple[int, ...], params: dict):
        """
        绘制模拟笔画（几何组合模拟汉字）
        
        使用横竖撇捺点等基本笔画的几何近似
        """
        margin = size // 6
        cx, cy = size // 2, size // 2
        stroke_count = params['stroke_count']
        sharpness = params['corner_sharpness']
        
        for _ in range(stroke_count):
            stroke_type = random.choice(['horizontal', 'vertical', 'diagonal', 'dot', 'curve'])
            x1 = random.randint(margin, size - margin)
            y1 = random.randint(margin, size - margin)
            x2 = random.randint(margin, size - margin)
            y2 = random.randint(margin, size - margin)
            
            width = max(1, int(random.uniform(1, 3) * (1 + params['pressure_intensity'])))
            
            if stroke_type == 'horizontal':
                y1 = y2 = random.randint(margin, size - margin)
                draw.line([(x1, y1), (x2, y2)], fill=color, width=width)
            elif stroke_type == 'vertical':
                x1 = x2 = random.randint(margin, size - margin)
                draw.line([(x1, y1), (x2, y2)], fill=color, width=width)
            elif stroke_type == 'diagonal':
                draw.line([(x1, y1), (x2, y2)], fill=color, width=width)
            elif stroke_type == 'dot':
                r = random.randint(1, 3)
                draw.ellipse([(x1-r, y1-r), (x1+r, y1+r)], fill=color)
            elif stroke_type == 'curve':
                # 使用贝塞尔曲线模拟弧度
                cp_x = (x1 + x2) // 2 + random.randint(-10, 10)
                cp_y = (y1 + y2) // 2 + random.randint(-10, 10)
                points = self._bezier_points(x1, y1, cp_x, cp_y, x2, y2)
                for i in range(len(points) - 1):
                    draw.line([points[i], points[i+1]], fill=color, width=width)
    
    def _bezier_points(self, x1, y1, cx, cy, x2, y2, steps=10) -> List[Tuple[float, float]]:
        """生成二次贝塞尔曲线点列"""
        points = []
        for t in range(steps + 1):
            t = t / steps
            x = (1 - t) ** 2 * x1 + 2 * (1 - t) * t * cx + t ** 2 * x2
            y = (1 - t) ** 2 * y1 + 2 * (1 - t) * t * cy + t ** 2 * y2
            points.append((x, y))
        return points
    
    def generate_printed_char(self, char: Optional[str] = None) -> np.ndarray:
        """
        生成模拟印刷体字形图片（用于印刷体过滤的负样本）
        
        特征：
        - 笔画粗细均匀
        - 结构标准方正
        - 无手写噪声
        - 边缘清晰锐利
        
        Returns:
            np.ndarray: RGBA图像 (image_size × image_size × 4), uint8
        """
        if char is None:
            char = random.choice(self.COMMON_CHARS)
        
        img, draw = self._create_rgba_image(self.image_size)
        
        # 印刷体特征：标准黑色，无透明度变化
        color = (0, 0, 0, 255)
        
        # 尝试使用系统字体
        font = None
        if self.font_paths:
            try:
                font_size = int(self.image_size * 0.7)
                font = ImageFont.truetype(self.font_paths[0], font_size)
            except:
                pass
        
        if font is None:
            font = ImageFont.load_default()
        
        # 居中绘制文字
        bbox = draw.textbbox((0, 0), char, font=font)
        text_w = bbox[2] - bbox[0]
        text_h = bbox[3] - bbox[1]
        x = (self.image_size - text_w) // 2
        y = (self.image_size - text_h) // 2 - bbox[1]
        
        draw.text((x, y), char, fill=color, font=font)
        
        # 印刷体无噪声，仅轻微抗锯齿
        arr = np.array(img)
        return arr
    
    def generate_batch(self, 
                       count: int = 100,
                       wuxing_bias: Optional[str] = None,
                       handwritten: bool = True) -> Tuple[np.ndarray, list]:
        """
        批量生成字形图片
        
        Args:
            count: 生成数量
            wuxing_bias: 五行偏向
            handwritten: True=手写体, False=印刷体
        
        Returns:
            images: (count, image_size, image_size, 4) uint8数组
            chars: 字符列表
        """
        images = []
        chars = []
        
        for i in range(count):
            char = self.COMMON_CHARS[i % len(self.COMMON_CHARS)]
            chars.append(char)
            
            if handwritten:
                img = self.generate_handwritten_char(char, wuxing_bias)
            else:
                img = self.generate_printed_char(char)
            
            images.append(img)
        
        return np.stack(images), chars
    
    def save_samples(self, output_dir: str = "./output/mock_samples", n: int = 10):
        """
        保存Mock样本图片到目录（用于人工检查）
        
        Args:
            output_dir: 输出目录
            n: 每种类型保存的样本数
        """
        os.makedirs(output_dir, exist_ok=True)
        
        # 保存各种五行偏向的手写样本
        for wx in ["木", "火", "土", "金", "水", None]:
            label = wx if wx else "random"
            for i in range(n):
                img = self.generate_handwritten_char(wuxing_bias=wx)
                img_pil = Image.fromarray(img, 'RGBA')
                img_pil.save(os.path.join(output_dir, f"hand_{label}_{i}.png"))
        
        # 保存印刷体样本
        for i in range(n):
            img = self.generate_printed_char()
            img_pil = Image.fromarray(img, 'RGBA')
            img_pil.save(os.path.join(output_dir, f"print_{i}.png"))
        
        print(f"已保存 {n * 6} 张Mock样本到 {output_dir}")


# 便捷函数
def generate_100_glyphs(wuxing_bias: Optional[str] = None, seed: int = 42) -> Tuple[np.ndarray, list]:
    """
    生成首批100字Mock数据（五行灵根觉醒用）
    
    Returns:
        images: (100, 64, 64, 4) uint8数组
        chars: 100个字符列表
    """
    gen = MockDataGenerator(seed=seed)
    return gen.generate_batch(count=100, wuxing_bias=wuxing_bias, handwritten=True)
