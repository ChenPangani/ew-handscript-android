package com.ew.handscript.ui.screens.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import timber.log.Timber
import kotlin.math.max

/**
 * 扫描分割器（单字级优化版）
 * 
 * 核心算法：
 * 1. 行检测：水平投影法检测文本行区域
 * 2. 腐蚀预处理：断开粘连字
 * 3. 行内单字分割：动态阈值垂直投影法
 * 4. 分水岭算法：对粘连严重区域强制分割
 * 5. 字宽校验：确保每个格子只有一个单字
 */
object ScanSegmentation {

    /** 最小单字面积 */
    private const val MIN_GLYPH_AREA = 50

    /** TFLite 输入尺寸 */
    private const val TARGET_SIZE = 64

    /** 五行标签索引 */
    private val WUXING_LABELS = arrayOf("木", "火", "土", "金", "水")

    /** 投影分割的最小行高度 */
    private const val MIN_ROW_HEIGHT = 15

    /** 投影分割的最小字宽度 */
    private const val MIN_CHAR_WIDTH = 10

    /** 字间最小间距 */
    private const val MIN_CHAR_GAP = 1

    /** 最大宽高比 */
    private const val MAX_ASPECT_RATIO = 3.0

    /** 最小宽高比 */
    private const val MIN_ASPECT_RATIO = 0.3

    /** 腐蚀核大小（用于断开粘连） */
    private const val ERODE_KERNEL_SIZE = 2

    /** 膨胀核大小（用于恢复笔画） */
    private const val DILATE_KERNEL_SIZE = 1

    /** 分水岭标记距离 */
    private const val WATERSHED_DISTANCE = 10.0

    /** 疑似多字的最小宽度阈值（超过此值可能包含多个字） */
    private const val MULTI_CHAR_WIDTH_THRESHOLD = 80

    /** 单字最大宽度（超过此值需要强制分割） */
    private const val MAX_SINGLE_CHAR_WIDTH = 120

    /**
     * 分割手写体图片（统一预处理优化版）
     */
    suspend fun segmentHandwriting(
        bitmap: Bitmap,
        inferWuxing: suspend (Bitmap) -> FloatArray
    ): List<SegmentedGlyph> {
        val result = mutableListOf<SegmentedGlyph>()

        try {
            Timber.d("[ScanSegmentation] 开始切字，图片尺寸: ${bitmap.width}x${bitmap.height}")

            // 步骤A：Bitmap → Mat
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            Timber.d("[ScanSegmentation] 步骤A完成：Bitmap转Mat")

            // 步骤B：统一预处理（相机/相册共用）
            val binary = preprocessImage(mat)
            Timber.d("[ScanSegmentation] 步骤B完成：统一预处理（CLAHE + 二值化 + 开运算）")

            // 步骤C：行检测（水平投影法）
            val rows = detectRows(binary, bitmap.width, bitmap.height)
            Timber.d("[ScanSegmentation] 步骤C完成：检测到 ${rows.size} 行")

            // 步骤D：行内单字分割（垂直投影法 + 形态学预处理）
            for ((rowIndex, rowRect) in rows.withIndex()) {
                val rowGlyphs = segmentRowOptimized(binary, rowRect, mat)
                Timber.d("[ScanSegmentation] 第${rowIndex+1}行分割出 ${rowGlyphs.size} 个字")
                
                // 处理每行的单字
                for ((glyphIndex, glyphInfo) in rowGlyphs.withIndex()) {
                    if (result.size >= 9) break
                    
                    val (glyphBitmap, wuxing, confidence) = processGlyph(glyphInfo, inferWuxing)
                    result.add(
                        SegmentedGlyph(
                            id = "glyph_${rowIndex}_${glyphIndex}_${System.currentTimeMillis()}",
                            bitmap = glyphBitmap,
                            wuXing = wuxing,
                            confidence = confidence
                        )
                    )
                }
                
                if (result.size >= 9) break
            }

            // 释放资源
            mat.release()
            binary.release()

            Timber.d("[ScanSegmentation] 切字完成，共提取 ${result.size} 个字")

        } catch (e: Exception) {
            Timber.e(e, "[ScanSegmentation] 切字异常")
            return fallbackSegmentation(bitmap, inferWuxing)
        }

        return result
    }

