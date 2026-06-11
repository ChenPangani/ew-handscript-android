/**
 * 文件名: ApiContract.kt
 * 负责Agent: Agent-D (Android开发)
 * 所属模块: network
 * 最后修改: 2026-06-09
 * 版本: 0.4.2-wiki
 *
 * 功能说明: 前后端 API 数据契约（无序列化注解版本，避免 kapt 冲突）
 * 关键约束: 华为Mate30兼容，包体积<50MB
 */

package com.ew.handscript.network

import com.ew.handscript.model.GlyphModel

// ============================================
// 1. 字形上传相关
// ============================================

data class GlyphBatchUploadRequest(
    val batchId: String,
    val glyphs: List<GlyphUploadItem>,
    val uploadMetadata: UploadMetadata
)

data class GlyphUploadItem(
    val unicode: String,
    val character: String,
    val glyphVersion: Int = 1,
    val imageBase64: String,
    val width: Int,
    val height: Int,
    val baseline: Int,
    val advanceWidth: Int,
    val tags: List<String> = emptyList(),
    val ocrText: String? = null,
    val correctedText: String? = null
)

data class UploadMetadata(
    val totalCount: Int,
    val sourceType: String = "scan",
    val deviceId: String,
    val clientVersion: String
)

data class GlyphBatchUploadResponse(
    val batchId: String,
    val uploadedCount: Int,
    val failedItems: List<FailedUploadItem> = emptyList(),
    val storageUrls: Map<String, String> = emptyMap(),
    val status: String = "completed"
)

data class FailedUploadItem(
    val glyphId: String,
    val errorCode: String,
    val errorMessage: String
)

// ============================================
// 2. 字体生成相关
// ============================================

data class FontGenerationRequest(
    val fontName: String,
    val glyphSetId: String,
    val fontOptions: FontGenerationOptions = FontGenerationOptions()
)

data class FontGenerationOptions(
    val fontFamilyName: String = "",
    val includeFallbackGlyphs: Boolean = true,
    val hintingLevel: String = "light",
    val optimizeForScreen: Boolean = true,
    val enableLigatures: Boolean = false,
    val metadata: FontMetadata = FontMetadata()
)

data class FontMetadata(
    val designer: String = "",
    val description: String = "Personal handwriting font",
    val copyright: String = "",
    val version: String = "1.0"
)

data class FontGenerationResponse(
    val taskId: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val estimatedSeconds: Int = 0,
    val positionInQueue: Int = 0
)

enum class TaskStatus {
    QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED
}

data class FontTaskStatusResponse(
    val taskId: String,
    val status: TaskStatus,
    val progressPercent: Int = 0,
    val currentStage: String = "",
    val result: FontTaskResult? = null,
    val error: TaskError? = null
)

data class FontTaskResult(
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
    val fontPreviewUrl: String,
    val expiryTime: Long
)

data class TaskError(
    val code: String,
    val message: String,
    val retryable: Boolean = true
)

// ============================================
// 3. OCR纠错相关
// ============================================

data class OcrCorrectionRequest(
    val ocrResults: List<OcrResultItem>,
    val contextText: String? = null,
    val documentType: String = "general"
)

data class OcrResultItem(
    val glyphId: String,
    val unicode: String,
    val ocrText: String,
    val confidence: Float,
    val boundingBox: BoundingBox? = null
)

data class BoundingBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class OcrCorrectionResponse(
    val correctedResults: List<CorrectedResult>,
    val modelUsed: String = "",
    val processingTimeMs: Int = 0
)

data class CorrectedResult(
    val glyphId: String,
    val originalText: String,
    val correctedText: String,
    val confidence: Float,
    val isCorrected: Boolean,
    val correctionReason: String? = null
)

// ============================================
// 4. 语音识别相关
// ============================================

data class SpeechTranscriptionResponse(
    val transcriptionId: String,
    val text: String,
    val originalText: String,
    val segments: List<TranscriptionSegment> = emptyList(),
    val durationSeconds: Float = 0f,
    val processingTimeMs: Int = 0
)

data class TranscriptionSegment(
    val startTime: Float,
    val endTime: Float,
    val text: String,
    val confidence: Float
)

// ============================================
// 5. 用户字库管理
// ============================================

data class LibraryStatsResponse(
    val totalGlyphs: Int = 0,
    val uniqueChars: Int = 0,
    val libraryLevel: String = "STARTER",
    val progressPercent: Float = 0f,
    val coverageStats: CoverageStats? = null,
    val recentActivity: List<ActivityRecord> = emptyList()
)

data class CoverageStats(
    val commonChars3500: Float = 0f,
    val generalStandard8105: Float = 0f,
    val totalUnicodeCjk: Float = 0f
)

data class ActivityRecord(
    val date: String,
    val action: String,
    val count: Int = 1
)

// ============================================
// 6. 文档解析
// ============================================

data class DocumentParseResponse(
    val documentId: String,
    val fileName: String,
    val textContent: String,
    val paragraphs: List<ParsedParagraph> = emptyList(),
    val metadata: DocumentMetadata = DocumentMetadata()
)

data class ParsedParagraph(
    val index: Int,
    val text: String,
    val style: ParagraphStyle = ParagraphStyle()
)

data class ParagraphStyle(
    val isHeading: Boolean = false,
    val headingLevel: Int = 0,
    val alignment: String = "left",
    val indentLevel: Int = 0
)

data class DocumentMetadata(
    val totalPages: Int? = null,
    val totalWords: Int = 0,
    val author: String? = null,
    val title: String? = null
)

// ============================================
// 7. 字库同步
// ============================================

data class LibrarySyncRequest(
    val lastSyncTimestamp: Long? = null,
    val localGlyphIds: List<String> = emptyList()
)

data class LibrarySyncResponse(
    val syncTimestamp: Long = 0L,
    val addedGlyphs: List<GlyphModel> = emptyList(),
    val updatedGlyphs: List<GlyphModel> = emptyList(),
    val deletedGlyphIds: List<String> = emptyList(),
    val serverStats: LibraryStatsResponse = LibraryStatsResponse()
)

// ============================================
// 8. 用户认证
// ============================================

data class LoginRequest(
    val deviceId: String,
    val platform: String = "android",
    val appVersion: String = ""
)

data class LoginResponse(
    val userId: String = "",
    val token: String = "",
    val refreshToken: String = "",
    val expiresIn: Long = 0L,
    val isNewUser: Boolean = false
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class TokenResponse(
    val token: String = "",
    val expiresIn: Long = 0L
)

// ============================================
// 通用响应包装
// ============================================

data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val requestId: String = ""
)

data class PaginatedResponse<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val hasMore: Boolean = false
)

// ============================================
// 错误码
// ============================================

object ErrorCodes {
    const val SUCCESS = 0
    const val UNKNOWN_ERROR = 1000
    const val INVALID_PARAMETER = 1001
    const val UNAUTHORIZED = 1002
    const val FORBIDDEN = 1003
    const val NOT_FOUND = 1004
    const val RATE_LIMITED = 1005
    const val FILE_TOO_LARGE = 1006
    const val UNSUPPORTED_FILE_TYPE = 1007
    const val OCR_ENGINE_ERROR = 2001
    const val LLM_SERVICE_ERROR = 2002
    const val FONT_GENERATION_FAILED = 3001
    const val FONT_TASK_TIMEOUT = 3002
    const val STORAGE_FULL = 4001
    const val NETWORK_ERROR = 5001
    const val SERVER_INTERNAL_ERROR = 5002
}
