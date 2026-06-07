package com.ew.handscript.ui.screens.realm

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 突破动画组件
 *
 * 四阶段：暗化(0.8s) → 图腾(1s) → 光柱(1.2s) → 消散(0.5s)
 */
@Composable
fun BreakthroughAnimation(newRealm: CultivationRealm, onComplete: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    val alpha by animateFloatAsState(
        targetValue = when (phase) {
            0 -> 0.9f; 1 -> 0.7f; 2 -> 0.5f; else -> 0f
        },
        animationSpec = tween(800), label = "btAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (phase >= 2) 1.5f else 1f,
        animationSpec = tween(600), label = "btScale"
    )

    // 阶段自动推进
    LaunchedEffect(Unit) {
        delay(800); phase = 1
        delay(1000); phase = 2
        delay(1200); phase = 3
        delay(500); onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        when (phase) {
            1 -> TotemPhase(newRealm)
            2 -> LightPillarPhase(newRealm, scale)
        }
    }
}

/** 图腾阶段：五行图标 */
@Composable
private fun TotemPhase(realm: CultivationRealm) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = "图腾",
            tint = realm.color,
            modifier = Modifier.size(80.dp)
        )
        Text(
            "${realm.element}之力汇聚",
            color = Color.White,
            modifier = Modifier.padding(top = 120.dp),
            fontWeight = FontWeight.Bold
        )
    }
}

/** 光柱阶段：境界名称放大+突破成功 */
@Composable
private fun LightPillarPhase(realm: CultivationRealm, scale: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            realm.displayName,
            color = realm.color,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        )
        Spacer(Modifier.height(8.dp))
        Text("突破成功", color = Color(0xFFFFD700), fontSize = 18.sp)
    }
}
