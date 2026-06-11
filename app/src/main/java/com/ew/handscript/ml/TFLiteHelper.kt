package com.ew.handscript.ml

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * TFLite 模型推理助手
 *
 * 职责：
 * 1. 五行灵根觉醒 — Bitmap → 128维特征 → 简化K-Means聚类 → FiveElementValues [0,100]
 * 2. 印刷体过滤 — Bitmap → Sigmoid → 手写体/印刷体二分类
 *
 * 模型规格（Agent-Algo 确认）：
 * - 输入：1×4×64×64（RGBA，Float32，归一化 /255）
 * - 五行模型输出：1×128（特征向量）→ 简化K-Means → FiveElementValues
 * - 印刷体模型输出：1×1（Sigmoid 置信度）
 *
 * 兜底设计（Wiki v0.7 §8.6）：
 * - 模型文件不存在 → 返回 Mock FiveElementValues（范围 [0,100]）
 * - 推理异常 → 返回 Mock FiveElementValues
 * - 绝不崩溃，UI 流程不中断
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
        private const val WUXING_MODEL = "models/wuxing_feature.tflite"
        private const val PRINT_FILTER_MODEL = "models/print_filter.tflite"

        // 算法训练尺寸（Agent-Algo 确认：64×64）
        private const val INPUT_SIZE = 64
        private const val CHANNELS = 4  // RGBA
        private const val FEATURE_DIM = 128

        // 印刷体过滤阈值
        private const val PRINT_FILTER_THRESHOLD = 0.5f
    }

    private val appContext = context.applicationContext

    // 两个独立 Interpreter（五行 + 印刷体）
    private var wuxingInterpreter: Interpreter? = null
    private var printFilterInterpreter: Interpreter? = null

    /** Mock 模式标记：任一模型加载失败即进入 */
    @Volatile
    var isMockMode = false
        private set

    /**
     * 初始化：尝试加载两个模型
     * 任一失败 → isMockMode = true，后续推理自动走 Mock
     */
    fun initialize() {
        wuxingInterpreter = tryLoadModel(WUXING_MODEL, numThreads = 4)
        printFilterInterpreter = tryLoadModel(PRINT_FILTER_MODEL, numThreads = 2)

        isMockMode = (wuxingInterpreter == null || printFilterInterpreter == null)

        if (isMockMode) {
            Timber.w("[TFLiteHelper] 模型未就绪，Mock 模式已启用")
        } else {
            Timber.i("[TFLiteHelper] 两个模型加载成功，真实推理模式")
        }
    }

    /** 尝试加载单个模型 */
    private fun tryLoadModel(path: String, numThreads: Int): Interpreter? {
        return try {
            val buffer = loadModelFile(path)
            Interpreter(buffer, Interpreter.Options().apply {
                setNumThreads(numThreads)
                setUseXNNPACK(true)
            })
        } catch (e: Exception) {
            Timber.w(e, "[TFLiteHelper] 模型加载失败 [$path]")
            null
        }
    }

    /** 从 assets 加载 .tflite 文件 */
    private fun loadModelFile(path: String): MappedByteBuffer {
        val afd = appContext.assets.openFd(path)
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
     * 真实路径：Bitmap → RGBA FloatArray → TFLite(128维) → 简化K-Means → FiveElementValues
     * Mock 路径：Bitmap → 稳定哈希 → 五行分布 [0,100]
     *
     * @param bitmap 输入字形图片（RGBA，任意尺寸，内部缩放到 64×64）
     * @return FiveElementValues（范围 [0,100]，isGolden() ≥80 为金字招牌）
     */
    suspend fun awakenWuxing(bitmap: Bitmap): FiveElementValues = withContext(Dispatchers.IO) {
        if (isMockMode || wuxingInterpreter == null) {
            return@withContext generateMockWuxing(bitmap)
        }
        try {
            val inputArray = bitmapToFloatArray(bitmap)
            val features = Array(1) { FloatArray(FEATURE_DIM) }
            wuxingInterpreter!!.run(inputArray, features)

            // 128维 → 简化K-Means聚类 → 5维五行值
            FiveElementKMeans.cluster(features[0])
        } catch (e: Exception) {
            Timber.e(e, "[TFLiteHelper] 五行推理异常，回退 Mock")
            generateMockWuxing(bitmap)
        }
    }

    /**
     * 印刷体过滤
     *
     * 真实路径：Bitmap → RGBA FloatArray → TFLite(Sigmoid) → 置信度
     * Mock 路径：返回 isPrint=false（当作手写体通过），confidence=1.0
     *
     * @param bitmap 输入字形图片
     * @return PrintFilterResult（isPrint=true 表示印刷体，应拒绝）
     */
    suspend fun filterPrint(bitmap: Bitmap): PrintFilterResult = withContext(Dispatchers.IO) {
        if (isMockMode || printFilterInterpreter == null) {
            return@withContext PrintFilterResult(isPrint = false, confidence = 1.0f, isMock = true)
        }
        try {
            val inputArray = bitmapToFloatArray(bitmap)
            val output = Array(1) { FloatArray(1) }
            printFilterInterpreter!!.run(inputArray, output)
            val confidence = output[0][0]
            PrintFilterResult(
                isPrint = confidence <= PRINT_FILTER_THRESHOLD,
                confidence = confidence,
                isMock = false
            )
        } catch (e: Exception) {
            Timber.e(e, "[TFLiteHelper] 印刷体过滤异常，回退 Mock")
            PrintFilterResult(isPrint = false, confidence = 1.0f, isMock = true)
        }
    }

    /** 模型是否可用 */
    fun isModelAvailable(): Boolean = !isMockMode

    /** 释放资源 */
    fun close() {
        wuxingInterpreter?.close()
        printFilterInterpreter?.close()
        wuxingInterpreter = null
        printFilterInterpreter = null
        instance = null
        isMockMode = false
        Timber.i("[TFLiteHelper] 资源已释放")
    }

    // ==================== 图像预处理 ====================

    /**
     * Bitmap → FloatArray [NCHW: 1×4×64×64]
     *
     * 手动提取 RGBA 4 通道（TensorImage 默认 RGB 3 通道，不适用）
     * 归一化：pixel / 255.0f
     */
    private fun bitmapToFloatArray(bitmap: Bitmap): Any {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val floatArray = FloatArray(CHANNELS * INPUT_SIZE * INPUT_SIZE)
        var idx = 0

        // NCHW 格式：先 R 通道全部像素，再 G，再 B，再 A
        for (c in 0 until CHANNELS) {
            for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
                val pixel = pixels[i]
                val value = when (c) {
                    0 -> (pixel shr 16) and 0xFF  // R
                    1 -> (pixel shr 8) and 0xFF   // G
                    2 -> pixel and 0xFF             // B
                    3 -> (pixel shr 24) and 0xFF  // A
                    else -> 255
                }
                floatArray[idx++] = value / 255.0f
            }
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
        val step = kotlin.math.max(1, pixels.size / 100)
        for (i in pixels.indices step step) {
            hash = hash * 31 + pixels[i]
        }
        return hash
    }
}

