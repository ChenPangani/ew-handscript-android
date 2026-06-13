package com.ew.handscript.ui.screens.home

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

/**
 * 首页 ViewModel
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val glyphRepository: GlyphRepository,
    private val glyphDao: GlyphDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadLibraryStats()
    }

    private fun loadLibraryStats() {
        viewModelScope.launch {
            try {
                val totalCount = glyphDao.getTotalGlyphCount()
                val uniqueCount = glyphDao.getUniqueVerifiedCharCount()
                val level = GlyphModel.getLibraryLevel(uniqueCount)
                val targetCount = getNextLevelTarget(level)
                val progress = if (targetCount > 0) {
                    (uniqueCount.toFloat() / targetCount).coerceIn(0f, 1f)
                } else 1f

                _uiState.update {
                    it.copy(
                        verifiedCount = totalCount,  // 使用总入库数显示
                        targetCount = targetCount,
                        libraryLevel = level,
                        progress = progress,
                        recentDocuments = emptyList() // TODO: 加载最近文档
                    )
                }
                
                Timber.d("字库统计已刷新：总入库数=$totalCount, 去重字符数=$uniqueCount")
            } catch (e: Exception) {
                Timber.e(e, "加载字库统计失败")
            }
        }
    }
    
    /**
     * 手动刷新统计数据（用于入库后回调）
     */
    fun refreshStats() {
        loadLibraryStats()
    }

    private fun getNextLevelTarget(currentLevel: LibraryLevel): Int {
        return when (currentLevel) {
            LibraryLevel.STARTER -> GlyphModel.BASIC_GLYPH_COUNT
            LibraryLevel.BASIC -> GlyphModel.STANDARD_GLYPH_COUNT
            LibraryLevel.STANDARD -> GlyphModel.COMPLETE_GLYPH_COUNT
            LibraryLevel.COMPLETE -> GlyphModel.COMPLETE_GLYPH_COUNT + 1000
        }
    }
}
