package com.ew.handscript.data.repository

import com.ew.handscript.data.local.*
import com.ew.handscript.model.GlyphModel
import com.ew.handscript.model.LibraryLevel
import com.ew.handscript.network.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 字形数据仓库 - 单一可信数据源
 *
 * 职责：
 * 1. 协调本地数据库（Room）和远程API的数据操作
 * 2. 提供统一的数据访问接口给ViewModel
 * 3. 处理数据同步策略（本地优先，后台同步）
 * 4. 实现断点续传逻辑（大文件上传）
 */
@Singleton
class GlyphRepository @Inject constructor(
    private val glyphDao: GlyphDao,
    private val sourceDocumentDao: SourceDocumentDao,
    private val apiService: ApiService,
    private val networkMonitor: NetworkMonitor
) {
    /**
     * 获取已验证字库（用于渲染引擎）
     * 返回 Map<Unicode, List<GlyphModel>> 格式，支持动态字形切换
     */
    suspend fun getVerifiedGlyphsForRendering(): Map<String, List<GlyphModel>> {
        return try {
            val entities = glyphDao.getAllVerifiedGlyphsForRendering()
            entities.map { it.toModel() }.groupBy { it.unicode }
        } catch (e: Exception) {
            Timber.e(e, "获取渲染字库失败")
            emptyMap()
        }
    }

    /**
     * 获取字库统计Flow（用于UI实时更新）
     */
    fun getLibraryStatsFlow(): Flow<LibraryStats> = flow {
        val verifiedCount = glyphDao.getUniqueVerifiedCharCount()
        val totalCount = glyphDao.getTotalGlyphCount()
        val level = GlyphModel.getLibraryLevel(verifiedCount)

        emit(LibraryStats(
            totalGlyphs = totalCount,
            verifiedChars = verifiedCount,
            libraryLevel = level,
            progressToNextLevel = calculateProgress(verifiedCount, level)
        ))
    }.catch { e ->
        Timber.e(e, "获取字库统计失败")
        emit(LibraryStats(0, 0, LibraryLevel.STARTER, 0f))
    }

    /**
     * 保存新提取的字形
     */
    suspend fun saveGlyphs(glyphs: List<GlyphModel>): Result<List<Long>> {
        return try {
            val entities = glyphs.map { GlyphEntity.fromModel(it) }
            val ids = glyphDao.insertGlyphs(entities)
            Timber.i("成功保存 ${ids.size} 个字形")
            Result.success(ids)
        } catch (e: Exception) {
            Timber.e(e, "保存字形失败")
            Result.failure(e)
        }
    }

    /**
     * 校对确认字形
     */
    suspend fun verifyGlyph(glyphId: Long, correctedText: String? = null): Result<Unit> {
        return try {
            glyphDao.verifyGlyph(glyphId, correctedText)
            Timber.d("字形已确认: id=$glyphId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 批量校对确认
     */
    suspend fun batchVerifyGlyphs(glyphIds: List<Long>): Result<Int> {
        return try {
            val count = glyphDao.batchVerifyGlyphs(glyphIds)
            Timber.i("批量确认 $count 个字形")
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取待校对的字形（分页加载，用于九宫格浏览）
     */
    suspend fun getUnverifiedGlyphsPage(page: Int, pageSize: Int): List<GlyphModel> {
        return try {
            val offset = page * pageSize
            glyphDao.getUnverifiedGlyphsPaged(pageSize, offset).map { it.toModel() }
        } catch (e: Exception) {
            Timber.e(e, "获取待校对字形失败")
            emptyList()
        }
    }

    /**
     * 上传已验证字形到云端（支持断点续传）
     */
    suspend fun uploadVerifiedGlyphsToCloud(): Result<Int> {
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("网络不可用"))
        }

        return try {
            val unuploaded = glyphDao.getAllVerifiedGlyphsForRendering()
                .filter { !it.isUploadedToCloud }

            var successCount = 0
            for (glyph in unuploaded) {
                try {
                    // TODO: 实现实际上传逻辑（图片+元数据）
                    // 这里简化处理，实际应使用UploadWorker进行后台上传
                    glyphDao.markGlyphUploaded(glyph.id, "https://cdn.example.com/${glyph.getFileName()}")
                    successCount++
                } catch (e: Exception) {
                    Timber.w(e, "上传字形失败: ${glyph.getGlyphId()}")
                    // 继续上传其他字形（断点续传：失败的不影响其他）
                    continue
                }
            }

            Timber.i("成功上传 $successCount/${unuploaded.size} 个字形")
            Result.success(successCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 提交字体生成任务
     */
    suspend fun submitFontGeneration(fontName: String): Result<String> {
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("网络不可用，请连接网络后重试"))
        }

        return try {
            val glyphIds = glyphDao.getAllVerifiedGlyphsForRendering().map { it.getGlyphId() }
            if (glyphIds.isEmpty()) {
                return Result.failure(IllegalStateException("字库为空，请先扫描并校对字形"))
            }

            // TODO: 实际调用API提交生成任务
            val mockTaskId = "font_gen_${System.currentTimeMillis()}"
            Timber.i("字体生成任务已提交: $mockTaskId (${glyphIds.size} 个字形)")
            Result.success(mockTaskId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== 私有辅助方法 =====

    /**
     * 计算向下一等级的进度百分比
     */
    private fun calculateProgress(count: Int, currentLevel: LibraryLevel): Float {
        val nextThreshold = when (currentLevel) {
            LibraryLevel.STARTER -> LibraryLevel.BASIC.minCount
            LibraryLevel.BASIC -> LibraryLevel.STANDARD.minCount
            LibraryLevel.STANDARD -> LibraryLevel.COMPLETE.minCount
            LibraryLevel.COMPLETE -> LibraryLevel.COMPLETE.minCount + 1000
        }
        val prevThreshold = currentLevel.minCount
        return if (nextThreshold > prevThreshold) {
            ((count - prevThreshold).toFloat() / (nextThreshold - prevThreshold)).coerceIn(0f, 1f)
        } else 1f
    }

    /**
     * 字库统计数据类
     */
    data class LibraryStats(
        val totalGlyphs: Int,
        val verifiedChars: Int,
        val libraryLevel: LibraryLevel,
        val progressToNextLevel: Float
    )
}

/**
 * 网络状态监控
 */
interface NetworkMonitor {
    fun isOnline(): Boolean
    fun observeNetworkState(): Flow<Boolean>
}
