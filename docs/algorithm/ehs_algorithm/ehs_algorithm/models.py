"""
数据模型与JSON Schema定义
定义Agent-D（消消乐主模块）与算法模块之间的接口契约
"""

import numpy as np
from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import List, Optional, Dict, Any


class WuxingType(str, Enum):
    """五行类型"""
    MU = "木"    # 生长流畅，笔画舒展
    HUO = "火"   # 笔压强烈，锋芒外露
    TU = "土"    # 结构方正，稳重厚实
    JIN = "金"   # 棱角分明，笔锋锐利
    SHUI = "水"  # 流畅连贯，圆润顺滑


class MinggeType(str, Enum):
    """命格类型 - 由五行属性组合决定"""
    MU_HUO_TONG_MING = "木火通明"    # 木+火组合
    HUO_TU_WANG = "火土旺相"         # 火+土组合
    TU_JIN_Sheng = "土金相生"        # 土+金组合
    JIN_SHUI_XIANG = "金水相涵"      # 金+水组合
    SHUI_MU_RUN = "水木润泽"         # 水+木组合
    MU_TU_KE = "木土相克"            # 木+土冲突
    HUO_JIN_KE = "火金相克"          # 火+金冲突
    TU_SHUI_KE = "土水相克"          # 土+水冲突
    JIN_MU_KE = "金木相克"           # 金+木冲突
    SHUI_HUO_JI = "水火既济"         # 水+火特殊
    WU_XING_JUN_HENG = "五行均衡"    # 五行均衡
    WU_XING_PIAN_KU = "五行偏枯"     # 某一五行过强


@dataclass
class Glyph:
    """
    字形对象 (Glyph Block / Glyph Card)
    
    Agent-D传入的单个字形数据结构，包含图像和元数据信息
    """
    # 核心字段
    char: str                          # 汉字字符（如 "道"）
    image: np.ndarray                  # 字形图像 (H×W×C, uint8, 透明背景PNG)
    glyph_id: str                      # 唯一标识符 (UUID)
    
    # 可选元数据（用于金字招牌检测等）
    occurrence_count: int = 0          # 出场次数（该字在文稿中出现次数）
    stability_score: float = 0.0       # 稳定性评分 (0.0 ~ 1.0)
    is_user_verified: bool = False     # 用户是否手动精修过
    user_id: str = ""                 # 用户ID
    timestamp: float = 0.0             # 创建时间戳
    
    # 额外扩展字段
    meta: Dict[str, Any] = field(default_factory=dict)
    
    def __post_init__(self):
        """验证输入数据"""
        if self.image is not None:
            assert self.image.dtype == np.uint8, f"图像必须是uint8类型，当前: {self.image.dtype}"
            assert len(self.image.shape) in [2, 3], f"图像维度必须是2或3，当前: {len(self.image.shape)}"


@dataclass
class WuxingRadar:
    """五行雷达图数据 (5维归一化评分 0-100)"""
    mu: float = 0.0      # 木：笔画流畅舒展度
    huo: float = 0.0     # 火：笔压强烈度
    tu: float = 0.0      # 土：结构方正稳定度
    jin: float = 0.0     # 金：棱角锐利度
    shui: float = 0.0    # 水：连贯圆润度
    
    def to_list(self) -> List[float]:
        return [self.mu, self.huo, self.tu, self.jin, self.shui]
    
    def dominant(self) -> WuxingType:
        """返回主导五行"""
        values = self.to_list()
        labels = [WuxingType.MU, WuxingType.HUO, WuxingType.TU, WuxingType.JIN, WuxingType.SHUI]
        return labels[int(np.argmax(values))]


@dataclass
class WuxingResult:
    """
    五行灵根觉醒结果
    
    Agent-D消费此结果，用于：
    - 展示五行雷达图
    - 确定玩家命格
    - 影响消消乐五行属性分配
    """
    # 核心输出
    wuxing_radar: WuxingRadar          # 五行雷达图五维数据
    dominant_wuxing: WuxingType        # 主导五行
    secondary_wuxing: Optional[WuxingType]  # 次要五行（可选）
    mingge: MinggeType                 # 命格标签
    
    # 中间数据（用于调试和可视化）
    feature_vector: List[float] = field(default_factory=list)  # 128维特征向量
    cluster_center: List[float] = field(default_factory=list)  # 所属聚类中心
    
    # 置信度
    confidence: float = 0.0            # 整体置信度 (0.0 ~ 1.0)
    
    def to_dict(self) -> Dict[str, Any]:
        """转为字典，用于JSON序列化"""
        return {
            "wuxing_radar": {
                "木": round(self.wuxing_radar.mu, 2),
                "火": round(self.wuxing_radar.huo, 2),
                "土": round(self.wuxing_radar.tu, 2),
                "金": round(self.wuxing_radar.jin, 2),
                "水": round(self.wuxing_radar.shui, 2),
            },
            "dominant_wuxing": self.dominant_wuxing.value,
            "secondary_wuxing": self.secondary_wuxing.value if self.secondary_wuxing else None,
            "mingge": self.mingge.value,
            "feature_vector": [round(x, 6) for x in self.feature_vector[:10]] + ["..."],  # 截断展示
            "confidence": round(self.confidence, 4),
        }


