package com.ew.handscript.ui.screens.verify

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ew.handscript.model.GlyphModel

/**
 * 校对屏幕 - 沉浸式人机校对
 *
 * 交互设计：
 * 1. 九宫格/卡片式浏览单字
 * 2. 上滑/点击绿勾确认
 * 3. 下滑修正文字
 * 4. 批量操作支持
 * 5. 修改应用到相同字形提示
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VerifyScreen(
    navController: NavHostController,
    documentId: Long,
    viewModel: VerifyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(documentId) {
        viewModel.loadGlyphs(documentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("字库校对")
                        Text(
                            "${uiState.currentIndex + 1} / ${uiState.totalCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 批量选择模式切换
                    IconButton(onClick = { viewModel.toggleBatchMode() }) {
                        Icon(
                            if (uiState.isBatchMode) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = "批量选择"
                        )
                    }
                    // 一键确认全部
                    TextButton(onClick = { viewModel.confirmAll() }) {
                        Text("全部确认")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            VerifyBottomBar(
                onConfirmCurrent = { viewModel.confirmCurrent() },
                onCorrectCurrent = { viewModel.showCorrectDialog() },
                onSkipCurrent = { viewModel.skipCurrent() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> LoadingView()
                uiState.glyphs.isEmpty() -> EmptyVerifyView()
                else -> GlyphVerifyPager(
                    glyphs = uiState.glyphs,
                    currentIndex = uiState.currentIndex,
                    isBatchMode = uiState.isBatchMode,
                    selectedIds = uiState.selectedIds,
                    onGlyphSelected = { viewModel.toggleSelection(it) },
                    onPageChanged = { viewModel.setCurrentIndex(it) }
                )
            }
        }
    }

    // 纠错对话框
    if (uiState.showCorrectDialog) {
        val currentGlyph = uiState.glyphs.getOrNull(uiState.currentIndex)
        CorrectGlyphDialog(
            currentText = currentGlyph?.character ?: "",
            ocrText = currentGlyph?.ocrText,
            onConfirm = { correctedText, applyToAll ->
                viewModel.correctCurrent(correctedText, applyToAll)
            },
            onDismiss = { viewModel.dismissCorrectDialog() }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlyphVerifyPager(
    glyphs: List<GlyphModel>,
    currentIndex: Int,
    isBatchMode: Boolean,
    selectedIds: Set<Long>,
    onGlyphSelected: (Long) -> Unit,
    onPageChanged: (Int) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { glyphs.size }
    )

    LaunchedEffect(currentIndex) {
        pagerState.animateScrollToPage(currentIndex)
    }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { page ->
        val glyph = glyphs[page]
        GlyphCard(
            glyph = glyph,
            isSelected = selectedIds.contains(glyph.id),
            isBatchMode = isBatchMode,
            onSelect = { onGlyphSelected(glyph.id) }
        )
    }
}

@Composable
private fun GlyphCard(
    glyph: GlyphModel,
    isSelected: Boolean,
    isBatchMode: Boolean,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 字形卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 8.dp else 2.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            ),
            onClick = { if (isBatchMode) onSelect() }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 字形图片
                AsyncImage(
                    model = glyph.imagePath,
                    contentDescription = "字形: ${glyph.character}",
                    modifier = Modifier
                        .fillMaxSize(0.8f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )

                // 选中标记
                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }

                // 原图缩略图（溯源）
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "原图",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // OCR识别结果
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "OCR识别结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = glyph.ocrText ?: "未识别",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (glyph.correctedText != null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                if (glyph.correctedText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "已修正为: ${glyph.correctedText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { glyph.confidence },
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = when {
                        glyph.confidence > 0.8f -> MaterialTheme.colorScheme.tertiary
                        glyph.confidence > 0.5f -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "置信度: ${(glyph.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CorrectGlyphDialog(
    currentText: String,
    ocrText: String?,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentText) }
    var applyToAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修正字形") },
        text = {
            Column {
                if (ocrText != null) {
                    Text(
                        text = "OCR识别: $ocrText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("正确文字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = applyToAll,
                        onCheckedChange = { applyToAll = it }
                    )
                    Text(
                        text = "应用到后续所有该字形",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text, applyToAll) }) {
                Text("确认")
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
private fun VerifyBottomBar(
    onConfirmCurrent: () -> Unit,
    onCorrectCurrent: () -> Unit,
    onSkipCurrent: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 跳过按钮
            FilledTonalButton(
                onClick = onSkipCurrent,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "跳过")
                Spacer(modifier = Modifier.width(4.dp))
                Text("跳过")
            }

            // 修正按钮
            Button(
                onClick = onCorrectCurrent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "修正")
                Spacer(modifier = Modifier.width(4.dp))
                Text("修正")
            }

            // 确认按钮
            Button(
                onClick = onConfirmCurrent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = "确认")
                Spacer(modifier = Modifier.width(4.dp))
                Text("确认")
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyVerifyView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "所有字形已校对完成",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "你可以返回首页继续操作",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// UI State
data class VerifyUiState(
    val isLoading: Boolean = false,
    val glyphs: List<GlyphModel> = emptyList(),
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val isBatchMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val showCorrectDialog: Boolean = false
)
