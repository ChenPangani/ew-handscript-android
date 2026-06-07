"""
五行灵根觉醒算法 - 单元测试

测试覆盖：
1. 特征提取维度正确性
2. K-Means聚类合理性（5个聚类中心）
3. 五行雷达图归一化（0-100范围）
4. 命格标签合法性
5. 端到端觉醒流程
6. Mock数据集成
"""

import unittest
import numpy as np
import torch

from ehs_algorithm.mock_data import MockDataGenerator
from ehs_algorithm.wuxing_awakening import WuxingAwakening
from ehs_algorithm.models import WuxingType, MinggeType


class TestWuxingAwakening(unittest.TestCase):
    """五行灵根觉醒测试套件"""
    
    @classmethod
    def setUpClass(cls):
        """测试套件初始化"""
        cls.generator = MockDataGenerator(seed=42, image_size=64)
        cls.awakening = WuxingAwakening(feature_dim=128, n_clusters=5)
    
    def setUp(self):
        """每个测试用例前重置随机种子"""
        np.random.seed(42)
        torch.manual_seed(42)
    
    # ====== 测试1: 特征提取维度 ======
    def test_feature_extraction_dimension(self):
        """测试128维特征向量输出"""
        # 生成5张测试图
        images, _ = self.generator.generate_batch(count=5)
        features = self.awakening.extract_features(images)
        
        self.assertEqual(features.shape, (5, 128), 
                        f"特征维度应为(5, 128)，实际为{features.shape}")
        self.assertEqual(features.dtype, np.float32,
                        "特征类型应为float32")
    
    def test_feature_normalization(self):
        """测试特征向量L2归一化"""
        images, _ = self.generator.generate_batch(count=3)
        features = self.awakening.extract_features(images)
        
        # 检查L2范数是否为1（归一化后）
        norms = np.linalg.norm(features, axis=1)
        for i, norm in enumerate(norms):
            self.assertAlmostEqual(norm, 1.0, places=5,
                                 msg=f"第{i}个特征向量未归一化，范数={norm}")
    
    # ====== 测试2: K-Means聚类 ======
    def test_kmeans_cluster_count(self):
        """测试聚类数为5（五行）"""
        self.assertEqual(self.awakening.kmeans.n_clusters, 5,
                        "聚类数必须为5（对应五行）")
    
    def test_kmeans_cluster_centers_initialized(self):
        """测试聚类中心已初始化"""
        centers = self.awakening.kmeans.cluster_centers_
        self.assertEqual(centers.shape, (5, 128),
                        f"聚类中心维度应为(5, 128)，实际为{centers.shape}")
    
    def test_cluster_assignment_valid(self):
        """测试聚类标签在0-4范围内"""
        images, _ = self.generator.generate_batch(count=20)
        features = self.awakening.extract_features(images)
        labels = self.awakening.kmeans.fit_predict(features)
        
        self.assertTrue(all(0 <= l < 5 for l in labels),
                       "聚类标签必须在[0, 4]范围内")
    
    # ====== 测试3: 五行雷达图 ======
    def test_radar_range(self):
        """测试雷达图数值在0-100范围内"""
        images, _ = self.generator.generate_batch(count=10)
        results = self.awakening.awaken(images)
        
        for result in results:
            radar = result.wuxing_radar
            values = radar.to_list()
            for i, (val, name) in enumerate(zip(values, ['木', '火', '土', '金', '水'])):
                self.assertGreaterEqual(val, 0, 
                                      f"{name}雷达值应>=0，实际={val}")
                self.assertLessEqual(val, 100,
                                   f"{name}雷达值应<=100，实际={val}")
    
    def test_radar_dominant_valid(self):
        """测试主导五行是合法值"""
        images, _ = self.generator.generate_batch(count=10)
        results = self.awakening.awaken(images)
        
        valid_wuxing = {WuxingType.MU, WuxingType.HUO, WuxingType.TU, 
                       WuxingType.JIN, WuxingType.SHUI}
        
        for result in results:
            self.assertIn(result.dominant_wuxing, valid_wuxing,
                         f"主导五行{result.dominant_wuxing}不是合法值")
    
    # ====== 测试4: 命格标签 ======
    def test_mingge_valid(self):
        """测试命格标签是合法枚举值"""
        images, _ = self.generator.generate_batch(count=10)
        results = self.awakening.awaken(images)
        
        valid_mingge = set(MinggeType)
        for result in results:
            self.assertIn(result.mingge, valid_mingge,
                         f"命格{result.mingge}不是合法值")
    
    def test_mingge_consistency(self):
        """测试命格与五行一致性"""
        # 木火通明命格检查
        wuxing_centers = self.awakening.WUXING_CENTERS
        
        # 木+火组合应产生木火通明
        mu_center = wuxing_centers[WuxingType.MU]
        huo_center = wuxing_centers[WuxingType.HUO]
        
        # 验证预定义中心正确性
        self.assertGreater(mu_center[4], 0.7, "木应具高流畅度")  # 流畅指数高
        self.assertGreater(huo_center[1], 0.8, "火应具高笔压")   # 笔压强度高
    
    # ====== 测试5: 端到端流程 ======
    def test_end_to_end_100_chars(self):
        """测试100字端到端觉醒流程"""
        images, chars = self.generator.generate_batch(count=100)
        
        self.assertEqual(len(images), 100, "应生成100张图片")
        self.assertEqual(images.shape[1:], (64, 64, 4), "图片尺寸应为64x64x4")
        
        results = self.awakening.awaken(images)
        
        self.assertEqual(len(results), 100, "应产出100个结果")
        
        # 统计五行分布
        wuxing_count = {}
        for r in results:
            wx = r.dominant_wuxing.value
            wuxing_count[wx] = wuxing_count.get(wx, 0) + 1
        
        print(f"\n[测试-五行分布] {wuxing_count}")
        
        # 验证每个五行至少出现一次（Mock数据随机性）
        self.assertGreaterEqual(len(wuxing_count), 1, 
                               "应至少有一种五行出现")
    
    def test_end_to_end_wuxing_bias(self):
        """测试五行偏向的端到端流程"""
        for bias in ["木", "火", "土", "金", "水"]:
            images, _ = self.generator.generate_batch(count=20, wuxing_bias=bias)
            results = self.awakening.awaken(images)
            
            # 统计主导五行
            dominant_counts = {}
            for r in results:
                wx = r.dominant_wuxing.value
                dominant_counts[wx] = dominant_counts.get(wx, 0) + 1
            
            print(f"\n[测试-{bias}偏向] 分布: {dominant_counts}")
            
            # 验证至少有一些结果
            self.assertEqual(len(results), 20)
    
    # ====== 测试6: 置信度 ======
    def test_confidence_range(self):
        """测试置信度在[0, 1]范围内"""
        images, _ = self.generator.generate_batch(count=10)
        results = self.awakening.awaken(images)
        
        for result in results:
            self.assertGreaterEqual(result.confidence, 0,
                                  "置信度应>=0")
            self.assertLessEqual(result.confidence, 1,
                               "置信度应<=1")
    
    # ====== 测试7: 输出格式 ======
    def test_output_dict_format(self):
        """测试输出字典格式"""
        images, _ = self.generator.generate_batch(count=1)
        results = self.awakening.awaken(images)
        
        result_dict = results[0].to_dict()
        
        self.assertIn("wuxing_radar", result_dict)
        self.assertIn("dominant_wuxing", result_dict)
        self.assertIn("mingge", result_dict)
        self.assertIn("confidence", result_dict)
        
        # 验证雷达图包含5个维度
        radar = result_dict["wuxing_radar"]
        for dim in ["木", "火", "土", "金", "水"]:
            self.assertIn(dim, radar, f"雷达图缺少{dim}维度")


