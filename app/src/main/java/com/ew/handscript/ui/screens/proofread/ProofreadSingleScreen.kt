package com.ew.handscript.ui.screens.proofread

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.ew.handscript.data.local.GlyphEntity
import com.ew.handscript.ui.screens.scan.GlyphDataHolder
import com.ew.handscript.ui.screens.scan.SegmentedGlyph
import com.ew.handscript.ui.navigation.BottomTab
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 单个字形校对页
 * 
 * 功能：
 * 1. 显示选中字形的放大图
 * 2. 显示五行标签和置信度
 * 3. "确认入库"和"重新切割"两个操作按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofreadSingleScreen(
    navController: NavHostController,
    glyphId: String,
    viewModel: ProofreadViewModel = hiltViewModel()
) {
    // 从数据持有者获取字形数据
    val glyph = GlyphDataHolder.getGlyph()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // 重新切割 BottomSheet 状态
    val showBottomSheet = remember { mutableStateOf(false) }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("字形校对", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { 
                        // 返回前清空数据
                        GlyphDataHolder.clear()
                        navController.popBackStack() 
                    }) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (glyph != null) {
                // 字形放大图展示区
                Surface(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(glyph.bitmap),
                        contentDescription = "字形放大图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 五行标签和置信度
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(glyph.getWuXingColor().copy(alpha = 0.1f))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = glyph.wuXing,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = glyph.getWuXingColor()
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = glyph.getFormattedConfidence(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // 操作按钮
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            // 确认入库逻辑
                            scope.launch {
                                val result = saveGlyphToLibrary(context, glyph, viewModel)
                                if (result) {
                                    scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "【${glyph.wuXing}】已入库",
                                    duration = SnackbarDuration.Short
                                )
                            }
                                    delay(1500)
                                    GlyphDataHolder.clear()
                                    navController.popBackStack()
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "入库失败，请重试",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "确认入库",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            // 显示重新切割选项 BottomSheet
                            showBottomSheet.value = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "重新切割",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // 未找到字形数据
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "未找到字形数据",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { navController.popBackStack() }
                    ) {
                        Text("返回")
                    }
                }
            }
        }
    }
    
    // 重新切割选项 BottomSheet
    RetrySegmentationBottomSheet(
        showBottomSheet = showBottomSheet,
        glyph = glyph,
        navController = navController,
        snackbarHostState = snackbarHostState,
        scope = scope,
        context = context
    )
}

/**
 * 保存字形到字库
 * 1. 将Bitmap保存为PNG到内部存储
 * 2. 将元数据写入Room数据库
 */
private suspend fun saveGlyphToLibrary(
    context: Context,
    glyph: SegmentedGlyph,
    viewModel: ProofreadViewModel
): Boolean {
    return try {
        // 1. 创建保存目录
        val glyphDir = File(context.filesDir, "glyphs")
        if (!glyphDir.exists()) {
            glyphDir.mkdirs()
        }
        
        // 2. 生成文件名（时间戳+五行标签）
        val timestamp = System.currentTimeMillis()
        val charName = glyph.wuXing.take(1).ifEmpty { "unknown" }
        val fileName = "glyph_${charName}_${timestamp}.png"
        val outputFile = File(glyphDir, fileName)
        
        // 3. 保存Bitmap为PNG
        FileOutputStream(outputFile).use { fos ->
            glyph.bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        
        // 4. 写入Room数据库
        val result = viewModel.saveGlyphToDatabase(
            character = glyph.wuXing,
            imagePath = outputFile.absolutePath,
            ocrText = glyph.wuXing,
            confidence = glyph.confidence
        )
        
        result != null
    } catch (e: IOException) {
        timber.log.Timber.e(e, "保存字形图片失败")
        false
    } catch (e: Exception) {
        timber.log.Timber.e(e, "保存字形到数据库失败")
        false
    }
}

/**
 * 记录精修请求事件到 SharedPreferences
 */
private fun recordRefinementRequest(context: Context, character: String) {
    val prefs: SharedPreferences = context.getSharedPreferences("handscript_events", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    
    // 记录事件类型和时间戳
    val eventKey = "refinement_request_${System.currentTimeMillis()}"
    val eventValue = "{\"eventType\":\"refinement_request\",\"timestamp\":${System.currentTimeMillis()},\"character\":\"${character}\"}"
    editor.putString(eventKey, eventValue)
    editor.apply()
    
    timber.log.Timber.d("精修请求已记录: character=$character")
}

/**
 * 重新切割选项 BottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetrySegmentationBottomSheet(
    showBottomSheet: MutableState<Boolean>,
    glyph: SegmentedGlyph?,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    context: Context
) {
    val sheetState = rememberModalBottomSheetState()
    val localScope = rememberCoroutineScope()
    
    if (showBottomSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet.value = false },
            sheetState = sheetState,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 标题
                Text(
                    text = "该字切废了，请选择处理方式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // 选项A：跳过该字
                Button(
                    onClick = {
                        // 从九宫格数据源中移除当前字
                        GlyphDataHolder.removeCurrentGlyph()
                        showBottomSheet.value = false
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "跳过该字",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "从九宫格移除，继续校对下一个",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 选项B：精修对齐（V2预埋）
                // TODO-V2: 双层卡片质心扩展精修模式
                // 触发条件：用户境界达到筑基期后解锁
                // 技术方案：ROI内连通域+分水岭 → 方圆透出底稿 → 拖动对齐 → 重新入库
                Button(
                    onClick = {
                        // 记录点击事件到本地
                        glyph?.wuXing?.let { char ->
                            recordRefinementRequest(context, char)
                        }
                        
                        // 显示Toast提示
                        localScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "精修对齐将在筑基期开放，当前可先跳过该字",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = false, // 禁用状态
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "锁定",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "精修对齐",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "筑基期开放（长按底稿手动调框）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // 选项C：重扫底稿
                Button(
                    onClick = {
                        GlyphDataHolder.clear()
                        showBottomSheet.value = false
                        navController.navigate(BottomTab.Library.route)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "重扫底稿",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "返回首页，重新拍照或选相册",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

