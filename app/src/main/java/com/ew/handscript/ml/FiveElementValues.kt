package com.ew.handscript.ml

/**
 * 五行数值数据类
 *
 * 纯 Kotlin 数据类，零外部注解：
 * - 无 @Serializable（质量门禁 §8.6）
 * - 无 @Entity / @Dao（质量门禁 §8.6）
 *
 * 字段范围：[0, 100]，0=该五行极弱，100=该五行极强
 * 与 Python 算法模块 K-Means 聚类输出一致
 */
data class FiveElementValues(
    val wood: Float = 0f,    // 木 - 流畅舒展度
    val fire: Float = 0f,    // 火 - 笔压强烈度
    val earth: Float = 0f,   // 土 - 方正稳定度
    val metal: Float = 0f,   // 金 - 棱角锐利度
    val water: Float = 0f    // 水 - 连贯圆润度
) {
    /**
     * 返回主导五行属性名（得分最高者）
     */
    fun dominant(): String {
        val map = mapOf(
            "wood" to wood,
            "fire" to fire,
            "earth" to earth,
            "metal" to metal,
            "water" to water
        )
        return map.maxByOrNull { it.value }?.key ?: "earth"
    }

    /**
     * 返回次要五行属性名（第二高分且 > 主导分 × 0.7）
     */
    fun secondary(): String? {
        val sorted = listOf(
            "wood" to wood, "fire" to fire, "earth" to earth,
            "metal" to metal, "water" to water
        ).sortedByDescending { it.second }
        val sec = sorted.getOrNull(1) ?: return null
        val dom = sorted[0]
        return if (sec.second > dom.second * 0.7f) sec.first else null
    }

    /**
     * 是否金字招牌（任一项 ≥ 80）
     */
    fun isGolden(): Boolean = listOf(wood, fire, earth, metal, water).any { it >= 80f }

    /**
     * 中文主导五行名
     */
    fun dominantChinese(): String = dominant().toChinese()

    /**
     * 中文次要五行名
     */
    fun secondaryChinese(): String? = secondary()?.toChinese()

    /**
     * 转换为百分比 Map（Agent-D UI 消费）
     */
    fun toPercentMap(): Map<String, Float> = mapOf(
        "wood" to wood.coerceIn(0f, 100f),
        "fire" to fire.coerceIn(0f, 100f),
        "earth" to earth.coerceIn(0f, 100f),
        "metal" to metal.coerceIn(0f, 100f),
        "water" to water.coerceIn(0f, 100f)
    )

    /**
     * 五行均衡度（标准差，越小越均衡）
     */
    fun balanceStd(): Float {
        val values = floatArrayOf(wood, fire, earth, metal, water)
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return kotlin.math.sqrt(variance)
    }

    companion object {
        /** 从 FloatArray [wood, fire, earth, metal, water] 创建 */
        fun fromArray(values: FloatArray): FiveElementValues {
            require(values.size >= 5)
            return FiveElementValues(
                wood = values[0].coerceIn(0f, 100f),
                fire = values[1].coerceIn(0f, 100f),
                earth = values[2].coerceIn(0f, 100f),
                metal = values[3].coerceIn(0f, 100f),
                water = values[4].coerceIn(0f, 100f)
            )
        }

        /** 全零实例（占位） */
        fun zero(): FiveElementValues = FiveElementValues()
    }
}

/** 五行英文名 → 中文 */
internal fun String.toChinese(): String = when (this) {
    "wood" -> "木"
    "fire" -> "火"
    "earth" -> "土"
    "metal" -> "金"
    "water" -> "水"
    else -> "土"
}