    /**
     * 行检测：使用水平投影法
     */
    private fun detectRows(binary: Mat, width: Int, height: Int): List<Rect> {
        val rows = mutableListOf<Rect>()
        
        val projection = IntArray(height)
        for (y in 0 until height) {
            var count = 0
            for (x in 0 until width) {
                if (binary.get(y, x)[0] > 0) {
                    count++
                }
            }
            projection[y] = count
        }

        val threshold = width * 0.03
        var inLine = false
        var startY = 0
        var consecutiveEmpty = 0
        val maxEmptyLines = 2
        
        for (y in 0 until height) {
            val hasContent = projection[y] > threshold
            
            if (hasContent) {
                consecutiveEmpty = 0
                if (!inLine) {
                    inLine = true
                    startY = max(0, y - 2)
                }
            } else if (inLine) {
                consecutiveEmpty++
                if (consecutiveEmpty >= maxEmptyLines) {
                    inLine = false
                    val rowHeight = y - startY - maxEmptyLines
                    if (rowHeight >= MIN_ROW_HEIGHT) {
                        rows.add(Rect(0, startY, width, rowHeight))
                    }
                }
            }
        }

        if (inLine) {
            val rowHeight = height - startY
            if (rowHeight >= MIN_ROW_HEIGHT) {
                rows.add(Rect(0, startY, width, rowHeight))
            }
        }

        return rows
    }

    /**
     * 单字级行内分割（优化版）
     * - 先做腐蚀预处理断开粘连
     * - 动态阈值垂直投影分割
     * - 分水岭算法处理严重粘连
     * - 字宽校验确保单字
     */
    private fun segmentRowOptimized(binary: Mat, rowRect: Rect, originalMat: Mat): List<Pair<Rect, Mat>> {
        val glyphs = mutableListOf<Pair<Rect, Mat>>()
        
        // 提取行ROI
        val rowMat = Mat(binary, rowRect)
        val width = rowMat.cols()
        val height = rowMat.rows()
        
        // 步骤1：腐蚀预处理（断开粘连字）
        val erodedRow = applyErosion(rowMat)
        
        // 步骤2：计算垂直投影（使用腐蚀后的图像）
        val projection = IntArray(width)
        for (x in 0 until width) {
            var count = 0
            for (y in 0 until height) {
                if (erodedRow.get(y, x)[0] > 0) {
                    count++
                }
            }
            projection[x] = count
        }

        // 步骤3：动态阈值检测字间间隙
        val gaps = detectGapsDynamic(projection, height)
        Timber.d("[ScanSegmentation] 检测到 ${gaps.size} 个间隙")

        // 步骤4：根据间隙分割单字
        var startX = 0
        val tempGlyphs = mutableListOf<Pair<Rect, Mat>>()
        
        gaps.forEach { gapX ->
            if (gapX - startX > MIN_CHAR_WIDTH) {
                val charRect = Rect(startX + rowRect.x, rowRect.y, gapX - startX, rowRect.height)
                // 对宽字进行二次分割
                val splitGlyphs = splitWideGlyph(charRect, originalMat, rowRect)
                tempGlyphs.addAll(splitGlyphs)
            }
            startX = gapX + 1
        }

        // 处理最后一个字
        if (width - startX > MIN_CHAR_WIDTH) {
            val charRect = Rect(startX + rowRect.x, rowRect.y, width - startX, rowRect.height)
            val splitGlyphs = splitWideGlyph(charRect, originalMat, rowRect)
            tempGlyphs.addAll(splitGlyphs)
        }

        // 步骤5：如果垂直投影分割效果不好，尝试分水岭算法
        if (tempGlyphs.isEmpty() || tempGlyphs.size == 1 && width > MIN_CHAR_WIDTH * 4) {
            Timber.d("[ScanSegmentation] 垂直投影分割效果不佳，尝试分水岭算法")
            val watershedGlyphs = applyWatershedSegmentation(rowMat, rowRect, originalMat)
            if (watershedGlyphs.isNotEmpty()) {
                glyphs.addAll(watershedGlyphs)
            } else {
                // Fallback到强制均分
                val forcedGlyphs = forceEqualDivision(rowRect, originalMat, width, height)
                glyphs.addAll(forcedGlyphs)
            }
        } else {
            glyphs.addAll(tempGlyphs)
        }

        // 步骤6：过滤无效区域并按位置排序
        val validGlyphs = glyphs.filter { isValidGlyph(it.first, rowRect) }
            .sortedBy { it.first.x }

        Timber.d("[ScanSegmentation] 行分割完成，得到 ${validGlyphs.size} 个有效单字")

        // 释放资源
        rowMat.release()
        erodedRow.release()
        
        return validGlyphs
    }

