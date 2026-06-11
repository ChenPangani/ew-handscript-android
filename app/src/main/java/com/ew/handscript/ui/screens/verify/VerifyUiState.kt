package com.ew.handscript.ui.state

import com.ew.handscript.model.GlyphModel

data class VerifyUiState(
    val isLoading: Boolean = false,
    val glyphs: List<GlyphModel> = emptyList(),
    val totalCount: Int = 0,
    val currentIndex: Int = 0,
    val isBatchMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val showCorrectDialog: Boolean = false
)