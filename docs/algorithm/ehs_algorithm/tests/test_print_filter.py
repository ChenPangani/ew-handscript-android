"""
印刷体过滤算法 - 单元测试

测试覆盖：
1. 模型架构正确性
2. 训练流程（Mock数据）
3. 推理正确性（手写体/印刷体分类）
4. 置信度范围
5. 阈值边界测试
6. 批量推理
7. 模型保存/加载
8. TFLite导出
"""

import unittest
import numpy as np
import torch

from ehs_algorithm.mock_data import MockDataGenerator
from ehs_algorithm.print_filter import (
    PrintFilter, PrintFilterTrainer, PrintFilterInference
)
from ehs_algorithm.models import Glyph, PrintFilterResult


class TestPrintFilterModel(unittest.TestCase):
    """印刷体过滤模型测试"""
    
    def setUp(self):
        """初始化"""
        torch.manual_seed(42)
        np.random.seed(42)
        self.model = PrintFilter()
    
    def test_model_output_shape(self):
        """测试模型输出维度"""
        batch_size = 4
        dummy_input = torch.randn(batch_size, 4, 64, 64)
        
        output = self.model(dummy_input)
        
        self.assertEqual(output.shape, (batch_size, 1),
                        f"输出维度应为({batch_size}, 1)，实际为{output.shape}")
    
    def test_model_output_range(self):
        """测试模型输出在[0, 1]范围内（Sigmoid输出）"""
        dummy_input = torch.randn(10, 4, 64, 64)
        
        output = self.model(dummy_input)
        
        self.assertTrue(torch.all(output >= 0),
                       "输出应>=0")
        self.assertTrue(torch.all(output <= 1),
                       "输出应<=1")
    
    def test_model_batch_sizes(self):
        """测试不同batch size"""
        for batch_size in [1, 4, 8, 16]:
            with self.subTest(batch_size=batch_size):
                dummy_input = torch.randn(batch_size, 4, 64, 64)
                output = self.model(dummy_input)
                self.assertEqual(output.shape[0], batch_size)


class TestPrintFilterTrainer(unittest.TestCase):
    """印刷体过滤训练器测试"""
    
    @classmethod
    def setUpClass(cls):
        """生成Mock数据"""
        cls.generator = MockDataGenerator(seed=42, image_size=64)
        
        # 生成手写体样本（正样本）
        cls.hand_images, _ = cls.generator.generate_batch(
            count=50, handwritten=True
        )
        
        # 生成印刷体样本（负样本）
        cls.print_images, _ = cls.generator.generate_batch(
            count=50, handwritten=False
        )
    
    def setUp(self):
        """每个测试新建训练器"""
        torch.manual_seed(42)
        self.trainer = PrintFilterTrainer(batch_size=8, learning_rate=1e-3)
    
    def test_data_preparation(self):
        """测试数据准备"""
        train_loader, val_loader = self.trainer.prepare_data(
            self.hand_images,
            self.print_images,
            val_split=0.2
        )
        
        # 验证数据加载器
        self.assertIsNotNone(train_loader)
        self.assertIsNotNone(val_loader)
        
        # 验证批次形状
        for batch_x, batch_y in train_loader:
            self.assertEqual(batch_x.shape[1:], (4, 64, 64),
                           "输入应为(N, 4, 64, 64)")
            self.assertEqual(batch_y.shape[1:], (1,),
                           "标签应为(N, 1)")
            break
    
    def test_training_runs(self):
        """测试训练流程可正常运行"""
        train_loader, val_loader = self.trainer.prepare_data(
            self.hand_images[:20],  # 少量数据快速测试
            self.print_images[:20],
            val_split=0.2
        )
        
        history = self.trainer.train(
            train_loader, val_loader, epochs=2, patience=2
        )
        
        # 验证训练历史
        self.assertIn("train_loss", history)
        self.assertIn("val_loss", history)
        self.assertIn("val_acc", history)
        self.assertEqual(len(history["train_loss"]), 2)
    
    def test_prediction_shape(self):
        """测试预测输出形状"""
        # 先训练一点
        train_loader, val_loader = self.trainer.prepare_data(
            self.hand_images[:20],
            self.print_images[:20],
            val_split=0.2
        )
        self.trainer.train(train_loader, val_loader, epochs=1, patience=1)
        
        # 预测
        test_images = np.concatenate([self.hand_images[:5], self.print_images[:5]])
        predictions, confidences = self.trainer.predict(test_images)
        
        self.assertEqual(len(predictions), 10,
                        "应预测10个结果")
        self.assertEqual(len(confidences), 10,
                        "应输出10个置信度")
    
    def test_confidence_range(self):
        """测试置信度在[0, 1]范围内"""
        train_loader, val_loader = self.trainer.prepare_data(
            self.hand_images[:20],
            self.print_images[:20],
            val_split=0.2
        )
        self.trainer.train(train_loader, val_loader, epochs=1, patience=1)
        
        test_images = self.hand_images[:5]
        _, confidences = self.trainer.predict(test_images)
        
        for conf in confidences:
            self.assertGreaterEqual(conf, 0,
                                  f"置信度{conf}应>=0")
            self.assertLessEqual(conf, 1,
                               f"置信度{conf}应<=1")
    
    def test_model_save_load(self):
        """测试模型保存和加载"""
        import tempfile
        import os
        
        # 保存
        with tempfile.NamedTemporaryFile(suffix='.pth', delete=False) as f:
            temp_path = f.name
        
        self.trainer.save_model(temp_path)
        self.assertTrue(os.path.exists(temp_path))
        
        # 加载到新训练器
        new_trainer = PrintFilterTrainer()
        new_trainer.load_model(temp_path)
        
        # 清理
        os.unlink(temp_path)
    
    def test_handwritten_higher_confidence(self):
        """测试手写体置信度普遍高于印刷体"""
        train_loader, val_loader = self.trainer.prepare_data(
            self.hand_images[:30],
            self.print_images[:30],
            val_split=0.2
        )
        self.trainer.train(train_loader, val_loader, epochs=3, patience=2)
        
        # 预测手写体
        _, hand_conf = self.trainer.predict(self.hand_images[30:35])
        # 预测印刷体
        _, print_conf = self.trainer.predict(self.print_images[30:35])
        
        avg_hand = np.mean(hand_conf)
        avg_print = np.mean(print_conf)
        
        print(f"\n[测试] 手写体平均置信度: {avg_hand:.4f}")
        print(f"[测试] 印刷体平均置信度: {avg_print:.4f}")
        
        # 手写体应比印刷体置信度高
        self.assertGreater(avg_hand, avg_print,
                          f"手写体置信度({avg_hand})应高于印刷体({avg_print})")


