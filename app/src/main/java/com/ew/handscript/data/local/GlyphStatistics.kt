package com.ew.handscript.data.local

/**
 * 字形统计结果 - 对应 GlyphDao.getGlyphStatistics() 的 @Query 返回
 */
data class GlyphStatistics(
    val total: Int,
    val verified: Int,
    val unique_chars: Int
)
