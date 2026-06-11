/**
 * 文件名: SourceDocumentEntity.kt
 * 负责Agent: Agent-D (Android开发) — 已由 Kimi 总控窗口修复 Room 兼容性问题
 * 所属模块: data/local
 * 最后修改: 2026-06-09
 * 版本: 0.4.2-wiki-fix1
 */

package com.ew.handscript.data.local

import androidx.room.*

/**
 * 源文档实体 - 扫描导入的原始手写稿
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

    /**
     * 检测到的基线位置列表，以逗号分隔的字符串存储
     * 如 "120,240,360" 表示第1行基线120px，第2行240px...
     * Room 不支持 List<Int>，改用 String 存储
     */
    @ColumnInfo(name = "detected_baselines")
    val detectedBaselines: String? = null,  // 修复：List<Int> → String（JSON/逗号分隔）

    @ColumnInfo(name = "processing_status")
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,  // 修复：Room 默认值不支持方法调用

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L     // 修复：Room 默认值不支持方法调用
) {
    /**
     * 将基线字符串解析为 List<Int>
     */
    fun getBaselinesList(): List<Int> {
        return detectedBaselines?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    }

    companion object {
        /**
         * 将 List<Int> 格式化为基线字符串
         */
        fun baselinesToString(baselines: List<Int>): String {
            return baselines.joinToString(",")
        }
    }
}

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