"""
笔画方向直方图提取器 (Stroke Direction Histogram Extractor)

功能：
- 从单字灰度图提取8方向笔画方向直方图
- 用于与MobileNetV3视觉特征融合（128+8=136维）

技术路线：
    1. 骨架化（Zhang-Suen thinning）
    2. 骨架点邻域方向分析（8方向模板）
    3. 直方图归一化

输出: [8] float32 方向直方图（与视觉特征拼接后输入FC层）
"""

import numpy as np
import cv2
from typing import Tuple


# 8方向定义（顺时针，从正上方开始）
DIRECTIONS_8 = [
    (0, -1),    # 0: 北（上）
    (1, -1),    # 1: 东北
    (1, 0),     # 2: 东（右）
    (1, 1),     # 3: 东南
    (0, 1),     # 4: 南（下）
    (-1, 1),    # 5: 西南
    (-1, 0),    # 6: 西（左）
    (-1, -1),   # 7: 西北
]


def extract_direction_histogram(image_gray: np.ndarray) -> np.ndarray:
    """
    提取8方向笔画方向直方图

    Args:
        image_gray: 64x64 uint8 灰度图

    Returns:
        np.ndarray: [8] float32 方向直方图（归一化和为1）
    """
    if image_gray.dtype != np.uint8:
        image_gray = (image_gray * 255).astype(np.uint8)

    # 二值化
    _, binary = cv2.threshold(image_gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    # 骨架化
    skeleton = _zhang_suen_thinning(binary)

    # 提取方向直方图
    histogram = np.zeros(8, dtype=np.float32)

    h, w = skeleton.shape
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            if skeleton[y, x] == 0:
                continue

            # 统计该骨架点8邻域内其他骨架点的方向
            for dir_idx, (dx, dy) in enumerate(DIRECTIONS_8):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and skeleton[ny, nx] > 0:
                    histogram[dir_idx] += 1

    # 归一化
    total = histogram.sum()
    if total > 0:
        histogram /= total

    return histogram


def _zhang_suen_thinning(binary: np.ndarray) -> np.ndarray:
    """
    Zhang-Suen 骨架化算法

    将二值图像中的前景区域细化成单像素宽的骨架
    """
    img = (binary > 0).astype(np.uint8)
    skeleton = img.copy()

    changing = True
    while changing:
        changing = False
        to_remove = []

        # 第一子迭代
        for y in range(1, skeleton.shape[0] - 1):
            for x in range(1, skeleton.shape[1] - 1):
                if skeleton[y, x] == 0:
                    continue

                p = _get_neighbors(skeleton, x, y)
                if _zs_condition_1(p) and _zs_condition_2(p) and _zs_condition_3(p):
                    to_remove.append((x, y))

        for x, y in to_remove:
            skeleton[y, x] = 0
        changing = len(to_remove) > 0

        to_remove = []

        # 第二子迭代
        for y in range(1, skeleton.shape[0] - 1):
            for x in range(1, skeleton.shape[1] - 1):
                if skeleton[y, x] == 0:
                    continue

                p = _get_neighbors(skeleton, x, y)
                if _zs_condition_1(p) and _zs_condition_2(p) and _zs_condition_4(p):
                    to_remove.append((x, y))

        for x, y in to_remove:
            skeleton[y, x] = 0
        changing = changing or len(to_remove) > 0

    return (skeleton * 255).astype(np.uint8)


def _get_neighbors(img: np.ndarray, x: int, y: int) -> list:
    """获取8邻域像素值 [p1, p2, ..., p8]（顺时针从上方开始）"""
    return [
        int(img[y-1, x]),     # p1: 上
        int(img[y-1, x+1]),   # p2: 右上
        int(img[y, x+1]),     # p3: 右
        int(img[y+1, x+1]),   # p4: 右下
        int(img[y+1, x]),     # p5: 下
        int(img[y+1, x-1]),   # p6: 左下
        int(img[y, x-1]),     # p7: 左
        int(img[y-1, x-1]),   # p8: 左上
    ]


def _zs_condition_1(p: list) -> bool:
    """条件1：非零邻居数在2到6之间"""
    non_zero = sum(1 for v in p if v > 0)
    return 2 <= non_zero <= 6


def _zs_condition_2(p: list) -> bool:
    """条件2：0→1转换次数为1"""
    transitions = sum(1 for i in range(8) if p[i] == 0 and p[(i+1) % 8] > 0)
    return transitions == 1


def _zs_condition_3(p: list) -> bool:
    """条件3：p2*p4*p6 = 0（第一子迭代）"""
    return p[1] * p[3] * p[5] == 0


def _zs_condition_4(p: list) -> bool:
    """条件4：p4*p6*p8 = 0（第二子迭代）"""
    return p[3] * p[5] * p[7] == 0


# ==================== 融合接口 ====================

def fuse_features(visual_features: np.ndarray,
                  direction_histogram: np.ndarray) -> np.ndarray:
    """
    融合视觉特征与笔画方向特征

    Args:
        visual_features: [128] MobileNetV3-Small输出
        direction_histogram: [8] 方向直方图

    Returns:
        np.ndarray: [136] 融合特征
    """
    return np.concatenate([visual_features, direction_histogram]).astype(np.float32)


if __name__ == "__main__":
    # 测试
    test_img = np.zeros((64, 64), dtype=np.uint8)
    cv2.putText(test_img, "道", (10, 50), cv2.FONT_HERSHEY_SIMPLEX, 1.5, 255, 2)

    hist = extract_direction_histogram(test_img)
    print(f"方向直方图: {hist}")
    print(f"和: {hist.sum():.4f} (应为1.0)")
