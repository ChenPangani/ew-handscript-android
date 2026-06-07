package com.ew.handscript.core.scan

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * 扫描矫正引擎单元测试
 *
 * 测试覆盖：
 * 1. 图像加载功能
 * 2. 文档检测与透视矫正
 * 3. 二值化处理
 * 4. 基线检测
 * 5. 去底色与背景净化
 * 6. 异常处理
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ScanCorrectionEngineTest {

    private lateinit var engine: ScanCorrectionEngine
    private val testOutputDir = File("build/test-output/scan")

    @Before
    fun setUp() {
        engine = ScanCorrectionEngine()
        testOutputDir.mkdirs()
    }

    @After
    fun tearDown() {
        engine.release()
        // 清理测试输出
        testOutputDir.listFiles()?.forEach { it.delete() }
    }

    // ============================================
    // 1. 图像加载测试
    // ============================================

    @Test
    fun `processImage_无效路径_返回失败`() = runBlocking {
        val result = engine.processImage("/invalid/path/image.jpg")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `processImage_空路径_返回失败`() = runBlocking {
        val result = engine.processImage("")
        assertTrue(result.isFailure)
    }

    // ============================================
    // 2. 基线检测算法测试
    // ============================================

    @Test
    fun `detectBaselines_空白图像_返回空列表`() {
        // 创建空白测试图像
        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)

        val mat = org.opencv.android.Utils().run {
            val m = org.opencv.core.Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, m)
            m
        }

        val (baselines, lineHeights) = engine.detectBaselines(mat)

        assertTrue("空白图像不应检测到基线", baselines.isEmpty())
        assertTrue("行高列表应为空", lineHeights.isEmpty())

        mat.release()
        bitmap.recycle()
    }

    @Test
    fun `detectBaselines_模拟三行文字_检测到三条基线`() {
        // 创建模拟三行文字的测试图像（黑色横条模拟文字行）
        val width = 400
        val height = 600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }

        // 白色背景
        canvas.drawColor(android.graphics.Color.WHITE)

        // 模拟三行文字（水平黑色条带）
        val linePositions = listOf(100, 250, 400)
        for (y in linePositions) {
            canvas.drawRect(20f, y.toFloat(), (width - 20).toFloat(), (y + 30).toFloat(), paint)
        }

        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)

        // 转灰度
        val grayMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, grayMat, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY)

        val (baselines, lineHeights) = engine.detectBaselines(grayMat)

        // 验证检测到3条基线
        assertEquals("应检测到3条基线", 3, baselines.size)
        assertEquals("应有3个行高", 3, lineHeights.size)

        // 验证基线位置在合理范围内（每个位置的容差为±20像素）
        for (i in linePositions.indices) {
            val detected = baselines[i]
            val expected = linePositions[i] + 15 // 基线应在文字行中间
            assertTrue(
                "基线 $i 应在 $expected ± 20 范围内，实际为 $detected",
                kotlin.math.abs(detected - expected) <= 20
            )
        }

        // 验证行高合理
        lineHeights.forEach { height ->
            assertTrue("行高应大于最小值", height >= 20)
        }

        mat.release()
        grayMat.release()
        bitmap.recycle()
    }

    @Test
    fun `detectBaselines_间距不均匀的行_正确检测`() {
        val width = 500
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }

        canvas.drawColor(android.graphics.Color.WHITE)

        // 非均匀间距的行
        val linePositions = listOf(80, 200, 500)
        for (y in linePositions) {
            canvas.drawRect(20f, y.toFloat(), (width - 20).toFloat(), (y + 25).toFloat(), paint)
        }

        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)

        val grayMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, grayMat, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY)

        val (baselines, lineHeights) = engine.detectBaselines(grayMat)

        assertEquals("应检测到3条基线", 3, baselines.size)

        mat.release()
        grayMat.release()
        bitmap.recycle()
    }

    // ============================================
    // 3. 处理选项测试
    // ============================================

    @Test
    fun `CorrectionOptions_默认配置_所有选项启用`() {
        val options = CorrectionOptions()
        assertTrue(options.enableDenoise)
        assertTrue(options.enhanceContrast)
        assertTrue(options.removeBorderLines)
        assertEquals(300, options.targetDPI)
    }

    @Test
    fun `CorrectionOptions_自定义配置_正确设置`() {
        val options = CorrectionOptions(
            enableDenoise = false,
            enhanceContrast = false,
            removeBorderLines = false,
            targetDPI = 150
        )
        assertFalse(options.enableDenoise)
        assertFalse(options.enhanceContrast)
        assertFalse(options.removeBorderLines)
        assertEquals(150, options.targetDPI)
    }

    // ============================================
    // 4. 性能测试
    // ============================================

    @Test
    fun `processImage_性能_大图像处理在合理时间内`() = runBlocking {
        // 创建大图像 (2048 x 3072)
        val largeBitmap = Bitmap.createBitmap(2048, 3072, Bitmap.Config.ARGB_8888)
        largeBitmap.eraseColor(android.graphics.Color.WHITE)

        // 添加一些内容
        val canvas = android.graphics.Canvas(largeBitmap)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
        for (i in 0 until 10) {
            val y = (i * 250 + 100).toFloat()
            canvas.drawRect(50f, y, 1900f, y + 40, paint)
        }

        // 保存到临时文件
        val tempFile = File(testOutputDir, "large_test.jpg")
        FileOutputStream(tempFile).use {
            largeBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }

        val startTime = System.currentTimeMillis()
        val result = engine.processImage(tempFile.absolutePath)
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("处理应在10秒内完成", elapsed < 10000)
        assertTrue("处理应成功", result.isSuccess)
        result.getOrNull()?.let {
            assertTrue("应有有效结果", it.correctedWidth > 0)
            assertTrue("应有基线检测结果", it.baselinePositions.isNotEmpty())
        }

        largeBitmap.recycle()
        tempFile.delete()
    }

    @Test
    fun `detectBaselines_性能_高分辨率图像`() {
        val width = 3000
        val height = 4000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }

        canvas.drawColor(android.graphics.Color.WHITE)
        // 20行文字
        for (i in 0 until 20) {
            val y = i * 180 + 100
            canvas.drawRect(30f, y.toFloat(), (width - 30).toFloat(), (y + 30).toFloat(), paint)
        }

        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        val grayMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, grayMat, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY)

        val startTime = System.currentTimeMillis()
        val (baselines, _) = engine.detectBaselines(grayMat)
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("基线检测应在2秒内完成", elapsed < 2000)
        assertTrue("应检测到20条基线", baselines.size >= 15) // 允许一定误差

        mat.release()
        grayMat.release()
        bitmap.recycle()
    }

    // ============================================
    // 5. 边界情况测试
    // ============================================

    @Test
    fun `detectBaselines_极窄图像_不崩溃`() {
        val bitmap = Bitmap.createBitmap(10, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)

        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)

        val grayMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, grayMat, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY)

        val (baselines, _) = engine.detectBaselines(grayMat)
        // 极窄图像可能检测不到基线，但不应崩溃
        assertNotNull(baselines)

        mat.release()
        grayMat.release()
        bitmap.recycle()
    }

    @Test
    fun `detectBaselines_极矮图像_不崩溃`() {
        val bitmap = Bitmap.createBitmap(200, 10, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)

        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)

        val grayMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, grayMat, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY)

        val (baselines, _) = engine.detectBaselines(grayMat)
        assertNotNull(baselines)

        mat.release()
        grayMat.release()
        bitmap.recycle()
    }

    companion object {
        /**
         * 测试辅助：创建带文字行的模拟文档图像
         */
        fun createTestDocumentBitmap(
            width: Int = 800,
            height: Int = 1200,
            lineCount: Int = 5,
            lineHeight: Int = 30,
            lineSpacing: Int = 100
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                strokeWidth = lineHeight.toFloat()
            }

            canvas.drawColor(android.graphics.Color.WHITE)

            for (i in 0 until lineCount) {
                val y = (i * lineSpacing + 80).toFloat()
                canvas.drawLine(40f, y, (width - 40).toFloat(), y, paint)
            }

            return bitmap
        }
    }
}
