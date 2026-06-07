package com.ew.handscript.core.render

import com.ew.handscript.model.GlyphModel
import com.ew.handscript.model.typeset.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 排版计算引擎单元测试
 *
 * 测试覆盖：
 * 1. 基础排版计算
 * 2. 随机扰动参数生成与一致性
 * 3. 动态字形切换
 * 4. 分页逻辑
 * 5. 后备字体处理
 * 6. 复杂文本场景
 */
class LayoutComputationEngineTest {

    private lateinit var layoutEngine: LayoutComputationEngine
    private lateinit var renderEngine: HandwritingRenderEngine

    @Before
    fun setUp() {
        layoutEngine = LayoutComputationEngine()
    }

    // ============================================
    // 1. 基础排版计算测试
    // ============================================

    @Test
    fun `computeDocumentLayout_空文本_返回空页面`() = runBlocking {
        val result = layoutEngine.computeDocumentLayout(
            textContent = "",
            fontConfig = FontConfig.DEFAULT,
            userGlyphs = emptyMap(),
            pageWidth = 2480f,
            pageHeight = 3508f
        )

        assertEquals("空文本应生成0页", 0, result.pages.size)
        assertEquals("总字符数应为0", 0, result.totalChars)
    }

    @Test
    fun `computeDocumentLayout_单行文本_正确排版`() = runBlocking {
        val result = layoutEngine.computeDocumentLayout(
            textContent = "你好世界",
            fontConfig = FontConfig.DEFAULT,
            userGlyphs = createTestGlyphMap(listOf("你", "好", "世", "界")),
            pageWidth = 2480f,
            pageHeight = 3508f
        )

        assertTrue("应至少生成1页", result.pages.size >= 1)
        assertEquals("总字符数应为4", 4, result.totalChars)
        assertEquals("后备字应为0", 0, result.totalFallbackChars)

        // 验证所有字都在第一页
        val firstPage = result.pages.first()
        assertTrue("第一页应有内容", firstPage.lines.isNotEmpty())
    }

    @Test
    fun `computeDocumentLayout_未收录字_标记为后备`() = runBlocking {
        // 不传入字库，所有字都应标记为后备
        val result = layoutEngine.computeDocumentLayout(
            textContent = "测试文本",
            fontConfig = FontConfig.DEFAULT,
            userGlyphs = emptyMap(),
            pageWidth = 2480f,
            pageHeight = 3508f
        )

        assertEquals("所有字应为后备字", result.totalChars, result.totalFallbackChars)

        // 验证每个glyph的isFallback标志
        val allGlyphs = result.pages.flatMap { it.lines }.flatMap { it.glyphs }
        assertTrue("所有glyph应标记为后备", allGlyphs.all { it.isFallback })
    }

    // ============================================
    // 2. 随机扰动参数测试
    // ============================================

    @Test
    fun `generateRandomPerturbation_相同种子_结果一致`() {
        val seed = 12345L
        val p1 = GlyphLayoutData.generateRandomPerturbation(seed)
        val p2 = GlyphLayoutData.generateRandomPerturbation(seed)

        assertEquals("相同种子应产生相同offsetX", p1.offsetX, p2.offsetX, 0.001f)
        assertEquals("相同种子应产生相同offsetY", p1.offsetY, p2.offsetY, 0.001f)
        assertEquals("相同种子应产生相同rotation", p1.rotation, p2.rotation, 0.001f)
        assertEquals("相同种子应产生相同scale", p1.scale, p2.scale, 0.001f)
    }

    @Test
    fun `generateRandomPerturbation_不同种子_结果不同`() {
        val p1 = GlyphLayoutData.generateRandomPerturbation(12345L)
        val p2 = GlyphLayoutData.generateRandomPerturbation(54321L)

        // 至少有一个参数不同
        val allSame = p1.offsetX == p2.offsetX &&
                p1.offsetY == p2.offsetY &&
                p1.rotation == p2.rotation &&
                p1.scale == p2.scale
        assertFalse("不同种子应产生不同结果", allSame)
    }

