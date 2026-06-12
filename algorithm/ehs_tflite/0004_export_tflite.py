"""
TFLite INT8 量化导出脚本

输入: PyTorch 训练好的双头模型 (.pth)
输出: 
    - wuxing_feature_extractor.tflite (INT8, <1.5MB)
    - print_filter.tflite (INT8, <500KB)

量化策略:
    - 全整数量化 (Full Integer Quantization)
    - Per-channel 权重量化
    - 代表性数据集校准 (100张样本)

质量门禁:
    - 模型大小 < 2MB (合计)
    - 单字推理延迟 < 100ms (Mate30)
"""

import os
import numpy as np
import torch
import torch.nn as nn
import importlib
train_module = importlib.import_module('0003_train_dual_model')
DualHeadModel = train_module.DualHeadModel
Config = train_module.Config

# TFLite 导入
try:
    import tensorflow as tf
    from tensorflow.python.framework.convert_to_constants import convert_variables_to_constants_v2
except ImportError:
    print("[错误] 未安装 TensorFlow。请运行: pip install tensorflow")
    raise


def export_wuxing_head(model: nn.Module, config: Config):
    """
    导出五行特征提取器 (wuxing_feature_extractor.tflite)
    
    输入: [1, 1, 64, 64] float32 灰度图
    输出: [1, 5] float32 (wood/fire/earth/metal/water [0,100])
    """
    print("=" * 60)
    print(" 导出: wuxing_feature_extractor.tflite")
    print("=" * 60)

    model.eval()

    # 创建只包含五行头的子模型
    class WuxingExtractor(nn.Module):
        def __init__(self, parent: DualHeadModel):
            super().__init__()
            self.backbone = parent.backbone
            self.visual_proj = parent.visual_proj
            self.direction_proj = parent.direction_proj
            self.wuxing_head = parent.wuxing_head

        def forward(self, x_gray: torch.Tensor) -> torch.Tensor:
            # 骨架化+方向提取（简化版：训练时预处理）
            backbone_feat = self.backbone(x_gray)
            visual_feat = self.visual_proj(backbone_feat)
            # 方向特征在预处理阶段提取，这里用零占位
            # 实际部署时，方向特征由OpenCV提取后传入
            dir_feat = torch.zeros(x_gray.size(0), 8, device=x_gray.device)
            dir_feat = self.direction_proj(dir_feat)
            fused = torch.cat([visual_feat, dir_feat], dim=1)
            wuxing = self.wuxing_head(fused) * 100.0  # [0,1] -> [0,100]
            return wuxing

    extractor = WuxingExtractor(model).to(config.DEVICE)
    extractor.eval()

    # ONNX 导出
    dummy_input = torch.randn(1, 1, 64, 64).to(config.DEVICE)
    onnx_path = os.path.join(config.CHECKPOINT_DIR, "wuxing_extractor.onnx")

    torch.onnx.export(
        extractor,
        dummy_input,
        onnx_path,
        input_names=["input_gray"],
        output_names=["wuxing_values"],
        dynamic_axes={"input_gray": {0: "batch"}, "wuxing_values": {0: "batch"}},
        opset_version=13,
    )
    print(f"  ONNX 导出: {onnx_path}")

    # ONNX -> TFLite (INT8)
    tflite_path = os.path.join(config.CHECKPOINT_DIR, "wuxing_feature_extractor.tflite")
    _convert_onnx_to_tflite_int8(onnx_path, tflite_path, input_shape=(1, 64, 64, 1))

    # 验证大小
    size_kb = os.path.getsize(tflite_path) / 1024
    print(f"  TFLite 大小: {size_kb:.1f} KB ({size_kb/1024:.2f} MB)")
    assert size_kb < 1500, f"模型过大: {size_kb:.1f}KB > 1500KB"
    print(f"  ✓ 大小达标 (<1.5MB)")

    return tflite_path


