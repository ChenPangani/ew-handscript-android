"""
蚯蚓.手书修仙传 - 核心算法包
Eruca HandScript: Cultivation Saga - Core Algorithm Package

包含三大核心算法模块：
1. 五行灵根觉醒 (WuxingAwakening) - 首批100字特征提取与五行分类
2. 金字招牌检测 (GoldenSignDetector) - 高稳定性字形触发检测
3. 印刷体过滤 (PrintFilter) - 手写体 vs 印刷体二分类
"""

__version__ = "0.1.0"
__author__ = "算法工程师 (Algorithm Engineer)"

from .models import (
    Glyph,
    WuxingResult,
    GoldenSignResult,
    PrintFilterResult,
    MinggeType,
    WuxingType,
)
from .mock_data import MockDataGenerator
from .wuxing_awakening import WuxingAwakening
from .golden_sign import GoldenSignDetector
from .print_filter import PrintFilter

__all__ = [
    "Glyph",
    "WuxingResult",
    "GoldenSignResult",
    "PrintFilterResult",
    "MinggeType",
    "WuxingType",
    "MockDataGenerator",
    "WuxingAwakening",
    "GoldenSignDetector",
    "PrintFilter",
]
