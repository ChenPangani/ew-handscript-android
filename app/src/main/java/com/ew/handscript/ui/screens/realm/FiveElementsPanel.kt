package com.ew.handscript.ui.screens.realm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 五行属性面板
 */
@Composable
fun FiveElementsPanel(elements: FiveElements) {
    val items = listOf(
        ("金" to elements.metal) to Color(0xFFFFC107),
        ("木" to elements.wood) to Color(0xFF4CAF50),
        ("水" to elements.water) to Color(0xFF2196F3),
        ("火" to elements.fire) to Color(0xFFF44336),
        ("土" to elements.earth) to Color(0xFF795548)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("五行属性", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEach { (pair, color) ->
                    val (name, value) = pair
                    ElementItem(name = name, value = value, color = color)
                }
            }
        }
    }
}

/** 单五行属性项 */
@Composable
private fun ElementItem(name: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(name, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("$value", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
