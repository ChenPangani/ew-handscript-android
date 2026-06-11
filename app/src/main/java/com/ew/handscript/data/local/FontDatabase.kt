/**
 * 文件名: FontDatabase.kt
 * 负责Agent: Agent-D (Android开发)
 * 所属模块: data/local
 * 最后修改: 2026-06-09
 * 版本: 0.4.2-wiki
 * 
 * 功能说明: FontDatabase功能实现
 * 关键约束: 华为Mate30兼容，包体积<50MB
 */

package com.ew.handscript.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库 - 蚯蚓手书修仙传
 *
 * 数据库版本历史：
 * - v1: 初始版本，包含glyphs表
 * - v2: 添加documents表、export_history表
 */
@Database(
    entities = [
        GlyphEntity::class,
        SourceDocumentEntity::class,
        ExportHistoryEntity::class
    ],
    version = 2,
    exportSchema = false  // 不导出schema文件，消除编译警告
)
@TypeConverters(Converters::class)
abstract class FontDatabase : RoomDatabase() {

    abstract fun glyphDao(): GlyphDao
    abstract fun sourceDocumentDao(): SourceDocumentDao
    abstract fun exportHistoryDao(): ExportHistoryDao

    companion object {
        const val DATABASE_NAME = "ew_handscript.db"

        // 数据库迁移: v1 -> v2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建源文档表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS source_documents (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        original_image_path TEXT NOT NULL,
                        corrected_image_path TEXT,
                        page_count INTEGER NOT NULL DEFAULT 1,
                        detected_baselines TEXT,
                        processing_status TEXT NOT NULL DEFAULT 'PENDING',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """)

                // 创建导出历史表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS export_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        document_title TEXT NOT NULL,
                        export_type TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        file_size_bytes INTEGER NOT NULL,
                        page_count INTEGER NOT NULL DEFAULT 1,
                        font_config_snapshot TEXT,
                        created_at INTEGER NOT NULL
                    )
                """)
            }
        }
    }
}
