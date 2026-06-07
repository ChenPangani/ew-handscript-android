#!/usr/bin/env python3
"""
蚯蚓.手书修仙传 - 核心算法演示入口
Eruca HandScript: Cultivation Saga - Algorithm Demo

运行三大核心算法模块的完整演示流程：
1. 五行灵根觉醒（首批100字）
2. 金字招牌检测
3. 印刷体过滤

使用方式:
    python main.py [--save-samples] [--export-models] [--verbose]
"""

import argparse
import numpy as np
import torch
import json
import os

from ehs_algorithm.mock_data import MockDataGenerator
from ehs_algorithm.wuxing_awakening import WuxingAwakening
from ehs_algorithm.golden_sign import GoldenSignDetector
from ehs_algorithm.print_filter import PrintFilterTrainer, PrintFilterInference
from ehs_algorithm.models import Glyph


def set_seed(seed=42):
    """设置全局随机种子，保证可复现性"""
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed(seed)


def demo_task1_wuxing_awakening(save_samples=False, verbose=False):
    """
    任务1：五行灵根觉醒演示
    
    流程：
    1. 生成100个Mock手写单字
    2. 提取128维特征向量
    3. K-Means聚类映射到五行
    4. 生成五行雷达图和命格报告
    """
    print("\n" + "=" * 60)
    print("  任务1：五行灵根觉醒 (首批100字特征提取)")
    print("=" * 60)
    
    # 步骤1：生成Mock数据
    print("\n[Step 1] 生成首批100字Mock手写数据...")
    generator = MockDataGenerator(seed=42, image_size=64)
    images, chars = generator.generate_batch(count=100, handwritten=True)
    print(f"  ✓ 生成完成: {images.shape} (100张, 64×64, RGBA)")
    
    if save_samples:
        generator.save_samples(output_dir="./output/mock_samples", n=3)
    
    # 步骤2：初始化五行觉醒器
    print("\n[Step 2] 初始化MobileNetV3-Small特征提取器...")
    awakening = WuxingAwakening(feature_dim=128, n_clusters=5)
    print("  ✓ 模型加载完成")
    
    # 步骤3：五行觉醒
    print("\n[Step 3] 执行五行灵根觉醒...")
    results = awakening.awaken(images)
    print(f"  ✓ 觉醒完成: 生成 {len(results)} 条命格报告")
    
    # 步骤4：统计展示
    print("\n[Step 4] 五行分布统计:")
    wuxing_dist = {}
    mingge_dist = {}
    for r in results:
        wx = r.dominant_wuxing.value
        mg = r.mingge.value
        wuxing_dist[wx] = wuxing_dist.get(wx, 0) + 1
        mingge_dist[mg] = mingge_dist.get(mg, 0) + 1
    
    for wx, count in sorted(wuxing_dist.items()):
        bar = "█" * (count // 2)
        print(f"  {wx}: {count:3d}字 {bar}")
    
    print("\n  命格分布:")
    for mg, count in sorted(mingge_dist.items(), key=lambda x: -x[1]):
        print(f"  · {mg}: {count}人")
    
    # 步骤5：展示前5个详细结果
    if verbose:
        print("\n[Step 5] 前5字详细命格报告:")
        for i, (char, result) in enumerate(zip(chars[:5], results[:5])):
            radar = result.wuxing_radar
            print(f"\n  [{i+1}] 字: '{char}'")
            print(f"      主导五行: {result.dominant_wuxing.value}")
            if result.secondary_wuxing:
                print(f"      次要五行: {result.secondary_wuxing.value}")
            print(f"      命格: {result.mingge.value}")
            print(f"      五行雷达: 木{radar.mu:.1f} 火{radar.huo:.1f} "
                  f"土{radar.tu:.1f} 金{radar.jin:.1f} 水{radar.shui:.1f}")
            print(f"      置信度: {result.confidence:.4f}")
    
    # 步骤6：计算总体五行
    print("\n[Step 6] 计算总体五行命盘...")
    overall = awakening.compute_overall_wuxing(results)
    print(f"  总体主导: {overall.dominant_wuxing.value}")
    print(f"  总体命格: {overall.mingge.value}")
    
    # 保存JSON结果
    output = {
        "total_chars": 100,
        "wuxing_distribution": wuxing_dist,
        "mingge_distribution": mingge_dist,
        "overall": {
            "dominant_wuxing": overall.dominant_wuxing.value,
            "mingge": overall.mingge.value,
            "radar": {
                "木": round(overall.wuxing_radar.mu, 2),
                "火": round(overall.wuxing_radar.huo, 2),
                "土": round(overall.wuxing_radar.tu, 2),
                "金": round(overall.wuxing_radar.jin, 2),
                "水": round(overall.wuxing_radar.shui, 2),
            }
        },
        "sample_results": [r.to_dict() for r in results[:5]],
    }
    
    os.makedirs("./output", exist_ok=True)
    with open("./output/wuxing_result.json", "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"  ✓ 结果已保存到 ./output/wuxing_result.json")
    
    return results


def demo_task2_golden_sign(verbose=False):
    """
    任务2：金字招牌检测演示
    
    流程：
    1. 创建各种边界条件的字形对象
    2. 检测是否触发金字招牌
    3. 输出判定详情
    """
    print("\n" + "=" * 60)
    print("  任务2：金字招牌检测")
    print("=" * 60)
    
    detector = GoldenSignDetector(
        min_occurrence=5,
        min_stability=0.95,
        require_verified=True,
    )
    
    # 测试用例
    test_cases = [
        # (描述, occurrence, stability, verified, 期望触发)
        ("三围达标-普通级", 5, 0.95, True, True),
        ("三围达标-稀有级", 12, 0.98, True, True),
        ("三围达标-传说级", 25, 0.995, True, True),
        ("出场不足", 4, 0.95, True, False),
        ("稳定性不足", 5, 0.94, True, False),
        ("未精修", 5, 0.95, False, False),
        ("两项不足", 3, 0.90, False, False),
        ("刚好达标边界", 5, 0.95, True, True),
        ("刚好不达标边界", 5, 0.949, True, False),
    ]
    
    print(f"\n{'测试场景':<20} {'出场':>4} {'稳定':>6} {'精修':>4} {'触发':>6} {'等级':>4} {'结果':>6}")
    print("-" * 70)
    
    results = []
    for desc, occ, stab, ver, expected in test_cases:
        glyph = GoldenSignDetector.create_glyph(
            char="道",
            occurrence_count=occ,
            stability_score=stab,
            is_user_verified=ver,
        )
        
        result = detector.check(glyph)
        passed = result.is_triggered == expected
        status = "✓" if passed else "✗"
        
        print(f"{desc:<18} {occ:4d} {stab:6.3f} {'是' if ver else '否':>4} "
              f"{'是' if result.is_triggered else '否':>6} "
              f"{result.level:4d} {status:>6}")
        
        results.append({
            "case": desc,
            "expected": expected,
            "actual": result.is_triggered,
            "passed": passed,
            "details": result.to_dict(),
        })
    
    # 统计
    passed_count = sum(1 for r in results if r["passed"])
    print(f"\n  测试通过: {passed_count}/{len(results)}")
    
    # 保存结果
    with open("./output/golden_sign_result.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"  ✓ 结果已保存到 ./output/golden_sign_result.json")
    
    return results


def demo_task3_print_filter(verbose=False):
    """
    任务3：印刷体过滤演示
    
    流程：
    1. 生成Mock手写体和印刷体样本
    2. 训练二分类模型
    3. 验证模型性能
    4. 测试推理
    """
    print("\n" + "=" * 60)
    print("  任务3：印刷体过滤 (手写体 vs 印刷体二分类)")
    print("=" * 60)
    
    generator = MockDataGenerator(seed=42)
    
    # 步骤1：生成训练数据
    print("\n[Step 1] 生成训练数据...")
    hand_train, _ = generator.generate_batch(count=40, handwritten=True)
    print_train, _ = generator.generate_batch(count=40, handwritten=False)
    print(f"  ✓ 手写体: {len(hand_train)}张 | 印刷体: {len(print_train)}张")
    
    # 步骤2：训练模型
    print("\n[Step 2] 训练MobileNetV3-Small二分类模型...")
    trainer = PrintFilterTrainer(batch_size=8, learning_rate=1e-3)
    train_loader, val_loader = trainer.prepare_data(
        hand_train, print_train, val_split=0.2
    )
    history = trainer.train(train_loader, val_loader, epochs=5, patience=3)
    print(f"  ✓ 训练完成 | 最终验证准确率: {history['val_acc'][-1]:.4f}")
    
    # 步骤3：测试推理
    print("\n[Step 3] 推理测试...")
    
    # 生成测试数据（10手写 + 10印刷体）
    hand_test, hand_chars = generator.generate_batch(count=10, handwritten=True)
    print_test, print_chars = generator.generate_batch(count=10, handwritten=False)
    
    # 保存模型
    model_path = "./output/print_filter_model.pth"
    trainer.save_model(model_path)
    
    # 推理器
    inference = PrintFilterInference(model_path=model_path, device="cpu")
    
    # 测试手写体
    print("\n  [手写体测试 - 应判定为手写体(1)]")
    hand_results = inference.classify_batch(hand_test)
    hand_correct = sum(1 for r in hand_results if r.is_handwritten)
    for i, (char, result) in enumerate(zip(hand_chars[:5], hand_results[:5])):
        status = "✓" if result.is_handwritten else "✗"
        print(f"    '{char}' -> 手写体={result.is_handwritten} "
              f"置信度={result.confidence:.4f} {status}")
    print(f"    准确率: {hand_correct}/10")
    
    # 测试印刷体
    print("\n  [印刷体测试 - 应判定为印刷体(0)]")
    print_results = inference.classify_batch(print_test)
    print_correct = sum(1 for r in print_results if not r.is_handwritten)
    for i, (char, result) in enumerate(zip(print_chars[:5], print_results[:5])):
        status = "✓" if not result.is_handwritten else "✗"
        print(f"    '{char}' -> 手写体={result.is_handwritten} "
              f"置信度={result.confidence:.4f} {status}")
    print(f"    准确率: {print_correct}/10")
    
    # 总体统计
    total_correct = hand_correct + print_correct
    print(f"\n  总体准确率: {total_correct}/20 ({total_correct/20*100:.1f}%)")
    
    # 步骤4：单字推理测试
    print("\n[Step 4] 单字推理测试...")
    glyph = Glyph(
        char="修",
        image=hand_test[0],
        glyph_id="test_glyph_001"
    )
    single_result = inference.classify(glyph)
    print(f"  字: '{glyph.char}'")
    print(f"  结果: {'手写体 ✓' if single_result.is_handwritten else '印刷体 ✗'}")
    print(f"  置信度: {single_result.confidence:.4f}")
    
    # 保存结果
    output = {
        "total_test": 20,
        "handwritten_test": {
            "count": 10,
            "correct": hand_correct,
            "accuracy": hand_correct / 10,
        },
        "printed_test": {
            "count": 10,
            "correct": print_correct,
            "accuracy": print_correct / 10,
        },
        "overall_accuracy": total_correct / 20,
        "sample_results": {
            "handwritten": [r.to_dict() for r in hand_results[:3]],
            "printed": [r.to_dict() for r in print_results[:3]],
        }
    }
    
    with open("./output/print_filter_result.json", "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n  ✓ 结果已保存到 ./output/print_filter_result.json")
    
    return output


def export_models():
    """导出TFLite模型"""
    print("\n" + "=" * 60)
    print("  TFLite模型导出")
    print("=" * 60)
    
    # 任务1：导出五行特征提取器
    print("\n[任务1] 导出五行特征提取器...")
    awakening = WuxingAwakening()
    try:
        awakening.export_tflite("./output/wuxing_feature_extractor.tflite")
    except Exception as e:
        print(f"  ! 导出失败（可能需要安装tensorflow）: {e}")
    
    # 任务3：导出印刷体过滤器
    print("\n[任务3] 导出印刷体过滤模型...")
    from ehs_algorithm.print_filter import export_print_filter_tflite, PrintFilter
    model = PrintFilter()
    try:
        export_print_filter_tflite(model, "./output/print_filter.tflite")
    except Exception as e:
        print(f"  ! 导出失败（可能需要安装tensorflow）: {e}")


def main():
    parser = argparse.ArgumentParser(
        description="蚯蚓.手书修仙传 - 核心算法演示",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python main.py                    # 运行所有演示
  python main.py --save-samples     # 保存Mock样本图片
  python main.py --export-models    # 导出TFLite模型
  python main.py --verbose          # 输出详细信息
        """
    )
    parser.add_argument("--save-samples", action="store_true",
                       help="保存Mock样本图片")
    parser.add_argument("--export-models", action="store_true",
                       help="导出TFLite模型")
    parser.add_argument("--verbose", action="store_true",
                       help="输出详细信息")
    parser.add_argument("--task", type=int, choices=[1, 2, 3], default=None,
                       help="仅运行指定任务 (1=五行, 2=金字, 3=印刷体)")
    
    args = parser.parse_args()
    
    # 设置随机种子
    set_seed(42)
    
    # 创建输出目录
    os.makedirs("./output", exist_ok=True)
    
    print("\n" + "╔" + "=" * 58 + "╗")
    print("║" + " " * 12 + "蚯蚓.手书修仙传 - 核心算法演示" + " " * 15 + "║")
    print("║" + " " * 10 + "Eruca HandScript: Cultivation Saga" + " " * 12 + "║")
    print("╚" + "=" * 58 + "╝")
    
    # 运行指定任务或全部
    if args.task is None or args.task == 1:
        demo_task1_wuxing_awakening(
            save_samples=args.save_samples,
            verbose=args.verbose
        )
    
    if args.task is None or args.task == 2:
        demo_task2_golden_sign(verbose=args.verbose)
    
    if args.task is None or args.task == 3:
        demo_task3_print_filter(verbose=args.verbose)
    
    if args.export_models:
        export_models()
    
    print("\n" + "=" * 60)
    print("  所有演示完成！输出文件保存在 ./output/ 目录")
    print("=" * 60 + "\n")


if __name__ == "__main__":
    main()
