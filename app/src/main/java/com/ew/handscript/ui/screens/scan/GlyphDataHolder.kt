package com.ew.handscript.ui.screens.scan

import android.graphics.Bitmap

/**
 * 字形数据临时持有者（用于页面间传递数据）
 * 
 * 职责：在ScanScreen和ProofreadScreen之间传递选中的字形数据
 */
object GlyphDataHolder {
    
    /** 当前选中的字形数据 */
    private var currentGlyph: SegmentedGlyph? = null
    
    /**
     * 保存选中的字形数据
     */
    fun setGlyph(glyph: SegmentedGlyph) {
        currentGlyph = glyph
    }
    
    /**
     * 获取当前字形数据
     */
    fun getGlyph(): SegmentedGlyph? {
        return currentGlyph
    }
    
    /**
     * 清空数据
     */
    fun clear() {
        currentGlyph = null
    }
}
