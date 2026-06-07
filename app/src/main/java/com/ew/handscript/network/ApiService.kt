package com.ew.handscript.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * HandCraft Font API 服务接口
 *
 * 使用Retrofit + Kotlin Serialization实现HTTP通信
 * 所有接口统一返回ApiResponse包装的标准响应
 */
interface ApiService {

    // ============================================
    // 字形上传接口
    // ============================================

    /**
     * 批量上传字形数据
     * POST /api/v1/glyphs/batch-upload
     */
    @POST("glyphs/batch-upload")
    suspend fun uploadGlyphs(
        @Body request: GlyphBatchUploadRequest
    ): Response<ApiResponse<GlyphBatchUploadResponse>>

    /**
     * 上传字形图片文件（二进制）
     * POST /api/v1/glyphs/upload-image
     */
    @Multipart
    @POST("glyphs/upload-image")
    suspend fun uploadGlyphImage(
        @Part image: MultipartBody.Part,
        @Part("unicode") unicode: RequestBody,
        @Part("glyph_version") glyphVersion: RequestBody
    ): Response<ApiResponse<Map<String, String>>>  // 返回 { "url": "..." }

    // ============================================
    // 字体生成接口
    // ============================================

    /**
     * 提交字体生成任务
     * POST /api/v1/fonts/generate
     */
    @POST("fonts/generate")
    suspend fun submitFontGeneration(
        @Body request: FontGenerationRequest
    ): Response<ApiResponse<FontGenerationResponse>>

    /**
     * 查询字体生成任务状态
     * GET /api/v1/fonts/tasks/{task_id}
     */
    @GET("fonts/tasks/{task_id}")
    suspend fun getFontTaskStatus(
        @Path("task_id") taskId: String
    ): Response<ApiResponse<FontTaskStatusResponse>>

    /**
     * 取消字体生成任务
     * POST /api/v1/fonts/tasks/{task_id}/cancel
     */
    @POST("fonts/tasks/{task_id}/cancel")
    suspend fun cancelFontTask(
        @Path("task_id") taskId: String
    ): Response<ApiResponse<Unit>>

    // ============================================
    // OCR 纠错接口
    // ============================================

    /**
     * OCR语义纠错
     * POST /api/v1/ocr/correct
     */
    @POST("ocr/correct")
    suspend fun correctOcrResults(
        @Body request: OcrCorrectionRequest
    ): Response<ApiResponse<OcrCorrectionResponse>>

    // ============================================
    // 语音识别接口
    // ============================================

    /**
     * 语音转写
     * POST /api/v1/speech/transcribe
     */
    @Multipart
    @POST("speech/transcribe")
    suspend fun transcribeSpeech(
        @Part audioFile: MultipartBody.Part,
        @Part("language") language: RequestBody = MultipartBody.Part.createFormData("language", "zh-CN").body,
        @Part("enable_punctuation") enablePunctuation: RequestBody = MultipartBody.Part.createFormData("enable_punctuation", "true").body,
        @Part("remove_filler_words") removeFillerWords: RequestBody = MultipartBody.Part.createFormData("remove_filler_words", "true").body
    ): Response<ApiResponse<SpeechTranscriptionResponse>>

    // ============================================
    // 字库管理接口
    // ============================================

    /**
     * 获取字库统计
     * GET /api/v1/library/stats
     */
    @GET("library/stats")
    suspend fun getLibraryStats(): Response<ApiResponse<LibraryStatsResponse>>

    /**
     * 同步字库数据
     * POST /api/v1/library/sync
     */
    @POST("library/sync")
    suspend fun syncLibrary(
        @Body request: LibrarySyncRequest
    ): Response<ApiResponse<LibrarySyncResponse>>

    // ============================================
    // 文档解析接口
    // ============================================

    /**
     * 解析文档
     * POST /api/v1/documents/parse
     */
    @Multipart
    @POST("documents/parse")
    suspend fun parseDocument(
        @Part document: MultipartBody.Part,
        @Part("extract_structure") extractStructure: RequestBody = MultipartBody.Part.createFormData("extract_structure", "true").body
    ): Response<ApiResponse<DocumentParseResponse>>

    /**
     * 获取解析任务状态
     * GET /api/v1/documents/tasks/{task_id}
     */
    @GET("documents/tasks/{task_id}")
    suspend fun getDocumentParseStatus(
        @Path("task_id") taskId: String
    ): Response<ApiResponse<DocumentParseResponse>>

    // ============================================
    // 用户相关接口
    // ============================================

    /**
     * 用户登录/注册
     * POST /api/v1/auth/login
     */
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<LoginResponse>>

    /**
     * 刷新Token
     * POST /api/v1/auth/refresh
     */
    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<ApiResponse<TokenResponse>>
}

/**
 * 字库同步请求
 */
@kotlinx.serialization.Serializable
 data class LibrarySyncRequest(
    val lastSyncTimestamp: Long? = null,
    val localGlyphIds: List<String> = emptyList()
)

/**
 * 字库同步响应
 */
@kotlinx.serialization.Serializable
 data class LibrarySyncResponse(
    val syncTimestamp: Long,
    val addedGlyphs: List<com.ew.handscript.model.GlyphModel>,
    val updatedGlyphs: List<com.ew.handscript.model.GlyphModel>,
    val deletedGlyphIds: List<String>,
    val serverStats: LibraryStatsResponse
)

/**
 * 登录请求
 */
@kotlinx.serialization.Serializable
 data class LoginRequest(
    val deviceId: String,
    val platform: String = "android",
    val appVersion: String
)

/**
 * 登录响应
 */
@kotlinx.serialization.Serializable
 data class LoginResponse(
    val userId: String,
    val token: String,
    val refreshToken: String,
    val expiresIn: Long,
    val isNewUser: Boolean
)

/**
 * Token刷新请求
 */
@kotlinx.serialization.Serializable
 data class RefreshTokenRequest(
    val refreshToken: String
)

/**
 * Token响应
 */
@kotlinx.serialization.Serializable
 data class TokenResponse(
    val token: String,
    val expiresIn: Long
)
