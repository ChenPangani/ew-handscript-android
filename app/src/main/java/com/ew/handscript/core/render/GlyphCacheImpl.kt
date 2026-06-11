package com.ew.handscript.core.render

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlyphCacheImpl @Inject constructor() : GlyphCache {
    private val cache = mutableMapOf<String, GlyphData>()

    override fun getGlyph(char: String): GlyphData? {
        return cache[char]
    }

    override fun putGlyph(char: String, data: GlyphData) {
        cache[char] = data
    }
}
