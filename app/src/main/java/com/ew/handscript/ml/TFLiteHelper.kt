package com.ew.handscript.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.MappedByteBuffer
import java.util.Random

/**
 * TFLite模型推理助手
 *
 * 管理两个模型：
 * 1. 五行特征提取（wuxing_feature.tflite）→ 5维输出
 * 2. 印刷体过滤（print_filter.tflite）→ 2维输出
 *
 * 支持Mock模式（无模型文件时用随机数模拟输出）
 */
class TFLiteHelper(private val context: Context) {

    /** Mock模式开关：true时用随机数模拟推理 */
    var isMockMode: Boolean = true

    /** 模型输入尺寸 */
    private val inputSize = 224

    /** 图像预处理器：Resize → Normalize([0,1]) */
    private val imageProcessor by lazy {
        ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f)) // [0,255] → [0,1]
            .build()
    }

    // 模型Interpreter（延迟初始化）
    private var wuxingInterpreter: Interpreter? = null
    private var printInterpreter: Interpreter? = null

    /** 五行模型文件名 */
    private val wuxingModelPath = "models/wuxing_feature_extractor.tflite"
    /** 印刷体过滤模型文件名 */
    private val printModelPath = "models/print_filter.tflite"

    /**
     * 初始化五行模型（按需调用，不阻塞启动）
     */
    fun initWuxingModel(): Boolean {
        if (isMockMode) return true
        return try {
            wuxingInterpreter = loadModel(wuxingModelPath)
            true
        } catch (e: Exception) {
            android.util.Log.w(TAG, "五行模型加载失败，回退Mock模式: ${e.message}")
            isMockMode = true
            false
        }
    }

    /**
     * 初始化印刷体过滤模型（按需调用）
     */
    fun initPrintFilterModel(): Boolean {
        if (isMockMode) return true
        return try {
            printInterpreter = loadModel(printModelPath)
            true
        } catch (e: Exception) {
            android.util.Log.w(TAG, "印刷体过滤模型加载失败，回退Mock模式: ${e.message}")
            isMockMode = true
            false
        }
    }

    /**
     * 从assets加载TFLite模型
     */
    private fun loadModel(modelPath: String): Interpreter {
        val model: MappedByteBuffer = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options().apply {
            numThreads = 2 // 麒麟990双线程推理
            useXNNPACK = true // 启用XNNPACK加速
        }
        return Interpreter(model, options)
    }

    /**
     * 五行特征推理：Bitmap → 五行属性值
     *
     * @param bitmap 输入图像（单字截图）
     * @return WuXingResult 五行属性分布
     */
    fun inferWuXing(bitmap: Bitmap): WuXingResult {
        if (isMockMode) return mockWuXingResult()

        val interpreter = wuxingInterpreter ?: run {
            initWuxingModel()
            wuxingInterpreter
        } ?: return mockWuXingResult()

        return try {
            val input = preprocess(bitmap)
            val output = Array(1) { FloatArray(5) }
            interpreter.run(input.buffer, output)
            parseWuXingOutput(output[0])
        } catch (e: Exception) {
            android.util.Log.e(TAG, "五行推理失败: ${e.message}")
            mockWuXingResult()
        }
    }

    /**
     * 印刷体过滤推理：Bitmap → 是否手写体
     *
     * @param bitmap 输入图像
     * @return PrintFilterResult 二分类结果
     */
    fun inferPrintFilter(bitmap: Bitmap): PrintFilterResult {
        if (isMockMode) return mockPrintFilterResult()

        val interpreter = printInterpreter ?: run {
            initPrintFilterModel()
            printInterpreter
        } ?: return mockPrintFilterResult()

        return try {
            val input = preprocess(bitmap)
            val output = Array(1) { FloatArray(2) }
            interpreter.run(input.buffer, output)
            parsePrintFilterOutput(output[0])
        } catch (e: Exception) {
            android.util.Log.e(TAG, "印刷体过滤推理失败: ${e.message}")
            mockPrintFilterResult()
        }
    }

    /**
     * 图像预处理：Bitmap → TensorImage (224×224×3)
     */
    private fun preprocess(bitmap: Bitmap): TensorImage {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        return imageProcessor.process(tensorImage)
    }

    /**
     * 解析五行输出：[金, 木, 水, 火, 土]
     */
    private fun parseWuXingOutput(raw: FloatArray): WuXingResult {
        // Softmax归一化
        val exp = raw.map { kotlin.math.exp(it) }
        val sum = exp.sum()
        val norm = exp.map { (it / sum).toFloat() }

        val maxIndex = norm.indices.maxByOrNull { norm[it] } ?: 0
        val elements = listOf("金", "木", "水", "火", "土")

        return WuXingResult(
            metal = norm[0],
            wood = norm[1],
            water = norm[2],
            fire = norm[3],
            earth = norm[4],
            destinyTag = WuXingResult.DESTINY_TAGS[elements[maxIndex]] ?: "未知"
        )
    }

    /**
     * 解析印刷体过滤输出：[手写体分数, 印刷体分数]
     */
    private fun parsePrintFilterOutput(raw: FloatArray): PrintFilterResult {
        val handwrittenScore = kotlin.math.exp(raw[0])
        val printScore = kotlin.math.exp(raw[1])
        val total = handwrittenScore + printScore
        val confidence = (handwrittenScore / total).toFloat()
        return PrintFilterResult(
            isHandwritten = confidence > PRINT_FILTER_THRESHOLD,
            confidence = confidence
        )
    }

    // ===== Mock模式 =====

    /** Mock五行结果（随机分布，每次不同） */
    private fun mockWuXingResult(): WuXingResult {
        val rnd = Random(System.nanoTime())
        val raw = List(5) { rnd.nextFloat() * 2 - 1 } // [-1, 1]
        val exp = raw.map { kotlin.math.exp(it) }
        val sum = exp.sum()
        val norm = exp.map { (it / sum).toFloat() }
        val maxIndex = norm.indices.maxByOrNull { norm[it] } ?: 0
        val elements = listOf("金", "木", "水", "火", "土")
        return WuXingResult(
            metal = norm[0], wood = norm[1], water = norm[2],
            fire = norm[3], earth = norm[4],
            destinyTag = WuXingResult.DESTINY_TAGS[elements[maxIndex]] ?: "未知"
        )
    }

    /** Mock印刷体过滤结果（90%概率通过） */
    private fun mockPrintFilterResult(): PrintFilterResult {
        val rnd = Random(System.nanoTime())
        val confidence = 0.6f + rnd.nextFloat() * 0.35f // 0.6~0.95
        return PrintFilterResult(isHandwritten = true, confidence = confidence)
    }

    /**
     * 释放模型资源（Activity销毁时调用）
     */
    fun close() {
        wuxingInterpreter?.close()
        printInterpreter?.close()
        wuxingInterpreter = null
        printInterpreter = null
    }

    companion object {
        private const val TAG = "TFLiteHelper"
        /** 印刷体过滤阈值 */
        private const val PRINT_FILTER_THRESHOLD = 0.5f

        /** 单例实例 */
        @Volatile
        private var instance: TFLiteHelper? = null

        fun getInstance(context: Context): TFLiteHelper {
            return instance ?: synchronized(this) {
                instance ?: TFLiteHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
