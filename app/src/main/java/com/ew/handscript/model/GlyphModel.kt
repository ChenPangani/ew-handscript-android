/**
 * 文件名: GlyphModel.kt
 * 负责Agent: Agent-D (Android开发) — 已由 Kimi 总控窗口修复 Room 兼容性问题
 * 所属模块: model
 * 最后修改: 2026-06-09
 * 版本: 0.4.2-wiki-fix1
 *
 * 功能说明: 字形数据模型 - 字库的核心数据单元
 * 关键约束: 华为Mate30兼容，包体积<<50MB
 *
 * 【修复记录】
 * 修复1: tags: List<String> → String（Room 不支持 List 直接存储，改用 JSON/逗号分隔字符串）
 * 修复2: createdAt/updatedAt 默认值 System.currentTimeMillis() → 0L（Room 默认值只支持常量表达式）
 * 修复3: 新增 getTagsList() / setTagsList() 辅助方法，保持外部接口不变
 */

package com.ew.handscript.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 字形数据模型 - 字库的核心数据单元
 *
 * 每个字形代表一个从用户手写稿中提取的独立汉字图像及其元数据。
 * 以Unicode编码为主键建立索引，支持同一汉字的多种写法存储。
 */
@Entity(
    tableName = "glyphs",
    indices = [
        Index(value = ["unicode"]),
        Index(value = ["character"]),
        Index(value = ["isVerified"]),
        Index(value = ["unicode", "glyph_version"], unique = true)
    ]
)
data class GlyphModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val unicode: String,

    val character: String,

    val glyphVersion: Int = 1,

    val imagePath: String,

    val width: Int,

    val height: Int,

    val baseline: Int,

    val leftBearing: Int = 0,

    val rightBearing: Int = 0,

    val advanceWidth: Int,

    /**
     * 标签字符串，以逗号分隔存储（如 "连笔,行书"）
     * Room 不支持 List<String>，改用 String 存储
     * 使用 getTagsList() / setTagsList() 进行转换
     */
    val tags: String = "",

    val confidence: Float = 0f,

    val ocrText: String? = null,

    val correctedText: String? = null,

    val isVerified: Boolean = false,

    val sourceDocumentId: Long = 0,

    /**
     * 创建时间戳（毫秒）
     * 默认值 0L，实际插入时由 Repository 层填充 System.currentTimeMillis()
     */
    val createdAt: Long = 0L,

    /**
     * 最后更新时间戳（毫秒）
     * 默认值 0L，实际插入时由 Repository 层填充 System.currentTimeMillis()
     */
    val updatedAt: Long = 0L,

    val isUploadedToCloud: Boolean = false,

    val cloudUrl: String? = null
) {
    /**
     * 获取字形唯一标识符，格式："{unicode}_{glyphVersion:02d}"
     */
    fun getGlyphId(): String = "${unicode}_${String.format("%02d", glyphVersion)}"

    /**
     * 获取语义化文件名，用于云端存储
     */
    fun getFileName(): String = "${character}_${String.format("%02d", glyphVersion)}.png"

    /**
     * 检查该字形是否已被校对确认
     */
    fun isConfirmed(): Boolean = isVerified && correctedText == null

    /**
     * 获取最终确认的有效字符
     */
    fun getEffectiveCharacter(): String = correctedText ?: character

    /**
     * 将 tags 字符串解析为 List
     */
    fun getTagsList(): List<String> = if (tags.isEmpty()) emptyList() else tags.split(",").map { it.trim() }

    /**
     * 将 List 格式化为 tags 字符串
     */
    fun setTagsList(tagList: List<String>): String = tagList.joinToString(",")

    companion object {
        // 字库等级阈值
        const val BASIC_GLYPH_COUNT = 500    // 基础版：500字
        const val STANDARD_GLYPH_COUNT = 2000 // 标准版：2000字
        const val COMPLETE_GLYPH_COUNT = 6000 // 完整版：6000字

        fun getLibraryLevel(count: Int): LibraryLevel = when {
            count >= COMPLETE_GLYPH_COUNT -> LibraryLevel.COMPLETE
            count >= STANDARD_GLYPH_COUNT -> LibraryLevel.STANDARD
            count >= BASIC_GLYPH_COUNT -> LibraryLevel.BASIC
            else -> LibraryLevel.STARTER
        }
    }
}

/**
 * 字库等级枚举
 */
enum class LibraryLevel(val displayName: String, val minCount: Int, val description: String) {
    STARTER("入门版", 0, "开始收集你的字迹"),
    BASIC("基础版", GlyphModel.BASIC_GLYPH_COUNT, "满足日常书写需求"),
    STANDARD("标准版", GlyphModel.STANDARD_GLYPH_COUNT, "覆盖常用汉字"),
    COMPLETE("完整版", GlyphModel.COMPLETE_GLYPH_COUNT, "全量汉字覆盖")
}