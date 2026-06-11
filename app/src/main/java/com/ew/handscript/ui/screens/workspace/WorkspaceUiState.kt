package com.ew.handscript.ui.state

import android.graphics.Bitmap
import com.ew.handscript.model.typeset.FontConfig

data class WorkspaceUiState(
    val showTextInputDialog: Boolean = false,
    val textContent: String = "",
    val hasContent: Boolean = false,
    val fontConfig: FontConfig = FontConfig(),
    val showExportDialog: Boolean = false,
    val isExporting: Boolean = false,
    val previewBitmap: Bitmap? = null
)