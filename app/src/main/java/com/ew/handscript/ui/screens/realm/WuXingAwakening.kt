package com.ew.handscript.ui.screens.realm

import androidx.compose.runtime.*
import com.ew.handscript.ml.FiveElementValues
import com.ew.handscript.ml.TFLiteHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 五行灵根觉醒Composable（TFLite集成）
 *
 * 首批100字完成后自动触发五行推理
 */
@Composable
fun rememberWuXingAwakening(
    tfLiteHelper: TFLiteHelper,
    verifiedCount: Int
): FiveElementValues? {
    var result by remember { mutableStateOf<FiveElementValues?>(null) }

    LaunchedEffect(verifiedCount) {
        if (verifiedCount >= 100 && result == null) {
            val mockBitmap = android.graphics.Bitmap.createBitmap(
                224, 224, android.graphics.Bitmap.Config.ARGB_8888
            )
            val inferResult = withContext(Dispatchers.IO) {
                tfLiteHelper.awakenWuxing(mockBitmap)
            }
            mockBitmap.recycle()
            result = inferResult
        }
    }
    return result
}
