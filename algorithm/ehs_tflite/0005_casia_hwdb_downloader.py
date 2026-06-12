"""
CASIA-HWDB 数据集下载器

手写体数据来源（公开免费）：
    - CASIA-HWDB1.1: 在线手写汉字，约3,755,221样本，3755类常用字
    - CASIA-HWDB2.0: 离线手写汉字，约1,245,490样本
    - 下载地址: http://www.nlpr.ia.ac.cn/databases/handwriting/Home.html

印刷体数据来源（开源）：
    - 思源宋体 (Noto Serif CJK)
    - 思源黑体 (Noto Sans CJK)
    - 使用 PIL 渲染单字图片作为负样本

输出目录结构:
    data/
    ├── casia_hwdb/          # 手写体样本 (PNG灰度图)
    │   ├── train/
    │   └── test/
    ├── print_fonts/         # 印刷体样本 (PNG灰度图)
    │   ├── noto_serif/
    │   └── noto_sans/
    └── pseudo_labels.json   # 五行伪标签
"""

import os
import json
import urllib.request
import zipfile
import shutil
from pathlib import Path
from tqdm import tqdm
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont


# ============ 配置 ============

DATA_ROOT = "./data"
CASIA_URLS = {
    "HWDB1.1trn": "http://www.nlpr.ia.ac.cn/databases/Download/6d60d7c55fe3480594abafee0f75ed6e/HWDB1.1trn_gnt.zip",
    "HWDB1.1tst": "http://www.nlpr.ia.ac.cn/databases/Download/6d60d7c55fe3480594abafee0f75ed6e/HWDB1.1tst_gnt.zip",
}

# 常用汉字3500字（用于印刷体渲染）
COMMON_CHARS = """的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发年动同工也能下过子说产种面而方后多定行学法所民得经十三之进着等同部度家电力里如水化高自二理起小物现实加 """


def download_file(url: str, dest_path: str):
    """带进度条的文件下载"""
    print(f"[下载] {url}")
    print(f"  目标: {dest_path}")

    os.makedirs(os.path.dirname(dest_path), exist_ok=True)

    req = urllib.request.Request(url)
    req.add_header('User-Agent', 'Mozilla/5.0')

    with urllib.request.urlopen(req, timeout=60) as response:
        total_size = int(response.headers.get('Content-Length', 0))
        block_size = 8192

        with open(dest_path, 'wb') as f, tqdm(
            total=total_size, unit='B', unit_scale=True, desc=os.path.basename(dest_path)
        ) as pbar:
            while True:
                chunk = response.read(block_size)
                if not chunk:
                    break
                f.write(chunk)
                pbar.update(len(chunk))

    print(f"  ✓ 完成")


def extract_gnt(gnt_path: str, output_dir: str, max_samples: int = 10000):
    """
    解压 GNT 格式（CASIA-HWDB 专用格式）并转换为 PNG

    GNT 格式说明:
        每个样本: [tag_code(2B)] [width(2B)] [height(2B)] [bitmap_data]
        tag_code: GB2312 编码
    """
    print(f"[解压] {gnt_path} → {output_dir}")
    os.makedirs(output_dir, exist_ok=True)

    sample_count = 0
    with open(gnt_path, 'rb') as f:
        with tqdm(total=max_samples, desc="解压样本") as pbar:
            while sample_count < max_samples:
                # 读取头部
                header = f.read(10)
                if len(header) < 10:
                    break

                tag_code = int.from_bytes(header[0:2], 'little')
                width = int.from_bytes(header[2:4], 'little')
                height = int.from_bytes(header[4:6], 'little')

                if width <= 0 or height <= 0 or width > 512 or height > 512:
                    break

                # 读取位图数据
                bitmap_size = width * height
                bitmap_data = f.read(bitmap_size)
                if len(bitmap_data) < bitmap_size:
                    break

                # 转换为 numpy 数组
                img_array = np.frombuffer(bitmap_data, dtype=np.uint8).reshape(height, width)

                # 保存为 PNG
                char_str = f"u{tag_code:04X}"
                img_path = os.path.join(output_dir, f"{char_str}_{sample_count}.png")
                cv2.imwrite(img_path, img_array)

                sample_count += 1
                pbar.update(1)

    print(f"  ✓ 提取 {sample_count} 样本")
    return sample_count


