package com.ew.handscript.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max

/**
 * TFLite 模型推理助手
 *
 * 职责：五行灵根觉醒 — Bitmap → 灰度图 → 64×64 → TFLite → FiveElementValues [0,100]
 *
 * 模型规格（真实模型）：
 * - 输入：[1, 1, 64, 64]（单通道灰度图，Float32，归一化 0-1）
 * - 输出：[1, 5]（五行特征值：wood, fire, earth, metal, water）
 *
 * 兜底设计：
 * - 模型文件不存在 → 抛出异常并记录日志
 * - 推理异常 → 返回 Mock FiveElementValues
 */
class TFLiteHelper private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: TFLiteHelper? = null

        fun getInstance(context: Context): TFLiteHelper {
            return instance ?: synchronized(this) {
                instance ?: TFLiteHelper(context.applicationContext).also { instance = it }
            }
        }

        // 模型文件路径（assets/models/ 下）
        private const val WUXING_MODEL = "models/wuxing_feature_extractor.tflite"

        // 算法训练尺寸：64×64 单通道灰度图
        private const val INPUT_SIZE = 64
        private const val CHANNELS = 1  // 单通道灰度图
        private const val OUTPUT_DIM = 5  // 五行输出维度
    }

    private val appContext = context.applicationContext

    // 五行模型 Interpreter
    private var wuxingInterpreter: Interpreter? = null

    /** Mock 模式标记：模型加载失败时进入 */
    @Volatile
    var isMockMode = false
        private set

    /**
     * 初始化：加载五行模型
     * 失败则抛出异常并记录详细日志
     */
    fun initialize() {
        try {
            wuxingInterpreter = loadModel(WUXING_MODEL, numThreads = 4)
            isMockMode = false
            Timber.i("[TFLiteHelper] 五行模型加载成功: $WUXING_MODEL")
        } catch (e: Exception) {
            Timber.e(e, "[TFLiteHelper] 五行模型加载失败: $WUXING_MODEL")
            throw RuntimeException("模型加载失败: ${e.message}", e)
        }
    }

    /** 加载模型（失败抛出异常） */
    private fun loadModel(path: String, numThreads: Int): Interpreter {
        val buffer = loadModelFile(path)
        return Interpreter(buffer, Interpreter.Options().apply {
            setNumThreads(numThreads)
            setUseXNNPACK(true)
        })
    }

    /** 从 assets 加载 .tflite 文件 */
    private fun loadModelFile(path: String): MappedByteBuffer {
        Timber.d("[TFLiteHelper] 尝试加载模型: $path")
        val afd = appContext.assets.openFd(path)
        Timber.d("[TFLiteHelper] 文件描述符: startOffset=${afd.startOffset}, length=${afd.declaredLength}")
        FileInputStream(afd.fileDescriptor).use { fis ->
            val channel = fis.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }

    // ==================== 公开 API ====================

    /**
     * 五行灵根觉醒
     *
     * 真实路径：Bitmap → 灰度图 → 64×64 → FloatArray → TFLite → FiveElementValues
     * Mock 路径：Bitmap → 稳定哈希 → 五行分布 [0,100]
     *
     * @param bitmap 输入字形图片（任意尺寸，内部自动转换为64×64灰度图）
     * @return FiveElementValues（范围 [0,100]）
     */
    suspend fun awakenWuxing(bitmap: Bitmap): FiveElementValues = withContext(Dispatchers.IO) {
        if (wuxingInterpreter == null) {
            Timber.w("[TFLiteHelper] 模型未初始化，返回 Mock 数据")
            return@withContext generateMockWuxing(bitmap)
        }
        try {
            // Bitmap → 灰度图 → 64×64 → FloatArray [1, 1, 64, 64]
            val inputArray = bitmapToGrayscaleFloatArray(bitmap)
            
            // 执行推理
            val output = Array(1) { FloatArray(OUTPUT_DIM) }
            wuxingInterpreter!!.run(inputArray, output)

            // 解析输出：[wood, fire, earth, metal, water]
            val rawValues = output[0]
            Timber.d("[TFLiteHelper] 原始输出: ${rawValues.contentToString()}")

            // 转换为 FiveElementValues（范围 0-100）
            FiveElementValues(
                wood = rawValues[0] * 100,
                fire = rawValues[1] * 100,
                earth = rawValues[2] * 100,
                metal = rawValues[3] * 100,
                water = rawValues[4] * 100
            )
        } catch (e: Exception) {
            Timber.e(e, "[TFLiteHelper] 五行推理异常，回退 Mock")
            generateMockWuxing(bitmap)
        }
    }

    /** 模型是否可用 */
    fun isModelAvailable(): Boolean = !isMockMode

    /** 释放资源 */
    fun close() {
        wuxingInterpreter?.close()
        wuxingInterpreter = null
        instance = null
        isMockMode = false
        Timber.i("[TFLiteHelper] 资源已释放")
    }

    // ==================== 图像预处理 ====================

    /**
     * Bitmap → FloatArray [NCHW: 1×1×64×64]
     *
     * 将Bitmap转换为单通道灰度图，缩放至64×64，归一化到0-1
     */
    private fun bitmapToGrayscaleFloatArray(bitmap: Bitmap): Any {
        // 缩放至 64×64
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        
        // 获取像素数据
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // 单通道灰度图：[CHANNELS × INPUT_SIZE × INPUT_SIZE] = [1 × 64 × 64]
        val floatArray = FloatArray(CHANNELS * INPUT_SIZE * INPUT_SIZE)
        var idx = 0

        // NCHW 格式：单通道，按行优先存储
        for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
            val pixel = pixels[i]
            // 转换为灰度值：0.299*R + 0.587*G + 0.114*B
            val gray = Color.red(pixel) * 0.299f + 
                       Color.green(pixel) * 0.587f + 
                       Color.blue(pixel) * 0.114f
            floatArray[idx++] = gray / 255.0f  // 归一化到 0-1
        }

        if (scaled !== bitmap) scaled.recycle()
        return floatArray
    }

    // ==================== Mock 兜底 ====================

    /**
     * Mock 五行数据生成
     *
     * 基于 Bitmap 稳定哈希，同一图片总返回相同结果。
     * 范围 [0, 100]，与真实模型输出一致。
     */
    private fun generateMockWuxing(bitmap: Bitmap): FiveElementValues {
        val seed = bitmapStableHash(bitmap)
        val rng = java.util.Random(seed)

        val dominantIdx = rng.nextInt(5)
        val secondaryIdx = (dominantIdx + 1 + rng.nextInt(4)) % 5

        val scores = FloatArray(5) { rng.nextFloat() * 35f }     // 基础 0-35
        scores[dominantIdx] = 78f + rng.nextFloat() * 22f         // 主导 78-100
        scores[secondaryIdx] = 50f + rng.nextFloat() * 20f        // 次要 50-70

        return FiveElementValues.fromArray(scores)
    }

    /** Bitmap 稳定哈希（采样 100 像素） */
    private fun bitmapStableHash(bitmap: Bitmap): Long {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var hash = 0L
        val step = max(1, pixels.size / 100)
        for (i in pixels.indices step step) {
            hash = hash * 31 + pixels[i]
        }
        return hash
    }
}
