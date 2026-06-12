"""
五行伪标签生成器 (Five Element Pseudo Label Generator)

冷启动策略（MVP权宜之计）：
- 真实五行标注需大量人工标注，冷启动阶段不可行
- 采用启发式规则从单字图像提取视觉特征，映射到五行属性
- 等数据飞轮积累真实标注后替换

启发式规则（来自项目经理Kimi指令）：
    笔画竖长横短    → 木↑（舒展生长）
    笔画粗重多顿笔  → 火↑（强烈外放）
    结构方正对称    → 土↑（稳重厚实）
    转折锐利棱角多  → 金↑（刚硬锐利）
    连笔流畅圆润    → 水↑（流动柔和）

输入: 单字灰度图 (64x64)
输出: FiveElementValues [wood, fire, earth, metal, water] ∈ [0, 100]
"""

import numpy as np
import cv2
import os
from typing import Tuple


def generate_pseudo_labels(image_gray: np.ndarray) -> np.ndarray:
    """
    从单字灰度图生成五行伪标签

    Args:
        image_gray: 64x64 uint8 灰度图（背景=0，前景=笔画=255）

    Returns:
        np.ndarray: [5] float32, [wood, fire, earth, metal, water] ∈ [0, 100]
    """
    if image_gray.dtype != np.uint8:
        image_gray = (image_gray * 255).astype(np.uint8)

    # 二值化
    _, binary = cv2.threshold(image_gray, 127, 255, cv2.THRESH_BINARY)

    # 骨架化（用于笔画分析）
    skeleton = cv2.ximgproc.thinning(binary)

    # 计算五行特征
    wood_score  = _calc_wood_feature(binary, skeleton)
    fire_score  = _calc_fire_feature(binary, skeleton)
    earth_score = _calc_earth_feature(binary, skeleton)
    metal_score = _calc_metal_feature(binary, skeleton)
    water_score = _calc_water_feature(binary, skeleton)

    scores = np.array([wood_score, fire_score, earth_score, metal_score, water_score],
                      dtype=np.float32)

    # 归一化到 [0, 100]
    scores = _normalize_to_100(scores)

    return scores


def _calc_wood_feature(binary: np.ndarray, skeleton: np.ndarray) -> float:
    """
    木特征：竖长横短 → 竖笔画占比高
    竖笔画(纵向连通域长宽比>2)占比越高 → 木↑
    """
    h, w = binary.shape

    # 纵向投影（统计每行像素数）
    row_proj = np.sum(binary > 0, axis=1)
    col_proj = np.sum(binary > 0, axis=0)

    # 竖笔画 = 列投影峰值高且窄
    col_peaks = np.sum(col_proj > col_proj.mean() * 1.5)
    row_peaks = np.sum(row_proj > row_proj.mean() * 1.5)

    if col_peaks == 0:
        return 30.0

    # 竖笔画占比高 → 木↑
    vertical_ratio = col_peaks / (col_peaks + row_peaks + 1e-6)

    # 骨架纵向连通域分析
    num_labels, labels, stats, centroids = cv2.connectedComponentsWithStats(skeleton)
    vertical_strokes = 0
    for i in range(1, num_labels):
        _, _, _, cw, ch = stats[i]
        if ch > cw * 2:  # 纵向长条
            vertical_strokes += 1

    score = vertical_ratio * 60 + min(vertical_strokes * 8, 40)
    return float(np.clip(score, 0, 100))


def _calc_fire_feature(binary: np.ndarray, skeleton: np.ndarray) -> float:
    """
    火特征：笔画粗重多顿笔 → 笔画密度高、顿笔点多
    笔画平均宽度大 → 火↑
    """
    # 距离变换（笔画宽度近似）
    dist = cv2.distanceTransform(binary, cv2.DIST_L2, 5)

    # 骨架点上的平均距离 = 笔画半宽
    skeleton_points = skeleton > 0
    if np.sum(skeleton_points) == 0:
        return 30.0

    avg_width = np.mean(dist[skeleton_points]) * 2

    # 顿笔检测：骨架端点处宽度突增
    endpoints = _find_endpoints(skeleton)
    thick_ends = sum(1 for ep in endpoints if dist[ep] > avg_width * 1.3)

    score = avg_width * 15 + thick_ends * 10
    return float(np.clip(score, 0, 100))


