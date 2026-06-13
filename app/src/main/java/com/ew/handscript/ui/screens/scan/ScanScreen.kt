package com.ew.handscript.ui.screens.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.ew.handscript.ml.FiveElementValues
import com.ew.handscript.ui.navigation.SubRoute
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    navController: NavHostController,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 当前拍照文件（用于相机拍照）
    var currentPhotoFile by remember { mutableStateOf<File?>(null) }

    // 相机拍照启动器（必须先定义，因为 cameraPermissionLauncher 会用到）
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            try {
                if (success) {
                    currentPhotoFile?.let { file ->
                        if (file.exists() && file.length() > 0) {
                            // 直接使用拍照文件，不复制（避免目录为空的问题）
                            // 拍照文件已经在 getExternalFilesDir(Pictures) 目录下创建
                            viewModel.processSelectedImages(listOf(file.absolutePath))
                        } else {
                            Timber.e("拍照文件无效：${file?.absolutePath}")
                            viewModel.cancelCamera()
                        }
                    } ?: run {
                        Timber.e("拍照文件为空")
                        viewModel.cancelCamera()
                    }
                } else {
                    Timber.d("用户取消拍照")
                    viewModel.cancelCamera()
                }
            } catch (e: Exception) {
                Timber.e(e, "拍照回调异常")
                viewModel.cancelCamera()
            }
        }
    )

    // 相机权限请求启动器
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                // 权限已授予，启动相机
                try {
                    currentPhotoFile = FileUtils.createImageFile(context)
                    currentPhotoFile?.let { file ->
                        val photoUri = FileUtils.getFileProviderUri(context, file)
                        Timber.d("启动相机，文件路径: ${file.absolutePath}")
                        takePictureLauncher.launch(photoUri)
                    } ?: run {
                        Timber.e("创建拍照文件失败")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "启动相机异常")
                }
            } else {
                Timber.e("相机权限未授予")
            }
        }
    )

    // 检查并请求相机权限
    fun requestCameraPermission() {
        val permission = android.Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予，直接启动相机
            try {
                currentPhotoFile = FileUtils.createImageFile(context)
                currentPhotoFile?.let { file ->
                    val photoUri = FileUtils.getFileProviderUri(context, file)
                    Timber.d("启动相机，文件路径: ${file.absolutePath}")
                    takePictureLauncher.launch(photoUri)
                } ?: run {
                    Timber.e("创建拍照文件失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "启动相机异常")
            }
        } else {
            // 请求权限
            cameraPermissionLauncher.launch(permission)
        }
    }

    // 相册选择启动器
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let { selectedUri ->
                val realPath = FileUtils.getRealPathFromUri(context, selectedUri)
                if (realPath != null) {
                    viewModel.processSelectedImages(listOf(realPath))
                } else {
                    // 如果无法获取真实路径，尝试复制文件到私有目录
                    val tempFile = File(context.filesDir, "temp_image_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    viewModel.processSelectedImages(listOf(tempFile.absolutePath))
                }
            } ?: run {
                viewModel.cancelGallery()
            }
        }
    )

    // 相机启动（状态变化时触发）
    LaunchedEffect(uiState) {
        if (uiState is ScanUiState.Camera) {
            try {
                currentPhotoFile = FileUtils.createImageFile(context)
                currentPhotoFile?.let { file ->
                    val photoUri = FileUtils.getFileProviderUri(context, file)
                    Timber.d("启动相机，文件路径: ${file.absolutePath}")
                    takePictureLauncher.launch(photoUri)
                } ?: run {
                    Timber.e("创建拍照文件失败")
                    viewModel.cancelCamera()
                }
            } catch (e: Exception) {
                Timber.e(e, "创建拍照文件异常")
                viewModel.cancelCamera()
            }
        } else if (uiState is ScanUiState.Gallery) {
            try {
                pickImageLauncher.launch("image/*")
            } catch (e: Exception) {
                Timber.e(e, "打开相册异常")
                viewModel.cancelGallery()
            }
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.initializeTFLite(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描手写稿") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ScanUiState.Initial -> ScanInitialView(
                    onTakePhoto = { requestCameraPermission() },
                    onPickFromGallery = { viewModel.openGallery() }
                )
                is ScanUiState.Camera -> CameraView(
                    onClose = { viewModel.cancelCamera() }
                )
                is ScanUiState.Gallery -> GalleryPickerView(
                    onClose = { viewModel.cancelGallery() }
                )
                is ScanUiState.Processing -> ProcessingView(
                    progress = state.progress,
                    stage = state.stage
                )
                is ScanUiState.Segmenting -> ProcessingView(
                    progress = state.progress,
                    stage = state.stage
                )
                is ScanUiState.GridResult -> GridResultView(
                    navController = navController,
                    bitmap = state.originalBitmap,
                    glyphs = state.glyphs,
                    totalWuxing = state.totalWuxing,
                    isMock = state.isMock,
                    onRetake = { viewModel.retake() },
                    onRetry = { 
                        // 重新执行切字算法，而不是打开相册
                        state.originalBitmap?.let { bitmap ->
                            val tempFile = File(context.filesDir, "retry_image_${System.currentTimeMillis()}.jpg")
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, tempFile.outputStream())
                            viewModel.processSelectedImages(listOf(tempFile.absolutePath))
                        } ?: run {
                            viewModel.retry()
                        }
                    }
                )
                is ScanUiState.Preview -> PreviewView(
                    originalImage = state.originalImage,
                    correctedImage = state.correctedImage,
                    cornerPoints = state.cornerPoints,
                    baselines = state.baselines,
                    onConfirm = {
                        navController.navigate(SubRoute.Proofread.route)
                    },
                    onRetake = { viewModel.retake() },
                    onManualAdjust = { viewModel.enterManualAdjust() }
                )
                is ScanUiState.Error -> ErrorView(
                    message = state.message,
                    onRetry = { viewModel.retry() },
                    onCancel = { navController.navigateUp() }
                )
            }
        }
    }
}

