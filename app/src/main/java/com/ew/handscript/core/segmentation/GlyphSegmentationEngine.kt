package com.ew.handscript.core.segmentation

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单字切割引擎 (GlyphSegmentationEngine)
 *
 * 核心职责：从扫描矫正后的文档图像中自动分割出独立的单字图像。
 * 解决汉字笔画粘连、部件分离、标点符号处理等复杂场景。
 *
 * 技术方案：
 * 1. 连通域分析（Connected Component Analysis）作为基础分割手段
 * 2. 形态学处理解决笔画粘连：先腐蚀分离粘连笔画，再膨胀恢复字形
 * 3. 垂直投影分析辅助列分割
 * 4. 基于先验知识的合并策略：将过度分割的部件重新合并为完整汉字
 * 5. 过滤噪声和非文字区域
 *
 * @author HandCraft Font Team
 * 
 * TODO-V2: 光影腐蚀处理（夜晚低光场景二值化阈值自适应）
 * TODO-V2: 印刷体/格式线过滤（需 print_filter.tflite 模型）
 * TODO-V2: 表格框线排除（霍夫直线检测 + ROI 过滤）
 */
@Singleton
class GlyphSegmentationEngine @Inject constructor() {

    companion object {
        // 连通域分析常量
        private const val MIN_GLYPH_SIZE = 15       // 最小字形尺寸（宽高均不小于此值，过滤噪点）
        private const val MAX_GLYPH_SIZE_RATIO = 0.3f  // 最大字形尺寸占图像比例
        private const val MIN_ASPECT_RATIO = 0.15f  // 最小宽高比（过滤竖线等窄条）
        private const val MAX_ASPECT_RATIO = 5.0f   // 最大宽高比

        // 形态学处理常量
        private const val ERODE_KERNEL_SIZE = 3     // 腐蚀核大小（用于分离粘连笔画）
        private const val DILATE_KERNEL_SIZE = 3    // 膨胀核大小（用于恢复字形）

        // 投影分析常量
        private const val VERTICAL_PROJECTION_THRESHOLD = 0.05f  // 垂直投影分割阈值

        // 合并策略常量
        private const val HORIZONTAL_MERGE_THRESHOLD = 5  // 水平方向合并距离阈值（像素）
        private const val OVERLAP_RATIO_THRESHOLD = 0.3f  // 垂直重叠比例阈值
    }

    /**
     * 单字切割结果
     *
     * @property glyphBitmaps 切割出的单字Bitmap列表
     * @property boundingBoxes 每个单字在原图中的包围盒
     * @property baselinePositions 检测到的基线位置（复用扫描矫正阶段的结果）
     * @property recognizedTexts OCR初步识别结果列表
     * @property confidenceScores 每个字的OCR置信度
     * @property lineAssignments 每个字所属的行号
     * @property processingTimeMs 处理耗时
     */
    data class SegmentationResult(
        val glyphBitmaps: List<Bitmap>,
        val boundingBoxes: List<Rect>,
        val baselinePositions: List<Int>,
        val recognizedTexts: List<String>,
        val confidenceScores: List<Float>,
        val lineAssignments: List<Int>,
        val processingTimeMs: Long
    )