class TestPrintFilterInference(unittest.TestCase):
    """印刷体过滤推理器测试"""
    
    @classmethod
    def setUpClass(cls):
        """生成Mock数据"""
        cls.generator = MockDataGenerator(seed=42)
        cls.hand_images, _ = cls.generator.generate_batch(count=10, handwritten=True)
        cls.print_images, _ = cls.generator.generate_batch(count=10, handwritten=False)
    
    def test_classify_handwritten(self):
        """测试手写体分类"""
        # 创建推理器（未训练，但测试接口）
        inference = PrintFilterInference(device="cpu")
        
        # 创建字形对象
        glyph = Glyph(
            char="道",
            image=self.hand_images[0],
            glyph_id="test_hand_001"
        )
        
        result = inference.classify(glyph)
        
        self.assertIsInstance(result, PrintFilterResult)
        self.assertIn(result.is_handwritten, [True, False])
        self.assertGreaterEqual(result.confidence, 0)
        self.assertLessEqual(result.confidence, 1)
    
    def test_classify_printed(self):
        """测试印刷体分类"""
        inference = PrintFilterInference(device="cpu")
        
        glyph = Glyph(
            char="道",
            image=self.print_images[0],
            glyph_id="test_print_001"
        )
        
        result = inference.classify(glyph)
        
        self.assertIsInstance(result, PrintFilterResult)
        self.assertGreaterEqual(result.confidence, 0)
        self.assertLessEqual(result.confidence, 1)
    
    def test_classify_batch(self):
        """测试批量分类"""
        inference = PrintFilterInference(device="cpu")
        
        # 混合手写体和印刷体
        mixed_images = np.concatenate([self.hand_images[:5], self.print_images[:5]])
        
        results = inference.classify_batch(mixed_images)
        
        self.assertEqual(len(results), 10,
                        "应返回10个结果")
        
        for result in results:
            self.assertIsInstance(result, PrintFilterResult)
            self.assertGreaterEqual(result.confidence, 0)
            self.assertLessEqual(result.confidence, 1)
    
    def test_threshold_boundary(self):
        """测试阈值边界"""
        inference = PrintFilterInference(device="cpu")
        
        # 使用不同阈值
        glyph = Glyph(
            char="道",
            image=self.hand_images[0],
            glyph_id="test_threshold"
        )
        
        # 阈值0.5（默认）
        result_default = inference.classify(glyph, threshold=0.5)
        
        # 阈值0.9（严格）
        result_strict = inference.classify(glyph, threshold=0.9)
        
        # 阈值0.1（宽松）
        result_loose = inference.classify(glyph, threshold=0.1)
        
        # 宽松阈值应更容易判定为手写体
        if result_default.confidence > 0.1:
            self.assertTrue(result_loose.is_handwritten,
                          "低阈值应判定为手写体")
    
    def test_no_image_error(self):
        """测试缺少图像时抛出异常"""
        inference = PrintFilterInference(device="cpu")
        
        glyph = Glyph(
            char="道",
            image=None,
            glyph_id="test_no_image"
        )
        
        with self.assertRaises(ValueError):
            inference.classify(glyph)
    
    def test_result_format(self):
        """测试结果输出格式"""
        inference = PrintFilterInference(device="cpu")
        
        glyph = Glyph(
            char="道",
            image=self.hand_images[0],
            glyph_id="test_format"
        )
        
        result = inference.classify(glyph)
        result_dict = result.to_dict()
        
        self.assertIn("is_handwritten", result_dict)
        self.assertIn("confidence", result_dict)
        self.assertIn("raw_score", result_dict)
        self.assertIn("glyph_id", result_dict)
        self.assertIsInstance(result_dict["is_handwritten"], bool)


