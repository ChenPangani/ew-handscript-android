package com.ew.handscript.ui.screens.proofread

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ew.handscript.ui.theme.HandCraftFontTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 双层卡片交互组件（P0-3）
 *
 * 状态机：
 *   S0_IDLE → 单击 → S1_SELECTED → 长按0.5s → S2_EXPANDED → 双击 → S3_DRAWING
 *             ↓                      ↓                        ↓
 *           单击取消               单击收缩                  单击退出
 *
 * S2态特性：Canvas圆形mask底稿透出，可拖动对齐
 * S3态特性：进入绘制模式重录字形
 */
enum class CardState {
    S0_IDLE,       // 默认：显示单字
    S1_SELECTED,   // 选中：边框高亮
    S2_EXPANDED,   // 展开：圆形mask底稿透出+拖动
    S3_DRAWING     // 绘制：进入重录模式
}

/**
 * 双层卡片组件
 *
 * @param glyphChar 当前字形字符
 * @param bottomImage 底稿Bitmap（原始扫描图，S2态透出）
 * @param onRecut Mock重切回调（100ms延迟后触发）
 * @param onStateChange 状态变化回调
 */
@Composable
fun DoubleLayerCard_Phase3(
    glyphChar: String = "春",
    bottomImage: ImageBitmap? = null,
    onRecut: () -> Unit = {},
    onStateChange: (CardState) -> Unit = {}
) {
    var state by remember { mutableStateOf(CardState.S0_IDLE) }
    var expandedScale by remember { mutableFloatStateOf(1f) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isRecutting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 动画值
    val scale by animateFloatAsState(
        targetValue = when (state) {
            CardState.S0_IDLE -> 1f
            CardState.S1_SELECTED -> 1.05f
            CardState.S2_EXPANDED -> expandedScale.coerceIn(1.5f, 2.5f)
            CardState.S3_DRAWING -> 1f
        },
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (state == CardState.S2_EXPANDED) 1f else 0f,
        label = "alpha"
    )

    // 状态转换
    val transitionTo: (CardState) -> Unit = { newState ->
        state = newState
        onStateChange(newState)
    }

    // 长按触发S2（0.5s延迟）
    val longPressTimer = remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(240.dp)
            .height(280.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        when (state) {
                            CardState.S0_IDLE -> transitionTo(CardState.S1_SELECTED)
                            CardState.S1_SELECTED -> transitionTo(CardState.S0_IDLE)
                            CardState.S2_EXPANDED -> transitionTo(CardState.S1_SELECTED)
                            CardState.S3_DRAWING -> transitionTo(CardState.S2_EXPANDED)
                        }
                    },
                    onDoubleTap = {
                        if (state == CardState.S2_EXPANDED) {
                            transitionTo(CardState.S3_DRAWING)
                        }
                    },
                    onLongPress = {
                        if (state == CardState.S1_SELECTED) {
                            transitionTo(CardState.S2_EXPANDED)
                        }
                    }
                )
            }
            .pointerInput(state) {
                if (state == CardState.S2_EXPANDED) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        dragOffset = Offset(
                            dragOffset.x + dragAmount.x,
                            dragOffset.y + dragAmount.y
                        )
                    }
                }
            },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = when (state) {
                CardState.S1_SELECTED -> 3.dp
                CardState.S2_EXPANDED -> 2.dp
                else -> 1.dp
            },
            color = when (state) {
                CardState.S0_IDLE -> MaterialTheme.colorScheme.outlineVariant
                CardState.S1_SELECTED -> MaterialTheme.colorScheme.primary
                CardState.S2_EXPANDED -> Color(0xFFFFA000)
                CardState.S3_DRAWING -> Color(0xFF4CAF50)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (state == CardState.S2_EXPANDED)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                CardState.S0_IDLE, CardState.S1_SELECTED -> IdleLayer(glyphChar, state)
                CardState.S2_EXPANDED -> ExpandedLayer(
                    glyphChar = glyphChar,
                    bottomImage = bottomImage,
                    dragOffset = dragOffset,
                    alpha = alpha,
                    onRecut = {
                        scope.launch {
                            isRecutting = true
                            delay(100) // Mock重切100ms延迟
                            isRecutting = false
                            onRecut()
                        }
                    },
                    isRecutting = isRecutting
                )
                CardState.S3_DRAWING -> DrawingLayer(glyphChar)
            }
        }
    }
}

/** S0/S1态：显示单字 */
@Composable
private fun IdleLayer(glyphChar: String, state: CardState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = glyphChar,
            fontSize = 72.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1A1A2E)
        )
        if (state == CardState.S1_SELECTED) {
            Spacer(Modifier.height(8.dp))
            Text(
                "长按展开",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** S2态：圆形mask底稿透出+拖动对齐 */
@Composable
private fun ExpandedLayer(
    glyphChar: String,
    bottomImage: ImageBitmap?,
    dragOffset: Offset,
    alpha: Float,
    onRecut: () -> Unit,
    isRecutting: Boolean
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 底稿图层（通过Canvas圆形Mask透出）
        if (bottomImage != null && alpha > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension * 0.35f
                val circlePath = Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            center.x - radius + dragOffset.x,
                            center.y - radius + dragOffset.y,
                            center.x + radius + dragOffset.x,
                            center.y + radius + dragOffset.y
                        )
                    )
                }
                clipPath(circlePath, clipOp = ClipOp.Intersect) {
                    drawImage(
                        image = bottomImage,
                        dstOffset = IntOffset(
                            (dragOffset.x).toInt(),
                            (dragOffset.y).toInt()
                        ),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        alpha = alpha
                    )
                }
            }
        } else {
            // 无底稿时的占位提示
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(Color(0xFFF5E6D3).copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = "底稿",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // 当前字形（叠加在底稿上）
        Text(
            text = glyphChar,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A2E).copy(alpha = 0.9f),
            modifier = Modifier.offset(dragOffset.x.dp / 4, dragOffset.y.dp / 4)
        )

        // 操作按钮区
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mock重切按钮
            Button(
                onClick = onRecut,
                enabled = !isRecutting,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                if (isRecutting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.Crop, null, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(if (isRecutting) "重切中" else "重切", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "双击进入绘制 · 单击收缩",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        // 拖动指示
        Icon(
            Icons.Outlined.DragIndicator,
            contentDescription = "拖动对齐",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )
    }
}

/** S3态：绘制模式 */
@Composable
private fun DrawingLayer(glyphChar: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 半透明参考字
        Text(
            text = glyphChar,
            fontSize = 64.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1A1A2E).copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(16.dp))
        // 绘制提示
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFE8F5E9)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Brush, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("书写模式", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "单击返回展开态",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/** 预览 */
@Preview(showBackground = true)
@Composable
private fun DoubleLayerCardPreview() {
    HandCraftFontTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DoubleLayerCard_Phase3()
            }
        }
    }
}