def _calc_earth_feature(binary: np.ndarray, skeleton: np.ndarray) -> float:
    """
    土特征：结构方正对称 → 宽高比接近1、重心居中
    字形接近正方形 → 土↑
    """
    # 前景像素坐标
    ys, xs = np.where(binary > 0)
    if len(xs) == 0:
        return 50.0

    # 边界框
    x_min, x_max = xs.min(), xs.max()
    y_min, y_max = ys.min(), ys.max()
    bbox_w = x_max - x_min + 1
    bbox_h = y_max - y_min + 1

    # 宽高比（接近1=方正）
    aspect_ratio = min(bbox_w, bbox_h) / max(bbox_w, bbox_h + 1e-6)

    # 重心偏移（居中=对称）
    cx, cy = xs.mean(), ys.mean()
    img_cx, img_cy = binary.shape[1] / 2, binary.shape[0] / 2
    center_offset = 1 - abs(cx - img_cx) / (binary.shape[1] / 2) - abs(cy - img_cy) / (binary.shape[0] / 2)

    score = aspect_ratio * 50 + center_offset * 30 + 20
    return float(np.clip(score, 0, 100))


def _calc_metal_feature(binary: np.ndarray, skeleton: np.ndarray) -> float:
    """
    金特征：转折锐利棱角多 → 骨架拐点多、角度变化大
    HOG方向变化剧烈 → 金↑
    """
    # 骨架拐点检测（角度变化大的点）
    corners = _find_skeleton_corners(skeleton)

    # 骨架方向变化标准差
    directions = _extract_directions(skeleton)
    if len(directions) < 2:
        return 20.0

    # 方向变化剧烈 = 棱角多
    direction_changes = np.abs(np.diff(directions))
    sharpness = np.mean(direction_changes) / (np.pi / 4)  # 归一化到45度

    score = len(corners) * 5 + sharpness * 40
    return float(np.clip(score, 0, 100))


def _calc_water_feature(binary: np.ndarray, skeleton: np.ndarray) -> float:
    """
    水特征：连笔流畅圆润 → 骨架平滑、曲率连续
    方向变化连续无突变 → 水↑
    """
    # 骨架方向变化标准差（小=平滑=水↑）
    directions = _extract_directions(skeleton)
    if len(directions) < 2:
        return 50.0

    direction_changes = np.abs(np.diff(directions))
    smoothness = 1 / (1 + np.std(direction_changes) * 2)  # 越小越平滑

    # 笔画连贯性（连通域数量少=连笔多）
    num_labels, _, _, _ = cv2.connectedComponentsWithStats(skeleton)
    stroke_count = num_labels - 1  # 减去背景

    # 笔画少 = 连笔多 = 水↑
    connectivity = max(0, 1 - stroke_count / 10)

    score = smoothness * 50 + connectivity * 30 + 20
    return float(np.clip(score, 0, 100))


# ==================== 辅助函数 ====================

def _normalize_to_100(scores: np.ndarray) -> np.ndarray:
    """归一化到 [0, 100]，保持相对关系"""
    min_val = scores.min()
    max_val = scores.max()
    if max_val - min_val < 1e-6:
        return np.full(5, 50.0, dtype=np.float32)
    normalized = (scores - min_val) / (max_val - min_val) * 80 + 10  # 保留10-90避免极端
    return normalized.astype(np.float32)


