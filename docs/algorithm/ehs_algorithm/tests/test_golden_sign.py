"""
金字招牌检测算法 - 单元测试

测试覆盖：
1. 正常触发场景（三围达标）
2. 刚好达标边界测试
3. 刚好不达标边界测试
4. 缺字段异常测试
5. 不同等级计算
6. 批量检测
7. 配置参数变更
"""

import unittest
import numpy as np

from ehs_algorithm.golden_sign import GoldenSignDetector
from ehs_algorithm.models import Glyph


class TestGoldenSignDetector(unittest.TestCase):
    """金字招牌检测测试套件"""
    
    @classmethod
    def setUpClass(cls):
        """测试套件初始化"""
        cls.detector = GoldenSignDetector()
    
    def setUp(self):
        """每个测试用例前执行"""
        pass
    
    # ====== 测试1: 正常触发（三围达标） ======
    def test_all_criteria_met(self):
        """测试三围全部达标，应触发金字招牌"""
        glyph = GoldenSignDetector.create_glyph(
            char="道",
            occurrence_count=10,
            stability_score=0.97,
            is_user_verified=True,
            glyph_id="test_001"
        )
        
        result = self.detector.check(glyph)
        
        self.assertTrue(result.is_triggered, 
                       "三围达标应触发金字招牌")
        self.assertEqual(result.level, 2, 
                        "出场10次+稳定0.97应为稀有(2级)")
        self.assertEqual(result.glyph_id, "test_001")
    
    def test_high_level_trigger(self):
        """测试传说级(3级)触发条件"""
        glyph = GoldenSignDetector.create_glyph(
            char="剑",
            occurrence_count=25,
            stability_score=0.995,
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertTrue(result.is_triggered)
        self.assertEqual(result.level, 3, 
                        "出场25次+稳定0.995应为传说(3级)")
    
    def test_basic_level_trigger(self):
        """测试普通级(1级)触发条件"""
        glyph = GoldenSignDetector.create_glyph(
            char="人",
            occurrence_count=5,
            stability_score=0.95,
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertTrue(result.is_triggered)
        self.assertEqual(result.level, 1,
                        "刚好达标应为普通(1级)")
    
    # ====== 测试2: 刚好达标边界测试 ======
    def test_boundary_occurrence_exact(self):
        """测试出场次数刚好等于阈值(5)"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=5,  # 刚好等于阈值
            stability_score=0.95,
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertTrue(result.is_triggered,
                       "出场次数=5应刚好达标")
        self.assertTrue(result.criteria_check["occurrence_sufficient"])
    
    def test_boundary_stability_exact(self):
        """测试稳定性刚好等于阈值(0.95)"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=5,
            stability_score=0.95,  # 刚好等于阈值
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertTrue(result.is_triggered,
                       "稳定性=0.95应刚好达标")
        self.assertTrue(result.criteria_check["stability_sufficient"])
    
    def test_boundary_high_stability(self):
        """测试极高稳定性边界"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=5,
            stability_score=1.0,  # 最高值
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertTrue(result.is_triggered)
        self.assertEqual(result.level, 1)
    
    # ====== 测试3: 刚好不达标边界测试 ======
    def test_boundary_occurrence_below(self):
        """测试出场次数刚好低于阈值(4)"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=4,  # 低于阈值1
            stability_score=0.95,
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertFalse(result.is_triggered,
                        "出场次数=4不应触发")
        self.assertFalse(result.criteria_check["occurrence_sufficient"])
    
    def test_boundary_stability_below(self):
        """测试稳定性刚好低于阈值(0.949)"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=5,
            stability_score=0.949,  # 低于阈值0.001
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertFalse(result.is_triggered,
                        "稳定性=0.949不应触发")
        self.assertFalse(result.criteria_check["stability_sufficient"])
    
    def test_not_verified(self):
        """测试用户未精修"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=10,
            stability_score=0.97,
            is_user_verified=False,  # 未精修
        )
        
        result = self.detector.check(glyph)
        
        self.assertFalse(result.is_triggered,
                        "未精修不应触发")
        self.assertFalse(result.criteria_check["user_verified"])
    
    # ====== 测试4: 缺字段异常测试 ======
    def test_zero_occurrence(self):
        """测试出场次数为0"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=0,
            stability_score=0.95,
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertFalse(result.is_triggered)
        self.assertFalse(result.criteria_check["occurrence_sufficient"])
    
    def test_negative_occurrence(self):
        """测试出场次数为负数（异常情况）"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=-1,
            stability_score=0.95,
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertFalse(result.is_triggered)
    
    def test_zero_stability(self):
        """测试稳定性为0"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=5,
            stability_score=0.0,
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertFalse(result.is_triggered)
        self.assertFalse(result.criteria_check["stability_sufficient"])
    
    def test_extreme_high_occurrence(self):
        """测试极大出场次数（稳定性不足仍为普通级）"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=99999,
            stability_score=0.95,
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        
        self.assertTrue(result.is_triggered)
        self.assertEqual(result.level, 1)  # 稳定性0.95只满足普通级
        
        # 极高出场 + 极高稳定 = 传说级
        glyph2 = GoldenSignDetector.create_glyph(
            occurrence_count=99999,
            stability_score=0.999,
            is_user_verified=True,
        )
        result2 = self.detector.check(glyph2)
        self.assertEqual(result2.level, 3)
    
    # ====== 测试5: 等级计算 ======
    def test_level_calculation(self):
        """详细测试各等级计算"""
        test_cases = [
            # (occurrence, stability, expected_level)
            (5, 0.95, 1),    # 普通
            (10, 0.97, 2),   # 稀有
            (20, 0.99, 3),   # 传说
            (15, 0.98, 2),   # 稀有（出场>=10 且 稳定>=0.97）
            (5, 0.99, 1),    # 普通（稳定够但出场不够10）
            (25, 0.96, 1),   # 普通（出场够20但稳定<0.97，连稀有都不够）
            (25, 0.98, 2),   # 稀有（出场够20但稳定<0.99，不满足传说）
        ]
        
        for occ, stab, expected_level in test_cases:
            with self.subTest(occurrence=occ, stability=stab):
                glyph = GoldenSignDetector.create_glyph(
                    occurrence_count=occ,
                    stability_score=stab,
                    is_user_verified=True,
                )
                result = self.detector.check(glyph)
                
                self.assertTrue(result.is_triggered,
                              f"occ={occ}, stab={stab}应触发")
                self.assertEqual(result.level, expected_level,
                               f"occ={occ}, stab={stab}应为{expected_level}级")
    
    # ====== 测试6: 批量检测 ======
    def test_batch_check(self):
        """测试批量检测"""
        glyphs = [
            GoldenSignDetector.create_glyph(occurrence_count=10, stability_score=0.97, is_user_verified=True),
            GoldenSignDetector.create_glyph(occurrence_count=3, stability_score=0.97, is_user_verified=True),
            GoldenSignDetector.create_glyph(occurrence_count=20, stability_score=0.99, is_user_verified=True),
            GoldenSignDetector.create_glyph(occurrence_count=5, stability_score=0.95, is_user_verified=False),
        ]
        
        results = self.detector.check_batch(glyphs)
        
        self.assertEqual(len(results), 4)
        self.assertTrue(results[0].is_triggered,   # 达标
                       "第1个应触发")
        self.assertFalse(results[1].is_triggered,  # 出场不够
                        "第2个不应触发")
        self.assertTrue(results[2].is_triggered,   # 传说级
                       "第3个应触发")
        self.assertEqual(results[2].level, 3)
        self.assertFalse(results[3].is_triggered,  # 未精修
                        "第4个不应触发")
    
    # ====== 测试7: 配置参数 ======
    def test_custom_thresholds(self):
        """测试自定义阈值"""
        detector = GoldenSignDetector(
            min_occurrence=3,
            min_stability=0.90,
            require_verified=False,
        )
        
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=3,
            stability_score=0.90,
            is_user_verified=False,  # 不检测精修
        )
        
        result = detector.check(glyph)
        
        self.assertTrue(result.is_triggered,
                       "自定义阈值应触发")
    
    def test_no_verified_requirement(self):
        """测试不要求精修的配置"""
        detector = GoldenSignDetector(require_verified=False)
        
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=5,
            stability_score=0.95,
            is_user_verified=False,
        )
        
        result = detector.check(glyph)
        
        self.assertTrue(result.is_triggered,
                       "不要求精修时应触发")
        self.assertTrue(result.criteria_check["user_verified"],
                       "不要求精修时该条件应自动满足")
    
    def test_very_strict_threshold(self):
        """测试极严格阈值"""
        detector = GoldenSignDetector(
            min_occurrence=100,
            min_stability=1.0,
        )
        
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=99,
            stability_score=0.999,
            is_user_verified=True,
        )
        
        result = detector.check(glyph)
        
        self.assertFalse(result.is_triggered,
                        "极严格阈值不应触发")
    
    # ====== 测试8: 结果格式 ======
    def test_result_format(self):
        """测试结果字典格式"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=10,
            stability_score=0.97,
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        result_dict = result.to_dict()
        
        self.assertIn("is_triggered", result_dict)
        self.assertIn("criteria_check", result_dict)
        self.assertIn("level", result_dict)
        self.assertIn("glyph_id", result_dict)
        
        # 验证criteria_check包含三个条件
        criteria = result_dict["criteria_check"]
        self.assertIn("occurrence_sufficient", criteria)
        self.assertIn("stability_sufficient", criteria)
        self.assertIn("user_verified", criteria)
    
    def test_criteria_details(self):
        """测试各条件明细正确性"""
        glyph = GoldenSignDetector.create_glyph(
            occurrence_count=5,
            stability_score=0.95,
            is_user_verified=True,
        )
        
        result = self.detector.check(glyph)
        criteria = result.criteria_check
        
        self.assertTrue(criteria["occurrence_sufficient"],
                       "出场=5应满足")
        self.assertTrue(criteria["stability_sufficient"],
                       "稳定=0.95应满足")
        self.assertTrue(criteria["user_verified"],
                       "已精修应满足")


if __name__ == "__main__":
    unittest.main(verbosity=2)