@Composable
private fun ScanInitialView(
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "导入历史手写稿",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "拍照或从相册选择手写稿\n系统将自动矫正、切割和识别",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onTakePhoto,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("拍照", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onPickFromGallery,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("从相册选择", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CameraView(
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 相机预览占位符（实际相机预览由系统相机应用处理）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Camera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "正在打开相机...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 底部操作栏
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun GalleryPickerView(
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("相册选择器", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "正在打开相册...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onClose) {
            Text("取消")
        }
    }
}

@Composable
private fun ProcessingView(
    progress: Float,
    stage: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "正在处理...",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GridResultView(
    navController: NavHostController,
    bitmap: android.graphics.Bitmap,
    glyphs: List<SegmentedGlyph>,
    totalWuxing: FiveElementValues,
    isMock: Boolean,
    onRetake: () -> Unit,
    onRetry: () -> Unit
) {
    // 使用Box作为根容器，内部使用垂直滚动
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 小图预览区
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Image(
                    painter = rememberAsyncImagePainter(bitmap),
                    contentDescription = "原始手写体",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Mock模式提示
            if (isMock) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "⚠️ 模型未加载，显示模拟数据",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 五行总览
            Column {
                Text(
                    text = "五行灵根",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WuxingBadge("木", totalWuxing.wood, Color(0xFF22C55E))
                    WuxingBadge("火", totalWuxing.fire, Color(0xFFEF4444))
                    WuxingBadge("土", totalWuxing.earth, Color(0xFFA16207))
                    WuxingBadge("金", totalWuxing.metal, Color(0xFFEAB308))
                    WuxingBadge("水", totalWuxing.water, Color(0xFF3B82F6))
                }
            }

            // 九宫格区域
            Column {
                // 九宫格标题
                Text(
                    text = "九字真言",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 九宫格 - 使用固定高度避免嵌套滚动冲突
                Box(modifier = Modifier.height(280.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(glyphs.size, key = { it }) { index ->
                            GlyphCard(
                                glyph = glyphs[index],
                                onClick = {
                                    if (!glyphs[index].isPlaceholder) {
                                        // 保存选中的字形数据到临时持有者
                                        GlyphDataHolder.setGlyph(glyphs[index])
                                        // 跳转到校对页，传递glyphId作为参数
                                        navController.navigate("proofread/${glyphs[index].id}")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新识别")
                }
                Button(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("拍照识五行")
                }
            }

            // 底部间距
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WuxingBadge(
    title: String,
    value: Float,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "${value.toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GlyphCard(
    glyph: SegmentedGlyph,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 2.dp,
            color = if (glyph.isPlaceholder) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                glyph.getWuXingColor()
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (glyph.isPlaceholder) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "待扫描",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(glyph.bitmap),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = glyph.wuXing,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = glyph.getWuXingColor()
                    )
                    Text(
                        text = glyph.getFormattedConfidence(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewView(
    originalImage: String?,
    correctedImage: String?,
    cornerPoints: List<android.graphics.PointF>,
    baselines: List<Int>,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onManualAdjust: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (correctedImage != null) {
                AsyncImage(
                    model = correctedImage,
                    contentDescription = "矫正后的图像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    "预览区域",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            BaselineOverlay(
                baselines = baselines,
                modifier = Modifier.fillMaxSize()
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "检测到 ${baselines.size} 行文字",
                    style = MaterialTheme.typography.bodyLarge
                )

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("确认导入")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("重拍")
                    }
                    OutlinedButton(
                        onClick = onManualAdjust,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("手动调整")
                    }
                }
            }
        }
    }
}

@Composable
private fun BaselineOverlay(
    baselines: List<Int>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "处理失败",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("重试")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onCancel) {
            Text("返回")
        }
    }
}

sealed class ScanUiState {
    data object Initial : ScanUiState()
    data object Camera : ScanUiState()
    data object Gallery : ScanUiState()
    data class Processing(
        val progress: Float,
        val stage: String
    ) : ScanUiState()
    data class Segmenting(
        val progress: Float,
        val stage: String
    ) : ScanUiState()
    data class GridResult(
        val originalBitmap: android.graphics.Bitmap,
        val glyphs: List<SegmentedGlyph>,
        val totalWuxing: FiveElementValues,
        val isMock: Boolean
    ) : ScanUiState()
    data class Preview(
        val documentId: Long,
        val originalImage: String?,
        val correctedImage: String?,
        val cornerPoints: List<android.graphics.PointF>,
        val baselines: List<Int>
    ) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}
