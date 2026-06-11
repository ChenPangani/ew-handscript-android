package com.ew.handscript.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ew.handscript.model.GlyphItem
import kotlinx.coroutines.delay

/**
 * 双层卡片组件（Phase 3）
 * 未触碰：显示预切字（上层）
 * 触碰后：方圆透出底稿（下层透出）
 */
@Composable
fun DoubleLayerCard(
    glyphItem: GlyphItem,
    baseImage: ImageBitmap?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var pressPosition by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current

    Card(
        modifier = modifier
            .size(104.dp, 120.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { offset ->
                        pressPosition = offset
                        isPressed = true
                        onLongPress()
                    },
                    onPress = {
                        // 按下时触发，释放时恢复
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPressed) Color(0xFFF5F5DC) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 8.dp else 2.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 下层：底稿（长按透出）
            if (isPressed && baseImage != null) {
                RevealLayer(
                    baseImage = baseImage,
                    pressPosition = pressPosition,
                    containerSize = IntSize(104.dp.value.toInt(), 120.dp.value.toInt()),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 上层：预切字
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = glyphItem.character,
                    fontSize = 48.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = glyphItem.wuXingTag,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 方圆透出层：以按压点为中心，圆形区域透出底稿
 */
@Composable
private fun RevealLayer(
    baseImage: ImageBitmap,
    pressPosition: Offset,
    containerSize: IntSize,
    modifier: Modifier = Modifier
) {
    Image(
        bitmap = baseImage,
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        alpha = 0.85f
    )
}