    /**
     * 内部数据结构：候选连通域
     */
    private data class ConnectedComponent(
        val id: Int,
        val boundingBox: Rect,
        val centroid: Point,
        val area: Int,
        val pixelCount: Int
    ) {
        /**
         * 计算与其他连通域的水平距离
         */
        fun horizontalDistanceTo(other: ConnectedComponent): Int {
            return if (boundingBox.right < other.boundingBox.left) {
                other.boundingBox.left - boundingBox.right
            } else if (other.boundingBox.right < boundingBox.left) {
                boundingBox.left - other.boundingBox.right
            } else {
                0 // 水平方向有重叠
            }
        }

        /**
         * 计算垂直重叠比例
         */
        fun verticalOverlapRatio(other: ConnectedComponent): Float {
            val overlapTop = max(boundingBox.top, other.boundingBox.top)
            val overlapBottom = min(boundingBox.bottom, other.boundingBox.bottom)
            if (overlapTop >= overlapBottom) return 0f

            val overlapHeight = overlapBottom - overlapTop
            val minHeight = min(boundingBox.height(), other.boundingBox.height())
            return overlapHeight.toFloat() / minHeight
        }

        /**
         * 检查是否应该与另一个连通域合并（可能是同一汉字的部件）
         */
        fun shouldMergeWith(other: ConnectedComponent): Boolean {
            // 水平距离近
            val hDist = horizontalDistanceTo(other)
            if (hDist > HORIZONTAL_MERGE_THRESHOLD * 3) return false

            // 垂直方向有足够重叠
            val vOverlap = verticalOverlapRatio(other)
            if (vOverlap < OVERLAP_RATIO_THRESHOLD) return false

            // 合并后的宽高比在合理范围内
            val mergedWidth = max(boundingBox.right, other.boundingBox.right) -
                    min(boundingBox.left, other.boundingBox.left)
            val mergedHeight = max(boundingBox.bottom, other.boundingBox.bottom) -
                    min(boundingBox.top, other.boundingBox.top)
            val mergedAspect = mergedWidth.toFloat() / mergedHeight

            return mergedAspect in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO
        }
    }

