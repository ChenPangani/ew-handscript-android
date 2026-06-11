package com.ew.handscript.model

import com.ew.handscript.ml.FiveElementValues

/**
 * 字形数据模型（Model 层全局数据类）
 *
 * 跨模块共享：Scan → Proofread → Realm → Repository 全链路使用。
 * 非 UI 层局部 DTO，禁止阉割字段。
 *
 * @property character 汉字字符（原 charValue，与 GlyphEntity.character 统一）
 * @property id 字形唯一标识
 * @property wuXingTag 五行命格标签（如"剑灵命"）
 * @property wuXingValues 五行属性数值（TFLite 异步推理结果）
 * @property isGolden 是否为金字招牌（境界系统标记）
 * @property sourceImagePath 源稿图片本地路径
 * @property lineIndex 所在行索引（排版引擎用）
 * @property charIndex 所在字符索引（排版引擎用）
 * @property timestamp 收录时间戳
 * @property isConfirmed 是否已确认（校对状态）
 * @property isPrint 是否为印刷体（过滤标记）
 */
data class GlyphItem(
    val character: String,
    val id: String,
    val wuXingTag: String = "未知",
    val wuXingValues: FiveElementValues? = null,
    val isGolden: Boolean = false,
    val sourceImagePath: String? = null,
    val lineIndex: Int = -1,
    val charIndex: Int = -1,
    val timestamp: Long = System.currentTimeMillis(),
    val isConfirmed: Boolean = false,
    val isPrint: Boolean = false
)
