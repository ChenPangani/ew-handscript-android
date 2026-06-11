package com.ew.handscript.ui.screens.proofread

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ew.handscript.ui.theme.HandCraftFontTheme
import kotlinx.coroutines.delay

/**
 * 校对页（P0-2）- 瀑布流字格列表面
 *
 * 功能：
 * 1. LazyVerticalGrid 3列瀑布流展示所有待校对字形
 * 2. 四种状态色：绿(已确认)/黄(待定)/红(问题)/灰(未处理)
 * 3. 点击切换状态，长按进入S2态，双击进入S3态
 * 4. 上滑消除/下拉待定手势
 * 5. 底部统计栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofreadScreen_Phase3(
    navController: NavHostController,
    glyphs: List<GlyphItem> = mockGlyphItems()
) {
    var glyphList by remember { mutableStateOf(glyphs) }
    val gridState = rememberLazyGridState()

    // 统计数据
    val stats = remember(glyphList) { computeStats(glyphList) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校对", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { /* TODO: 批量操作 */ }) {
                        Text("全选", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            StatsBottomBar(stats = stats)
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(glyphList.size, key = { glyphList[it].id }) { index ->
                val item = glyphList[index]
                GlyphCard(
                    item = item,
                    onTap = {
                        // 单击切换状态：循环 UNVERIFIED→CONFIRMED→REJECTED→PENDING→UNVERIFIED
                        glyphList = glyphList.toMutableList().apply {
                            val next = when (item.status) {
                                VerifyStatus.UNVERIFIED -> VerifyStatus.CONFIRMED
                                VerifyStatus.CONFIRMED -> VerifyStatus.REJECTED
                                VerifyStatus.REJECTED -> VerifyStatus.PENDING
                                VerifyStatus.PENDING -> VerifyStatus.UNVERIFIED
                            }
                            this[index] = item.copy(status = next)
                        }
                    },
                    onLongPress = {
                        // 长按S2态：打开双层卡片
                        /* TODO: 导航到双层卡片 */
                    },
                    onDoubleTap = {
                        // 双击S3态：进入绘制重录
                        /* TODO: 跳转书写界面 */
                    },
                    onSwipeUp = {
                        // 上滑消除：标记为问题
                        glyphList = glyphList.toMutableList().apply {
                            this[index] = item.copy(status = VerifyStatus.REJECTED)
                        }
                    },
                    onSwipeDown = {
                        // 下拉待定
                        glyphList = glyphList.toMutableList().apply {
                            this[index] = item.copy(status = VerifyStatus.PENDING)
                        }
                    }
                )
            }
        }
    }
}

/** 字形数据 */
data class GlyphItem(
    val id: Int,
    val glyphChar: String,
    val status: VerifyStatus = VerifyStatus.UNVERIFIED
)

/** 校对状态 */
enum class VerifyStatus(val displayName: String, val color: Color) {
    UNVERIFIED("未处理", Color(0xFF9E9E9E)),
    CONFIRMED("已确认", Color(0xFF4CAF50)),
    REJECTED("问题", Color(0xFFF44336)),
    PENDING("待定", Color(0xFFFFC107))
}

/** 状态颜色映射（用于边框和标签） */
private fun statusColor(status: VerifyStatus) = status.color

/** 单字卡片 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlyphCard(
    item: GlyphItem,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit
) {
    val borderColor = statusColor(item.status)
    val bgColor = borderColor.copy(alpha = 0.08f)

    val offsetY = remember { Animatable(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .offset(y = offsetY.value.dp)
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = { onDoubleTap() }
                )
            }
            .pointerInput(item.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val (dx, dy) = dragAmount
                    when {
                        dy < -30f -> onSwipeUp()
                        dy > 30f -> onSwipeDown()
                    }
                }
            },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 字符主体
            Text(
                text = item.glyphChar,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A2E),
                textAlign = TextAlign.Center
            )
            // 状态标签（右下角小圆点）
            StatusDot(
                status = item.status,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

/** 状态圆点指示器 */
@Composable
private fun StatusDot(status: VerifyStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(statusColor(status))
    )
}

/** 统计信息 */
private data class Stats(val total: Int, val confirmed: Int, val rejected: Int, val pending: Int)

/** 计算统计 */
private fun computeStats(list: List<GlyphItem>): Stats {
    return Stats(
        total = list.size,
        confirmed = list.count { it.status == VerifyStatus.CONFIRMED },
        rejected = list.count { it.status == VerifyStatus.REJECTED },
        pending = list.count { it.status == VerifyStatus.PENDING }
    )
}

/** 底部统计栏 */
@Composable
private fun StatsBottomBar(stats: Stats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("总数", stats.total, Color(0xFF9E9E9E))
            StatItem("已确认", stats.confirmed, Color(0xFF4CAF50))
            StatItem("问题", stats.rejected, Color(0xFFF44336))
            StatItem("待定", stats.pending, Color(0xFFFFC107))
        }
    }
}

/** 统计项 */
@Composable
private fun StatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Mock数据 */
private fun mockGlyphItems(): List<GlyphItem> {
    val chars = "春眠不觉晓处处闻啼鸟夜来风雨声花落知多少"
    return chars.mapIndexed { i, c ->
        GlyphItem(
            id = i,
            glyphChar = c.toString(),
            status = when (i % 4) {
                0 -> VerifyStatus.CONFIRMED
                1 -> VerifyStatus.UNVERIFIED
                2 -> VerifyStatus.PENDING
                else -> VerifyStatus.REJECTED
            }
        )
    }
}

/** 预览 */
@Preview(showBackground = true)
@Composable
private fun ProofreadScreenPreview() {
    HandCraftFontTheme {
        Surface { ProofreadScreen_Phase3(rememberNavController()) }
    }
}
