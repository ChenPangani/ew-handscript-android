#!/bin/bash
echo "🧹 开始系统清理..."

# 1. 停止 Gradle 守护进程
echo "   停止 Gradle 守护进程..."
./gradlew --stop 2>/dev/null

# 2. 删除项目内编译产物
echo "   删除项目编译产物..."
rm -rf app/build/ .gradle/ build/

# 3. 删除全局 Gradle 缓存
echo "   删除全局 Gradle 缓存..."
rm -rf ~/.gradle/caches/build-cache-1/

# 4. 删除 Python 缓存（如果存在）
echo "   删除 Python 缓存..."
find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null
find . -name "*.pyc" -delete 2>/dev/null

# 5. 删除算法模块临时图片（如果存在）
echo "   删除临时图片..."
rm -rf algorithm/ehs_tflite/data/ 2>/dev/null

# 6. 确认关键文件状态
echo ""
echo "🔍 关键文件检查："

# 检查模型文件
MODEL_FILE="app/src/main/assets/models/wuxing_feature_extractor.tflite"
if [ -f "$MODEL_FILE" ]; then
    MODEL_SIZE=$(du -sh "$MODEL_FILE" | cut -f1)
    echo "✅ $MODEL_FILE 存在 ($MODEL_SIZE)"
else
    echo "❌ $MODEL_FILE 缺失！"
fi

# 检查源代码目录
if [ -d "app/src/main/java/com/ew/handscript" ]; then
    SRC_COUNT=$(find app/src/main/java -name "*.kt" | wc -l)
    echo "✅ 源代码完整（$SRC_COUNT 个 Kotlin 文件）"
else
    echo "❌ 源代码目录缺失！"
fi

# 输出项目大小
echo ""
echo "✅ 清理完成！"
echo "📦 项目当前大小："
du -sh .
