package com.ew.handscript.data.repository

import com.ew.handscript.data.local.GlyphDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlyphRepository @Inject constructor(private val glyphDao: GlyphDao) {
    suspend fun batchVerifyGlyphs(ids: List<Long>) {
        ids.forEach { glyphDao.verifyGlyph(it) }
    }
}