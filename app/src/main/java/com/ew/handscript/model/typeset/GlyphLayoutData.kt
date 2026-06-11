/**
 * 文件名: GlyphLayoutData.kt
 * 负责Agent: Agent-D (Android开发)
 * 所属模块: model/typeset
 * 最后修改: 2026-06-09
 * 版本: 0.4.2-wiki
 *
 * 功能说明: GlyphLayoutData功能实现
 * 关键约束: 华为Mate30兼容，包体积<50MB
 */

package com.ew.handscript.model.typeset

/**
 * 排版数据模型 - 逐字渲染的核心数据结构
 *
 * 该模型描述了文档中每个字符的精确排版信息，包含字符本身、
 * 对应的字形ID、在画布上的精确位置偏移以及旋转角度。
 * 此数据结构是 Canvas 逐字渲染引擎的输入，支持单字级别的独立变换。
 *
 * 字段设计约束说明：
 * - offset_x, offset_y: 相对于基准网格位置的微调偏移（单位：像素）
 *   用于实现自然手写的位置浮动效果，避免机械排列
 * - rotation: 极小角度的倾斜旋转（单位：度，范围 -3° ~ +3°）
 *   模拟真实手写时每个字的微小倾斜差异
 * - scale: 尺寸缩放因子（范围 0.95 ~ 1.05）
 *   实现 ±5% 的字号微调，打破完全一致的视觉重复感
 *
 * @property char 原始字符（如 "你"）
 * @property unicode 字符的Unicode码点（如 "U+4F60"）
 * @property glyphId 字形唯一标识符（如 "U+4F60_01" 或 "U+4F60_02"）
 * @property glyphImagePath 字形图片的本地路径（用于Canvas绘制）
 * @property offsetX 相对于基准X坐标的水平偏移量（像素，含随机扰动）
 * @property offsetY 相对于基准Y坐标的垂直偏移量（像素，含随机扰动）
 * @property rotation 旋转角度（度），范围 [-3, 3]
 * @property scale 尺寸缩放因子，范围 [0.95, 1.05]
 * @property baseX 基准X坐标（像素，由排版引擎根据行/列计算）
 * @property baseY 基准Y坐标（像素，由排版引擎根据行/列计算）
 * @property finalX 最终绘制X坐标 = baseX + offsetX
 * @property finalY 最终绘制Y坐标 = baseY + offsetY
 * @property glyphWidth 字形实际绘制宽度 = width * scale
 * @property glyphHeight 字形实际绘制高度 = height * scale
 * @property isFallback 是否为后备字体（该字未收录时使用开源手写体兜底）
 * @property lineIndex 所在行索引（从0开始）
 * @property charIndex 所在字符索引（从0开始）
 * @property paragraphIndex 所在段落索引（从0开始）
 */

 data class GlyphLayoutData(

    val char: String,

    val unicode: String,

    val glyphId: String,

    val glyphImagePath: String,

    val offsetX: Float = 0f,

    val offsetY: Float = 0f,

    val rotation: Float = 0f,

    val scale: Float = 1f,

    val baseX: Float = 0f,

    val baseY: Float = 0f,

    val glyphWidth: Float,

    val glyphHeight: Float,

    val isFallback: Boolean = false,

    val lineIndex: Int = 0,

    val charIndex: Int = 0,

    val paragraphIndex: Int = 0
) {
    /**
     * 计算最终绘制X坐标
     */
    fun computeFinalX(): Float = baseX + offsetX

    /**
     * 计算最终绘制Y坐标
     */
    fun computeFinalY(): Float = baseY + offsetY

    /**
     * 计算变换后的绘制矩形（用于碰撞检测和区域计算）
     */
    fun computeBounds(): android.graphics.RectF {
        val cx = computeFinalX() + glyphWidth / 2
        val cy = computeFinalY() + glyphHeight / 2
        val rad = Math.toRadians(rotation.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()

        // 四个角点绕中心旋转
        val halfW = glyphWidth / 2
        val halfH = glyphHeight / 2

        val corners = listOf(
            floatArrayOf(-halfW, -halfH),
            floatArrayOf(halfW, -halfH),
            floatArrayOf(halfW, halfH),
            floatArrayOf(-halfW, halfH)
        )

        val rotatedCorners = corners.map { (x, y) ->
            floatArrayOf(
                cx + (x * cos - y * sin),
                cy + (x * sin + y * cos)
            )
        }

        val minX = rotatedCorners.minOf { it[0] }
        val maxX = rotatedCorners.maxOf { it[0] }
        val minY = rotatedCorners.minOf { it[1] }
        val maxY = rotatedCorners.maxOf { it[1] }

        return android.graphics.RectF(minX, minY, maxX, maxY)
    }

    companion object {
        /**
         * 随机扰动参数常量 - 控制自然手写效果的程度
         */
        // 位置浮动范围（像素）
        const val PERTURBATION_OFFSET_X = 2.5f      // X轴最大浮动 ±2.5px
        const val PERTURBATION_OFFSET_Y = 1.5f      // Y轴最大浮动 ±1.5px

        // 旋转角度范围（度）
        const val PERTURBATION_ROTATION_MIN = -3f   // 最小旋转角度
        const val PERTURBATION_ROTATION_MAX = 3f    // 最大旋转角度

        // 尺寸缩放范围
        const val PERTURBATION_SCALE_MIN = 0.95f    // 最小缩放 95%
        const val PERTURBATION_SCALE_MAX = 1.05f    // 最大缩放 105%

        // 行间距基准值（像素，基于字号72px）
        const val BASE_LINE_SPACING_72PX = 96f

        /**
         * 生成随机扰动参数 - 用于模拟自然手写的不规则感
         *
         * @param seed 随机种子（基于字符位置和文档ID生成，保证同一文档每次渲染结果一致）
         * @return 包含随机offsetX, offsetY, rotation, scale的扰动参数
         */
        fun generateRandomPerturbation(seed: Long): PerturbationParams {
            val random = java.util.Random(seed)
            return PerturbationParams(
                offsetX = (random.nextFloat() * 2 - 1) * PERTURBATION_OFFSET_X,
                offsetY = (random.nextFloat() * 2 - 1) * PERTURBATION_OFFSET_Y,
                rotation = PERTURBATION_ROTATION_MIN +
                        random.nextFloat() * (PERTURBATION_ROTATION_MAX - PERTURBATION_ROTATION_MIN),
                scale = PERTURBATION_SCALE_MIN +
                        random.nextFloat() * (PERTURBATION_SCALE_MAX - PERTURBATION_SCALE_MIN)
            )
        }
    }
}

/**
 * 扰动参数数据类 - 封装单个字形的随机变换参数
 */

 data class PerturbationParams(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val scale: Float = 1f
)

/**
 * 排版行数据 - 描述一行的排版信息
 *
 * @property lineIndex 行索引
 * @property paragraphIndex 所属段落索引
 * @property baseY 行的基准Y坐标
 * @property glyphs 该行包含的所有字形排版数据
 * @property lineHeight 行高（像素）
 * @property actualWidth 实际内容宽度（像素）
 */

 data class LineLayoutData(

    val lineIndex: Int,

    val paragraphIndex: Int,

    val baseY: Float,

    val glyphs: List<GlyphLayoutData>,

    val lineHeight: Float,

    val actualWidth: Float
)

/**
 * 排版页面数据 - 描述一整页的排版结果
 *
 * @property pageIndex 页码（从0开始）
 * @property lines 页面包含的所有行
 * @property pageWidth 页面宽度（像素）
 * @property pageHeight 页面高度（像素）
 * @property marginLeft 左边距（像素）
 * @property marginRight 右边距（像素）
 * @property marginTop 上边距（像素）
 * @property marginBottom 下边距（像素）
 */

 data class PageLayoutData(

    val pageIndex: Int,

    val lines: List<LineLayoutData>,

    val pageWidth: Float,

    val pageHeight: Float,

    val marginLeft: Float,

    val marginRight: Float,

    val marginTop: Float,

    val marginBottom: Float
)

/**
 * 完整文档排版结果
 *
 * @property pages 所有页面的排版数据
 * @property totalChars 总字符数
 * @property totalFallbackChars 使用后备字体的字符数
 * @property fontConfig 排版配置参数
 */

 data class DocumentLayoutResult(

    val pages: List<PageLayoutData>,

    val totalChars: Int,

    val totalFallbackChars: Int,

    val fontConfig: FontConfig
)

/**
 * 排版配置参数 - 用户可调节的排版选项
 *
 * @property paperTemplate 信纸模板类型
 * @property fontSizePx 字号（像素）
 * @property lineSpacingPx 行间距（像素）
 * @property letterSpacingPx 字间距（像素）
 * @property marginLeftPx 左边距（像素）
 * @property marginRightPx 右边距（像素）
 * @property marginTopPx 上边距（像素）
 * @property marginBottomPx 下边距（像素）
 * @property inkColor 墨水颜色（ARGB格式）
 * @property inkThickness 墨水粗细（1.0为基准）
 * @property enablePerturbation 是否启用随机扰动
 * @property enableDynamicGlyph 是否启用动态字形切换（同一字使用不同写法）
 * @property enableScanFilter 是否启用扫描滤镜
 */

 data class FontConfig(

    val paperTemplate: PaperTemplate = PaperTemplate.PLAIN_WHITE,

    val fontSizePx: Float = 72f,

    val lineSpacingPx: Float = 96f,

    val letterSpacingPx: Float = 2f,

    val marginLeftPx: Float = 60f,

    val marginRightPx: Float = 60f,

    val marginTopPx: Float = 80f,

    val marginBottomPx: Float = 80f,

    val inkColor: Long = 0xFF1A1A2E, // 默认深蓝黑色

    val inkThickness: Float = 1.0f,

    val enablePerturbation: Boolean = true,

    val enableDynamicGlyph: Boolean = true,

    val enableScanFilter: Boolean = false
) {
    companion object {
        // 预设配置
        val DEFAULT = FontConfig()
        val COMPACT = FontConfig(
            fontSizePx = 56f,
            lineSpacingPx = 72f,
            letterSpacingPx = 1f,
            marginLeftPx = 40f,
            marginRightPx = 40f,
            marginTopPx = 50f,
            marginBottomPx = 50f
        )
        val COMFORTABLE = FontConfig(
            fontSizePx = 80f,
            lineSpacingPx = 112f,
            letterSpacingPx = 3f,
            marginLeftPx = 80f,
            marginRightPx = 80f,
            marginTopPx = 100f,
            marginBottomPx = 100f
        )
    }
}

/**
 * 信纸模板枚举
 */
enum class PaperTemplate(
    val displayName: String,
    val backgroundColor: Long,
    val hasRuledLines: Boolean = false,
    val hasGrid: Boolean = false,
    val textureResId: String? = null
) {
    PLAIN_WHITE("纯白纸张", 0xFFFFFFFF),
    RULED_LINE("横线信纸", 0xFFFAFAFA, hasRuledLines = true),
    GRID_SQUARE("方格稿纸", 0xFFFAFAFA, hasGrid = true),
    KRAFT_PAPER("牛皮纸张", 0xFFD4A574),
    AGED_PAPER("泛黄旧纸", 0xFFF5E6C8),
    MINT_GREEN("薄荷绿纸", 0xFFE8F5E9),
    CUSTOM("自定义", 0xFFFFFFFF)
}