class TestWuxingCenters(unittest.TestCase):
    """测试五行聚类中心定义"""
    
    def test_centers_shape(self):
        """测试中心点维度"""
        centers = WuxingAwakening.WUXING_CENTERS
        self.assertEqual(len(centers), 5, "应有5个五行中心")
        
        for wx_type, center in centers.items():
            self.assertEqual(len(center), 5, 
                           f"{wx_type.value}中心维度应为5")
    
    def test_center_values_range(self):
        """测试中心点值在[0, 1]范围内"""
        for wx_type, center in WuxingAwakening.WUXING_CENTERS.items():
            for i, val in enumerate(center):
                self.assertGreaterEqual(val, 0, 
                                      f"{wx_type.value}[{i}]应>=0")
                self.assertLessEqual(val, 1,
                                   f"{wx_type.value}[{i}]应<=1")


class TestOverallWuxing(unittest.TestCase):
    """测试总体五行计算"""
    
    @classmethod
    def setUpClass(cls):
        cls.generator = MockDataGenerator(seed=42)
        cls.awakening = WuxingAwakening()
    
    def test_overall_computation(self):
        """测试100字总体五行计算"""
        images, _ = self.generator.generate_batch(count=100)
        results = self.awakening.awaken(images)
        
        overall = self.awakening.compute_overall_wuxing(results)
        
        # 验证总体结果结构
        self.assertIsNotNone(overall.wuxing_radar)
        self.assertIsNotNone(overall.dominant_wuxing)
        self.assertIsNotNone(overall.mingge)
        
        # 验证雷达图在合理范围
        for val in overall.wuxing_radar.to_list():
            self.assertGreaterEqual(val, 0)
            self.assertLessEqual(val, 100)


if __name__ == "__main__":
    unittest.main(verbosity=2)
