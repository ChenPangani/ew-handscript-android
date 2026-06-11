/**
 * 文件名: GlyphDao.kt
 * 负责Agent: Agent-D (Android开发)
 * 所属模块: data/local
 * 最后修改: 2026-06-09
 * 版本: 0.4.2-wiki
 * 
 * 功能说明: GlyphDao功能实现
 * 关键约束: 华为Mate30兼容，包体积<50MB
 */

package com.ew.handscript.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 字形 DAO - 数据访问对象
 *
 * 注意：所有参数名避免使用Java关键字（如char），改用ch或character。
 * Room生成Java代码时，参数名会直接出现在生成的Java方法中，
 * 使用char会导致编译错误（char是Java基本类型关键字）。
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

    /**
     * 按汉字字符查询（参数名用ch，避免Java关键字char）
     */
    @Query("SELECT * FROM glyphs WHERE character = :ch ORDER BY glyph_version ASC")
    suspend fun getGlyphsByCharacter(ch: String): List<GlyphEntity>

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
    fun getVerifiedCountFlow(): Flow<Int>

    /**
     * 字形统计（unique_chars需加@ColumnInfo映射）
     */
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
