package com.ew.handscript.ml

import androidx.compose.ui.graphics.Color

/**
 * 五行属性推理结果
 *
 * @property metal 金属性值 [0,1]
 * @property wood 木属性值 [0,1]
 * @property water 水属性值 [0,1]
 * @property fire 火属性值 [0,1]
 * @property earth 土属性值 [0,1]
 * @property destinyTag 命格标签（由最强属性决定）
 */
data class WuXingResult(
    val metal: Float = 0f,
    val wood: Float = 0f,
    val water: Float = 0f,
    val fire: Float = 0f,
    val earth: Float = 0f,
    val destinyTag: String = "未知"
) {
    /** 转换为五行数据对象（供UI显示） */
    fun toElements(): FiveElementValues = FiveElementValues(
        metal = (metal * 100).toInt(),
        wood = (wood * 100).toInt(),
        water = (water * 100).toInt(),
        fire = (fire * 100).toInt(),
        earth = (earth * 100).toInt()
    )

    companion object {
        /** 命格标签映射 */
        val DESTINY_TAGS = mapOf(
            "金" to "剑灵命",
            "木" to "青帝命",
            "水" to "玄冥命",
            "火" to "炎帝命",
            "土" to "后土命"
        )

        /** 五行颜色映射 */
        val WUXING_COLORS = mapOf(
            "金" to Color(0xFFFFC107),
            "木" to Color(0xFF4CAF50),
            "水" to Color(0xFF2196F3),
            "火" to Color(0xFFF44336),
            "土" to Color(0xFF795548)
        )
    }
}

/**
 * 五行数值（UI展示用，0-100整数）
 */
data class FiveElementValues(
    val metal: Int = 0,
    val wood: Int = 0,
    val water: Int = 0,
    val fire: Int = 0,
    val earth: Int = 0
)

/**
 * 印刷体过滤结果
 *
 * @property isHandwritten true=手写体通过，false=印刷体拒绝
 * @property confidence 置信度 [0,1]
 */
data class PrintFilterResult(
    val isHandwritten: Boolean = true,
    val confidence: Float = 1f
)
