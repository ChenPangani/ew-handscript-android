package com.ew.handscript.ui.screens.workspace

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ew.handscript.core.render.HandwritingRenderEngine
import com.ew.handscript.core.render.LayoutComputationEngine
import com.ew.handscript.data.local.GlyphDao
import com.ew.handscript.model.typeset.FontConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val glyphDao: GlyphDao,
    private val layoutEngine: LayoutComputationEngine,
    private val renderEngine: HandwritingRenderEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    fun showTextInput() {
        _uiState.update { it.copy(showTextInputDialog = true) }
    }

    fun dismissTextInput() {
        _uiState.update { it.copy(showTextInputDialog = false) }
    }

    fun showDocumentImport() {
        // TODO: 打开文档导入
    }

    fun showVoiceInput() {
        // TODO: 打开语音输入
    }

    fun setTextContent(text: String) {
        _uiState.update {
            it.copy(
                textContent = text,
                hasContent = text.isNotBlank(),
                showTextInputDialog = false
            )
        }
        if (text.isNotBlank()) {
            generatePreview()
        }
    }

    fun updateText(text: String) {
        _uiState.update { it.copy(textContent = text) }
    }

    fun updateFontConfig(config: FontConfig) {
        _uiState.update { it.copy(fontConfig = config) }
        if (_uiState.value.hasContent) {
            generatePreview()
        }
    }

    fun showExportOptions() {
        _uiState.update { it.copy(showExportDialog = true) }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(showExportDialog = false) }
    }

    fun exportAsPng() {
        // TODO: 实现PNG导出
        _uiState.update { it.copy(showExportDialog = false, isExporting: true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(isExporting: false) }
        }
    }

    fun exportAsPdf() {
        // TODO: 实现PDF导出
        _uiState.update { it.copy(showExportDialog = false, isExporting: true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(isExporting: false) }
        }
    }

    private fun generatePreview() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val userGlyphs = glyphDao.getAllVerifiedGlyphsForRendering()
                    .map { it.toModel() }
                    .groupBy { it.unicode }

                if (userGlyphs.isEmpty()) return@launch

                val layoutResult = layoutEngine.computeDocumentLayout(
                    textContent = state.textContent,
                    fontConfig = state.fontConfig,
                    userGlyphs = userGlyphs,
                    pageWidth = 2480f,
                    pageHeight = 3508f
                )

                if (layoutResult.pages.isNotEmpty()) {
                    val bitmap = renderEngine.renderPage(
                        pageLayout = layoutResult.pages.first(),
                        fontConfig = state.fontConfig,
                        userGlyphs = userGlyphs
                    )
                    _uiState.update { it.copy(previewBitmap = bitmap) }
                }
            } catch (e: Exception) {
                Timber.e(e, "预览生成失败")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        renderEngine.clearCache()
    }
}