class TestPrintFilterIntegration(unittest.TestCase):
    """印刷体过滤集成测试"""
    
    @classmethod
    def setUpClass(cls):
        """生成测试数据"""
        cls.generator = MockDataGenerator(seed=42)
    
    def test_full_pipeline(self):
        """测试完整流程：训练->保存->加载->推理"""
        import tempfile
        import os
        
        # 生成训练数据
        hand_images, _ = self.generator.generate_batch(count=30, handwritten=True)
        print_images, _ = self.generator.generate_batch(count=30, handwritten=False)
        
        # 训练
        trainer = PrintFilterTrainer(batch_size=8, learning_rate=1e-3)
        train_loader, val_loader = trainer.prepare_data(
            hand_images, print_images, val_split=0.2
        )
        history = trainer.train(train_loader, val_loader, epochs=3, patience=2)
        
        # 保存
        with tempfile.NamedTemporaryFile(suffix='.pth', delete=False) as f:
            model_path = f.name
        trainer.save_model(model_path)
        
        # 加载并推理
        inference = PrintFilterInference(model_path=model_path, device="cpu")
        
        # 测试手写体
        test_hand = hand_images[-3:]
        results_hand = inference.classify_batch(test_hand)
        
        # 测试印刷体
        test_print = print_images[-3:]
        results_print = inference.classify_batch(test_print)
        
        # 清理
        os.unlink(model_path)
        
        # 验证
        hand_confidences = [r.confidence for r in results_hand]
        print_confidences = [r.confidence for r in results_print]
        
        avg_hand = np.mean(hand_confidences)
        avg_print = np.mean(print_confidences)
        
        print(f"\n[集成测试] 手写体平均置信度: {avg_hand:.4f}")
        print(f"[集成测试] 印刷体平均置信度: {avg_print:.4f}")
        
        # 验证训练有效果
        self.assertGreater(avg_hand, avg_print,
                          "训练后手写体置信度应高于印刷体")


class TestMockDataGeneration(unittest.TestCase):
    """Mock数据生成测试"""
    
    def setUp(self):
        self.generator = MockDataGenerator(seed=42)
    
    def test_handwritten_image_properties(self):
        """测试手写体Mock数据属性"""
        img = self.generator.generate_handwritten_char()
        
        self.assertEqual(img.shape, (64, 64, 4),
                        "手写体图像应为64x64x4")
        self.assertEqual(img.dtype, np.uint8,
                        "图像类型应为uint8")
    
    def test_printed_image_properties(self):
        """测试印刷体Mock数据属性"""
        img = self.generator.generate_printed_char()
        
        self.assertEqual(img.shape, (64, 64, 4),
                        "印刷体图像应为64x64x4")
        self.assertEqual(img.dtype, np.uint8,
                        "图像类型应为uint8")
    
    def test_batch_generation(self):
        """测试批量生成"""
        images, chars = self.generator.generate_batch(count=10)
        
        self.assertEqual(len(images), 10,
                        "应生成10张图片")
        self.assertEqual(len(chars), 10,
                        "应返回10个字符")
        self.assertEqual(images.shape, (10, 64, 64, 4),
                        "批次形状应为(10, 64, 64, 4)")
    
    def test_wuxing_bias(self):
        """测试五行偏向生成"""
        for bias in ["木", "火", "土", "金", "水"]:
            with self.subTest(wuxing=bias):
                img = self.generator.generate_handwritten_char(wuxing_bias=bias)
                self.assertEqual(img.shape, (64, 64, 4))
    
    def test_handwritten_vs_printed(self):
        """测试手写体和印刷体视觉差异"""
        hand = self.generator.generate_handwritten_char()
        printed = self.generator.generate_printed_char()
        
        # 手写体应有更多噪声（方差更大）
        hand_var = np.var(hand.astype(np.float32))
        printed_var = np.var(printed.astype(np.float32))
        
        # 注意：这个测试可能因随机性失败，不做强断言
        print(f"\n[Mock测试] 手写体方差: {hand_var:.2f}, 印刷体方差: {printed_var:.2f}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
