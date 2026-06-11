/**
 * 文件名: ApiService.kt
 * 负责Agent: Agent-D (Android开发)
 * 所属模块: network
 * 最后修改: 2026-06-09
 * 版本: 0.4.2-wiki
 *
 * 功能说明: 后端 API 服务接口（纯 Kotlin 接口，无 Retrofit 注解）
 *          等待 Agent-Arch 添加 Retrofit 依赖后，再添加 HTTP 注解
 * 关键约束: 华为Mate30兼容，包体积<50MB
 */

package com.ew.handscript.network

/**
 * API 服务接口 - 后端通信契约
 *
 * 当前版本为纯 Kotlin 接口（占位实现），原因：
 * 1. build.gradle.kts 暂未包含 Retrofit/OkHttp 依赖
 * 2. 避免 @Serializable 与 kapt 编译冲突
 *
 * 当 Agent-Arch 添加 Retrofit 依赖后，将本接口升级为完整 Retrofit Service：
 * - 添加 @POST/@GET/@Multipart 等注解
 * - 参数添加 @Body/@Part/@Path 等注解
 * - ApiContract.kt 可重新添加 @Serializable
 */
interface ApiService {

    // ============================================
    // 1. 字形上传
    // ============================================

    /** POST /api/v1/glyphs/batch-upload */
    suspend fun uploadGlyphs(request: GlyphBatchUploadRequest): ApiResponse<GlyphBatchUploadResponse>

    /** POST /api/v1/glyphs/upload-image (multipart) */
    suspend fun uploadGlyphImage(
        imageBytes: ByteArray,
        unicode: String,
        glyphVersion: Int
    ): ApiResponse<Map<String, String>>

    // ============================================
    // 2. 字体生成
    // ============================================

    /** POST /api/v1/fonts/generate */
    suspend fun submitFontGeneration(request: FontGenerationRequest): ApiResponse<FontGenerationResponse>

    /** GET /api/v1/fonts/tasks/{task_id} */
    suspend fun getFontTaskStatus(taskId: String): ApiResponse<FontTaskStatusResponse>

    /** POST /api/v1/fonts/tasks/{task_id}/cancel */
    suspend fun cancelFontTask(taskId: String): ApiResponse<Unit>

    // ============================================
    // 3. OCR 纠错
    // ============================================

    /** POST /api/v1/ocr/correct */
    suspend fun correctOcrResults(request: OcrCorrectionRequest): ApiResponse<OcrCorrectionResponse>

    // ============================================
    // 4. 语音识别
    // ============================================

    /** POST /api/v1/speech/transcribe (multipart) */
    suspend fun transcribeSpeech(
        audioBytes: ByteArray,
        language: String = "zh-CN",
        enablePunctuation: Boolean = true,
        removeFillerWords: Boolean = true
    ): ApiResponse<SpeechTranscriptionResponse>

    // ============================================
    // 5. 字库管理
    // ============================================

    /** GET /api/v1/library/stats */
    suspend fun getLibraryStats(): ApiResponse<LibraryStatsResponse>

    /** POST /api/v1/library/sync */
    suspend fun syncLibrary(request: LibrarySyncRequest): ApiResponse<LibrarySyncResponse>

    // ============================================
    // 6. 文档解析
    // ============================================

    /** POST /api/v1/documents/parse (multipart) */
    suspend fun parseDocument(
        documentBytes: ByteArray,
        extractStructure: Boolean = true
    ): ApiResponse<DocumentParseResponse>

    /** GET /api/v1/documents/tasks/{task_id} */
    suspend fun getDocumentParseStatus(taskId: String): ApiResponse<DocumentParseResponse>

    // ============================================
    // 7. 用户认证
    // ============================================

    /** POST /api/v1/auth/login */
    suspend fun login(request: LoginRequest): ApiResponse<LoginResponse>

    /** POST /api/v1/auth/refresh */
    suspend fun refreshToken(request: RefreshTokenRequest): ApiResponse<TokenResponse>
}

/**
 * Mock 实现 - 无网络时用于 UI 开发测试
 */
object MockApiService : ApiService {

    override suspend fun uploadGlyphs(request: GlyphBatchUploadRequest) =
        ApiResponse(data = GlyphBatchUploadResponse(batchId = request.batchId, uploadedCount = request.glyphs.size))

    override suspend fun uploadGlyphImage(imageBytes: ByteArray, unicode: String, glyphVersion: Int) =
        ApiResponse(data = mapOf("url" to "https://mock.cdn.handcraft.font/$unicode.png"))

    override suspend fun submitFontGeneration(request: FontGenerationRequest) =
        ApiResponse(data = FontGenerationResponse(taskId = "mock-task-001"))

    override suspend fun getFontTaskStatus(taskId: String) =
        ApiResponse(data = FontTaskStatusResponse(taskId = taskId, status = TaskStatus.COMPLETED, progressPercent = 100))

    override suspend fun cancelFontTask(taskId: String) = ApiResponse(data = Unit)

    override suspend fun correctOcrResults(request: OcrCorrectionRequest) =
        ApiResponse(data = OcrCorrectionResponse(correctedResults = request.ocrResults.map {
            CorrectedResult(it.glyphId, it.ocrText, it.ocrText, it.confidence, isCorrected = false)
        }))

    override suspend fun transcribeSpeech(
        audioBytes: ByteArray, language: String, enablePunctuation: Boolean, removeFillerWords: Boolean
    ) = ApiResponse(data = SpeechTranscriptionResponse(
        transcriptionId = "mock-asr-001",
        text = "Mock 语音转写结果",
        originalText = "Mock 语音转写结果"
    ))

    override suspend fun getLibraryStats() =
        ApiResponse(data = LibraryStatsResponse(totalGlyphs = 128, uniqueChars = 128, libraryLevel = "BRONZE"))

    override suspend fun syncLibrary(request: LibrarySyncRequest) =
        ApiResponse(data = LibrarySyncResponse())

    override suspend fun parseDocument(documentBytes: ByteArray, extractStructure: Boolean) =
        ApiResponse(data = DocumentParseResponse(documentId = "mock-doc-001", fileName = "mock.txt", textContent = ""))

    override suspend fun getDocumentParseStatus(taskId: String) =
        ApiResponse(data = DocumentParseResponse(documentId = taskId, fileName = "", textContent = ""))

    override suspend fun login(request: LoginRequest) =
        ApiResponse(data = LoginResponse(userId = "mock-user-001", token = "mock-jwt-token"))

    override suspend fun refreshToken(request: RefreshTokenRequest) =
        ApiResponse(data = TokenResponse(token = "mock-refreshed-token"))
}