@dataclass
class GoldenSignResult:
    """
    金字招牌检测结果
    
    Agent-D消费此结果，用于：
    - 触发冲屏特效（金字招牌全屏动画）
    - 标记高价值字形
    - 炼字炉材料筛选
    """
    # 核心输出
    is_triggered: bool                 # 是否触发金字招牌
    
    # 判定详情（用于调试和展示）
    criteria_check: Dict[str, bool] = field(default_factory=dict)  # 各条件检查详情
    
    # 触发等级
    level: int = 0                     # 1=普通金字, 2=稀有金字, 3=传说金字
    
    # 元数据
    glyph_id: str = ""                # 对应字形ID
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "is_triggered": bool(self.is_triggered),
            "criteria_check": {k: bool(v) for k, v in self.criteria_check.items()},
            "level": int(self.level),
            "glyph_id": str(self.glyph_id) if self.glyph_id else "",
        }


@dataclass
class PrintFilterResult:
    """
    印刷体过滤结果
    
    Agent-D消费此结果，用于：
    - 自动拒绝印刷体（不进入字库）
    - 提示用户重写
    """
    # 核心输出
    is_handwritten: bool               # True=手写体(通过), False=印刷体(拒绝)
    
    # 置信度
    confidence: float = 0.0            # 置信度 (0.0 ~ 1.0)
    
    # 详细评分
    raw_score: float = 0.0             # 模型原始输出
    
    # 元数据
    glyph_id: str = ""                # 对应字形ID
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "is_handwritten": bool(self.is_handwritten),
            "confidence": round(float(self.confidence), 4),
            "raw_score": round(float(self.raw_score), 6),
            "glyph_id": str(self.glyph_id) if self.glyph_id else "",
        }


# JSON Schema 定义（供Agent-D参考）
WXING_RESULT_SCHEMA = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "title": "WuxingResult",
    "description": "五行灵根觉醒结果",
    "type": "object",
    "required": ["wuxing_radar", "dominant_wuxing", "mingge"],
    "properties": {
        "wuxing_radar": {
            "type": "object",
            "required": ["木", "火", "土", "金", "水"],
            "properties": {
                "木": {"type": "number", "minimum": 0, "maximum": 100},
                "火": {"type": "number", "minimum": 0, "maximum": 100},
                "土": {"type": "number", "minimum": 0, "maximum": 100},
                "金": {"type": "number", "minimum": 0, "maximum": 100},
                "水": {"type": "number", "minimum": 0, "maximum": 100},
            }
        },
        "dominant_wuxing": {"type": "string", "enum": ["木", "火", "土", "金", "水"]},
        "secondary_wuxing": {"type": ["string", "null"], "enum": ["木", "火", "土", "金", "水", None]},
        "mingge": {"type": "string"},
        "confidence": {"type": "number", "minimum": 0, "maximum": 1},
    }
}

GOLDEN_SIGN_RESULT_SCHEMA = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "title": "GoldenSignResult",
    "description": "金字招牌检测结果",
    "type": "object",
    "required": ["is_triggered"],
    "properties": {
        "is_triggered": {"type": "boolean"},
        "criteria_check": {"type": "object"},
        "level": {"type": "integer", "minimum": 0, "maximum": 3},
        "glyph_id": {"type": "string"},
    }
}

PRINT_FILTER_RESULT_SCHEMA = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "title": "PrintFilterResult",
    "description": "印刷体过滤结果",
    "type": "object",
    "required": ["is_handwritten"],
    "properties": {
        "is_handwritten": {"type": "boolean"},
        "confidence": {"type": "number", "minimum": 0, "maximum": 1},
        "raw_score": {"type": "number"},
        "glyph_id": {"type": "string"},
    }
}
