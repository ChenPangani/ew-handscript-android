package com.ew.handscript.core.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import timber.log.Timber
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫描矫正引擎 (ScanCorrectionEngine)
 *
 * 核心职责：对手写稿照片进行图像增强、透视矫正、去底色和二值化处理。
 * 解决历史稿件常见的折痕、阴影、倾斜、背景色不均等问题。
 *
 * 处理流水线：
 * 1. 图像预处理（灰度化、高斯模糊降噪）
 * 2. 边缘检测与轮廓分析
 * 3. 文档区域检测与透视变换矩阵计算
 * 4. 图像增强（对比度增强、自适应阈值二值化）
 * 5. 去底色与背景净化
 * 6. 基线检测与虚拟网格生成
 *
 * @author HandCraft Font Team
 */
@Singleton
class ScanCorrectionEngine @Inject constructor() {

    companion object {
        // 文档检测常量
        private const val CANNY_THRESHOLD_LOW = 50.0
        private const val CANNY_THRESHOLD_HIGH = 150.0
        private const val GAUSSIAN_BLUR_SIZE = 5.0
        private const val MIN_DOCUMENT_AREA_RATIO = 0.15  // 文档区域至少占图像15%
        private const val MAX_DOCUMENT_AREA_RATIO = 0.98  // 文档区域最多占图像98%
        private const val PERSPECTIVE_MARGIN = 10          // 透视矫正后边距（像素）

        // 二值化常量
        private const val ADAPTIVE_BLOCK_SIZE = 15         // 自适应阈值块大小（奇数）
        private const val ADAPTIVE_C = 10.0               // 自适应阈值常数

        // 基线检测常量
        private const val BASELINE_PROJECTION_THRESHOLD = 0.3f  // 水平投影基线检测阈值
        private const val MIN_LINE_HEIGHT = 20                   // 最小行高（像素）
    }

    /**
     * 扫描矫正结果数据类
     *
     * @property correctedBitmap 矫正后的Bitmap图像（已二值化、去底色）
     * @property originalWidth 原始图像宽度
     * @property originalHeight 原始图像高度
     * @property correctedWidth 矫正后图像宽度
     * @property correctedHeight 矫正后图像高度
     * @property cornerPoints 检测到的文档四个角点（原始坐标系）
     * @property perspectiveMatrix 透视变换矩阵（用于调试和溯源）
     * @property baselinePositions 检测到的文字基线位置列表（像素，从顶部算起）
     * @property lineHeights 检测到的行高列表
     * @property isDocumentDetected 是否成功检测到文档区域
     * @property processingTimeMs 处理耗时（毫秒）
     */
    data class ScanCorrectionResult(
        val correctedBitmap: Bitmap,
        val originalWidth: Int,
        val originalHeight: Int,
        val correctedWidth: Int,
        val correctedHeight: Int,
        val cornerPoints: List<PointF>,
        val perspectiveMatrix: Matrix?,
        val baselinePositions: List<Int>,
        val lineHeights: List<Int>,
        val isDocumentDetected: Boolean,
        val processingTimeMs: Long
    )

    /**
     * 主入口：执行完整的扫描矫正流水线
     *
     * @param imagePath 原始图像文件路径
     * @param options 处理选项配置
     * @return 扫描矫正结果
     */
    suspend fun processImage(
        imagePath: String,
        options: CorrectionOptions = CorrectionOptions()
    ): Result<ScanCorrectionResult> = withContext(Dispatchers.Default) {
        try {
            val startTime = System.currentTimeMillis()

            // 1. 加载并解码图像
            val originalBitmap = loadBitmap(imagePath)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("无法加载图像: $imagePath")
                )

            // 2. OpenCV Mat 转换
            val originalMat = Mat()
            Utils.bitmapToMat(originalBitmap, originalMat)

            // 3. 执行文档检测与透视矫正
            val (warpedMat, cornerPoints, isDetected) = detectAndCorrectDocument(
                originalMat,
                options
            )

            // 4. 图像增强与二值化
            val enhancedMat = enhanceAndBinarize(warpedMat, options)

            // 5. 去底色与背景净化
            val cleanedMat = removeBackground(enhancedMat, options)

            // 6. 基线检测（用于后续单字切割的虚拟网格参考线）
            val (baselines, lineHeights) = detectBaselines(cleanedMat)

            // 7. 转回 Bitmap
            val resultBitmap = Bitmap.createBitmap(
                cleanedMat.cols(),
                cleanedMat.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(cleanedMat, resultBitmap)

            // 8. 资源释放
            originalMat.release()
            warpedMat.release()
            enhancedMat.release()
            cleanedMat.release()

            val processingTime = System.currentTimeMillis() - startTime

            Result.success(
                ScanCorrectionResult(
                    correctedBitmap = resultBitmap,
                    originalWidth = originalBitmap.width,
                    originalHeight = originalBitmap.height,
                    correctedWidth = resultBitmap.width,
                    correctedHeight = resultBitmap.height,
                    cornerPoints = cornerPoints,
                    perspectiveMatrix = null, // 在需要时可序列化存储
                    baselinePositions = baselines,
                    lineHeights = lineHeights,
                    isDocumentDetected = isDetected,
                    processingTimeMs = processingTime
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "扫描矫正处理失败")
            Result.failure(e)
        }
    }

    /**
     * 从文件路径加载Bitmap，自动处理大图缩放
     */
    private fun loadBitmap(imagePath: String): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)

