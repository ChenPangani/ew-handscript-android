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
 * 扫描分割器（终极优化版）
 * 
 * 核心算法：
 * 1. 行检测：水平投影法检测文本行区域
 * 2. 行内单字分割：垂直投影法（带形态学预处理）
 * 3. 强制均分：单字时强制分割为3-4个候选区
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

    /** 字间最小间距（降低阈值，手写稿字间距小） */
    private const val MIN_CHAR_GAP = 1

    /** 最大宽高比 */
    private const val MAX_ASPECT_RATIO = 3.0

    /** 最小宽高比 */
    private const val MIN_ASPECT_RATIO = 0.3

    /** 膨胀/腐蚀核大小 */
    private const val MORPH_KERNEL_SIZE = 2

    /**
     * 分割手写体图片（终极优化版）
     */
    suspend fun segmentHandwriting(
        bitmap: Bitmap,
        inferWuxing: suspend (Bitmap) -> FloatArray
    ): List<SegmentedGlyph> {
        val result = mutableListOf<SegmentedGlyph>()

        try {
            Timber.d("[ScanSegmentation] 开始切字，图片尺寸: ${bitmap.width}x${bitmap.height}")

            // 步骤A：Bitmap → Mat → 灰度化
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            Timber.d("[ScanSegmentation] 步骤A完成：灰度化")

            // 步骤B：自适应二值化（黑字白底）
            val binary = Mat()
            Imgproc.adaptiveThreshold(
                gray, binary, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                21, 8.0
            )
            Timber.d("[ScanSegmentation] 步骤B完成：自适应二值化")

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
            gray.release()
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
     * 优化版行内单字分割
     * - 先做形态学闭运算（膨胀-腐蚀）
     * - 再用垂直投影分割
     * - 如果只切出1个，强制均分
     */
    private fun segmentRowOptimized(binary: Mat, rowRect: Rect, originalMat: Mat): List<Pair<Rect, Mat>> {
        val glyphs = mutableListOf<Pair<Rect, Mat>>()
        
        // 提取行ROI
        val rowMat = Mat(binary, rowRect)
        val width = rowMat.cols()
        val height = rowMat.rows()
        
        // 步骤1：形态学闭运算（先膨胀后腐蚀）
        val processedRow = applyMorphologicalClosing(rowMat)
        
        // 步骤2：计算垂直投影
        val projection = IntArray(width)
        for (x in 0 until width) {
            var count = 0
            for (y in 0 until height) {
                if (processedRow.get(y, x)[0] > 0) {
                    count++
                }
            }
            projection[x] = count
        }

        // 步骤3：检测字间间隙（降低阈值到1px）
        val gaps = detectGapsOptimized(projection, height)
        Timber.d("[ScanSegmentation] 检测到 ${gaps.size} 个间隙")

        // 步骤4：根据间隙分割单字
        var startX = 0
        val tempGlyphs = mutableListOf<Pair<Rect, Mat>>()
        
        gaps.forEach { gapX ->
            if (gapX - startX > MIN_CHAR_WIDTH) {
                val charRect = Rect(startX + rowRect.x, rowRect.y, gapX - startX, rowRect.height)
                if (isValidGlyph(charRect, rowRect)) {
                    val glyphMat = Mat(originalMat, charRect)
                    tempGlyphs.add(charRect to glyphMat)
                }
            }
            startX = gapX + 1
        }

        // 处理最后一个字
        if (width - startX > MIN_CHAR_WIDTH) {
            val charRect = Rect(startX + rowRect.x, rowRect.y, width - startX, rowRect.height)
            if (isValidGlyph(charRect, rowRect)) {
                val glyphMat = Mat(originalMat, charRect)
                tempGlyphs.add(charRect to glyphMat)
            }
        }

        // 步骤5：强制均分处理（如果只切出1个或很少的ROI）
        if (tempGlyphs.size <= 1 && width >= MIN_CHAR_WIDTH * 3) {
            Timber.d("[ScanSegmentation] 强制均分处理，宽度=$width")
            val forcedGlyphs = forceEqualDivision(rowRect, originalMat, width, height)
            glyphs.addAll(forcedGlyphs)
        } else {
            glyphs.addAll(tempGlyphs)
        }

        // 释放资源
        rowMat.release()
        processedRow.release()
        
        return glyphs
    }

    /**
     * 形态学闭运算（先膨胀后腐蚀）
     * 目的：连接断裂笔画，使垂直投影更准确
     */
    private fun applyMorphologicalClosing(src: Mat): Mat {
        val result = Mat()
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(MORPH_KERNEL_SIZE.toDouble(), MORPH_KERNEL_SIZE.toDouble())
        )
        
        // 先膨胀
        Imgproc.dilate(src, result, kernel)
        // 再腐蚀
        Imgproc.erode(result, result, kernel)
        
        kernel.release()
        return result
    }

    /**
     * 优化版间隙检测（降低阈值到1px）
     */
    private fun detectGapsOptimized(projection: IntArray, height: Int): List<Int> {
        val gaps = mutableListOf<Int>()
        val threshold = height * 0.05 // 降低到5%
        
        for (x in 1 until projection.size - 1) {
            val current = projection[x]
            val left = projection[x - 1]
            val right = projection[x + 1]
            
            // 检测局部最小值（低谷）
            if (current < threshold && current <= left && current <= right) {
                // 合并相邻的低谷（阈值降低到1px）
                if (gaps.isEmpty() || x - gaps.last() > MIN_CHAR_GAP) {
                    gaps.add(x)
                }
            }
        }
        
        return gaps
    }

    /**
     * 强制均分处理
     * 当垂直投影只切出1个ROI时，强制将该行均分为3~4个等宽候选区
     */
    private fun forceEqualDivision(rowRect: Rect, originalMat: Mat, width: Int, height: Int): List<Pair<Rect, Mat>> {
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
