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
    
    /** 已跳过的字形索引列表 */
    private val skippedIndices = mutableSetOf<Int>()
    
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
        skippedIndices.clear()
    }
    
    /**
     * 标记当前字形已跳过
     */
    fun removeCurrentGlyph() {
        currentGlyph?.let { glyph ->
            // 记录被跳过的字形索引
            glyph.index?.let { index ->
                skippedIndices.add(index)
            }
        }
        currentGlyph = null
    }
    
    /**
     * 检查指定索引的字形是否已被跳过
     */
    fun isSkipped(index: Int): Boolean {
        return skippedIndices.contains(index)
    }
    
    /**
     * 获取已跳过的索引列表
     */
    fun getSkippedIndices(): Set<Int> {
        return skippedIndices
    }
}
