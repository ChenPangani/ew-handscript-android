package com.ew.handscript.ui.screens.scan

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PhotoLibrary
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
import com.ew.handscript.ui.Screen

/**
 * 扫描屏幕 - 手写稿导入与处理
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    navController: NavHostController,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                    onTakePhoto = { viewModel.startCamera() },
                    onPickFromGallery = { viewModel.openGallery() }
                )
                is ScanUiState.Camera -> CameraView(
                    onCapture = { viewModel.captureImage(it) },
                    onClose = { viewModel.cancelCamera() }
                )
                is ScanUiState.Gallery -> GalleryPickerView(
                    onImagesSelected = { viewModel.processSelectedImages(it) },
                    onClose = { viewModel.cancelGallery() }
                )
                is ScanUiState.Processing -> ProcessingView(
                    progress = state.progress,
                    stage = state.stage
                )
                is ScanUiState.Preview -> PreviewView(
                    originalImage = state.originalImage,
                    correctedImage = state.correctedImage,
                    cornerPoints = state.cornerPoints,
                    baselines = state.baselines,
                    onConfirm = {
                        navController.navigate(Screen.Verify.createRoute(state.documentId))
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
        // 扫描图标
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

        // 拍照按钮
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

        // 相册按钮
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
    onCapture: (String) -> Unit,
    onClose: () -> Unit
) {
    // 实际实现中这里会集成CameraX预览
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 占位：相机预览区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                "相机预览区域",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                // 关闭按钮
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 拍照按钮
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                    onClick = { onCapture("") }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Camera,
                            contentDescription = "拍照",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // 占位
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun GalleryPickerView(
    onImagesSelected: (List<String>) -> Unit,
    onClose: () -> Unit
) {
    // 实际实现中使用系统图库选择器或自定义图库
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("相册选择器", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onImagesSelected(listOf("mock_path_1")) }) {
            Text("模拟选择图片")
        }
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
        // 预览图像
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

            // 基线可视化覆盖层
            BaselineOverlay(
                baselines = baselines,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 底部操作栏
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
    // 绘制基线参考线
    Box(modifier = modifier) {
        // 实际实现中应使用Canvas绘制水平线
        // 这里简化处理
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

// UI State
 sealed class ScanUiState {
    data object Initial : ScanUiState()
    data object Camera : ScanUiState()
    data object Gallery : ScanUiState()
    data class Processing(
        val progress: Float,
        val stage: String
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