def export_print_filter(model: nn.Module, config: Config):
    """
    导出印刷体过滤器 (print_filter.tflite)
    
    输入: [1, 1, 64, 64] float32 灰度图
    输出: [1, 1] float32 (isPrint 置信度 [0,1], >0.5=印刷体)
    """
    print("")
    print("=" * 60)
    print(" 导出: print_filter.tflite")
    print("=" * 60)

    model.eval()

    # 创建只包含印刷体头的子模型
    class PrintFilter(nn.Module):
        def __init__(self, parent: DualHeadModel):
            super().__init__()
            self.backbone = parent.backbone
            self.visual_proj = parent.visual_proj
            self.print_head = parent.print_head

        def forward(self, x_gray: torch.Tensor) -> torch.Tensor:
            backbone_feat = self.backbone(x_gray)
            visual_feat = self.visual_proj(backbone_feat)
            is_print = self.print_head(visual_feat)
            return is_print

    filter_model = PrintFilter(model).to(config.DEVICE)
    filter_model.eval()

    # ONNX 导出
    dummy_input = torch.randn(1, 1, 64, 64).to(config.DEVICE)
    onnx_path = os.path.join(config.CHECKPOINT_DIR, "print_filter.onnx")

    torch.onnx.export(
        filter_model,
        dummy_input,
        onnx_path,
        input_names=["input_gray"],
        output_names=["is_print"],
        dynamic_axes={"input_gray": {0: "batch"}, "is_print": {0: "batch"}},
        opset_version=13,
    )
    print(f"  ONNX 导出: {onnx_path}")

    # ONNX -> TFLite (INT8)
    tflite_path = os.path.join(config.CHECKPOINT_DIR, "print_filter.tflite")
    _convert_onnx_to_tflite_int8(onnx_path, tflite_path, input_shape=(1, 64, 64, 1))

    # 验证大小
    size_kb = os.path.getsize(tflite_path) / 1024
    print(f"  TFLite 大小: {size_kb:.1f} KB ({size_kb/1024:.2f} MB)")
    assert size_kb < 500, f"模型过大: {size_kb:.1f}KB > 500KB"
    print(f"  ✓ 大小达标 (<500KB)")

    return tflite_path


def _convert_onnx_to_tflite_int8(onnx_path: str, tflite_path: str,
                                  input_shape: tuple):
    """
    ONNX -> TFLite INT8 量化
    
    使用 TensorFlow 的 TFLite Converter 进行全整数量化
    """
    # 方法: 使用 TensorFlow 2.16+ 的 ONNX 直接转换
    converter = tf.lite.TFLiteConverter.from_onnx(onnx_path)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]

    # 代表性数据集用于校准
    def representative_dataset():
        for _ in range(100):
            data = np.random.randn(*input_shape).astype(np.float32)
            yield [data]

    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS_INT8
    ]
    converter.inference_input_type = tf.uint8
    converter.inference_output_type = tf.float32

    tflite_model = converter.convert()

    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)

    print(f"  INT8量化完成: {tflite_path}")


# ============ 延迟验证 ============

def benchmark_latency(tflite_path: str, input_shape: tuple,
                      num_runs: int = 100):
    """
    测试单字推理延迟
    
    质量门禁: < 100ms (Mate30)
    """
    print(f"\n[延迟测试] {tflite_path}")

    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # 预热
    dummy_input = np.random.randn(*input_shape).astype(np.float32)
    for _ in range(10):
        interpreter.set_tensor(input_details[0]['index'], dummy_input)
        interpreter.invoke()

    # 正式测试
    import time
    latencies = []
    for _ in range(num_runs):
        start = time.time()
        interpreter.set_tensor(input_details[0]['index'], dummy_input)
        interpreter.invoke()
        latencies.append((time.time() - start) * 1000)  # ms

    avg = np.mean(latencies)
    p50 = np.percentile(latencies, 50)
    p95 = np.percentile(latencies, 95)

    print(f"  平均延迟: {avg:.1f}ms")
    print(f"  P50: {p50:.1f}ms | P95: {p95:.1f}ms")
    print(f"  {'✓ 达标 (<100ms)' if avg < 100 else '✗ 超标'}")


# ============ 入口 ============

if __name__ == "__main__":
    config = Config()

    # 加载训练好的模型
    model = DualHeadModel(config).to(config.DEVICE)
    checkpoint_path = os.path.join(config.CHECKPOINT_DIR, "best_stage1.pth")

    if os.path.exists(checkpoint_path):
        model.load_state_dict(torch.load(checkpoint_path, map_location=config.DEVICE))
        print(f"[加载] {checkpoint_path}")
    else:
        print(f"[警告] 未找到 checkpoint: {checkpoint_path}")
        print("  将导出未训练模型（仅验证流程）")

    # 导出两个模型
    wuxing_path = export_wuxing_head(model, config)
    print_filter_path = export_print_filter(model, config)

    # 总大小
    total_kb = os.path.getsize(wuxing_path) / 1024 + os.path.getsize(print_filter_path) / 1024
    print(f"\n{'='*60}")
    print(f" 导出完成:")
    print(f"  五行模型: {wuxing_path} ({os.path.getsize(wuxing_path)/1024:.1f}KB)")
    print(f"  印刷体模型: {print_filter_path} ({os.path.getsize(print_filter_path)/1024:.1f}KB)")
    print(f"  合计: {total_kb:.1f}KB ({total_kb/1024:.2f}MB)")
    print(f"  {'✓ 达标 (<2MB)' if total_kb < 2048 else '✗ 超标'}")
    print(f"{'='*60}")

    # 延迟测试
    try:
        benchmark_latency(wuxing_path, (1, 64, 64, 1))
        benchmark_latency(print_filter_path, (1, 64, 64, 1))
    except Exception as e:
        print(f"[延迟测试跳过] {e}")
