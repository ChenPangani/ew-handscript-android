package com.ew.handscript.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glyphs")
data class GlyphEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val unicode: String = "",
    @ColumnInfo(name = "character")
    val character: String = "",
    @ColumnInfo(name = "ocr_text")
    val ocrText: String? = null,
    @ColumnInfo(name = "corrected_text")
    val correctedText: String? = null,
    val confidence: Float = 0f,
    @ColumnInfo(name = "is_verified")
    val isVerified: Boolean = false,
    @ColumnInfo(name = "glyph_version")
    val glyphVersion: Int = 1,
    @ColumnInfo(name = "source_document_id")
    val sourceDocumentId: Long = 0,
    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,
    @ColumnInfo(name = "is_uploaded_to_cloud")
    val isUploadedToCloud: Boolean = false,
    @ColumnInfo(name = "cloud_url")
    val cloudUrl: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,  // 修复：Room 默认值不支持方法调用
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L   // 修复：Room 默认值不支持方法调用
) {
    fun toModel(): com.ew.handscript.model.GlyphModel {
        return com.ew.handscript.model.GlyphModel(
            id = id,
            unicode = unicode,
            character = character,  // 修复：匹配 GlyphModel 字段名
            glyphVersion = glyphVersion,
            imagePath = imagePath ?: "",
            width = 0,
            height = 0,
            baseline = 0,
            advanceWidth = 0,
            isVerified = isVerified,
            sourceDocumentId = sourceDocumentId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}