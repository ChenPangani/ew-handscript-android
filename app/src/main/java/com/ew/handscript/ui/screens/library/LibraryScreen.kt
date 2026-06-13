package com.ew.handscript.ui.screens.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.ew.handscript.data.local.GlyphEntity

/**
 * 字库列表页
 * 展示用户已收录的所有字形
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavHostController,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val glyphs by viewModel.glyphs.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的字库", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
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
        ) {
            if (glyphs.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.FontDownload,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "还没有收录任何汉字",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "导入手写稿开始收集吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // 字库网格
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(glyphs) { glyph ->
                        GlyphCard(glyph = glyph)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlyphCard(glyph: GlyphEntity) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 字形图像
            val bitmap = remember {
                glyph.imagePath?.let { path ->
                    BitmapFactory.decodeFile(path)
                }
            }
            
            if (bitmap != null) {
                Image(
                    painter = rememberAsyncImagePainter(bitmap),
                    contentDescription = glyph.character,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = glyph.character.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 字符和五行标签
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = glyph.character.take(1),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (glyph.ocrText != null) {
                    val wuxing = getWuXingLabel(glyph.ocrText)
                    Text(
                        text = wuxing,
                        style = MaterialTheme.typography.bodySmall,
                        color = getWuXingColor(wuxing)
                    )
                }
            }
        }
    }
}

/**
 * 五行标签映射（模拟）
 */
private fun getWuXingLabel(text: String): String {
    // 简单的五行映射示例
    val charCode = text.firstOrNull()?.code ?: 0
    val wuxingList = listOf("木", "火", "土", "金", "水")
    return wuxingList[charCode % 5]
}

@Composable
private fun getWuXingColor(wuxing: String): Color {
    return when (wuxing) {
        "木" -> Color(0xFF22C55E)
        "火" -> Color(0xFFEF4444)
        "土" -> Color(0xFFA16207)
        "金" -> Color(0xFF6B7280)
        "水" -> Color(0xFF3B82F6)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}