package com.ew.handscript.data.local

import androidx.room.*
import com.ew.handscript.model.GlyphModel
import com.ew.handscript.model.LibraryLevel

/**
 * 字形数据库实体 - Room ORM映射
 */
@Entity(
    tableName = "glyphs",
    indices = [
        Index(value = ["unicode"]),
        Index(value = ["character"]),
        Index(value = ["is_verified"]),
        Index(value = ["unicode", "glyph_version"], unique = true),
        Index(value = ["source_document_id"])
    ]
)
 data class GlyphEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "unicode")
    val unicode: String,

    @ColumnInfo(name = "character")
    val character: String,

    @ColumnInfo(name = "glyph_version")
    val glyphVersion: Int = 1,

    @ColumnInfo(name = "image_path")
    val imagePath: String,

    @ColumnInfo(name = "width")
    val width: Int,

    @ColumnInfo(name = "height")
    val height: Int,

    @ColumnInfo(name = "baseline")
    val baseline: Int,

    @ColumnInfo(name = "left_bearing")
    val leftBearing: Int = 0,

    @ColumnInfo(name = "right_bearing")
    val rightBearing: Int = 0,

    @ColumnInfo(name = "advance_width")
    val advanceWidth: Int,

    @ColumnInfo(name = "tags")
    val tags: List<String> = emptyList(),

    @ColumnInfo(name = "confidence")
    val confidence: Float = 0f,

    @ColumnInfo(name = "ocr_text")
    val ocrText: String? = null,

    @ColumnInfo(name = "corrected_text")
    val correctedText: String? = null,

    @ColumnInfo(name = "is_verified")
    val isVerified: Boolean = false,

    @ColumnInfo(name = "source_document_id")
    val sourceDocumentId: Long = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_uploaded_to_cloud")
    val isUploadedToCloud: Boolean = false,

    @ColumnInfo(name = "cloud_url")
    val cloudUrl: String? = null
) {
    /**
     * 转换为领域模型
     */
    fun toModel(): GlyphModel = GlyphModel(
        id = id,
        unicode = unicode,
        character = character,
        glyphVersion = glyphVersion,
        imagePath = imagePath,
        width = width,
        height = height,
        baseline = baseline,
        leftBearing = leftBearing,
        rightBearing = rightBearing,
        advanceWidth = advanceWidth,
        tags = tags,
        confidence = confidence,
        ocrText = ocrText,
        correctedText = correctedText,
        isVerified = isVerified,
        sourceDocumentId = sourceDocumentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isUploadedToCloud = isUploadedToCloud,
        cloudUrl = cloudUrl
    )

    companion object {
        /**
         * 从领域模型创建实体
         */
        fun fromModel(model: GlyphModel): GlyphEntity = GlyphEntity(
            id = model.id,
            unicode = model.unicode,
            character = model.character,
            glyphVersion = model.glyphVersion,
            imagePath = model.imagePath,
            width = model.width,
            height = model.height,
            baseline = model.baseline,
            leftBearing = model.leftBearing,
            rightBearing = model.rightBearing,
            advanceWidth = model.advanceWidth,
            tags = model.tags,
            confidence = model.confidence,
            ocrText = model.ocrText,
            correctedText = model.correctedText,
            isVerified = model.isVerified,
            sourceDocumentId = model.sourceDocumentId,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt,
            isUploadedToCloud = model.isUploadedToCloud,
            cloudUrl = model.cloudUrl
        )
    }
}

/**
 * 字形 DAO - 数据访问对象
 */
@Dao
interface GlyphDao {

    // ===== 插入操作 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlyph(glyph: GlyphEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlyphs(glyphs: List<GlyphEntity>): List<Long>

    // ===== 查询操作 =====

    @Query("SELECT * FROM glyphs WHERE id = :id")
    suspend fun getGlyphById(id: Long): GlyphEntity?

    @Query("SELECT * FROM glyphs WHERE unicode = :unicode ORDER BY glyph_version ASC")
    suspend fun getGlyphsByUnicode(unicode: String): List<GlyphEntity>

    @Query("SELECT * FROM glyphs WHERE character = :char ORDER BY glyph_version ASC")
    suspend fun getGlyphsByCharacter(char: String): List<GlyphEntity>

    /**
     * 获取用户字库分组统计 - 每个Unicode只返回最新版本
     */
    @Query("""
        SELECT g.* FROM glyphs g
        INNER JOIN (
            SELECT unicode, MAX(glyph_version) as max_version
            FROM glyphs
            WHERE is_verified = 1
            GROUP BY unicode
        ) latest ON g.unicode = latest.unicode AND g.glyph_version = latest.max_version
        ORDER BY g.unicode
    """)
    suspend fun getVerifiedUniqueGlyphs(): List<GlyphEntity>

    /**
     * 获取所有已验证字形，按Unicode分组用于动态切换
     */
    @Query("SELECT * FROM glyphs WHERE is_verified = 1 ORDER BY unicode, glyph_version ASC")
    suspend fun getAllVerifiedGlyphsForRendering(): List<GlyphEntity>

    @Query("SELECT COUNT(*) FROM glyphs")
    suspend fun getTotalGlyphCount(): Int

    @Query("SELECT COUNT(DISTINCT unicode) FROM glyphs WHERE is_verified = 1")
    suspend fun getUniqueVerifiedCharCount(): Int

    @Query("SELECT * FROM glyphs WHERE is_verified = 0 ORDER BY created_at DESC")
    suspend fun getUnverifiedGlyphs(): List<GlyphEntity>

