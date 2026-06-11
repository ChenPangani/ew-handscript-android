package com.ew.handscript.core.render

import com.ew.handscript.model.GlyphModel
import com.ew.handscript.model.typeset.FontConfig
import javax.inject.Inject
import javax.inject.Singleton

data class PageLayout(val glyphs: List<GlyphPosition>)
data class GlyphPosition(val x: Float, val y: Float, val glyph: GlyphModel)
data class LayoutResult(val pages: List<PageLayout>)

@Singleton
class LayoutComputationEngine @Inject constructor() {
    fun computeDocumentLayout(
        textContent: String,
        fontConfig: FontConfig,
        userGlyphs: Map<String, List<GlyphModel>>,
    pageWidth: Float,
    pageHeight: Float
    ): LayoutResult {
        return LayoutResult(emptyList())
    }
}