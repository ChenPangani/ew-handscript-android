package com.ew.handscript.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ew.handscript.data.local.GlyphDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 字库列表页 ViewModel
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val glyphDao: GlyphDao
) : ViewModel() {

    private val _glyphs = MutableStateFlow<List<com.ew.handscript.data.local.GlyphEntity>>(emptyList())
    val glyphs: StateFlow<List<com.ew.handscript.data.local.GlyphEntity>> = _glyphs.asStateFlow()

    init {
        loadGlyphs()
    }

    /**
     * 加载所有已验证的字形
     */
    fun loadGlyphs() {
        viewModelScope.launch {
            try {
                val allGlyphs = glyphDao.getAllVerifiedGlyphsForRendering()
                _glyphs.value = allGlyphs
                Timber.d("[LibraryViewModel] 加载字库成功，共 ${allGlyphs.size} 个字形")
            } catch (e: Exception) {
                Timber.e(e, "[LibraryViewModel] 加载字库失败")
                _glyphs.value = emptyList()
            }
        }
    }
}