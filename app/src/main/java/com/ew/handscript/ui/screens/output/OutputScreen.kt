package com.ew.handscript.ui.screens.output

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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ew.handscript.ui.theme.HandCraftFontTheme

/**
 * 万象法相输出页（P0-5）
 *
 * 四段式结构：
 * 1. 模板选择区（4种模板卡片）
 * 2. A4预览区（210:297比例，宣纸纹理，缺字标红）
 * 3. 缺字补天提示条（条件显示）
 * 4. 底部悬浮导出按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputScreen(
    navController: NavHostController,
    textContent: String = "春眠不觉晓，处处闻啼鸟。夜来风雨声，花落知多少。",
    glyphLibrary: Set<Char> = DEFAULT_GLYPH_LIBRARY
) {
    var selectedTemplate by remember { mutableStateOf(TemplateType.CHUAN_YIN_FU) }
    var showExporting by remember { mutableStateOf(false) }

    // 缺字检测
    val missingChars = remember(textContent, glyphLibrary) {
        textContent.toSet().filter { it !in glyphLibrary && !it.isWhitespace() }.toSet()
    }
    val hasMissing = missingChars.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("万象法相", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
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
                .verticalScroll(rememberScrollState())
        ) {
            TemplateSelector(selected = selectedTemplate) { selectedTemplate = it }
            PreviewArea(
                textContent = textContent,
                isVertical = selectedTemplate == TemplateType.ZOU_ZHE,
                missingChars = missingChars
            )
            if (hasMissing) {
                MissingGlyphBar(missingChars.size, missingChars)
            }
            Spacer(Modifier.height(100.dp)) // 底部留出导出按钮空间
        }
        ExportFloatingBar(
            onExportImage = { showExporting = true },
            onShare = { /* TODO: 系统分享 */ },
            modifier = Modifier.padding(paddingValues)
        )
    }

    if (showExporting) {
        ExportingOverlay { showExporting = false }
    }
}

/** 模板类型枚举 */
enum class TemplateType(val title: String, val desc: String, val icon: ImageVector) {
    CHUAN_YIN_FU("传音符", "短句 · 微信分享", Icons.Outlined.ChatBubbleOutline),
    BAI_TIE("拜帖", "请柬 · 贺卡", Icons.Outlined.MailOutline),
    ZOU_ZHE("奏折", "长文 · 竖排", Icons.Outlined.MenuBook),
    CUSTOM("自定义", "自由排版", Icons.Outlined.Brush)
}

/** 模板选择器：4张垂直排列的模板卡片 */
@Composable
private fun TemplateSelector(
    selected: TemplateType,
    onSelect: (TemplateType) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        SectionLabel("选择模板")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TemplateType.entries.forEach { t ->
                TemplateCard(t, selected == t) { onSelect(t) }
            }
        }
    }
}

/** 单张模板卡片：左侧圆形图标 + 标题/描述 */
@Composable
private fun TemplateCard(
    template: TemplateType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF1A1A2E) else MaterialTheme.colorScheme.outlineVariant
    val iconBg = if (isSelected) Color(0xFF1A1A2E) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    val iconTint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = iconBg, modifier = Modifier.size(48.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(template.icon, template.title, tint = iconTint, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(template.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, "已选中", tint = Color(0xFF1A1A2E), modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** A4预览区：210:297比例，宣纸纹理 */
@Composable
private fun PreviewArea(
    textContent: String,
    isVertical: Boolean,
    missingChars: Set<Char>
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionLabel("预览")
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(210f / 297f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF5F0E8)) // 宣纸色
                .border(1.dp, Color(0xFFD4C5B5), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                HandwritingPreviewCanvas(textContent, isVertical, missingChars)
            }
        }
    }
}

/** 分组标签 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/** 缺字补天提示条 */
@Composable
private fun MissingGlyphBar(
    missingCount: Int,
    missingChars: Set<Char>,
    onFixClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2F2)),
        border = BorderStroke(1.dp, Color(0xFFFFCCCC))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFFCC0000), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                val charList = missingChars.joinToString("、") { "「$it」" }
                Text(
                    "缺字 $missingCount 个：$charList",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFCC0000), maxLines = 1
                )
                Text(
                    "此字尚未收录，请现场临摹补天",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFCC0000).copy(alpha = 0.7f)
                )
            }
            Button(
                onClick = onFixClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("立即补字", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 底部悬浮导出栏 */
@Composable
private fun ExportFloatingBar(
    onExportImage: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 导出图片主按钮
                Button(
                    onClick = onExportImage,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
                ) {
                    Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导出图片", fontWeight = FontWeight.SemiBold)
                }
                // 分享次级按钮（白底黑边）
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.widthIn(min = 100.dp).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF1A1A2E)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1A1A2E))
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("分享", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** 导出中遮罩 */
@Composable
private fun ExportingOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                Text("正在导出...", style = MaterialTheme.typography.titleMedium)
                Text("请稍候，正在生成手写图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Mock字库：默认收录的常用字（用于缺字检测演示） */
internal val DEFAULT_GLYPH_LIBRARY = setOf(
    '春', '眠', '不', '觉', '晓', '处', '闻', '啼', '鸟',
    '夜', '来', '风', '雨', '声', '花', '落', '知', '多', '少',
    '。', '，', '！', '？', '、', '：', '；', ' '
)

/** 预览 */
@Preview(showBackground = true)
@Composable
private fun OutputScreenPreview() {
    HandCraftFontTheme {
        Surface { OutputScreen(rememberNavController()) }
    }
}
