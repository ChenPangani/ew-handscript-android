package com.ew.handscript.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandwritingRenderEngine @Inject constructor(private val glyphCache: GlyphCache) {
    private val renderMutex = Mutex()
    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

    suspend fun renderGlyph(
        charValue: String,
        width: Int,
        height: Int,
        style: RenderStyle = RenderStyle.DEFAULT
    ): Bitmap = renderMutex.withLock {
        val cacheKey = "${charValue}_${width}x${height}_${style.hashCode()}"
        bitmapCache[cacheKey]?.let { return it }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.inkColor
            strokeWidth = style.strokeWidth
        }

        val glyphData = glyphCache.getGlyph(charValue)
        glyphData?.let { drawGlyphPath(canvas, it, paint, width, height) }

        bitmapCache[cacheKey] = bitmap
        bitmap
    }

    fun renderPage(
        pageLayout: com.ew.handscript.core.render.PageLayout,
        fontConfig: com.ew.handscript.model.typeset.FontConfig,
        userGlyphs: Map<String, List<com.ew.handscript.model.GlyphModel>>
    ): Bitmap {
        return Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    }

    fun clearCache() {
        bitmapCache.values.forEach { it.recycle() }
        bitmapCache.clear()
    }

    private fun drawGlyphPath(
        canvas: Canvas,
        glyphData: GlyphData,
        paint: Paint,
        width: Int,
        height: Int
    ) {
        val bounds = Rect()
        paint.getTextBounds(glyphData.charValue, 0, glyphData.charValue.length, bounds)
        val x = (width - bounds.width()) / 2f - bounds.left
        val y = (height + bounds.height()) / 2f - bounds.bottom
        canvas.drawText(glyphData.charValue, x, y, paint)
    }

    data class RenderStyle(
        val inkColor: Int = 0xFF1A1A1A.toInt(),
        val strokeWidth: Float = 3f
    ) {
        companion object {
            val DEFAULT = RenderStyle()
        }
    }
}

interface GlyphCache {
    fun getGlyph(char: String): GlyphData?
    fun putGlyph(char: String, data: GlyphData)
}

data class GlyphData(
    val charValue: String,
    val pathData: String? = null,
    val sourceImage: String? = null
)