        // 如果图像过大，进行采样缩放（最大边不超过 2048）
        val maxDimension = max(options.outWidth, options.outHeight)
        val sampleSize = if (maxDimension > 2048) {
            Integer.highestOneBit(maxDimension / 2048)
        } else 1

        return BitmapFactory.decodeFile(imagePath, BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    /**
     * 文档检测与透视矫正
     *
     * 算法流程：
     * 1. 灰度化 + 高斯模糊降噪
     * 2. Canny边缘检测
     * 3. 膨胀操作连接断裂边缘
     * 4. 查找最大轮廓（假设为文档区域）
     * 5. 轮廓逼近为多边形（4边形）
     * 6. 计算透视变换矩阵并应用
     *
     * @param originalMat 原始图像Mat
     * @param options 处理选项
     * @return Triple<矫正后的Mat, 角点列表, 是否检测到文档>
     */
    private fun detectAndCorrectDocument(
        originalMat: Mat,
        options: CorrectionOptions
    ): Triple<Mat, List<PointF>, Boolean> {
        val grayMat = Mat()
        val blurredMat = Mat()
        val edgesMat = Mat()
        val dilatedMat = Mat()

        try {
            // 1. 灰度化
            Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_BGR2GRAY)

            // 2. 高斯模糊降噪（核大小根据图像尺寸自适应）
            val blurSize = if (options.enableDenoise) GAUSSIAN_BLUR_SIZE else 1.0
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(blurSize, blurSize), 0.0)

            // 3. Canny边缘检测
            Imgproc.Canny(blurredMat, edgesMat, CANNY_THRESHOLD_LOW, CANNY_THRESHOLD_HIGH)

            // 4. 膨胀操作，连接断裂的边缘线段
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(5.0, 5.0)
            )
            Imgproc.dilate(edgesMat, dilatedMat, kernel)

            // 5. 查找轮廓
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                dilatedMat,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            if (contours.isEmpty()) {
                // 未检测到轮廓，返回原图
                return Triple(originalMat.clone(), emptyList(), false)
            }

            // 6. 按面积排序，选择最大的轮廓
            val sortedContours = contours.sortedByDescending { Imgproc.contourArea(it) }
            val imageArea = originalMat.cols() * originalMat.rows().toDouble()

            // 遍历轮廓，寻找合适的文档区域
            for (contour in sortedContours) {
                val area = Imgproc.contourArea(contour)
                val areaRatio = area / imageArea

                // 面积过滤：排除过小或过大的区域
                if (areaRatio < MIN_DOCUMENT_AREA_RATIO || areaRatio > MAX_DOCUMENT_AREA_RATIO) {
                    continue
                }

                // 7. 轮廓逼近为多边形
                val perimeter = Imgproc.arcLength(
                    MatOfPoint2f(*contour.toArray()),
                    true
                )
                val approxCurve = MatOfPoint2f()
                Imgproc.approxPolyDP(
                    MatOfPoint2f(*contour.toArray()),
                    approxCurve,
                    0.02 * perimeter,
                    true
                )

                // 8. 检查是否为四边形（文档页面通常为矩形）
                if (approxCurve.toArray().size == 4) {
                    val points = approxCurve.toArray()
                    val sortedPoints = sortCornerPoints(points)

                    // 9. 计算透视变换目标尺寸
                    val width = maxOf(
                        distance(sortedPoints[0], sortedPoints[1]),
                        distance(sortedPoints[2], sortedPoints[3])
                    ).toInt()
                    val height = maxOf(
                        distance(sortedPoints[0], sortedPoints[3]),
                        distance(sortedPoints[1], sortedPoints[2])
                    ).toInt()

                    // 限制最大尺寸，防止内存溢出
                    val scale = min(1.0, 2048.0 / max(width, height))
                    val targetWidth = (width * scale).toInt()
                    val targetHeight = (height * scale).toInt()

                    // 10. 计算透视变换矩阵
                    val srcPoints = MatOfPoint2f(*sortedPoints)
                    val dstPoints = MatOfPoint2f(
                        Point(0.0, 0.0),
                        Point(targetWidth.toDouble(), 0.0),
                        Point(targetWidth.toDouble(), targetHeight.toDouble()),
                        Point(0.0, targetHeight.toDouble())
                    )

                    val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

                    // 11. 应用透视变换
                    val warpedMat = Mat()
                    Imgproc.warpPerspective(
                        originalMat,
                        warpedMat,
                        perspectiveMatrix,
                        Size(targetWidth.toDouble(), targetHeight.toDouble()),
                        Imgproc.INTER_LINEAR
                    )

                    // 转换为角点列表（原始坐标系，用于UI展示）
                    val cornerPoints = sortedPoints.map { PointF(it.x.toFloat(), it.y.toFloat()) }

                    // 释放中间资源
                    perspectiveMatrix.release()

                    return Triple(warpedMat, cornerPoints, true)
                }
            }