    /**
     * 主入口：执行单字切割流水线
     *
     * @param correctedBitmap 经扫描矫正后的二值化图像
     * @param baselinePositions 预检测的基线位置列表（从扫描矫正阶段传入）
     * @param ocrEngine OCR引擎实例（用于初步识别）
     * @return 分割结果
     */
    suspend fun segmentGlyphs(
        correctedBitmap: Bitmap,
        baselinePositions: List<Int>,
        ocrEngine: OcrAdapter? = null
    ): Result<SegmentationResult> = withContext(Dispatchers.Default) {
        try {
            val startTime = System.currentTimeMillis()

            // 1. Bitmap 转 OpenCV Mat
            val binaryMat = Mat()
            Utils.bitmapToMat(correctedBitmap, binaryMat)

            // 2. 形态学处理：先腐蚀分离粘连笔画，再膨胀恢复字形
            val processedMat = applyMorphologicalProcessing(binaryMat)

            // 3. 连通域分析提取候选区域
            val components = extractConnectedComponents(processedMat, correctedBitmap.width, correctedBitmap.height)

            // 4. 部件合并：将过度分割的部件重新合并为完整汉字
            val mergedComponents = mergeCharacterParts(components)

            // 5. 按阅读顺序排序（从左到右，从上到下）
            val sortedComponents = sortReadingOrder(mergedComponents, baselinePositions)

            // 6. 提取单字Bitmap
            val glyphBitmaps = extractGlyphBitmaps(binaryMat, sortedComponents)
            val boundingBoxes = sortedComponents.map { it.boundingBox }

            // 7. 行归属分析
            val lineAssignments = assignToLines(sortedComponents, baselinePositions)

            // 8. OCR初步识别（在协程中并行执行）
            val (recognizedTexts, confidenceScores) = if (ocrEngine != null) {
                performOcrAsync(glyphBitmaps, ocrEngine)
            } else {
                List(glyphBitmaps.size) { "" } to List(glyphBitmaps.size) { 0f }
            }

            // 9. 资源释放
            binaryMat.release()
            processedMat.release()

            val processingTime = System.currentTimeMillis() - startTime

            Timber.i("单字切割完成: ${glyphBitmaps.size} 个字, 耗时 ${processingTime}ms")

            Result.success(
                SegmentationResult(
                    glyphBitmaps = glyphBitmaps,
                    boundingBoxes = boundingBoxes,
                    baselinePositions = baselinePositions,
                    recognizedTexts = recognizedTexts,
                    confidenceScores = confidenceScores,
                    lineAssignments = lineAssignments,
                    processingTimeMs = processingTime
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "单字切割失败")
            Result.failure(e)
        }
    }

    /**
     * 形态学处理：解决笔画粘连问题
     *
     * 算法：
     * 1. 腐蚀操作（Erosion）：使用较小的核轻微腐蚀，分离粘连的笔画
     * 2. 膨胀操作（Dilation）：使用相同的核恢复字形到原始大小
     * 3. 开运算（Opening）：先腐蚀后膨胀，去除小噪点同时分离粘连
     *
     * 数学原理：
     * - 腐蚀: dst(x,y) = min_{(x',y') in kernel} src(x+x', y+y')
     *   使亮区域（文字）收缩，暗区域（背景）扩张，从而分离轻微粘连
     * - 膨胀: dst(x,y) = max_{(x',y') in kernel} src(x+x', y+y')
     *   使亮区域扩张回接近原始大小
     */
    private fun applyMorphologicalProcessing(binaryMat: Mat): Mat {
        val processedMat = Mat()
        val tempMat = Mat()

        try {
            // 1. 确保图像为单通道
            val grayMat = if (binaryMat.channels() > 1) {
                val gray = Mat()
                Imgproc.cvtColor(binaryMat, gray, Imgproc.COLOR_BGR2GRAY)
                gray
            } else {
                binaryMat.clone()
            }

            // 2. 开运算：先腐蚀分离粘连，再膨胀恢复字形
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(ERODE_KERNEL_SIZE.toDouble(), ERODE_KERNEL_SIZE.toDouble())
            )
            Imgproc.morphologyEx(grayMat, processedMat, Imgproc.MORPH_OPEN, kernel)

            // 3. 闭运算：填充笔画内部小孔洞
            val closeKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(DILATE_KERNEL_SIZE.toDouble(), DILATE_KERNEL_SIZE.toDouble())
            )
            Imgproc.morphologyEx(processedMat, tempMat, Imgproc.MORPH_CLOSE, closeKernel)
            tempMat.copyTo(processedMat)

            // 释放临时资源
            if (binaryMat !== grayMat) grayMat.release()
            tempMat.release()
            kernel.release()
            closeKernel.release()

            return processedMat
        } catch (e: Exception) {
            processedMat.release()
            tempMat.release()
            throw e
        }
    }

    /**
     * 连通域分析：提取所有候选文字区域
     *
     * 使用OpenCV的connectedComponentsWithStats函数，
     * 获取每个连通域的统计信息（包围盒、面积、质心等）。
     *
     * 过滤策略：
     * - 尺寸过滤：排除过小（噪点）和过大（图像噪声）的连通域
     * - 宽高比过滤：排除明显不是文字的细长条
     * - 像素密度过滤：排除过于稀疏的区域
     */
    private fun extractConnectedComponents(
        binaryMat: Mat,
        imageWidth: Int,
        imageHeight: Int
    ): List<ConnectedComponent> {
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()

        // 确保二值图像为8位单通道
        val binary8U = Mat()
        if (binaryMat.type() != CvType.CV_8UC1) {
            binaryMat.convertTo(binary8U, CvType.CV_8UC1)
        } else {
            binaryMat.copyTo(binary8U)
        }

        // 执行连通域分析（8连通）
        val numLabels = Imgproc.connectedComponentsWithStats(
            binary8U, labels, stats, centroids, 8, CvType.CV_32S
        )

        val maxSize = (max(imageWidth, imageHeight) * MAX_GLYPH_SIZE_RATIO).toInt()
        val components = mutableListOf<ConnectedComponent>()

        // 遍历所有连通域（跳过背景，label=0）
        for (i in 1 until numLabels) {
            val x = stats.get(i, Imgproc.CC_STAT_LEFT)[0].toInt()
            val y = stats.get(i, Imgproc.CC_STAT_TOP)[0].toInt()
            val w = stats.get(i, Imgproc.CC_STAT_WIDTH)[0].toInt()
            val h = stats.get(i, Imgproc.CC_STAT_HEIGHT)[0].toInt()
            val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
            val cx = centroids.get(i, 0)[0]
            val cy = centroids.get(i, 1)[0]

            // 尺寸过滤
            if (w < MIN_GLYPH_SIZE || h < MIN_GLYPH_SIZE) continue
            if (w > maxSize || h > maxSize) continue

            // 宽高比过滤
            val aspectRatio = w.toFloat() / h
            if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) continue

            // 像素密度过滤（排除过于稀疏的区域）
            val boundingBoxArea = w * h
            val density = area.toFloat() / boundingBoxArea
            if (density < 0.05f || density > 0.95f) continue

            val boundingBox = Rect(x, y, w, h)
            components.add(
                ConnectedComponent(
                    id = i,
                    boundingBox = boundingBox,
                    centroid = Point(cx, cy),
                    area = area,
                    pixelCount = area
                )
            )
        }

        // 释放资源
        labels.release()
        stats.release()
        centroids.release()
        binary8U.release()

        Timber.d("连通域分析: 原始 ${numLabels - 1} 个, 过滤后 ${components.size} 个")
        return components
    }

    /**
     * 部件合并策略：将过度分割的部件重新合并为完整汉字
     *
     * 汉字结构复杂，简单的连通域分析可能将左右结构（如"你"=亻+尔）、
     * 上下结构的部件错误地分割为多个独立区域。
     *
     * 合并规则：
     * 1. 垂直方向有足够重叠（同一行内的字）
     * 2. 水平距离在阈值范围内（相邻的部件）
     * 3. 合并后的宽高比在合理范围内
     */
    private fun mergeCharacterParts(
        components: List<ConnectedComponent>
    ): List<ConnectedComponent> {
        if (components.size <= 1) return components

        val merged = mutableListOf<ConnectedComponent>()
        val mergedFlags = BooleanArray(components.size) { false }

        // 按X坐标排序，从左到右处理
        val sorted = components.sortedBy { it.boundingBox.centerX() }

        for (i in sorted.indices) {
            if (mergedFlags[i]) continue

            var current = sorted[i]
            mergedFlags[i] = true

            // 尝试与右侧的连通域合并
            for (j in i + 1 until sorted.size) {
                if (mergedFlags[j]) continue

                val candidate = sorted[j]

                // 检查是否应该合并
                if (current.shouldMergeWith(candidate)) {
                    // 执行合并
                    val newBox = Rect(
                        min(current.boundingBox.left, candidate.boundingBox.left),
                        min(current.boundingBox.top, candidate.boundingBox.top),
                        max(current.boundingBox.right, candidate.boundingBox.right) -
                                min(current.boundingBox.left, candidate.boundingBox.left),
                        max(current.boundingBox.bottom, candidate.boundingBox.bottom) -
                                min(current.boundingBox.top, candidate.boundingBox.top)
                    )

                    current = ConnectedComponent(
                        id = current.id,
                        boundingBox = newBox,
                        centroid = Point(
                            (newBox.left + newBox.right) / 2.0,
                            (newBox.top + newBox.bottom) / 2.0
                        ),
                        area = current.area + candidate.area,
                        pixelCount = current.pixelCount + candidate.pixelCount
                    )
                    mergedFlags[j] = true
                }
            }

            merged.add(current)
        }

        Timber.d("部件合并: ${components.size} -> ${merged.size} 个")
        return merged
    }

    /**
     * 按阅读顺序排序：从左到右，从上到下
     *
     * 使用基线信息进行行分组，同一行内按X坐标排序。
     */
    private fun sortReadingOrder(
        components: List<ConnectedComponent>,
        baselinePositions: List<Int>
    ): List<ConnectedComponent> {
        return components.sortedWith(Comparator { a, b ->
            // 首先确定各自所属的行
            val lineA = findLineIndex(a.centroid.y.toInt(), baselinePositions)
            val lineB = findLineIndex(b.centroid.y.toInt(), baselinePositions)

            if (lineA != lineB) {
                // 不同行：按行号排序
                lineA - lineB
            } else {
                // 同一行：按X坐标排序
                a.boundingBox.left - b.boundingBox.left
            }
        })
    }

    /**
     * 确定一个Y坐标所属的行索引
     */
    private fun findLineIndex(y: Int, baselinePositions: List<Int>): Int {
        if (baselinePositions.isEmpty()) return 0

        // 找到最近的基线
        var closestLine = 0
        var minDistance = Int.MAX_VALUE

        for (i in baselinePositions.indices) {
            val dist = kotlin.math.abs(y - baselinePositions[i])
            if (dist < minDistance) {
                minDistance = dist
                closestLine = i
            }
        }

        return closestLine
    }

    /**
     * 从原图中提取单字Bitmap
     */
    private fun extractGlyphBitmaps(
        sourceMat: Mat,
        components: List<ConnectedComponent>
    ): List<Bitmap> {
        return components.map { component ->
            val box = component.boundingBox

            // 缩小边距（从4px减至2px，减少黑色留白边框）
            val margin = 2
            val x = max(0, box.left - margin)
            val y = max(0, box.top - margin)
            val w = min(sourceMat.cols() - x, box.width() + margin * 2)
            val h = min(sourceMat.rows() - y, box.height() + margin * 2)

            // 提取ROI
            val roi = Mat(sourceMat, Range(y, y + h), Range(x, x + w))

            // 转为Bitmap
            val glyphBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(roi, glyphBitmap)

            roi.release()
            glyphBitmap
        }
    }

    /**
     * 为每个连通域分配行号
     */
    private fun assignToLines(
        components: List<ConnectedComponent>,
        baselinePositions: List<Int>
    ): List<Int> {
        return components.map { component ->
            findLineIndex(component.centroid.y.toInt(), baselinePositions)
        }
    }

    /**
     * 并行OCR识别所有切割出的单字
     */
    private suspend fun performOcrAsync(
        glyphBitmaps: List<Bitmap>,
        ocrEngine: OcrAdapter
    ): Pair<List<String>, List<Float>> = withContext(Dispatchers.Default) {
        val deferredResults = glyphBitmaps.map { bitmap ->
            async {
                try {
                    ocrEngine.recognize(bitmap)
                } catch (e: Exception) {
                    Timber.w(e, "单字OCR识别失败")
                    "" to 0f
                }
            }
        }

        val results = deferredResults.awaitAll()
        results.map { it.first } to results.map { it.second }
    }

    /**
     * 垂直投影分析 - 辅助列分割（用于表格或竖排场景）
     *
     * 返回垂直投影值数组（归一化到0-1范围）
     */
    fun computeVerticalProjection(binaryMat: Mat): FloatArray {
        val width = binaryMat.cols()
        val height = binaryMat.rows()
        val projection = FloatArray(width)

        for (x in 0 until width) {
            var count = 0
            for (y in 0 until height) {
                if (binaryMat.get(y, x)[0] > 128) {
                    count++
                }
            }
            projection[x] = count.toFloat() / height
        }

        return projection
    }
}

/**
 * OCR适配器接口 - 解耦具体OCR实现
 */
interface OcrAdapter {
    /**
     * 识别单个字形Bitmap
     * @return Pair<识别文本, 置信度>
     */
    suspend fun recognize(bitmap: Bitmap): Pair<String, Float>

    /**
     * 批量识别（可能利用批量API优化性能）
     */
    suspend fun recognizeBatch(bitmaps: List<Bitmap>): List<Pair<String, Float>>
}
