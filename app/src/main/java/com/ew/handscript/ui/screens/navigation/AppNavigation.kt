package com.ew.handscript.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
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
import androidx.navigation.compose.*
import com.ew.handscript.ui.screens.home.HomeScreen
import com.ew.handscript.ui.screens.scan.ScanScreen
import com.ew.handscript.ui.screens.proofread.ProofreadScreen_Phase3
import com.ew.handscript.ui.screens.realm.RealmScreen_Phase3
import com.ew.handscript.ui.screens.output.OutputScreen
import com.ew.handscript.ui.screens.settings.SettingsScreen
import com.ew.handscript.ui.theme.HandCraftFontTheme

/**
 * 底部Tab定义（5个主Tab）
 */
sealed class BottomTab(
    val route: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
) {
    data object Scan : BottomTab("tab_scan", "扫描",
        Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt)
    data object Library : BottomTab("tab_library", "字库",
        Icons.Filled.CollectionsBookmark, Icons.Outlined.CollectionsBookmark)
    data object Realm : BottomTab("tab_realm", "修炼",
        Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents)
    data object Output : BottomTab("tab_output", "输出",
        Icons.Filled.Image, Icons.Outlined.Image)
    data object Settings : BottomTab("tab_settings", "设置",
        Icons.Filled.Settings, Icons.Outlined.Settings)

    companion object {
        val all = listOf(Scan, Library, Realm, Output, Settings)
    }
}

/**
 * 子页面路由（不显示底部导航）
 */
sealed class SubRoute(val route: String) {
    data object Proofread : SubRoute("proofread")
}

/**
 * 主应用导航框架
 *
 * 包含底部Tab导航 + NavHost路由管理
 * 子页面自动隐藏底部导航栏
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 判断当前是否在Tab页（子页面不显示底部导航）
    val isTabRoute = BottomTab.all.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isTabRoute) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onTabClick = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * 底部导航栏 - Material3 NavigationBar
 * 选中指示器：图标颜色+标签加粗，带动画过渡
 */
@Composable
private fun BottomNavBar(
    currentRoute: String?,
    onTabClick: (BottomTab) -> Unit
) {
    NavigationBar(
        modifier = Modifier.height(68.dp),
        tonalElevation = 3.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        BottomTab.all.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = {
                    BadgedTabIcon(tab = tab, selected = selected)
                },
                label = {
                    Text(
                        tab.label,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

/**
 * 带徽标的Tab图标
 */
@Composable
private fun BadgedTabIcon(tab: BottomTab, selected: Boolean) {
    val icon = if (selected) tab.iconSelected else tab.iconUnselected
    // 修炼Tab显示灵石数量徽标（Mock）
    if (tab is BottomTab.Realm) {
        BadgedBox(badge = { Badge { Text("3", style = MaterialTheme.typography.labelSmall) } }) {
            Icon(icon, contentDescription = tab.label, modifier = Modifier.size(24.dp))
        }
    } else {
        Icon(icon, contentDescription = tab.label, modifier = Modifier.size(24.dp))
    }
}

/**
 * NavHost路由图
 *
 * 5个Tab页 + 子页面路由
 */
@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = BottomTab.Library.route, // 默认打开字库Tab
        modifier = modifier
    ) {
        // Tab: 扫描
        composable(BottomTab.Scan.route) {
            ScanScreen(navController = navController)
        }
        // Tab: 字库（首页作为主入口）
        composable(BottomTab.Library.route) {
            HomeScreen(navController = navController)
        }
        // Tab: 修炼
        composable(BottomTab.Realm.route) {
            RealmScreen_Phase3(navController = navController)
        }
        // Tab: 输出
        composable(BottomTab.Output.route) {
            OutputScreen(navController = navController)
        }
        // Tab: 设置
        composable(BottomTab.Settings.route) {
            SettingsScreen(navController = navController)
        }
        // 子页面：校对（从字库Tab或扫描页跳转）
        composable(SubRoute.Proofread.route) {
            ProofreadScreen_Phase3(navController = navController)
        }
    }
}

/** 预览 */
@Preview(showBackground = true)
@Composable
private fun BottomNavPreview() {
    HandCraftFontTheme {
        Surface {
            BottomNavBar(currentRoute = BottomTab.Library.route) {}
        }
    }
}
