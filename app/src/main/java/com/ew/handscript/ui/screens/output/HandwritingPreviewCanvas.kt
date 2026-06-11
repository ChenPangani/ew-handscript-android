package com.ew.handscript.ui.screens.output

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * 万象法相预览画布：以文本形式展示排版效果，缺字标红。
 */
@Composable
fun HandwritingPreviewCanvas(
    textContent: String,
    isVertical: Boolean,
    missingChars: Set<Char>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val displayText = if (isVertical) {
            textContent.toList().joinToString("\n")
        } else {
            textContent
        }

        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = if (isVertical) 18.sp else 16.sp,
                lineHeight = if (isVertical) 28.sp else 24.sp
            ),
            textAlign = if (isVertical) TextAlign.Center else TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (missingChars.isNotEmpty()) {
            Text(
                text = "缺字：${missingChars.joinToString("")}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCC0000),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
