package com.ew.handscript.ui.screens.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ew.handscript.data.local.GlyphDao
import com.ew.handscript.data.repository.GlyphRepository
import com.ew.handscript.model.GlyphModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val glyphDao: GlyphDao,
    private val glyphRepository: GlyphRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyUiState())
    val uiState: StateFlow<VerifyUiState> = _uiState.asStateFlow()

    fun loadGlyphs(documentId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val glyphs = glyphDao.getUnverifiedGlyphs()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        glyphs = glyphs.map { entity -> entity.toModel() },
                        totalCount = glyphs.size,
                        currentIndex = 0
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "加载待校对字形失败")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        _uiState.update { it.copy(currentIndex = index) }
    }

    fun confirmCurrent() {
        val currentGlyph = getCurrentGlyph() ?: return
        viewModelScope.launch {
            glyphDao.verifyGlyph(currentGlyph.id)
            moveToNext()
        }
    }

    fun correctCurrent(correctedText: String, applyToAll: Boolean) {
        val currentGlyph = getCurrentGlyph() ?: return
        viewModelScope.launch {
            if (applyToAll) {
                // 应用到所有相同Unicode的未验证字形
                val sameGlyphs = _uiState.value.glyphs.filter {
                    it.unicode == currentGlyph.unicode && !it.isVerified
                }
                sameGlyphs.forEach {
                    glyphDao.correctGlyph(it.id, correctedText)
                    glyphDao.verifyGlyph(it.id, correctedText)
                }
            } else {
                glyphDao.correctGlyph(currentGlyph.id, correctedText)
                glyphDao.verifyGlyph(currentGlyph.id, correctedText)
            }
            dismissCorrectDialog()
            moveToNext()
        }
    }

    fun skipCurrent() {
        moveToNext()
    }

    fun confirmAll() {
        viewModelScope.launch {
            val allIds = _uiState.value.glyphs.map { it.id }
            glyphRepository.batchVerifyGlyphs(allIds)
            _uiState.update { it.copy(glyphs = emptyList()) }
        }
    }

    fun toggleBatchMode() {
        _uiState.update { it.copy(isBatchMode = !it.isBatchMode, selectedIds = emptySet()) }
    }

    fun toggleSelection(glyphId: Long) {
        _uiState.update { state ->
            val newSelection = state.selectedIds.toMutableSet()
            if (newSelection.contains(glyphId)) {
                newSelection.remove(glyphId)
            } else {
                newSelection.add(glyphId)
            }
            state.copy(selectedIds = newSelection)
        }
    }

    fun showCorrectDialog() {
        _uiState.update { it.copy(showCorrectDialog = true) }
    }

    fun dismissCorrectDialog() {
        _uiState.update { it.copy(showCorrectDialog = false) }
    }

    private fun getCurrentGlyph(): GlyphModel? {
        return _uiState.value.glyphs.getOrNull(_uiState.value.currentIndex)
    }

    private fun moveToNext() {
        _uiState.update { state ->
            val nextIndex = state.currentIndex + 1
            if (nextIndex >= state.glyphs.size) {
                state.copy(glyphs = state.glyphs.filterIndexed { index, _ ->
                    index != state.currentIndex
                }, currentIndex = (state.currentIndex).coerceIn(0, (state.glyphs.size - 2).coerceAtLeast(0)))
            } else {
                state.copy(currentIndex = nextIndex)
            }
        }
    }
}
