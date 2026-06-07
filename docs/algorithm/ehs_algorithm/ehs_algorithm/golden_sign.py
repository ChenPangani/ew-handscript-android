"""
金字招牌检测算法模块 (Golden Sign Detector)

核心功能：
- 检测字形是否满足"金字招牌"条件
- 触发条件（三围达标）：
  1. 出场率 >= 5次（occurrenceCount >= 5）
  2. 稳定性 >= 0.95（stabilityScore >= 0.95）
  3. 用户手动精修过（isUserVerified == true）

输出：
- 是否触发金字招牌（Boolean）
- 判定详情（各条件检查明细）
- 金字等级（1=普通, 2=稀有, 3=传说）
"""

import numpy as np
from typing import Dict, Optional

from .models import Glyph, GoldenSignResult, WuxingType


class GoldenSignDetector:
    """
    金字招牌检测器
    
    使用流程：
    1. 初始化：detector = GoldenSignDetector()
    2. 检测：result = detector.check(glyph)
    
    配置参数（可通过构造函数调整）：
    - min_occurrence: 最低出场次数 (默认5)
    - min_stability: 最低稳定性 (默认0.95)
    - require_verified: 是否要求用户精修 (默认True)
    """
    
    # 金字招牌等级阈值
    LEVEL_THRESHOLDS = {
        3: {"occurrence": 20, "stability": 0.99},  # 传说：出场20次+稳定度0.99
        2: {"occurrence": 10, "stability": 0.97},  # 稀有：出场10次+稳定度0.97
        1: {"occurrence": 5, "stability": 0.95},   # 普通：出场5次+稳定度0.95
    }
    
    def __init__(self, 
                 min_occurrence: int = 5,
                 min_stability: float = 0.95,
                 require_verified: bool = True):
        """
        初始化金字招牌检测器
        
        Args:
            min_occurrence: 最低出场次数阈值
            min_stability: 最低稳定性阈值 (0.0 ~ 1.0)
            require_verified: 是否要求用户手动精修
        """
        self.min_occurrence = min_occurrence
        self.min_stability = min_stability
        self.require_verified = require_verified
        
        print(f"[金字检测器] 初始化完成 | 出场≥{min_occurrence} | 稳定≥{min_stability} | 需精修={require_verified}")
    
    def check(self, glyph: Glyph) -> GoldenSignResult:
        """
        检测单个字形是否触发金字招牌
        
        Args:
            glyph: 字形对象（含 occurrenceCount / stabilityScore / isUserVerified）
        
        Returns:
            GoldenSignResult: 检测结果
        """
        # 初始化各条件检查状态
        criteria = {
            "occurrence_sufficient": False,
            "stability_sufficient": False,
            "user_verified": False,
        }
        
        # 条件1：出场率检测
        if hasattr(glyph, 'occurrence_count') or isinstance(glyph, Glyph):
            criteria["occurrence_sufficient"] = glyph.occurrence_count >= self.min_occurrence
        
        # 条件2：稳定性检测
        if hasattr(glyph, 'stability_score') or isinstance(glyph, Glyph):
            criteria["stability_sufficient"] = glyph.stability_score >= self.min_stability
        
        # 条件3：用户精修检测
        if hasattr(glyph, 'is_user_verified') or isinstance(glyph, Glyph):
            if self.require_verified:
                criteria["user_verified"] = glyph.is_user_verified
            else:
                criteria["user_verified"] = True  # 如不要求，默认满足
        
        # 综合判定：所有条件必须同时满足
        is_triggered = all(criteria.values())
        
        # 计算金字等级
        level = 0
        if is_triggered:
            level = self._calculate_level(glyph)
        
        return GoldenSignResult(
            is_triggered=is_triggered,
            criteria_check=criteria,
            level=level,
            glyph_id=glyph.glyph_id if glyph.glyph_id else "",
        )
    
    def _calculate_level(self, glyph: Glyph) -> int:
        """
        计算金字招牌等级
        
        等级规则（需同时满足出场次数和稳定性）：
        - Level 3 (传说): occurrence>=20 且 stability>=0.99
        - Level 2 (稀有): occurrence>=10 且 stability>=0.97
        - Level 1 (普通): occurrence>=5  且 stability>=0.95
        
        取满足条件的最高等级
        """
        occurrence = glyph.occurrence_count
        stability = glyph.stability_score
        
        # 从高到低检查，必须同时满足出场次数和稳定性两个条件
        if occurrence >= 20 and stability >= 0.99:
            return 3  # 传说
        elif occurrence >= 10 and stability >= 0.97:
            return 2  # 稀有
        else:
            return 1  # 普通（最低保底，因为已触发说明满足基础条件）
    
    def check_batch(self, glyphs: list) -> list:
        """
        批量检测字形列表
        
        Args:
            glyphs: List[Glyph] 字形对象列表
        
        Returns:
            List[GoldenSignResult] 检测结果列表
        """
        return [self.check(g) for g in glyphs]
    
    @staticmethod
    def create_glyph(char: str = "道",
                     occurrence_count: int = 0,
                     stability_score: float = 0.0,
                     is_user_verified: bool = False,
                     glyph_id: str = "") -> Glyph:
        """
        便捷创建字形对象（用于测试）
        
        Args:
            char: 汉字字符
            occurrence_count: 出场次数
            stability_score: 稳定性评分
            is_user_verified: 是否用户精修
            glyph_id: 字形ID
        
        Returns:
            Glyph: 字形对象
        """
        return Glyph(
            char=char,
            image=None,  # 金字招牌不需要图像
            glyph_id=glyph_id or f"glyph_{char}_{occurrence_count}",
            occurrence_count=occurrence_count,
            stability_score=stability_score,
            is_user_verified=is_user_verified,
        )


# 便捷函数
def check_golden_sign(glyph: Glyph,
                      min_occurrence: int = 5,
                      min_stability: float = 0.95,
                      require_verified: bool = True) -> GoldenSignResult:
    """
    便捷检测函数
    
    Args:
        glyph: 字形对象
        min_occurrence: 最低出场次数
        min_stability: 最低稳定性
        require_verified: 是否要求精修
    
    Returns:
        GoldenSignResult: 检测结果
    """
    detector = GoldenSignDetector(min_occurrence, min_stability, require_verified)
    return detector.check(glyph)
