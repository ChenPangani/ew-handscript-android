package com.ew.handscript.ui.screens.realm

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ew.handscript.ui.theme.HandCraftFontTheme

/**
 * 境界系统页（P0-4）
 *
 * 功能：
 * 1. 境界徽章（当前境界+层数展示）
 * 2. 经验条（当前修为/升级所需）
 * 3. 灵石余额（修仙货币）
 * 4. 突破动画：暗化→图腾→光柱→境界更新
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealmScreen_Phase3(
    navController: NavHostController,
    realmState: RealmState = defaultRealmState()
) {
    var showBreakthrough by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("修仙境界", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // 境界徽章
            RealmBadge(realm = realmState.currentRealm, level = realmState.level)

            Spacer(Modifier.height(20.dp))

            // 灵石余额卡片
            SpiritStoneCard(balance = realmState.spiritStones)

            Spacer(Modifier.height(20.dp))

            // 经验条
            ExpProgressBar(
                current = realmState.currentExp,
                max = realmState.maxExp,
                label = "修为进度"
            )

            Spacer(Modifier.height(20.dp))

            // 五行属性
            FiveElementsPanel(elements = realmState.elements)

            Spacer(Modifier.height(24.dp))

            // 突破按钮（Mock）
            BreakthroughButton(
                canBreakthrough = realmState.currentExp >= realmState.maxExp,
                onClick = { showBreakthrough = true }
            )

            Spacer(Modifier.height(16.dp))

            // 境界历程
            RealmHistoryList(history = realmState.history)

            Spacer(Modifier.height(24.dp))
        }
    }

    // 突破动画遮罩
    if (showBreakthrough) {
        BreakthroughAnimation(
            newRealm = realmState.currentRealm.next(),
            onComplete = { showBreakthrough = false }
        )
    }
}

/** 境界数据 */
data class RealmState(
    val currentRealm: CultivationRealm,
    val level: Int,
    val currentExp: Int,
    val maxExp: Int,
    val spiritStones: Int,
    val elements: FiveElements,
    val history: List<RealmHistoryItem>
)

/** 修仙境界枚举 */
enum class CultivationRealm(val displayName: String, val color: Color, val element: String) {
    LIAN_QI("炼气期", Color(0xFF8BC34A), "木"),
    ZHU_JI("筑基期", Color(0xFF795548), "土"),
    JIN_DAN("金丹期", Color(0xFFFFC107), "金"),
    YUAN_YING("元婴期", Color(0xFF2196F3), "水"),
    HUA_SHEN("化神期", Color(0xFFF44336), "火"),
    DU_JIE("渡劫期", Color(0xFF9C27B0), "雷"),
    DA_CHENG("大乘期", Color(0xFF1A1A2E), "道");

    fun next(): CultivationRealm = when (this) {
        LIAN_QI -> ZHU_JI
        ZHU_JI -> JIN_DAN
        JIN_DAN -> YUAN_YING
        YUAN_YING -> HUA_SHEN
        HUA_SHEN -> DU_JIE
        DU_JIE -> DA_CHENG
        DA_CHENG -> DA_CHENG
    }
}

/** 五行属性 */
data class FiveElements(
    val metal: Int = 0,
    val wood: Int = 0,
    val water: Int = 0,
    val fire: Int = 0,
    val earth: Int = 0
)

/** 境界历程 */
data class RealmHistoryItem(val realm: String, val date: String, val isBreakthrough: Boolean)

/** 境界徽章 */
@Composable
private fun RealmBadge(realm: CultivationRealm, level: Int) {
    val realmColor = realm.color

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = realmColor.copy(alpha = 0.12f)),
        border = BorderStroke(2.dp, realmColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 境界图标
            Surface(
                shape = CircleShape,
                color = realmColor.copy(alpha = 0.2f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = realm.displayName,
                        tint = realmColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(
                    realm.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = realmColor
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = realmColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        "第 $level 层",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = realmColor
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "五行：${realm.element}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 灵石余额卡片 */
@Composable
private fun SpiritStoneCard(balance: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFFC107).copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Diamond,
                        contentDescription = "灵石",
                        tint = Color(0xFFFFA000),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("灵石余额", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$balance",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFA000)
                )
            }
            Button(
                onClick = { /* TODO: 充值/获取灵石 */ },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("获取", style = MaterialTheme.typography.labelMedium, color = Color(0xFF3E2723))
            }
        }
    }
}

/** 经验条 */
@Composable
private fun ExpProgressBar(current: Int, max: Int, label: String) {
    val progress = (current.toFloat() / max).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("$current / $max", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        )
    }
}



/** 突破按钮 */
@Composable
private fun BreakthroughButton(canBreakthrough: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = canBreakthrough,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (canBreakthrough) Color(0xFF9C27B0) else Color(0xFFCCCCCC),
            disabledContainerColor = Color(0xFFCCCCCC)
        )
    ) {
        Icon(Icons.Filled.Upgrade, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (canBreakthrough) "突破境界" else "修为不足",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

/** 境界历程列表 */
@Composable
private fun RealmHistoryList(history: List<RealmHistoryItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("境界历程", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        history.forEach { item ->
            HistoryRow(item = item)
        }
    }
}

/** 历程单项 */
@Composable
private fun HistoryRow(item: RealmHistoryItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (item.isBreakthrough) Icons.Filled.Star else Icons.Filled.Circle,
            contentDescription = null,
            tint = if (item.isBreakthrough) Color(0xFFFFC107) else Color(0xFFCCCCCC),
            modifier = Modifier.size(if (item.isBreakthrough) 20.dp else 10.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(item.realm, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(item.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// BreakthroughAnimation已拆分到 BreakthroughAnimation.kt

/** 默认Mock数据 */
private fun defaultRealmState(): RealmState = RealmState(
    currentRealm = CultivationRealm.LIAN_QI,
    level = 3,
    currentExp = 1280,
    maxExp = 2000,
    spiritStones = 365,
    elements = FiveElements(metal = 5, wood = 12, water = 8, fire = 3, earth = 7),
    history = listOf(
        RealmHistoryItem("炼气期·一层", "2026-05-20", true),
        RealmHistoryItem("炼气期·二层", "2026-05-25", true),
        RealmHistoryItem("炼气期·三层", "2026-06-01", false)
    )
)

/** 预览 */
@Preview(showBackground = true)
@Composable
private fun RealmScreenPreview() {
    HandCraftFontTheme {
        Surface { RealmScreen_Phase3(rememberNavController()) }
    }
}
