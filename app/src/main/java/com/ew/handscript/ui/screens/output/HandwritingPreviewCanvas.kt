package com.ew.handscript.ui.screens.output

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas

/**
 * 手写排版预览Canvas组件
 *
 * 使用系统默认字体+随机扰动模拟手写效果。
 * 缺字用红色标注。
 *
 * @param textContent 待排版文本
 * @param isVertical 是否竖排（奏折模板使用竖排）
 * @param missingChars 缺字集合
 */
@Composable
fun HandwritingPreviewCanvas(
    textContent: String,
    isVertical: Boolean,
    missingChars: Set<Char>
) {
    val inkColor = Color(0xFF1A1A2E)
    val missingColor = Color(0xFFCC0000)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }

        if (isVertical) {
            drawVertical(textContent, w, h, missingChars, inkColor, missingColor, paint)
        } else {
            drawHorizontal(textContent, w, h, missingChars, inkColor, missingColor, paint)
        }
    }
}

/** 横排文本绘制 */
private fun drawHorizontal(
    text: String, w: Float, h: Float,
    missingChars: Set<Char>, inkColor: Color, missingColor: Color,
    paint: android.graphics.Paint
) {
    val fontSize = (w / 12).coerceIn(12f, 28f)
    val lineH = fontSize * 1.6f
    val margin = w * 0.06f
    val maxW = w - margin * 2
    paint.textSize = fontSize

    var x = margin
    var y = margin + fontSize
    val chars = text.toCharArray()
    var i = 0

    while (i < chars.size && y < h - margin) {
        val ch = chars[i]
        paint.color = colorForChar(ch, missingChars, inkColor, missingColor)

        if (ch == '\n') {
            x = margin; y += lineH; i++; continue
        }
        if (ch.isWhitespace()) { i++; continue }

        val seed = i * 7 + 3
        val rnd = java.util.Random(seed.toLong())
        val perturb = Perturbation(
            ox = (rnd.nextFloat() - 0.5f) * 2f,
            oy = (rnd.nextFloat() - 0.5f) * 1.5f,
            rot = (rnd.nextFloat() - 0.5f) * 3f
        )
        val cw = paint.measureText(ch.toString())
        if (x + cw > maxW + margin) { x = margin; y += lineH; continue }

        drawContext.canvas.nativeCanvas.apply {
            save()
            translate(x + perturb.ox, y + perturb.oy)
            rotate(perturb.rot)
            drawText(ch.toString(), 0f, 0f, paint)
            Restore()
        }
        x += cw + 2f; i++
    }
}

/** 竖排文本绘制 */
private fun drawVertical(
    text: String, w: Float, h: Float,
    missingChars: Set<Char>, inkColor: Color, missingColor: Color,
    paint: android.graphics.Paint
) {
    val fontSize = (w / 10).coerceIn(14f, 30f)
    val colW = fontSize * 1.5f
    val margin = w * 0.06f
    paint.textSize = fontSize

    var cx = w - margin - fontSize
    var y = margin + fontSize
    val chars = text.toCharArray()
    var i = 0

    while (i < chars.size && cx > margin) {
        val ch = chars[i]
        paint.color = colorForChar(ch, missingChars, inkColor, missingColor)

        if (ch.isWhitespace() || ch == '\n') { y += fontSize; i++; continue }

        if (y + fontSize > h - margin) { cx -= colW; y = margin + fontSize; continue }

        val seed = i * 11 + 5
        val rnd = java.util.Random(seed.toLong())
        val perturb = Perturbation(
            ox = (rnd.nextFloat() - 0.5f) * 1.5f,
            oy = (rnd.nextFloat() - 0.5f) * 2f,
            rot = (rnd.nextFloat() - 0.5f) * 2.5f
        )

        drawContext.canvas.nativeCanvas.apply {
            Save()
            translate(cx + perturb.ox, y + perturb.oy)
            rotate(perturb.rot)
            drawText(ch.toString(), 0f, 0f, paint)
            restore()
        }
        y += fontSize + 2f; i++
    }
}

/** 字符颜色判断 */
private fun colorForChar(ch: Char, missingChars: Set<Char>, ink: Color, missing: Color) =
    (if (ch in missingChars) missing else ink).toArgb()

/** 扰动参数 */
private data class Perturbation(val ox: Float, val oy: Float, val rot: Float)