            // 未找到合适的四边形轮廓，返回原图
            return Triple(originalMat.clone(), emptyList(), false)

        } finally {
            grayMat.release()
            blurredMat.release()
            edgesMat.release()
            dilatedMat.release()
        }
    }

    /**
     * 图像增强与二值化
     *
     * 使用自适应阈值进行局部二值化，能更好地处理不均匀的阴影和光照。
     * 对于手写文字，采用高斯加权的自适应方法，保留笔画细节。
     *
     * @param inputMat 输入图像（已透视矫正）
     * @param options 处理选项
     * @return 二值化后的图像
     */
    private fun enhanceAndBinarize(inputMat: Mat, options: CorrectionOptions): Mat {
        val grayMat = Mat()
        val binaryMat = Mat()

        try {
            // 1. 灰度化（如需要）
            if (inputMat.channels() > 1) {
                Imgproc.cvtColor(inputMat, grayMat, Imgproc.COLOR_BGR2GRAY)
            } else {
                inputMat.copyTo(grayMat)
            }

            // 2. 对比度增强（可选）
            val enhancedMat = if (options.enhanceContrast) {
                val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                val result = Mat()
                clahe.apply(grayMat, result)
                result
            } else {
                grayMat.clone()
            }

            // 3. 自适应高斯阈值二值化
            // 使用 INVERSE 因为文字是黑色、背景是白色
            Imgproc.adaptiveThreshold(
                enhancedMat,
                binaryMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                ADAPTIVE_BLOCK_SIZE,
                ADAPTIVE_C
            )

            // 如果做了对比度增强，需要释放临时Mat
            if (options.enhanceContrast) {
                enhancedMat.release()
            }

            return binaryMat
        } finally {
            grayMat.release()
        }
    }

    /**
     * 去底色与背景净化
     *
     * 通过形态学开运算去除小的噪点，保留主要笔画结构。
     * 同时检测并去除残留的边框线或装订线痕迹。
     *
     * @param binaryMat 二值化图像
     * @param options 处理选项
     * @return 净化后的图像
     */
    private fun removeBackground(binaryMat: Mat, options: CorrectionOptions): Mat {
        val cleanedMat = Mat()
        val tempMat = Mat()

        try {
            // 1. 形态学开运算：去除小的噪点（如纸张纤维、扫描噪点）
            val openKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(2.0, 2.0)
            )
            Imgproc.morphologyEx(binaryMat, tempMat, Imgproc.MORPH_OPEN, openKernel)

            // 2. 形态学闭运算：填充笔画内部的小孔洞
            val closeKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(2.0, 2.0)
            )
            Imgproc.morphologyEx(tempMat, cleanedMat, Imgproc.MORPH_CLOSE, closeKernel)

            // 3. 去除边缘残留（边框线、装订线）
            if (options.removeBorderLines) {
                removeBorderArtifacts(cleanedMat)
            }

            return cleanedMat
        } finally {
            tempMat.release()
        }
    }

    /**
     * 去除边缘伪影（边框线、装订线痕迹）
     */
    private fun removeBorderArtifacts(mat: Mat) {
        val borderSize = (min(mat.cols(), mat.rows()) * 0.02).toInt().coerceAtLeast(3)

        // 将四周边缘像素设为白色（背景）
        // 上边
        Mat(mat, Range(0, borderSize), Range.all()).setTo(Scalar(0.0))
        // 下边
        Mat(mat, Range(mat.rows() - borderSize, mat.rows()), Range.all()).setTo(Scalar(0.0))
        // 左边
        Mat(mat, Range.all(), Range(0, borderSize)).setTo(Scalar(0.0))
        // 右边
        Mat(mat, Range.all(), Range(mat.cols() - borderSize, mat.cols())).setTo(Scalar(0.0))
    }

    /**
     * 基线检测 - 通过水平投影分析检测文字行基线
     *
     * 算法原理：
     * 1. 对二值化图像进行水平方向像素投影
     * 2. 投影值高的区域为文字行，投影值低的区域为行间距
     * 3. 通过寻找投影曲线的波峰和波谷确定每行的基线位置
     *
     * 该信息用于后续单字切割模块的虚拟网格参考线生成。
     *
     * @param binaryMat 二值化图像（文字为白色255，背景为黑色0）
     * @return Pair<基线位置列表, 行高列表>
     */
    fun detectBaselines(binaryMat: Mat): Pair<List<Int>, List<Int>> {
        val height = binaryMat.rows()
        val width = binaryMat.cols()

        // 1. 水平投影：统计每行的白色像素数
        val horizontalProjection = FloatArray(height)
        for (y in 0 until height) {
            var count = 0
            for (x in 0 until width) {
                if (binaryMat.get(y, x)[0] > 128) {
                    count++
                }
            }
            horizontalProjection[y] = count.toFloat() / width  // 归一化
        }

        // 2. 寻找文字行区域（波峰）
        val textLineRegions = mutableListOf<Pair<Int, Int>>() // (startY, endY)
        var inTextLine = false
        var lineStart = 0

        for (y in 0 until height) {
            val isText = horizontalProjection[y] > BASELINE_PROJECTION_THRESHOLD
            if (isText && !inTextLine) {
                lineStart = y
                inTextLine = true
            } else if (!isText && inTextLine) {
                if (y - lineStart >= MIN_LINE_HEIGHT) {
                    textLineRegions.add(lineStart to y)
                }
                inTextLine = false
            }
        }
        // 处理最后一个行
        if (inTextLine && height - lineStart >= MIN_LINE_HEIGHT) {
            textLineRegions.add(lineStart to height)
        }

        // 3. 计算每行的基线位置（取行内投影最大值位置作为基线）
        val baselines = mutableListOf<Int>()
        val lineHeights = mutableListOf<Int>()

        for ((startY, endY) in textLineRegions) {
            // 在行区域内寻找投影最大值点作为基线
            var maxProjection = 0f
            var baselineY = startY
            for (y in startY until endY) {
                if (horizontalProjection[y] > maxProjection) {
                    maxProjection = horizontalProjection[y]
                    baselineY = y
                }
            }
            baselines.add(baselineY)
            lineHeights.add(endY - startY)
        }

        Timber.d("检测到 ${baselines.size} 行文字，基线位置: $baselines")
        return baselines to lineHeights
    }

    /**
     * 对四个角点进行排序：左上、右上、右下、左下
     */
    private fun sortCornerPoints(points: Array<Point>): Array<Point> {
        // 按Y坐标排序，分成上下两组
        val sortedByY = points.sortedBy { it.y }
        val topPoints = sortedByY.take(2).sortedBy { it.x }  // 左上、右上
        val bottomPoints = sortedByY.takeLast(2).sortedBy { it.x }  // 左下、右下

        return arrayOf(
            topPoints[0],     // 左上
            topPoints[1],     // 右上
            bottomPoints[1],  // 右下
            bottomPoints[0]   // 左下
        )
    }

    /**
     * 计算两点间距离
     */
    private fun distance(p1: Point, p2: Point): Double {
        return sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
    }

    /**
     * 释放引擎资源
     */
    fun release() {
        // 清理OpenCV缓存
        Timber.d("ScanCorrectionEngine 资源已释放")
    }
}

/**
 * 扫描矫正处理选项
 *
 * @property enableDenoise 是否启用降噪
 * @property enhanceContrast 是否启用对比度增强（CLAHE）
 * @property removeBorderLines 是否去除边框线
 * @property targetDPI 目标分辨率（默认300DPI）
 */
data class CorrectionOptions(
    val enableDenoise: Boolean = true,
    val enhanceContrast: Boolean = true,
    val removeBorderLines: Boolean = true,
    val targetDPI: Int = 300
)
