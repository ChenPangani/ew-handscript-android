package com.ew.handscript.ui.screens.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ew.handscript.ui.theme.HandCraftFontTheme
import kotlin.math.roundToInt

/**
 * 设置页屏幕（P0-6 极简版）
 *
 * 包含：用户信息卡片、音效/震动开关、字体美化度滑块、
 * 清除缓存/关于/用户协议/隐私政策入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrateEnabled by remember { mutableStateOf(true) }
    var beautyPercent by remember { mutableFloatStateOf(0.75f) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("设置", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 用户信息卡片
            UserProfileCard(nickname = "修仙者", realm = "炼气期 · 三层", glyphCount = 128)

            // 通用设置
            SectionTitle("通用设置")
            SettingsCard {
                SwitchItem("音效", "操作反馈音", Icons.Outlined.VolumeUp, soundEnabled) {
                    soundEnabled = it
                }
                SettingDivider()
                SwitchItem("震动", "触控震动反馈", Icons.Outlined.Vibration, vibrateEnabled) {
                    vibrateEnabled = it
                }
                SettingDivider()
                SliderItem("字体美化度", "控制生成字体平滑程度", Icons.Outlined.AutoFixHigh, beautyPercent) {
                    beautyPercent = it
                }
            }

            // 系统功能
            SectionTitle("系统")
            SettingsCard {
                ClickableItem("清除缓存", "释放本地存储空间", Icons.Outlined.CleaningServices) {
                    showClearCacheDialog = true
                }
                SettingDivider()
                ClickableItem("关于", "版本 1.0.0 · 蚯蚓手书修仙传", Icons.Outlined.Info) {
                    // 显示关于对话框或跳转到关于页面
                }
                SettingDivider()
                ClickableItem("用户协议", "使用条款与服务协议", Icons.Outlined.Description) {
                    // 跳转到用户协议页面或WebView
                }
                SettingDivider()
                ClickableItem("隐私政策", "个人信息保护政策", Icons.Outlined.Security) {
                    // 跳转到隐私政策页面或WebView
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "蚯蚓手书修仙传 v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showClearCacheDialog) {
        ClearCacheDialog(
            onConfirm = { showClearCacheDialog = false },
            onDismiss = { showClearCacheDialog = false }
        )
    }
}

/** 用户信息卡片：头像 + 昵称 + 境界 + 字数 */
@Composable
private fun UserProfileCard(nickname: String, realm: String, glyphCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person, contentDescription = "头像",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        realm,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$glyphCount", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                )
                Text("已收字", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
            }
        }
    }
}

/** 分组标题 */
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

/** 设置卡片容器 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(vertical = 8.dp), content = content)
    }
}

/** 分割线 */
@Composable
private fun SettingDivider() {
    Divider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/** 开关设置项 */
@Composable
private fun SwitchItem(
    title: String, subtitle: String, icon: ImageVector,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 滑块设置项（50%~100%，带浮动百分比标签） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderItem(
    title: String, subtitle: String, icon: ImageVector,
    value: Float, onValueChange: (Float) -> Unit
) {
    val displayPercent = (0.5f + value * 0.5f)
    val percentText = "${(displayPercent * 100).roundToInt()}%"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    percentText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Slider(value = value, onValueChange = onValueChange, modifier = Modifier.padding(horizontal = 8.dp), valueRange = 0f..1f, steps = 9)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("50%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text("100%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

/** 可点击设置项 */
@Composable
private fun ClickableItem(
    title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

/** 清除缓存确认对话框 */
@Composable
private fun ClearCacheDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除缓存") },
        text = { Text("确定要清除所有本地缓存数据吗？此操作不会删除已收录的字形。") },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("清除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 预览 */
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    HandCraftFontTheme {
        Surface { SettingsScreen(rememberNavController()) }
    }
}
