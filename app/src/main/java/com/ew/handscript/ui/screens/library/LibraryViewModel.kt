package com.ew.handscript.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ew.handscript.data.local.GlyphDao
import com.ew.handscript.data.repository.GlyphRepository
import com.ew.handscript.model.GlyphModel
import com.ew.handscript.model.LibraryLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val glyphDao: GlyphDao,
    private val glyphRepository: GlyphRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLibraryData()
    }

    private fun loadLibraryData() {
        viewModelScope.launch {
            try {
                val verifiedGlyphs = glyphDao.getVerifiedUniqueGlyphs()
                val count = verifiedGlyphs.size
                val level = GlyphModel.getLibraryLevel(count)
                val targetCount = when (level) {
                    LibraryLevel.STARTER -> GlyphModel.BASIC_GLYPH_COUNT
                    LibraryLevel.BASIC -> GlyphModel.STANDARD_GLYPH_COUNT
                    LibraryLevel.STANDARD -> GlyphModel.COMPLETE_GLYPH_COUNT
                    LibraryLevel.COMPLETE -> GlyphModel.COMPLETE_GLYPH_COUNT + 1000
                }
                val progress = if (targetCount > 0) {
                    (count.toFloat() / targetCount).coerceIn(0f, 1f)
                } else 1f

                // 按字符分组
                val grouped = verifiedGlyphs.map { it.toModel() }.groupBy { it.character }

                _uiState.update {
                    it.copy(
                        verifiedCount = count,
                        libraryLevel = level,
                        progress = progress,
                        groupedGlyphs = grouped
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "加载字库数据失败")
            }
        }
    }

    fun startFontGeneration() {
        if (_uiState.value.verifiedCount == 0) return
        _uiState.update { it.copy(showGenerateDialog = true) }
    }

    fun submitFontGeneration(fontName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(showGenerateDialog = false, isGenerating = true) }

            // 模拟生成进度
            val stages = listOf(
                "正在矢量化字形..." to 0.3f,
                "正在编译字体文件..." to 0.7f,
                "正在打包TTF..." to 0.9f,
                "生成完成！" to 1f
            )

            for ((stage, progress) in stages) {
                kotlinx.coroutines.delay(800)
                _uiState.update { it.copy(generationStage = stage, generationProgress = progress) }
            }

            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(isGenerating = false, generationProgress = 0f) }
        }
    }

    fun dismissGenerateDialog() {
        _uiState.update { it.copy(showGenerateDialog = false) }
    }

    fun addTag(glyphId: Long, tag: String) {
        // TODO: 实现标签添加
    }
}