    @Test
    fun `generateRandomPerturbation_参数在合理范围内`() {
        // 使用多个随机种子测试
        for (seed in 1L..100L) {
            val p = GlyphLayoutData.generateRandomPerturbation(seed)

            // offsetX 范围检查
            assertTrue(
                "offsetX应在范围内: ${p.offsetX}",
                kotlin.math.abs(p.offsetX) <= GlyphLayoutData.PERTURBATION_OFFSET_X
            )

            // offsetY 范围检查
            assertTrue(
                "offsetY应在范围内: ${p.offsetY}",
                kotlin.math.abs(p.offsetY) <= GlyphLayoutData.PERTURBATION_OFFSET_Y
            )

            // rotation 范围检查
            assertTrue(
                "rotation应在范围内: ${p.rotation}",
                p.rotation in GlyphLayoutData.PERTURBATION_ROTATION_MIN..GlyphLayoutData.PERTURBATION_ROTATION_MAX
            )

            // scale 范围检查
            assertTrue(
                "scale应在范围内: ${p.scale}",
                p.scale in GlyphLayoutData.PERTURBATION_SCALE_MIN..GlyphLayoutData.PERTURBATION_SCALE_MAX
            )
        }
    }

    @Test
    fun `generateRandomPerturbation_偏移量较小_不影响可读性`() {
        // 验证扰动量不会过大导致文字不可读
        val maxOffsetX = GlyphLayoutData.PERTURBATION_OFFSET_X
        val maxOffsetY = GlyphLayoutData.PERTURBATION_OFFSET_Y

        // 相对于72px字号，2.5px的偏移是 3.5%，在可接受范围内
        assertTrue(
            "X偏移应小于字号的5%",
            maxOffsetX <= 72f * 0.05f
        )
        assertTrue(
            "Y偏移应小于字号的3%",
            maxOffsetY <= 72f * 0.03f
        )
    }

    // ============================================
    // 3. 分页逻辑测试
    // ============================================

    @Test
    fun `computeDocumentLayout_长文本_正确分页`() = runBlocking {
        // 生成足够长的文本以触发分页
        val longText = (1..100).joinToString("\n") { "这是一段测试文本用于测试分页逻辑。" }

        val result = layoutEngine.computeDocumentLayout(
            textContent = longText,
            fontConfig = FontConfig.DEFAULT,
            userGlyphs = emptyMap(),
            pageWidth = 2480f,
            pageHeight = 3508f
        )

        assertTrue("长文本应生成多页", result.pages.size > 1)

        // 验证页码连续性
        result.pages.forEachIndexed { index, page ->
            assertEquals("页码应连续", index, page.pageIndex)
        }
    }

    @Test
    fun `computeDocumentLayout_不同字号_影响分页`() = runBlocking {
        val text = (1..50).joinToString("\n") { "测试文本" }

        val compactResult = layoutEngine.computeDocumentLayout(
            textContent = text,
            fontConfig = FontConfig.COMPACT,  // 小字号
            userGlyphs = emptyMap(),
            pageWidth = 2480f,
            pageHeight = 3508f
        )

        val comfortableResult = layoutEngine.computeDocumentLayout(
            textContent = text,
            fontConfig = FontConfig.COMFORTABLE,  // 大字号
            userGlyphs = emptyMap(),
            pageWidth = 2480f,
            pageHeight = 3508f
        )

        assertTrue(
            "大字号应产生更多页",
            comfortableResult.pages.size >= compactResult.pages.size
        )
    }

    // ============================================
    // 4. 排版数据结构测试
    // ============================================

    @Test
    fun `GlyphLayoutData_computeFinalX_正确计算`() {
        val glyph = GlyphLayoutData(
            char = "测",
            unicode = "U+6D4B",
            glyphId = "U+6D4B_01",
            glyphImagePath = "",
            baseX = 100f,
            baseY = 200f,
            offsetX = 2f,
            offsetY = -1f,
            glyphWidth = 72f,
            glyphHeight = 72f
        )

        assertEquals("最终X应为102", 102f, glyph.computeFinalX(), 0.001f)
        assertEquals("最终Y应为199", 199f, glyph.computeFinalY(), 0.001f)
    }

