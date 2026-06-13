package com.ew.handscript.ui.screens.proofread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ew.handscript.data.local.GlyphDao
import com.ew.handscript.data.local.GlyphEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 校对页 ViewModel
 */
@HiltViewModel
class ProofreadViewModel @Inject constructor(
    private val glyphDao: GlyphDao
) : ViewModel() {

    /**
     * 保存字形到数据库
     */
    suspend fun saveGlyphToDatabase(
        character: String,
        imagePath: String,
        ocrText: String? = null,
        confidence: Float = 0f
    ): Long? {
        return try {
            val glyph = GlyphEntity(
                unicode = character.ifEmpty { "unknown" },
                character = character,
                ocrText = ocrText,
                confidence = confidence,
                isVerified = true,
                imagePath = imagePath,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val id = glyphDao.insertGlyph(glyph)
            Timber.d("[ProofreadViewModel] 字形入库成功，ID: $id")
            id
        } catch (e: Exception) {
            Timber.e(e, "[ProofreadViewModel] 字形入库失败")
            null
        }
    }
}