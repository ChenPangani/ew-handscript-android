package com.ew.handscript.ui.screens.library

import com.ew.handscript.model.GlyphModel
import com.ew.handscript.model.LibraryLevel

data class LibraryUiState(
    val verifiedCount: Int = 0,
    val libraryLevel: LibraryLevel = LibraryLevel.STARTER,
    val progress: Float = 0f,
    val groupedGlyphs: Map<String, List<GlyphModel>> = emptyMap(),
    val showGenerateDialog: Boolean = false,
    val isGenerating: Boolean = false,
    val generationStage: String = "",
    val generationProgress: Float = 0f
)