    @Test
    fun `GlyphLayoutData_computeBounds_旋转后边界框正确`() {
        val glyph = GlyphLayoutData(
            char = "测",
            unicode = "U+6D4B",
            glyphId = "U+6D4B_01",
            glyphImagePath = "",
            baseX = 0f,
            baseY = 0f,
            offsetX = 0f,
            offsetY = 0f,
            rotation = 0f,
            scale = 1f,
            glyphWidth = 72f,
            glyphHeight = 72f
        )

        val bounds = glyph.computeBounds()
        assertEquals("无旋转时左边界", 0f, bounds.left, 0.5f)
        assertEquals("无旋转时右边界", 72f, bounds.right, 0.5f)
        assertEquals("无旋转时上边界", 0f, bounds.top, 0.5f)
        assertEquals("无旋转时下边界", 72f, bounds.bottom, 0.5f)
    }

    // ============================================
    // 5. 复杂场景测试
    // ============================================

    @Test
    fun `computeDocumentLayout_混合中英文_正确处理`() = runBlocking {
        val mixedText = "Hello 你好 World 世界\n这是第二行 mixed content"

        val result = layoutEngine.computeDocumentLayout(
            textContent = mixedText,
            fontConfig = FontConfig.DEFAULT,
            userGlyphs = emptyMap(),
            pageWidth = 2480f,
            pageHeight = 3508f
        )

        assertTrue("应有内容", result.totalChars > 0)

        // 验证所有glyph都有正确的unicode
        val allGlyphs = result.pages.flatMap { it.lines }.flatMap { it.glyphs }
        for (glyph in allGlyphs) {
            assertTrue("unicode应以U+开头", glyph.unicode.startsWith("U+"))
            assertTrue("unicode格式正确", glyph.unicode.length == 6)
        }
    }

    @Test
    fun `computeDocumentLayout_特殊字符_不崩溃`() = runBlocking {
        val specialText = "！？。，、；：\"\"''（）《》\n\t特殊符号测试"

        val result = layoutEngine.computeDocumentLayout(
            textContent = specialText,
            fontConfig = FontConfig.DEFAULT,
            userGlyphs = emptyMap(),
            pageWidth = 2480f,
            pageHeight = 3508f
        )

        // 不应崩溃，且应有合理的结果
        assertNotNull(result)
        assertTrue("应有内容", result.totalChars >= 0)
    }

    @Test
    fun `computeDocumentLayout_禁用扰动_所有参数为零`() = runBlocking {
        val result = layoutEngine.computeDocumentLayout(
            textContent = "测试文本",
            fontConfig = FontConfig.DEFAULT.copy(enablePerturbation = false),
            userGlyphs = emptyMap(),
            pageWidth = 2480f,
            pageHeight = 3508f
        )

        val allGlyphs = result.pages.flatMap { it.lines }.flatMap { it.glyphs }
        for (glyph in allGlyphs) {
            assertEquals("禁用扰动时offsetX应为0", 0f, glyph.offsetX, 0.001f)
            assertEquals("禁用扰动时offsetY应为0", 0f, glyph.offsetY, 0.001f)
            assertEquals("禁用扰动时rotation应为0", 0f, glyph.rotation, 0.001f)
            assertEquals("禁用扰动时scale应为1", 1f, glyph.scale, 0.001f)
        }
    }

    // ============================================
    // 6. 性能测试
    // ============================================

    @Test
    fun `computeDocumentLayout_性能_长文本处理时间`() = runBlocking {
        val longText = (1..500).joinToString("\n") { "这是一段用于性能测试的文本内容。" }

        val startTime = System.currentTimeMillis()
        val result = layoutEngine.computeDocumentLayout(
            textContent = longText,
            fontConfig = FontConfig.DEFAULT,
            userGlyphs = emptyMap(),
            pageWidth = 2480f,
            pageHeight = 3508f
        )
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("500行文本排版应在3秒内完成", elapsed < 3000)
        assertTrue("应生成多页", result.pages.size > 1)

        println("排版 $longText.length 字符, ${result.pages.size} 页, 耗时 ${elapsed}ms")
    }

    // ============================================
    // 辅助方法
    // ============================================

    private fun createTestGlyphMap(chars: List<String>): Map<String, List<GlyphModel>> {
        return chars.map { char ->
            val unicode = "U+${char.codePointAt(0).toString(16).uppercase().padStart(4, '0')}"
            unicode to listOf(
                GlyphModel(
                    unicode = unicode,
                    character = char,
                    glyphVersion = 1,
                    imagePath = "/test/$unicode.png",
                    width = 72,
                    height = 72,
                    baseline = 55,
                    advanceWidth = 72
                )
            )
        }.toMap()
    }
}
