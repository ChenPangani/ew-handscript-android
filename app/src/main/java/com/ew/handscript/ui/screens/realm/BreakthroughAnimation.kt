package com.ew.handscript.ui.screens.realm

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 境界突破动画
 * 炼气→筑基→金丹等突破时的全屏特效。
 */
@Composable
fun BreakthroughAnimation(
    fromRealm: String,
    toRealm: String,
    onComplete: () -> Unit
) {
    var stage by remember { mutableStateOf(0) }

    // 6 步时序动画
    LaunchedEffect(Unit) {
        delay(300.milliseconds)  // 蓄力
        stage = 1
        delay(500.milliseconds)  // 灵气汇聚
        stage = 2
        delay(800.milliseconds)  // 瓶颈震颤
        stage = 3
        delay(600.milliseconds)  // 突破闪光
        stage = 4
        delay(1.seconds)         // 境界稳定
        stage = 5
        delay(500.milliseconds)  // 收束
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        when (stage) {
            0 -> GatheringEffect()
            1, 2 -> TremorEffect()
            3 -> FlashBurstEffect()
            4 -> RealmNameReveal(toRealm)
            else -> Spacer(modifier = Modifier)
        }
    }
}

@Composable
private fun GatheringEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "gather")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .background(Color(0xFFFFD700).copy(alpha = 0.3f), CircleShape)
    )
}

@Composable
private fun TremorEffect() {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        repeat(10) {
            offsetX.animateTo(5f, tween(50))
            offsetX.animateTo(-5f, tween(50))
        }
        offsetX.animateTo(0f, tween(100))
    }

    Box(
        modifier = Modifier
            .size(200.dp)
            .offset(offsetX.value.dp, 0.dp)
            .background(Color(0xFF8B0000).copy(alpha = 0.5f), CircleShape)
    )
}

@Composable
private fun FlashBurstEffect() {
    val alpha by rememberInfiniteTransition(label = "flash").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(Color.White)
    )
}

@Composable
private fun RealmNameReveal(realmName: String) {
    val scale by rememberInfiniteTransition(label = "realm").animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "realmScale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "突破成功",
            fontSize = 24.sp,
            color = Color(0xFFFFD700),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = realmName,
            fontSize = 48.sp,
            color = Color(0xFFFFD700),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.scale(scale)
        )
    }
}