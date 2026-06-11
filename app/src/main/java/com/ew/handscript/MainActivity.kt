package com.ew.handscript.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ew.handscript.ui.navigation.AppNavigation
import com.ew.handscript.ui.theme.HandCraftFontTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主Activity - 应用入口
 *
 * Phase 4：统一底部Tab导航
 * 5个Tab：扫描 / 字库 / 修炼 / 输出 / 设置
 * 子页面：校对（从字库或扫描跳转）
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            HandCraftFontTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