    /**
     * 统一图像预处理函数（相机/相册共用）
     * 确保相机和相册进入切字前的图像格式一致
     */
    private fun preprocessImage(source: Mat): Mat {
        // 步骤1：转换为灰度图
        val gray = Mat()
        val sourceType = source.type()
        if (sourceType == CvType.CV_8UC4) {
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
        } else if (sourceType == CvType.CV_8UC3) {
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY)
        } else if (sourceType == CvType.CV_8UC1) {
            source.copyTo(gray)
        } else {
            // 其他格式强制转换
            source.convertTo(gray, CvType.CV_8UC1)
        }
        
        // 步骤2：自适应对比度增强（CLAHE）
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val enhanced = Mat()
        clahe.apply(gray, enhanced)
        gray.release()
        
        // 步骤3：自适应二值化（统一参数）
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            enhanced, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            15,  // 块大小
            10.0 // 常数C
        )
        enhanced.release()
        
        // 步骤4：开运算去噪（先腐蚀后膨胀）
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
        val opened = Mat()
        Imgproc.morphologyEx(binary, opened, Imgproc.MORPH_OPEN, kernel)
        binary.release()
        kernel.release()
        
        return opened
    }

    /**
     * 腐蚀预处理（断开粘连字）
     * 使用小核腐蚀断开字间的细小连接
     */
    private fun applyErosion(src: Mat): Mat {
        val result = Mat()
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(ERODE_KERNEL_SIZE.toDouble(), ERODE_KERNEL_SIZE.toDouble())
        )
        
        Imgproc.erode(src, result, kernel)
        
        kernel.release()
        return result
    }

    /**
     * 膨胀处理（恢复笔画）
     */
    private fun applyDilation(src: Mat): Mat {
        val result = Mat()
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(DILATE_KERNEL_SIZE.toDouble(), DILATE_KERNEL_SIZE.toDouble())
        )
        
        Imgproc.dilate(src, result, kernel)
        
        kernel.release()
        return result
    }

    /**
     * 动态阈值间隙检测
     * 根据行内笔画密度动态调整分割阈值
     */
    private fun detectGapsDynamic(projection: IntArray, height: Int): List<Int> {
        val gaps = mutableListOf<Int>()
        
        // 计算平均投影值（笔画密度）
        val avgProjection = projection.average()
        // 动态阈值：低密度行用低阈值，高密度行用高阈值
        val threshold = if (avgProjection < height * 0.2) {
            height * 0.08  // 低密度行：更宽松的分割
        } else {
            height * 0.03  // 高密度行：更严格的分割
        }
        
        // 检测局部最小值（谷底）
        for (x in 1 until projection.size - 1) {
            val current = projection[x]
            val left = projection[x - 1]
            val right = projection[x + 1]
            
            // 检测谷底：当前点小于左右两边，且低于阈值
            if (current < threshold && current <= left && current <= right) {
                // 合并相邻的谷底
                if (gaps.isEmpty() || x - gaps.last() > MIN_CHAR_GAP) {
                    gaps.add(x)
                }
            }
        }
        
        Timber.d("[ScanSegmentation] 动态阈值=${threshold.toInt()}, 检测到${gaps.size}个间隙")
        return gaps
    }

    /**
     * 宽字二次分割
     * 对宽度超过阈值的字块进行强制分割，确保单字级精度
     */
    private fun splitWideGlyph(charRect: Rect, originalMat: Mat, rowRect: Rect): List<Pair<Rect, Mat>> {
        val glyphs = mutableListOf<Pair<Rect, Mat>>()
        val width = charRect.width
        
        // 如果字宽在正常范围内，直接返回
        if (width <= MULTI_CHAR_WIDTH_THRESHOLD) {
            if (isValidGlyph(charRect, rowRect)) {
                glyphs.add(charRect to Mat(originalMat, charRect))
            }
            return glyphs
        }
        
        // 宽字需要分割
        Timber.d("[ScanSegmentation] 宽字分割：宽度=$width")
        
        // 根据宽度决定分割数量，确保最小分割数为2
        val avgCharWidth = 60 // 平均单字宽度估计
        val minChars = max(2, width / avgCharWidth)
        val charCount = when {
            width > MAX_SINGLE_CHAR_WIDTH * 2 -> 3  // 三字
            width > MAX_SINGLE_CHAR_WIDTH -> 2       // 两字
            else -> minChars
        }
        
        if (charCount == 1) {
            if (isValidGlyph(charRect, rowRect)) {
                glyphs.add(charRect to Mat(originalMat, charRect))
            }
            return glyphs
        }
        
        // 等宽分割
        val splitWidth = width / charCount
        val remainder = width % charCount
        
        var currentX = charRect.x
        for (i in 0 until charCount) {
            val w = if (i < remainder) splitWidth + 1 else splitWidth
            if (w >= MIN_CHAR_WIDTH) {
                val splitRect = Rect(currentX, charRect.y, w, charRect.height)
                if (isValidGlyph(splitRect, rowRect)) {
                    glyphs.add(splitRect to Mat(originalMat, splitRect))
                }
            }
            currentX += w
        }
        
        // 修复：如果分割后没有有效子字，返回原始字块
        if (glyphs.isEmpty()) {
            Timber.d("[ScanSegmentation] 宽字分割后无有效子字，返回原始字块")
            if (isValidGlyph(charRect, rowRect)) {
                glyphs.add(charRect to Mat(originalMat, charRect))
            }
        }
        
        Timber.d("[ScanSegmentation] 宽字分割完成，得到${glyphs.size}个子字")
        return glyphs
    }

    /**
     * 分水岭算法分割（处理严重粘连）
     */
    private fun applyWatershedSegmentation(binary: Mat, rowRect: Rect, originalMat: Mat): List<Pair<Rect, Mat>> {
        val glyphs = mutableListOf<Pair<Rect, Mat>>()
        
        try {
            // 修复：强制转换图像格式为 CV_8UC1（findContours要求）
            val input8U = Mat()
            if (binary.type() != CvType.CV_8UC1) {
                binary.convertTo(input8U, CvType.CV_8UC1)
            } else {
                binary.copyTo(input8U)
            }
            
            // 距离变换
            val dist = Mat()
            Imgproc.distanceTransform(input8U, dist, Imgproc.DIST_L2, 3)
            
            // 归一化距离变换结果
            Core.normalize(dist, dist, 0.0, 1.0, Core.NORM_MINMAX)
            
            // 二值化得到种子区域
            val distBinary = Mat()
            Imgproc.threshold(dist, distBinary, WATERSHED_DISTANCE / 100.0, 1.0, Imgproc.THRESH_BINARY)
            
            // 再次转换为 CV_8UC1 用于 findContours
            val distBinary8U = Mat()
            distBinary.convertTo(distBinary8U, CvType.CV_8UC1, 255.0)
            
            // 寻找轮廓作为标记
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(distBinary8U, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE)
            
            if (contours.size > 0) {
                // 创建标记图像
                val markers = Mat.zeros(binary.size(), CvType.CV_32S)
                
                // 为每个轮廓分配唯一标记
                for (i in contours.indices) {
                    Imgproc.drawContours(markers, contours, i, Scalar((i + 1).toDouble()), -1)
                }
                
                // 执行分水岭算法
                val watershedResult = Mat()
                binary.convertTo(watershedResult, CvType.CV_8U)
                Imgproc.watershed(watershedResult, markers)
                
                // 提取分割结果
                val labelCount = contours.size + 1
                for (label in 1 until labelCount) {
                    val mask = Mat()
                    Core.compare(markers, Scalar(label.toDouble()), mask, Core.CMP_EQ)
                    
                    // 找到轮廓边界
                    val tempContours = mutableListOf<MatOfPoint>()
                    Imgproc.findContours(mask, tempContours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                    
                    if (tempContours.isNotEmpty()) {
                        val rect = Imgproc.boundingRect(tempContours[0])
                        // 转换到原图坐标系
                        val glyphRect = Rect(
                            rect.x + rowRect.x,
                            rect.y + rowRect.y,
                            rect.width,
                            rect.height
                        )
                        
                        if (isValidGlyph(glyphRect, rowRect)) {
                            glyphs.add(glyphRect to Mat(originalMat, glyphRect))
                        }
                    }
                    
                    mask.release()
                }
                
                markers.release()
                watershedResult.release()
            }
            
            dist.release()
            distBinary.release()
            distBinary8U.release()
            input8U.release()
            hierarchy.release()
            
        } catch (e: Exception) {
            Timber.e(e, "[ScanSegmentation] 分水岭算法失败")
            return glyphs
        }
        
        Timber.d("[ScanSegmentation] 分水岭分割得到${glyphs.size}个单字")
        return glyphs
    }

    /**
     * 强制均分处理
     * 当垂直投影只切出1个ROI时，强制将该行均分为3~4个等宽候选区
     */
    private fun forceEqualDivision(rowRect: Rect, originalMat: Mat, width: Int, _height: Int): List<Pair<Rect, Mat>> {
        val glyphs = mutableListOf<Pair<Rect, Mat>>()
        
        // 根据行宽决定分割数量（3或4个）
        val charCount = if (width > 300) 4 else 3
        val charWidth = width / charCount
        val remainder = width % charCount
        
        var currentX = 0
        for (i in 0 until charCount) {
            val w = if (i < remainder) charWidth + 1 else charWidth
            if (w >= MIN_CHAR_WIDTH) {
                val charRect = Rect(currentX + rowRect.x, rowRect.y, w, rowRect.height)
                if (isValidGlyph(charRect, rowRect)) {
                    val glyphMat = Mat(originalMat, charRect)
                    glyphs.add(charRect to glyphMat)
                }
            }
            currentX += w
        }
        
        Timber.d("[ScanSegmentation] 强制均分得到 ${glyphs.size} 个候选区")
        return glyphs
    }

    /**
     * 验证单字区域是否有效
     */
    private fun isValidGlyph(charRect: Rect, rowRect: Rect): Boolean {
        // 宽度检查
        if (charRect.width < MIN_CHAR_WIDTH) return false
        
        // 高度检查（不能超过行高）
        if (charRect.height > rowRect.height) return false
        
        // 宽高比检查
        val aspectRatio = charRect.width.toFloat() / charRect.height
        if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) return false
        
        // 面积检查（不能超过行面积的一半）
        val charArea = charRect.width * charRect.height
        val rowArea = rowRect.width * rowRect.height
        if (charArea > rowArea / 2) return false
        
        // 最小面积检查
        if (charArea < MIN_GLYPH_AREA) return false
        
        return true
    }

    /**
     * 处理单个字形：缩放 + 推理五行
     */
    private suspend fun processGlyph(
        glyphInfo: Pair<Rect, Mat>,
        inferWuxing: suspend (Bitmap) -> FloatArray
    ): Triple<Bitmap, String, Float> {
        val (_, glyphMat) = glyphInfo
        
        try {
            val resized = Mat()
            Imgproc.resize(glyphMat, resized, Size(TARGET_SIZE.toDouble(), TARGET_SIZE.toDouble()))
            
            val glyphBitmap = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resized, glyphBitmap)

            val wuxingValues = inferWuxing(glyphBitmap)
            val maxIdx = wuxingValues.indices.maxByOrNull { wuxingValues[it] } ?: 0
            
            resized.release()
            glyphMat.release()
            
            return Triple(glyphBitmap, WUXING_LABELS[maxIdx], wuxingValues[maxIdx])
            
        } catch (e: Exception) {
            Timber.e(e, "[ScanSegmentation] 处理单字异常")
            glyphMat.release()
            
            val emptyBitmap = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
            return Triple(emptyBitmap, "木", 50f)
        }
    }

    /**
     * Fallback分割
     */
    suspend fun fallbackSegmentation(
        bitmap: Bitmap,
        inferWuxing: suspend (Bitmap) -> FloatArray
    ): List<SegmentedGlyph> {
        Timber.w("[ScanSegmentation] 使用Fallback分割")
        
        val result = mutableListOf<SegmentedGlyph>()
        
        try {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, TARGET_SIZE, TARGET_SIZE, true)
            val wuxingValues = inferWuxing(resizedBitmap)
            val maxIdx = wuxingValues.indices.maxByOrNull { wuxingValues[it] } ?: 0
            
            result.add(
                SegmentedGlyph(
                    id = "fallback_glyph_${System.currentTimeMillis()}",
                    bitmap = resizedBitmap,
                    wuXing = WUXING_LABELS[maxIdx],
                    confidence = wuxingValues[maxIdx]
                )
            )
            
        } catch (e: Exception) {
            Timber.e(e, "[ScanSegmentation] Fallback分割也失败")
        }
        
        return result
    }

    /**
     * 创建空白占位字形
     */
    fun createPlaceholderGlyph(): SegmentedGlyph {
        val bitmap = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF5F5F5.toInt())
        return SegmentedGlyph(
            id = "placeholder_${System.currentTimeMillis()}",
            bitmap = bitmap,
            wuXing = "",
            confidence = 0f,
            isPlaceholder = true
        )
    }
}

/**
 * 分割后的字形数据
 */
data class SegmentedGlyph(
    val id: String,
    val bitmap: Bitmap,
    val wuXing: String,
    val confidence: Float,
    val isPlaceholder: Boolean = false
) {
    fun getWuXingColor(): androidx.compose.ui.graphics.Color {
        return when (wuXing) {
            "木" -> androidx.compose.ui.graphics.Color(0xFF22C55E)
            "火" -> androidx.compose.ui.graphics.Color(0xFFEF4444)
            "土" -> androidx.compose.ui.graphics.Color(0xFFA16207)
            "金" -> androidx.compose.ui.graphics.Color(0xFFEAB308)
            "水" -> androidx.compose.ui.graphics.Color(0xFF3B82F6)
            else -> androidx.compose.ui.graphics.Color(0xFF9CA3AF)
        }
    }

    fun getFormattedConfidence(): String {
        val clampedValue = confidence.coerceIn(0f, 100f)
        return "${clampedValue.toInt()}%"
    }
}