// ==================== 印刷体过滤结果 ====================

data class PrintFilterResult(
    val isPrint: Boolean,       // true=印刷体(应拒绝), false=手写体(通过)
    val confidence: Float,      // 置信度 [0,1]
    val isMock: Boolean = false // 是否为兜底数据
)

// ==================== 简化 K-Means 聚类 ====================

/**
 * 端侧五行聚类：128维特征 → FiveElementValues [0,100]
 *
 * 与 Python 算法模块一致：
 * - 固定投影矩阵（seed=42）
 * - 预定义聚类中心
 * - 余弦相似度匹配
 */
private object FiveElementKMeans {

    // 五行聚类中心 [笔画密度, 笔压强度, 方正度, 棱角度, 流畅度]
    private val CENTERS = mapOf(
        "wood" to floatArrayOf(0.6f, 0.4f, 0.2f, 0.1f, 0.8f),
        "fire" to floatArrayOf(0.5f, 0.9f, 0.3f, 0.9f, 0.4f),
        "earth" to floatArrayOf(0.5f, 0.6f, 0.9f, 0.5f, 0.5f),
        "metal" to floatArrayOf(0.7f, 0.7f, 0.8f, 1.0f, 0.3f),
        "water" to floatArrayOf(0.5f, 0.3f, 0.1f, 0.0f, 1.0f)
    )

    /**
     * 128维特征 → FiveElementValues [0,100]
     */
    fun cluster(features: FloatArray): FiveElementValues {
        // 1. 投影到 5 维五行空间
        val traits5D = project128To5(features)

        // 2. 与各聚类中心计算余弦相似度
        val scores = mutableMapOf<String, Float>()
        for ((type, center) in CENTERS) {
            var dot = 0f
            var normT = 0f
            var normC = 0f
            for (i in 0 until 5) {
                val t = traits5D[i] / 100f
                dot += t * center[i]
                normT += t * t
                normC += center[i] * center[i]
            }
            scores[type] = dot / (sqrt(normT) * sqrt(normC) + 1e-8f)
        }

        // 3. 归一化到 [0, 100]
        val values = scores.values.toList()
        val minVal = values.minOrNull() ?: 0f
        val range = (values.maxOrNull() ?: 1f) - minVal

        return FiveElementValues(
            wood = normalize(scores["wood"]!!, minVal, range),
            fire = normalize(scores["fire"]!!, minVal, range),
            earth = normalize(scores["earth"]!!, minVal, range),
            metal = normalize(scores["metal"]!!, minVal, range),
            water = normalize(scores["water"]!!, minVal, range)
        )
    }

    /** 128维 → 5维投影（seed=42，与 Python 端一致） */
    private fun project128To5(features: FloatArray): FloatArray {
        val result = FloatArray(5)
        val proj = getProjectionMatrix()
        for (i in 0 until 5) {
            var sum = 0f
            for (j in 0 until 128) {
                sum += features[j] * proj[i][j]
            }
            result[i] = (1f / (1f + exp(-sum * 3))) * 100f
        }
        return result
    }

    /** 确定性投影矩阵（seed=42） */
    private fun getProjectionMatrix(): Array<FloatArray> {
        val matrix = Array(5) { FloatArray(128) }
        var seed = 42L
        for (i in 0 until 5) {
            for (j in 0 until 128) {
                seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
                matrix[i][j] = (seed.toFloat() / 0x7fffffff.toFloat() - 0.5f) * 0.6f
            }
        }
        return matrix
    }

    private fun normalize(value: Float, min: Float, range: Float): Float =
        if (range > 0) (value - min) / range * 100f else 50f
}