def render_print_fonts(output_dir: str, chars: str = COMMON_CHARS,
                       font_size: int = 64, img_size: int = 64):
    """
    使用思源字体渲染印刷体样本

    Args:
        output_dir: 输出目录
        chars: 要渲染的字符集
        font_size: 字体大小
        img_size: 输出图片尺寸
    """
    print(f"[渲染] 印刷体样本 → {output_dir}")
    os.makedirs(output_dir, exist_ok=True)

    # 尝试加载系统字体
    font_paths = [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",  # Linux
        "/System/Library/Fonts/PingFang.ttc",                      # macOS
        "C:\\Windows\\Fonts\\msyh.ttc",                             # Windows
        "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",            # WQY
    ]

    font = None
    for fp in font_paths:
        if os.path.exists(fp):
            try:
                font = ImageFont.truetype(fp, font_size)
                print(f"  使用字体: {fp}")
                break
            except:
                continue

    if font is None:
        print("  [警告] 未找到中文字体，使用默认字体")
        font = ImageFont.load_default()

    rendered = 0
    for i, char in enumerate(tqdm(chars, desc="渲染印刷体")):
        # 创建灰度图
        img = Image.new('L', (img_size, img_size), 255)  # 白底
        draw = ImageDraw.Draw(img)

        # 居中绘制
        bbox = draw.textbbox((0, 0), char, font=font)
        text_w = bbox[2] - bbox[0]
        text_h = bbox[3] - bbox[1]
        x = (img_size - text_w) // 2
        y = (img_size - text_h) // 2 - bbox[1]

        draw.text((x, y), char, fill=0, font=font)  # 黑字

        # 反转为黑底白字（与手写体格式一致）
        img_array = np.array(img)
        img_array = 255 - img_array  # 反转

        # 保存
        output_path = os.path.join(output_dir, f"print_{i:05d}_{char}.png")
        cv2.imwrite(output_path, img_array)
        rendered += 1

    print(f"  ✓ 渲染 {rendered} 个印刷体样本")
    return rendered


def generate_all_pseudo_labels(data_dir: str, output_path: str):
    """
    为所有样本生成五行伪标签
    """
    print(f"[伪标签] 生成中...")

    from generate_pseudo_labels import generate_pseudo_labels

    results = {}
    img_paths = list(Path(data_dir).rglob("*.png"))

    for img_path in tqdm(img_paths, desc="伪标签"):
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

    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print(f"  ✓ 生成 {len(results)} 条伪标签 → {output_path}")


def _get_dominant(labels: np.ndarray) -> str:
    names = ["wood", "fire", "earth", "metal", "water"]
    return names[int(np.argmax(labels))]


# ============ 入口 ============

def main():
    print("=" * 60)
    print(" CASIA-HWDB 数据集准备")
    print("=" * 60)

    os.makedirs(DATA_ROOT, exist_ok=True)

    # 1. 下载手写体数据集
    casia_dir = os.path.join(DATA_ROOT, "casia_hwdb")
    os.makedirs(casia_dir, exist_ok=True)

    for name, url in CASIA_URLS.items():
        zip_path = os.path.join(DATA_ROOT, f"{name}.zip")

        # 下载
        if not os.path.exists(zip_path):
            try:
                download_file(url, zip_path)
            except Exception as e:
                print(f"  [跳过] 下载失败: {e}")
                print(f"  请手动下载: {url}")
                continue
        else:
            print(f"[已有] {zip_path}")

        # 解压
        gnt_path = zip_path.replace(".zip", ".gnt")
        if os.path.exists(zip_path) and not os.path.exists(gnt_path):
            print(f"[解压] {zip_path}")
            with zipfile.ZipFile(zip_path, 'r') as z:
                z.extractall(DATA_ROOT)

        # GNT -> PNG
        output_subdir = os.path.join(casia_dir, name)
        if not os.path.exists(output_subdir):
            if os.path.exists(gnt_path):
                extract_gnt(gnt_path, output_subdir)
            else:
                print(f"  [跳过] 未找到 GNT 文件")

    # 2. 渲染印刷体样本
    print_dir = os.path.join(DATA_ROOT, "print_fonts")
    if not os.path.exists(print_dir) or len(list(Path(print_dir).glob("*.png"))) == 0:
        render_print_fonts(print_dir)
    else:
        print(f"[已有] 印刷体样本")

    # 3. 生成五行伪标签
    pseudo_label_path = os.path.join(DATA_ROOT, "pseudo_labels.json")
    if not os.path.exists(pseudo_label_path):
        all_img_dir = os.path.join(DATA_ROOT, "all_images")
        os.makedirs(all_img_dir, exist_ok=True)

        # 合并所有图片到一个目录（便于批量处理）
        for subdir in [casia_dir, print_dir]:
            if os.path.exists(subdir):
                for img_path in Path(subdir).rglob("*.png"):
                    shutil.copy2(str(img_path), os.path.join(all_img_dir, img_path.name))

        generate_all_pseudo_labels(all_img_dir, pseudo_label_path)
    else:
        print(f"[已有] 伪标签: {pseudo_label_path}")

    print("\n" + "=" * 60)
    print(" 数据集准备完成")
    print("=" * 60)
    print(f"  手写体: {casia_dir}")
    print(f"  印刷体: {print_dir}")
    print(f"  伪标签: {pseudo_label_path}")
    print("\n[下一步] 运行 0003_train_dual_model.py 开始训练")


if __name__ == "__main__":
    main()
