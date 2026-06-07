package com.ew.handscript.ui.screens.library

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ew.handscript.model.GlyphModel
import com.ew.handscript.model.LibraryLevel
import com.ew.handscript.ui.screens.home.HomeUiState

/**
 * 字库屏幕 - 多字形资产管理与版本控制
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavHostController,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的字库") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.startFontGeneration() },
                icon = { Icon(Icons.Filled.FontDownload, null) },
                text = { Text("生成字体") },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
            .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                LibraryStatsCard(
                    verifiedCount = uiState.verifiedCount,
                    libraryLevel = uiState.libraryLevel,
                    progress = uiState.progress
                )
            }

            item {
                Text(
                    text = "字形收藏 (${uiState.verifiedCount}字)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 按字符分组显示字形
            items(uiState.groupedGlyphs.entries.toList()) { (char, glyphs) ->
                GlyphGroupCard(
                    character = char,
                    glyphs = glyphs,
                    onTagClick = { viewModel.addTag(it, "连笔") }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp)) // FAB空间
            }
        }
    }

    // 字体生成对话框
    if (uiState.showGenerateDialog) {
        FontGenerationDialog(
            glyphCount = uiState.verifiedCount,
            onConfirm = { fontName ->
                viewModel.submitFontGeneration(fontName)
            },
            onDismiss = { viewModel.dismissGenerateDialog() }
        )
    }

    // 生成中对话框
    if (uiState.isGenerating) {
        GeneratingDialog(
            progress = uiState.generationProgress,
            stage = uiState.generationStage
        )
    }
}

@Composable
private fun LibraryStatsCard(
    verifiedCount: Int,
    libraryLevel: LibraryLevel,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = libraryLevel.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = libraryLevel.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$verifiedCount",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun GlyphGroupCard(
    character: String,
    glyphs: List<GlyphModel>,
    onTagClick: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 汉字展示
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = character,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${glyphs.size} 种写法",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        glyphs.flatMap { it.tags }.distinct().forEach { tag ->
                            AssistChip(
                                onClick = { },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Unicode
                Text(
                    text = glyphs.firstOrNull()?.unicode ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 字形图片行
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                glyphs.forEach { glyph ->
                    GlyphThumbnail(
                        glyph = glyph,
                        onTagClick = onTagClick
                    )
                }
            }

            // 时间线：按时间戳排序显示
            if (glyphs.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "时间线: ${glyphs.joinToString(" → ") { "v${it.glyphVersion}" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GlyphThumbnail(
    glyph: GlyphModel,
    onTagClick: (Long) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(64.dp)
        ) {
            AsyncImage(
                model = glyph.imagePath,
                contentDescription = "字形 ${glyph.character} v${glyph.glyphVersion}",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "v${String.format("%02d", glyph.glyphVersion)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FontGenerationDialog(
    glyphCount: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var fontName by remember { mutableStateOf("我的字体") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.FontDownload, null) },
        title = { Text("生成专属字体") },
        text = {
            Column {
                Text(
                    "基于你收集的 $glyphCount 个汉字生成TrueType字体文件",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = fontName,
                    onValueChange = { fontName = it },
                    label = { Text("字体名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "生成过程将在云端进行，完成后可下载 .ttf 文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(fontName) }) {
                Text("开始生成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun GeneratingDialog(
    progress: Float,
    stage: String
) {
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Filled.CloudSync, null) },
        title = { Text("字体生成中") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stage, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "离开此页面不影响生成进度，完成后将通知您",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { },
        dismissButton = { }
    )
}

// UI State
data class LibraryUiState(
    val verifiedCount: Int = 0,
    val libraryLevel: LibraryLevel = LibraryLevel.STARTER,
    val progress: Float = 0f,
    val groupedGlyphs: Map<String, List<GlyphModel>> = emptyMap(),
    val showGenerateDialog: Boolean = false,
    val isGenerating: Boolean = false,
    val generationProgress: Float = 0f,
    val generationStage: String = ""
)
