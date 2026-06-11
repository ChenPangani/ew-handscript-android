/**
 * 文件名: ExportHistoryEntity.kt
 * 负责Agent: Agent-D (Android开发) — 已由 Kimi 总控窗口修复 Room 兼容性问题
 * 所属模块: data/local
 * 最后修改: 2026-06-09
 * 版本: 0.4.2-wiki-fix1
 */

package com.ew.handscript.data.local

import androidx.room.*

/**
 * 导出历史实体 - 记录用户的导出操作
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
    val createdAt: Long = 0L  // 修复：Room 默认值不支持方法调用
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