def _find_endpoints(skeleton: np.ndarray) -> list:
    """找骨架端点（8邻域只有一个前景邻居）"""
    endpoints = []
    h, w = skeleton.shape
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            if skeleton[y, x] > 0:
                neighbors = np.sum(skeleton[y-1:y+2, x-1:x+2] > 0) - 1
                if neighbors == 1:
                    endpoints.append((y, x))
    return endpoints


def _find_skeleton_corners(skeleton: np.ndarray) -> list:
    """找骨架拐点（方向突变点）"""
    # 简化版：找 Harris 角点
    sk_float = skeleton.astype(np.float32)
    dst = cv2.cornerHarris(sk_float, 3, 3, 0.04)
    corners = np.where(dst > 0.01 * dst.max())
    return list(zip(corners[0], corners[1]))


def _extract_directions(skeleton: np.ndarray) -> np.ndarray:
    """提取骨架主方向序列"""
    # 用轮廓切线方向近似
    contours, _ = cv2.findContours(
        skeleton.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )
    directions = []
    for contour in contours:
        if len(contour) < 3:
            continue
        for i in range(len(contour) - 1):
            dx = contour[i + 1][0][0] - contour[i][0][0]
            dy = contour[i + 1][0][1] - contour[i][0][1]
            direction = np.arctan2(dy, dx)
            directions.append(direction)
    return np.array(directions) if directions else np.array([0.0])


# ==================== 批量处理入口 ====================

def batch_generate_labels(image_dir: str, output_dir: str):
    """
    批量生成五行伪标签

    Args:
        image_dir: 单字图片目录（PNG灰度图）
        output_dir: 输出JSON目录
    """
    import os
    import json
    from pathlib import Path

    os.makedirs(output_dir, exist_ok=True)
    results = {}

    for img_path in Path(image_dir).glob("*.png"):
        img = cv2.imread(str(img_path), cv2.IMREAD_GRAYSCALE)
        if img is None:
            continue
        img = cv2.resize(img, (64, 64))

        labels = generate_pseudo_labels(img)
        results[img_path.stem] = {
            "wood": round(float(labels[0]), 2),
            "fire": round(float(labels[1]), 2),
            "earth": round(float(labels[2]), 2),
            "metal": round(float(labels[3]), 2),
            "water": round(float(labels[4]), 2),
            "dominant": _get_dominant(labels)
        }

    output_path = os.path.join(output_dir, "pseudo_labels.json")
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print(f"[PseudoLabel] 生成 {len(results)} 条伪标签 → {output_path}")


def _get_dominant(labels: np.ndarray) -> str:
    """获取主导五行"""
    names = ["wood", "fire", "earth", "metal", "water"]
    return names[int(np.argmax(labels))]


if __name__ == "__main__":
    import json
    
    input_dir = "data/owner_handwriting"
    output_path = "data/owner_pseudo_labels.json"
    
    os.makedirs("data", exist_ok=True)
    
    # 获取所有PNG文件（不采样，全量处理）
    all_files = [f for f in sorted(os.listdir(input_dir)) if f.endswith('.png')]
    sampled_files = all_files  # 处理全部样本
    
    labels_dict = {}
    for i, filename in enumerate(sampled_files):
        img_path = os.path.join(input_dir, filename)
        img = cv2.imread(img_path, cv2.IMREAD_GRAYSCALE)
        if img is None:
            continue
            
        labels = generate_pseudo_labels(img)
        labels_dict[filename] = {
            "wood": float(labels[0]),
            "fire": float(labels[1]),
            "earth": float(labels[2]),
            "metal": float(labels[3]),
            "water": float(labels[4])
        }
        
        if (i + 1) % 50 == 0:
            print(f"已处理 {i+1}/{len(sampled_files)} 个样本")
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(labels_dict, f, ensure_ascii=False, indent=2)
    
    print(f"\n伪标签已保存到 {output_path}")
    print(f"采样数量: {len(labels_dict)}")
