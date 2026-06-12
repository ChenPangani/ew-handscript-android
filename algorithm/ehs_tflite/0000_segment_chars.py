import cv2
import numpy as np
import os
import shutil

def preprocess_page(image_path):
    """页面级预处理"""
    img = cv2.imread(image_path)
    if img is None:
        return None
    
    # 1. 灰度化
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # 2. 高斯去噪
    blurred = cv2.GaussianBlur(gray, (5, 5), 0)
    
    # 3. 自适应二值化
    binary = cv2.adaptiveThreshold(
        blurred, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY_INV, 15, 10
    )
    
    # 4. 形态学开运算（去除墨点噪点）
    kernel = np.ones((3, 3), np.uint8)
    opened = cv2.morphologyEx(binary, cv2.MORPH_OPEN, kernel, iterations=1)
    
    # 5. 倾斜校正（霍夫线检测）
    lines = cv2.HoughLines(opened, 1, np.pi / 180, 200)
    if lines is not None:
        angles = []
        for line in lines:
            rho, theta = line[0]
            angle = theta * 180 / np.pi
            if 80 < angle < 100:  # 接近水平的线
                angles.append(angle)
        
        if angles:
            avg_angle = np.mean(angles)
            if avg_angle > 90:
                avg_angle = avg_angle - 180
            
            # 旋转校正
            h, w = opened.shape
            center = (w // 2, h // 2)
            M = cv2.getRotationMatrix2D(center, avg_angle, 1.0)
            opened = cv2.warpAffine(opened, M, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_CONSTANT, borderValue=0)
    
    return opened

def segment_rows(binary_img, output_dir, page_name):
    """行分割"""
    # 1. 横向膨胀（连接同一行字）
    kernel = np.ones((1, 5), np.uint8)
    dilated = cv2.dilate(binary_img, kernel, iterations=2)
    
    # 2. 轮廓检测
    contours, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    
    # 3. 按y坐标排序，过滤小碎片
    row_rois = []
    for contour in contours:
        x, y, w, h = cv2.boundingRect(contour)
        if h > 20:  # 过滤高度<<20px的碎片
            row_rois.append((y, x, y + h, x + w))
    
    # 按y坐标排序
    row_rois.sort(key=lambda r: r[0])
    
    # 保存每行ROI
    rows = []
    os.makedirs(output_dir, exist_ok=True)
    for i, (y1, x1, y2, x2) in enumerate(row_rois):
        row_img = binary_img[y1:y2, x1:x2]
        row_path = os.path.join(output_dir, f"{page_name}_row_{i:03d}.jpg")
        cv2.imwrite(row_path, row_img)
        rows.append(row_img)
    
    return rows

def segment_chars_from_row(row_img, output_dir, page_name, row_idx):
    """从行ROI分割单字"""
    # 1. 垂直膨胀（连接同一字笔画）
    kernel = np.ones((3, 1), np.uint8)
    dilated = cv2.dilate(row_img, kernel, iterations=1)
    
    # 2. 轮廓检测
    contours, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    
    # 3. 按x坐标排序，过滤
    char_boxes = []
    for contour in contours:
        x, y, w, h = cv2.boundingRect(contour)
        area = w * h
        
        # 过滤条件
        if area < 100:  # 碎片
            continue
        aspect_ratio = w / h if h > 0 else 0
        if aspect_ratio > 3.0 or aspect_ratio < 0.3:  # 粘连多字或笔画碎片
            continue
        
        char_boxes.append((x, y, w, h))
    
    # 按x坐标排序
    char_boxes.sort(key=lambda b: b[0])
    
    # 4. 保存每个有效字框为64×64灰度PNG
    char_count = 0
    os.makedirs(output_dir, exist_ok=True)
    for j, (x, y, w, h) in enumerate(char_boxes):
        char_img = row_img[y:y+h, x:x+w]
        
        # 等比例缩放到56×56
        scale = 56 / max(w, h)
        new_w, new_h = int(w * scale), int(h * scale)
        resized = cv2.resize(char_img, (new_w, new_h), interpolation=cv2.INTER_AREA)
        
        # 居中放置在64×64黑底画布上
        final_img = np.zeros((64, 64), dtype=np.uint8)
        offset_x = (64 - new_w) // 2
        offset_y = (64 - new_h) // 2
        final_img[offset_y:offset_y+new_h, offset_x:offset_x+new_w] = resized
        
        char_path = os.path.join(output_dir, f"char_{page_name}_{row_idx:03d}_{j:03d}.png")
        cv2.imwrite(char_path, final_img)
        char_count += 1
    
    return char_count

def main():
    input_dir = "/Users/iMac/Documents/Tianjia_handwriting"
    output_base = "/Users/iMac/Projects/ew-handscript-android/algorithm/ehs_tflite/data"
    
    row_output_dir = os.path.join(output_base, "rows")
    char_output_dir = os.path.join(output_base, "owner_handwriting")
    
    # 清空旧数据
    if os.path.exists(char_output_dir):
        shutil.rmtree(char_output_dir)
    if os.path.exists(row_output_dir):
        shutil.rmtree(row_output_dir)
    
    os.makedirs(row_output_dir, exist_ok=True)
    os.makedirs(char_output_dir, exist_ok=True)
    
    total_pages = 0
    total_rows = 0
    total_chars = 0
    
    # 处理每个页面
    for filename in sorted(os.listdir(input_dir)):
        if not filename.endswith('.jpg'):
            continue
        
        page_path = os.path.join(input_dir, filename)
        page_name = os.path.splitext(filename)[0]
        
        print(f"处理页面: {filename}")
        
        # Step 1: 页面级预处理
        binary_img = preprocess_page(page_path)
        if binary_img is None:
            continue
        
        total_pages += 1
        
        # Step 2: 行分割
        rows = segment_rows(binary_img, row_output_dir, page_name)
        total_rows += len(rows)
        
        # Step 3: 字分割
        for i, row in enumerate(rows):
            char_count = segment_chars_from_row(row, char_output_dir, page_name, i)
            total_chars += char_count
    
    # Step 4: 质量过滤 - 生成质检报告
    print("\n" + "="*60)
    print("【质检报告】")
    print("="*60)
    print(f"总页数: {total_pages}")
    print(f"总行数: {total_rows}")
    print(f"总单字数: {total_chars}")
    if total_pages > 0:
        print(f"平均每页字数: {total_chars // total_pages}")
    print("="*60)

if __name__ == "__main__":
    main()