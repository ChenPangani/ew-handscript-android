package com.ew.handscript.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ew.handscript.ui.screens.home.HomeScreen
import com.ew.handscript.ui.screens.scan.ScanScreen
import com.ew.handscript.ui.screens.verify.VerifyScreen
import com.ew.handscript.ui.screens.library.LibraryScreen
import com.ew.handscript.ui.screens.output.OutputScreen
import com.ew.handscript.ui.screens.settings.SettingsScreen
import com.ew.handscript.ui.theme.HandCraftFontTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主Activity - 应用入口
 *
 * 使用Jetpack Compose + Navigation实现单Activity架构
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
                    HandCraftFontApp(navController = rememberNavController())
                }
            }
        }
    }
}

/**
 * 导航路由定义
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Scan : Screen("scan")
    data object Verify : Screen("verify/{documentId}") {
        fun createRoute(documentId: Long) = "verify/$documentId"
    }
    data object Library : Screen("library")
    data object Output : Screen("output")
    data object Export : Screen("export/{documentId}") {
        fun createRoute(documentId: Long) = "export/$documentId"
    }
    data object Settings : Screen("settings")
}

/**
 * 应用导航图
 */
@Composable
fun HandCraftFontApp(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.Scan.route) {
            ScanScreen(navController = navController)
        }
        composable(Screen.Verify.route) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId")?.toLongOrNull() ?: 0L
            VerifyScreen(navController = navController, documentId = documentId)
        }
        composable(Screen.Library.route) {
            LibraryScreen(navController = navController)
        }
        composable(Screen.Output.route) {
            OutputScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
    }
}
