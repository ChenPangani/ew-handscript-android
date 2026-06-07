package com.ew.handscript.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 字形数据模型 - 字库的核心数据单元
 *
 * 每个字形代表一个从用户手写稿中提取的独立汉字图像及其元数据。
 * 以Unicode编码为主键建立索引，支持同一汉字的多种写法存储。
 *
 * @property id 本地数据库自增ID
 * @property unicode 字符的Unicode码点 (如 "U+4F60" 表示 "你")
 * @property character 实际汉字字符 (如 "你")
 * @property glyphVersion 该字形的版本号，同一汉字的不同写法按时间戳递增 (如 01, 02...)
 * @property imagePath 字形图片的本地存储路径
 * @property width 字形图像宽度（像素）
 * @property height 字形图像高度（像素）
 * @property baseline 基线位置（像素，从顶部算起）
 * @property leftBearing 左留白（像素）
 * @property rightBearing 右留白（像素）
 * @property advanceWidth 字距宽度（像素）
 * @property tags 标签列表，如 ["连笔", "行书"] 等风格标记
 * @property confidence OCR识别置信度 (0.0 ~ 1.0)
 * @property ocrText OCR识别出的文本（用于校对参考）
 * @property correctedText 用户校对修正后的文本
 * @property isVerified 是否已通过用户校对确认
 * @property sourceDocumentId 来源文档ID（用于溯源）
 * @property createdAt 创建时间戳（毫秒）
 * @property updatedAt 最后更新时间戳（毫秒）
 * @property isUploadedToCloud 是否已上传至云端
 * @property cloudUrl 云端存储URL（上传成功后填充）
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
@Serializable
 data class GlyphModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerialName("unicode")
    val unicode: String,

    @SerialName("character")
    val character: String,

    @SerialName("glyph_version")
    val glyphVersion: Int = 1,

    @SerialName("image_path")
    val imagePath: String,

    @SerialName("width")
    val width: Int,

    @SerialName("height")
    val height: Int,

    @SerialName("baseline")
    val baseline: Int,

    @SerialName("left_bearing")
    val leftBearing: Int = 0,

    @SerialName("right_bearing")
    val rightBearing: Int = 0,

    @SerialName("advance_width")
    val advanceWidth: Int,

    @SerialName("tags")
    val tags: List<String> = emptyList(),

    @SerialName("confidence")
    val confidence: Float = 0f,

    @SerialName("ocr_text")
    val ocrText: String? = null,

    @SerialName("corrected_text")
    val correctedText: String? = null,

    @SerialName("is_verified")
    val isVerified: Boolean = false,

    @SerialName("source_document_id")
    val sourceDocumentId: Long = 0,

    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @SerialName("is_uploaded_to_cloud")
    val isUploadedToCloud: Boolean = false,

    @SerialName("cloud_url")
    val cloudUrl: String? = null
) {
    /**
     * 获取字形唯一标识符，格式："{unicode}_{glyphVersion:02d}"
     * 例如："U+4F60_01" 表示 "你" 的第一种写法
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