    @Query("SELECT * FROM glyphs WHERE source_document_id = :docId ORDER BY id ASC")
    suspend fun getGlyphsByDocument(docId: Long): List<GlyphEntity>

    /**
     * 模糊搜索字形
     */
    @Query("""
        SELECT * FROM glyphs 
        WHERE character LIKE '%' || :query || '%' 
        OR ocr_text LIKE '%' || :query || '%'
        OR corrected_text LIKE '%' || :query || '%'
        ORDER BY confidence DESC
        LIMIT :limit
    """)
    suspend fun searchGlyphs(query: String, limit: Int = 20): List<GlyphEntity>

    // ===== 更新操作 =====

    @Update
    suspend fun updateGlyph(glyph: GlyphEntity): Int

    @Query("UPDATE glyphs SET is_verified = 1, corrected_text = :correctedText, updated_at = :timestamp WHERE id = :id")
    suspend fun verifyGlyph(id: Long, correctedText: String? = null, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE glyphs SET corrected_text = :correctedText, updated_at = :timestamp WHERE id = :id")
    suspend fun correctGlyph(id: Long, correctedText: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE glyphs SET is_uploaded_to_cloud = 1, cloud_url = :cloudUrl WHERE id = :id")
    suspend fun markGlyphUploaded(id: Long, cloudUrl: String): Int

    /**
     * 批量验证字形
     */
    @Query("UPDATE glyphs SET is_verified = 1, updated_at = :timestamp WHERE id IN (:ids)")
    suspend fun batchVerifyGlyphs(ids: List<Long>, timestamp: Long = System.currentTimeMillis()): Int

    // ===== 删除操作 =====

    @Delete
    suspend fun deleteGlyph(glyph: GlyphEntity): Int

    @Query("DELETE FROM glyphs WHERE id = :id")
    suspend fun deleteGlyphById(id: Long): Int

    @Query("DELETE FROM glyphs WHERE unicode = :unicode")
    suspend fun deleteGlyphsByUnicode(unicode: String): Int

    // ===== 统计查询 =====

    @Query("SELECT COUNT(*) FROM glyphs WHERE is_verified = 1")
    fun getVerifiedCountFlow(): kotlinx.coroutines.flow.Flow<Int>

    @Query("""
        SELECT 
            COUNT(*) as total,
            COUNT(CASE WHEN is_verified = 1 THEN 1 END) as verified,
            COUNT(DISTINCT unicode) as unique_chars
        FROM glyphs
    """)
    suspend fun getGlyphStatistics(): GlyphStatistics

    /**
     * 分页获取字形（用于校对界面的九宫格）
     */
    @Query("SELECT * FROM glyphs WHERE is_verified = 0 ORDER BY id ASC LIMIT :pageSize OFFSET :offset")
    suspend fun getUnverifiedGlyphsPaged(pageSize: Int, offset: Int): List<GlyphEntity>
}

/**
 * 字形统计
 */
 data class GlyphStatistics(
    val total: Int,
    val verified: Int,
    val uniqueChars: Int
)

/**
 * 源文档实体
 */
@Entity(tableName = "source_documents")
 data class SourceDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "original_image_path")
    val originalImagePath: String,

    @ColumnInfo(name = "corrected_image_path")
    val correctedImagePath: String? = null,

    @ColumnInfo(name = "page_count")
    val pageCount: Int = 1,

    @ColumnInfo(name = "detected_baselines")
    val detectedBaselines: List<Int>? = null,

    @ColumnInfo(name = "processing_status")
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ProcessingStatus {
    PENDING,           // 待处理
    SCAN_CORRECTING,   // 扫描矫正中
    SEGMENTING,        // 单字切割中
    OCR_RECOGNIZING,   // OCR识别中
    VERIFYING,         // 待校对
    COMPLETED,         // 已完成
    FAILED             // 处理失败
}

@Dao
interface SourceDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: SourceDocumentEntity): Long

    @Query("SELECT * FROM source_documents WHERE id = :id")
    suspend fun getById(id: Long): SourceDocumentEntity?

    @Query("SELECT * FROM source_documents ORDER BY created_at DESC")
    suspend fun getAll(): List<SourceDocumentEntity>

    @Query("UPDATE source_documents SET processing_status = :status, updated_at = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ProcessingStatus, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(document: SourceDocumentEntity)
}

/**
 * 导出历史实体
 */
@Entity(tableName = "export_history")
 data class ExportHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "document_title")
    val documentTitle: String,

    @ColumnInfo(name = "export_type")
    val exportType: String,  // "png", "pdf", "jpg"

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,

    @ColumnInfo(name = "page_count")
    val pageCount: Int = 1,

    @ColumnInfo(name = "font_config_snapshot")
    val fontConfigSnapshot: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ExportHistoryDao {
    @Insert
    suspend fun insert(history: ExportHistoryEntity): Long

    @Query("SELECT * FROM export_history ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<ExportHistoryEntity>

    @Delete
    suspend fun delete(history: ExportHistoryEntity)
}

/**
 * Room类型转换器
 */
class Converters {
    @TypeConverter
    fun fromStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun fromIntList(value: String?): List<Int>? {
        return value?.let {
            if (it.isEmpty()) emptyList() else it.split(",").map { s -> s.toInt() }
        }
    }

    @TypeConverter
    fun toIntList(list: List<Int>?): String? {
        return list?.joinToString(",")
    }

    @TypeConverter
    fun fromProcessingStatus(status: ProcessingStatus): String = status.name

    @TypeConverter
    fun toProcessingStatus(value: String): ProcessingStatus =
        ProcessingStatus.valueOf(value)
}
