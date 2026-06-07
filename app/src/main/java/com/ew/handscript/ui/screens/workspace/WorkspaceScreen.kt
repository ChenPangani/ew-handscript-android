package com.ew.handscript.ui.screens.workspace

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ew.handscript.model.typeset.*

/**
 * 创作工作台 - 多模态内容输入与智能排版
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    navController: NavHostController,
    viewModel: WorkspaceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创作工作台") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 导出按钮
                    IconButton(
                        onClick = { viewModel.showExportOptions() },
                        enabled = uiState.hasContent
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "导出")
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
            // 内容输入区域
            if (!uiState.hasContent) {
                EmptyWorkspaceView(
                    onTextInput = { viewModel.showTextInput() },
                    onImportDocument = { viewModel.showDocumentImport() },
                    onVoiceInput = { viewModel.showVoiceInput() }
                )
            } else {
                // 编辑与预览区域
                WorkspaceEditor(
                    uiState = uiState,
                    onTextChange = { viewModel.updateText(it) },
                    onFontConfigChange = { viewModel.updateFontConfig(it) },
                    previewBitmap = uiState.previewBitmap
                )
            }
        }
    }

    // 文本输入对话框
    if (uiState.showTextInputDialog) {
        TextInputDialog(
            initialText = uiState.textContent,
            onConfirm = { viewModel.setTextContent(it) },
            onDismiss = { viewModel.dismissTextInput() }
        )
    }

    // 导出选项对话框
    if (uiState.showExportDialog) {
        ExportOptionsDialog(
            onExportPng = { viewModel.exportAsPng() },
            onExportPdf = { viewModel.exportAsPdf() },
            onDismiss = { viewModel.dismissExportDialog() }
        )
    }
}

@Composable
private fun EmptyWorkspaceView(
    onTextInput: () -> Unit,
    onImportDocument: () -> Unit,
    onVoiceInput: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.EditNote,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "开始创作",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "选择一种方式输入内容",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 输入方式选择
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InputMethodButton(
                icon = Icons.Filled.Edit,
                title = "输入文本",
                description = "直接粘贴或输入文字内容",
                onClick = onTextInput
            )

            InputMethodButton(
                icon = Icons.Filled.UploadFile,
                title = "导入文档",
                description = "支持 Word、PDF、TXT 格式",
                onClick = onImportDocument
            )

            InputMethodButton(
                icon = Icons.Filled.Mic,
                title = "语音输入",
                description = "语音转写并自动优化文本",
                onClick = onVoiceInput
            )
        }
    }
}

@Composable
private fun InputMethodButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkspaceEditor(
    uiState: WorkspaceUiState,
    onTextChange: (String) -> Unit,
    onFontConfigChange: (FontConfig) -> Unit,
    previewBitmap: android.graphics.Bitmap?
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 预览区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = uiState.fontConfig.paperTemplate.backgroundColor.let {
                    Color(it.toInt())
                }
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "手写预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        "预览区域",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 排版参数控制栏
        LayoutControlsBar(
            fontConfig = uiState.fontConfig,
            onConfigChange = onFontConfigChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutControlsBar(
    fontConfig: FontConfig,
    onConfigChange: (FontConfig) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 信纸模板选择
            ScrollableTabRow(
                selectedTabIndex = PaperTemplate.values().indexOf(fontConfig.paperTemplate),
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                PaperTemplate.values().forEach { template ->
                    Tab(
                        selected = fontConfig.paperTemplate == template,
                        onClick = { onConfigChange(fontConfig.copy(paperTemplate = template)) },
                        text = { Text(template.displayName, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // 滑块控制
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 字号
                SliderWithLabel(
                    label = "字号",
                    value = fontConfig.fontSizePx,
                    range = 36f..120f,
                    onValueChange = { onConfigChange(fontConfig.copy(fontSizePx = it)) }
                )

                // 行间距
                SliderWithLabel(
                    label = "行间距",
                    value = fontConfig.lineSpacingPx,
                    range = 48f..160f,
                    onValueChange = { onConfigChange(fontConfig.copy(lineSpacingPx = it)) }
                )

                // 字间距
                SliderWithLabel(
                    label = "字间距",
                    value = fontConfig.letterSpacingPx,
                    range = 0f..10f,
                    onValueChange = { onConfigChange(fontConfig.copy(letterSpacingPx = it)) }
                )

                // 墨水颜色选择
                InkColorSelector(
                    currentColor = fontConfig.inkColor,
                    onColorSelected = { onConfigChange(fontConfig.copy(inkColor = it)) }
                )
            }

            // 开关选项
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    selected = fontConfig.enablePerturbation,
                    onClick = { onConfigChange(fontConfig.copy(enablePerturbation = !fontConfig.enablePerturbation)) },
                    label = { Text("随机扰动") }
                )
                FilterChip(
                    selected = fontConfig.enableDynamicGlyph,
                    onClick = { onConfigChange(fontConfig.copy(enableDynamicGlyph = !fontConfig.enableDynamicGlyph)) },
                    label = { Text("动态字形") }
                )
                FilterChip(
                    selected = fontConfig.enableScanFilter,
                    onClick = { onConfigChange(fontConfig.copy(enableScanFilter = !fontConfig.enableScanFilter)) },
                    label = { Text("扫描滤镜") }
                )
            }
        }
    }
}

@Composable
private fun SliderWithLabel(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(48.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${value.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun InkColorSelector(
    currentColor: Long,
    onColorSelected: (Long) -> Unit
) {
    val colors = listOf(
        0xFF1A1A2E to "墨蓝",
        0xFF000000 to "纯黑",
        0xFF2C3E50 to "深灰",
        0xFF8B4513 to "棕色",
        0xFF1E3A5F to "藏青",
        0xFF4A6741 to "墨绿",
        0xFF8B0000 to "暗红"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "墨水",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(48.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colors.forEach { (color, name) ->
                val isSelected = currentColor == color
                Surface(
                    shape = CircleShape,
                    color = Color(color.toInt()),
                    border = if (isSelected) {
                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else null,
                    modifier = Modifier.size(28.dp),
                    onClick = { onColorSelected(color) }
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = name,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入文本") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("在此输入内容...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
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
private fun ExportOptionsDialog(
    onExportPng: () -> Unit,
    onExportPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出文档") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListItem(
                    headlineContent = { Text("导出为 PNG") },
                    supportingContent = { Text("适合社交媒体分享") },
                    leadingContent = {
                        Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable { onExportPng() }
                )
                ListItem(
                    headlineContent = { Text("导出为 PDF") },
                    supportingContent = { Text("适合打印提交") },
                    leadingContent = {
                        Icon(Icons.Filled.PictureAsPdf, null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable { onExportPdf() }
                )
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// 引入需要的Compose函数
@androidx.compose.runtime.Composable
private fun android.graphics.Bitmap.asImageBitmap() = androidx.compose.ui.graphics.asImageBitmap()

// UI State
data class WorkspaceUiState(
    val hasContent: Boolean = false,
    val textContent: String = "",
    val fontConfig: FontConfig = FontConfig.DEFAULT,
    val previewBitmap: android.graphics.Bitmap? = null,
    val showTextInputDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val isExporting: Boolean = false
)
