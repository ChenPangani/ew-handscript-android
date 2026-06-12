package com.ew.handscript.ui.screens.scan

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ew.handscript.core.scan.ScanCorrectionEngine
import com.ew.handscript.ml.FiveElementValues
import com.ew.handscript.ml.TFLiteHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanEngine: ScanCorrectionEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Initial)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var currentImagePath: String? = null
    private var tfliteHelper: TFLiteHelper? = null
    
    // 记录当前操作来源（用于重试时决定重新打开相机还是相册）
    private var lastSource: SourceType = SourceType.NONE

    enum class SourceType {
        NONE, CAMERA, GALLERY
    }

    fun initializeTFLite(context: Context) {
        if (tfliteHelper == null) {
            tfliteHelper = TFLiteHelper.getInstance(context)
            tfliteHelper?.initialize()
        }
    }

    fun startCamera() {
        lastSource = SourceType.CAMERA
        _uiState.value = ScanUiState.Camera
    }

    fun openGallery() {
        lastSource = SourceType.GALLERY
        _uiState.value = ScanUiState.Gallery
    }

    fun cancelCamera() {
        _uiState.value = ScanUiState.Initial
    }

    fun cancelGallery() {
        _uiState.value = ScanUiState.Initial
    }

    fun captureImage(path: String) {
        currentImagePath = path
        processImage()
    }

    fun processSelectedImages(paths: List<String>) {
        currentImagePath = paths.firstOrNull()
        processImage()
    }

    fun retake() {
        _uiState.value = ScanUiState.Initial
    }

    fun enterManualAdjust() {
        // TODO: 手动调整模式
    }

    fun retry() {
        // 根据上次操作来源重新打开相机或相册
        when (lastSource) {
            SourceType.CAMERA -> startCamera()
            SourceType.GALLERY -> openGallery()
            SourceType.NONE -> _uiState.value = ScanUiState.Initial
        }
    }

    private fun processImage() {
        val imagePath = currentImagePath
        // 拍照状态机安全检查
        if (imagePath.isNullOrEmpty()) {
            Timber.e("processImage: imagePath为空")
            _uiState.value = ScanUiState.Error(message = "图片路径无效，请重试")
            return
        }

        // 检查文件是否存在
        val imageFile = File(imagePath)
        if (!imageFile.exists() || imageFile.length() == 0L) {
            Timber.e("processImage: 文件不存在或为空: $imagePath")
            _uiState.value = ScanUiState.Error(message = "图片文件无效，请重试")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = ScanUiState.Processing(
                    progress = 0f,
                    stage = "正在检测文档区域..."
                )

                val result = scanEngine.processImage(imagePath)

                result.onSuccess { scanResult ->
                    try {
                        _uiState.value = ScanUiState.Processing(
                            progress = 0.5f,
                            stage = "正在分割字形..."
                        )
                        // 执行分割和推理
                        performSegmentation(scanResult.correctedBitmap)
                    } catch (e: Exception) {
                        Timber.e(e, "处理扫描结果异常")
                        _uiState.value = ScanUiState.Error(message = "处理失败，请重试")
                    }

                }.onFailure { error ->
                    Timber.e(error, "扫描处理失败")
                    _uiState.value = ScanUiState.Error(
                        message = error.message ?: "处理失败，请重试"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "processImage整体异常")
                _uiState.value = ScanUiState.Error(
                    message = e.message ?: "未知错误，请重试"
                )
            }
        }
    }

    /**
     * 执行字形分割和五行推理
     */
    private fun performSegmentation(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 检查Bitmap是否有效
                if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
                    Timber.e("无效的Bitmap: ${bitmap.width}x${bitmap.height}, recycled=${bitmap.isRecycled}")
                    _uiState.value = ScanUiState.Error(message = "图片无效，请重试")
                    return@launch
                }

                _uiState.value = ScanUiState.Segmenting(
                    progress = 0.6f,
                    stage = "正在识别字形..."
                )

                // 执行分割（包裹在try-catch中）
                var glyphs = try {
                    ScanSegmentation.segmentHandwriting(bitmap) { glyphBitmap ->
                        inferWuxingInternal(glyphBitmap)
                    }
                } catch (segmentEx: Exception) {
                    Timber.e(segmentEx, "切字异常，返回fallback数据")
                    ScanSegmentation.fallbackSegmentation(bitmap) { glyphBitmap ->
                        inferWuxingInternal(glyphBitmap)
                    }
                }

                // 如果返回空列表，使用fallback
                if (glyphs.isEmpty()) {
                    Timber.w("切字返回空列表，使用fallback")
                    glyphs = ScanSegmentation.fallbackSegmentation(bitmap) { glyphBitmap ->
                        inferWuxingInternal(glyphBitmap)
                    }
                }

                _uiState.value = ScanUiState.Segmenting(
                    progress = 0.8f,
                    stage = "正在分析五行..."
                )

                // 填充占位符（确保9个）
                while (glyphs.size < 9) {
                    glyphs = glyphs + ScanSegmentation.createPlaceholderGlyph()
                }

                // 计算总体五行
                val totalWuxing = calculateTotalWuxing(glyphs)

                // 完成
                _uiState.value = ScanUiState.GridResult(
                    originalBitmap = bitmap,
                    glyphs = glyphs,
                    totalWuxing = totalWuxing,
                    isMock = tfliteHelper?.isMockMode ?: true
                )

            } catch (e: Exception) {
                Timber.e(e, "分割处理异常")
                // 显示错误状态，而不是继续处理
                _uiState.value = ScanUiState.Error(message = "处理失败，请重试")
            }
        }
    }

    /**
     * 内部五行推理函数
     */
    private suspend fun inferWuxingInternal(glyphBitmap: Bitmap): FloatArray {
        if (glyphBitmap.isRecycled) {
            Timber.w("字形Bitmap已被回收，返回Mock数据")
            return floatArrayOf(50f, 50f, 50f, 50f, 50f)
        }
        return try {
            val result = tfliteHelper?.awakenWuxing(glyphBitmap)
                ?: generateMockResult()
            floatArrayOf(result.wood, result.fire, result.earth, result.metal, result.water)
        } catch (e: Exception) {
            Timber.e(e, "五行推理异常，返回Mock数据")
            floatArrayOf(50f, 50f, 50f, 50f, 50f)
        }
    }

    /**
     * 计算总体五行
     */
    private fun calculateTotalWuxing(glyphs: List<SegmentedGlyph>): FiveElementValues {
        val validGlyphs = glyphs.filter { !it.isPlaceholder }
        if (validGlyphs.isEmpty()) {
            return generateMockResult()
        }

        var wood = 0f
        var fire = 0f
        var earth = 0f
        var metal = 0f
        var water = 0f

        validGlyphs.forEach { glyph ->
            when (glyph.wuXing) {
                "木" -> wood += glyph.confidence
                "火" -> fire += glyph.confidence
                "土" -> earth += glyph.confidence
                "金" -> metal += glyph.confidence
                "水" -> water += glyph.confidence
            }
        }

        val count = validGlyphs.size.toFloat()
        return FiveElementValues(
            wood = wood / count,
            fire = fire / count,
            earth = earth / count,
            metal = metal / count,
            water = water / count
        )
    }

    /**
     * 生成Mock九宫格数据
     */
    private fun generateMockGlyphs(): List<SegmentedGlyph> {
        val wuxingLabels = arrayOf("木", "火", "土", "金", "水")
        val glyphs = mutableListOf<SegmentedGlyph>()

        for (i in 0 until 9) {
            val isPlaceholder = i >= 6
            if (isPlaceholder) {
                glyphs.add(ScanSegmentation.createPlaceholderGlyph())
            } else {
                val wuxing = wuxingLabels[i % 5]
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                glyphs.add(
                    SegmentedGlyph(
                        id = "mock_${System.currentTimeMillis()}_$i",
                        bitmap = bitmap,
                        wuXing = wuxing,
                        confidence = 0.7f + Math.random().toFloat() * 0.3f
                    )
                )
            }
        }
        return glyphs
    }

    /**
     * 生成Mock五行结果（范围[0,100]）
     */
    private fun generateMockResult(): FiveElementValues {
        return FiveElementValues(65f, 45f, 78f, 32f, 55f)
    }

    override fun onCleared() {
        super.onCleared()
        scanEngine.release()
        tfliteHelper?.close()
    }
}
