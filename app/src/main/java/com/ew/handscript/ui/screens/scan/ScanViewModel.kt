package com.ew.handscript.ui.screens.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ew.handscript.core.scan.ScanCorrectionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanEngine: ScanCorrectionEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Initial)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var currentImagePath: String? = null

    fun startCamera() {
        _uiState.value = ScanUiState.Camera
    }

    fun openGallery() {
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
        processImage()
    }

    private fun processImage() {
        val imagePath = currentImagePath ?: return

        viewModelScope.launch {
            _uiState.value = ScanUiState.Processing(
                progress = 0f,
                stage = "正在检测文档区域..."
            )

            try {
                val result = scanEngine.processImage(imagePath)

                result.onSuccess { scanResult ->
                    _uiState.value = ScanUiState.Processing(
                        progress = 0.8f,
                        stage = "检测基线位置..."
                    )

                    // 模拟短暂延迟以显示进度
                    kotlinx.coroutines.delay(300)

                    _uiState.value = ScanUiState.Preview(
                        documentId = System.currentTimeMillis(),
                        originalImage = imagePath,
                        correctedImage = imagePath, // 实际应使用矫正后的路径
                        cornerPoints = scanResult.cornerPoints,
                        baselines = scanResult.baselinePositions
                    )
                }.onFailure { error ->
                    Timber.e(error, "扫描处理失败")
                    _uiState.value = ScanUiState.Error(
                        message = error.message ?: "处理失败，请重试"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "扫描处理异常")
                _uiState.value = ScanUiState.Error(
                    message = e.message ?: "未知错误"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanEngine.release()
    }
}
