package com.ew.handscript.core.render

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.target.Target
import com.ew.handscript.model.GlyphModel
import com.ew.handscript.model.typeset.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 手写渲染引擎 (HandwritingRenderEngine)
 *
 * 核心技术壁垒：实现真实感手写效果的逐字渲染系统。
 *
 * 技术要点：
 * 1. 基于 Android Canvas API 的逐字绘制（非DOM/Compose节点排版）
 * 2. 每个字独立的平移、旋转、缩放变换矩阵
 * 3. 多层级随机扰动算法模拟自然手写不规则感
 * 4. 动态字形切换：同一字多次出现时自动轮换不同写法
 * 5. 平滑降级：未收录字使用开源手写体兜底
 * 6. 协程并行计算 + 主线程仅负责最终绘制
 *
 * @param context Android上下文
 * @param imageLoader Coil图片加载器（用于异步加载字形图片）
 */
class HandwritingRenderEngine(
    private val context: Context,
    private val imageLoader: ImageLoader
) {
    /**
     * 字形图片缓存 - 使用ConcurrentHashMap实现线程安全的内存缓存
     * Key: glyphId (如 "U+4F60_01")
     * Value: 加载完成的Bitmap
     */
    private val glyphBitmapCache = ConcurrentHashMap<String, Bitmap>()

    /**
     * 后备字体画笔 - 用于未收录字形
     */
    private val fallbackPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.DEFAULT  // 可替换为开源手写体
        textAlign = Paint.Align.LEFT
    }

    /**
     * 排版计算锁 - 保证排版参数计算的原子性
     */
    private val layoutMutex = Mutex()

    /**
     * 动态字形轮换计数器 - Key: unicode, Value: 下次使用的版本索引
     */
    private val glyphRotationCounters = ConcurrentHashMap<String, Int>()

    /**
     * 后备字体绘制时的随机扰动参数缓存
     * 保证同一文档每次渲染后备字体的效果一致
     */
    private val fallbackPerturbationCache = ConcurrentHashMap<Int, PerturbationParams>()

    /**
     * 渲染配置
     */
    data class RenderConfig(
        /** 页面宽度（像素） */
        val pageWidth: Int = 2480,  // A4 @ 300dpi
        /** 页面高度（像素） */
        val pageHeight: Int = 3508, // A4 @ 300dpi
        /** 是否启用抗锯齿 */
        val enableAntiAlias: Boolean = true,
        /** 是否启用子像素渲染 */
        val enableSubpixel: Boolean = true,
        /** 最大并发加载数 */
        val maxConcurrentLoads: Int = 16,
        /** 缓存最大大小（MB） */
        val cacheSizeMB: Int = 128
    )

    /**
     * 主入口：将排版结果渲染为Bitmap
     *
     * @param pageLayout 页面排版数据
     * @param fontConfig 字体配置
     * @param userGlyphs 用户字库（unicode -> 该字的所有字形版本列表）
     * @param renderConfig 渲染配置
     * @return 渲染完成的页面Bitmap
     */
    suspend fun renderPage(
        pageLayout: PageLayoutData,
        fontConfig: FontConfig,
        userGlyphs: Map<String, List<GlyphModel>>,
        renderConfig: RenderConfig = RenderConfig()
    ): Bitmap = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        // 1. 创建离屏Bitmap和Canvas
        val bitmap = Bitmap.createBitmap(
            pageLayout.pageWidth.toInt(),
            pageLayout.pageHeight.toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        // 2. 绘制背景（信纸模板）
        drawBackground(canvas, pageLayout, fontConfig)

        // 3. 预加载所有需要的字形图片（并行协程）
        preloadGlyphImages(pageLayout, userGlyphs, fontConfig)

        // 4. 逐行、逐字绘制
        val paint = createGlyphPaint(fontConfig)

        for (line in pageLayout.lines) {
            for (glyphLayout in line.glyphs) {
                drawSingleGlyph(
                    canvas = canvas,
                    glyphLayout = glyphLayout,
                    fontConfig = fontConfig,
                    userGlyphs = userGlyphs,
                    paint = paint
                )
            }
        }

        // 5. 应用扫描滤镜（可选）
        if (fontConfig.enableScanFilter) {
            applyScanFilter(bitmap)
        }

        val elapsed = System.currentTimeMillis() - startTime
        Timber.d("页面渲染完成: ${pageLayout.lines.sumOf { it.glyphs.size }} 字, 耗时 ${elapsed}ms")

        bitmap
    }

    /**
     * 绘制单个字形 - 核心技术：独立变换矩阵
     *
     * 每个字拥有独立的绘制流程：
     * 1. 计算最终位置（base + 扰动偏移）
     * 2. 构建变换矩阵（平移 -> 旋转 -> 缩放）
     * 3. 保存Canvas状态，应用变换
     * 4. 绘制字形Bitmap或后备字体
     * 5. 恢复Canvas状态
     */
    private fun drawSingleGlyph(
        canvas: Canvas,
        glyphLayout: GlyphLayoutData,
        fontConfig: FontConfig,
        userGlyphs: Map<String, List<GlyphModel>>,
        paint: Paint
    ) {
        val finalX = glyphLayout.computeFinalX()
        val finalY = glyphLayout.computeFinalY()

        // 1. 保存当前Canvas状态
        val saveCount = canvas.save()

        try {
            if (glyphLayout.isFallback || !userGlyphs.containsKey(glyphLayout.unicode)) {
                // 2a. 后备字体渲染路径
                drawFallbackGlyph(
                    canvas = canvas,
                    glyphLayout = glyphLayout,
                    fontConfig = fontConfig,
                    paint = paint,
                    finalX = finalX,
                    finalY = finalY
                )
            } else {
                // 2b. 用户字形渲染路径
                drawUserGlyph(
                    canvas = canvas,
                    glyphLayout = glyphLayout,
                    fontConfig = fontConfig,
                    finalX = finalX,
                    finalY = finalY
                )
            }
        } finally {
            // 3. 恢复Canvas状态
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * 绘制用户字形 - 使用Bitmap绘制
     *
     * 变换矩阵计算顺序（从右到左应用）：
     * M = T(finalX, finalY) * R(rotation) * S(scale) * T(-centerX, -centerY)
     *
     * 即：先将图像中心移到原点，然后缩放，再旋转，最后平移到最终位置
     */
    private fun drawUserGlyph(
        canvas: Canvas,
        glyphLayout: GlyphLayoutData,
        fontConfig: FontConfig,
        finalX: Float,
        finalY: Float
    ) {
        // 1. 从缓存获取字形Bitmap
        val glyphBitmap = glyphBitmapCache[glyphLayout.glyphId]
            ?: loadBitmapSync(glyphLayout.glyphImagePath)
            ?: return

        // 2. 计算字形中心点（用于旋转变换）
        val centerX = glyphLayout.glyphWidth / 2
        val centerY = glyphLayout.glyphHeight / 2

        // 3. 构建完整变换矩阵
        val matrix = Matrix()

        // 3.1 平移到最终绘制位置
        // 注意：需要考虑基线对齐，文字基线应对齐到行基线
        val drawX = finalX
        val drawY = finalY - glyphLayout.glyphHeight * 0.8f  // 基线对齐调整

        // 3.2 按顺序应用变换（矩阵乘法从右到左）
        // 最终位置
        matrix.postTranslate(drawX, drawY)

        // 以字形中心为锚点进行旋转和缩放
        matrix.preRotate(glyphLayout.rotation, centerX, centerY)
        matrix.preScale(glyphLayout.scale, glyphLayout.scale, centerX, centerY)

        // 4. 设置画笔颜色（墨水颜色）
        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(
                fontConfig.inkColor.toInt(),
                PorterDuff.Mode.SRC_IN
            )
            alpha = (255 * fontConfig.inkThickness).toInt().coerceIn(0, 255)
        }

        // 5. 应用变换矩阵并绘制
        canvas.concat(matrix)
        canvas.drawBitmap(glyphBitmap, 0f, 0f, tintPaint)
    }

    /**
     * 绘制后备字体 - 使用Canvas drawText
     *
     * 当用户字库中未收录某字时使用，同时应用随机扰动使其看起来更接近手写
     */
    private fun drawFallbackGlyph(
        canvas: Canvas,
        glyphLayout: GlyphLayoutData,
        fontConfig: FontConfig,
        paint: Paint,
        finalX: Float,
        finalY: Float
    ) {
        // 1. 获取或生成该后备字的扰动参数（保证同一文档一致性）
        val seed = glyphLayout.paragraphIndex * 10000 +
                glyphLayout.lineIndex * 100 +
                glyphLayout.charIndex
        val perturbation = fallbackPerturbationCache.getOrPut(seed) {
            GlyphLayoutData.generateRandomPerturbation(seed.toLong())
        }

        // 2. 配置后备字体画笔
        paint.apply {
            color = fontConfig.inkColor.toInt()
            textSize = fontConfig.fontSizePx * glyphLayout.scale * perturbation.scale
            alpha = (200 * fontConfig.inkThickness).toInt().coerceIn(50, 255) // 后备字体稍淡
        }

        // 3. 应用变换矩阵
        val matrix = Matrix()
        val drawX = finalX + perturbation.offsetX
        val drawY = finalY + perturbation.offsetY

        matrix.postTranslate(drawX, drawY)
        matrix.preRotate(
            glyphLayout.rotation + perturbation.rotation,
            fontConfig.fontSizePx / 2,
            fontConfig.fontSizePx / 2
        )

        canvas.concat(matrix)

        // 4. 绘制后备文字
        canvas.drawText(
            glyphLayout.char,
            0f,
            fontConfig.fontSizePx * 0.8f,  // 基线对齐
            paint
        )
    }

    /**
     * 绘制背景（信纸模板）
     */
    private fun drawBackground(
        canvas: Canvas,
        pageLayout: PageLayoutData,
        fontConfig: FontConfig
    ) {
        val template = fontConfig.paperTemplate

        // 1. 填充背景色
        canvas.drawColor(template.backgroundColor.toInt())

        // 2. 绘制横线
        if (template.hasRuledLines) {
            drawRuledLines(canvas, pageLayout, fontConfig)
        }

        // 3. 绘制方格
        if (template.hasGrid) {
            drawGridLines(canvas, pageLayout, fontConfig)
        }

        // 4. 自定义纹理背景（如果有）
        template.textureResId?.let { texturePath ->
            drawTextureBackground(canvas, texturePath, pageLayout)
        }
    }

    /**
     * 绘制横线信纸的横线
     */
    private fun drawRuledLines(
        canvas: Canvas,
        pageLayout: PageLayoutData,
        fontConfig: FontConfig
    ) {
        val linePaint = Paint().apply {
            color = 0xFFE0E0E0.toInt()  // 浅灰色横线
            strokeWidth = 1.5f
            alpha = 120
        }

        val contentTop = pageLayout.marginTop
        val contentBottom = pageLayout.pageHeight - pageLayout.marginBottom
        var currentY = contentTop + fontConfig.fontSizePx

        while (currentY < contentBottom) {
            canvas.drawLine(
                pageLayout.marginLeft,
                currentY,
                pageLayout.pageWidth - pageLayout.marginRight,
                currentY,
                linePaint
            )
            currentY += fontConfig.lineSpacingPx
        }
    }

    /**
     * 绘制方格稿纸的方格
     */
    private fun drawGridLines(
        canvas: Canvas,
        pageLayout: PageLayoutData,
        fontConfig: FontConfig
    ) {
        val gridPaint = Paint().apply {
            color = 0xFFD0D0D0.toInt()
            strokeWidth = 1f
            alpha = 100
        }

        val gridSize = fontConfig.fontSizePx + fontConfig.letterSpacingPx
        val contentLeft = pageLayout.marginLeft
        val contentRight = pageLayout.pageWidth - pageLayout.marginRight
        val contentTop = pageLayout.marginTop
        val contentBottom = pageLayout.pageHeight - pageLayout.marginBottom

        // 绘制竖线
        var x = contentLeft
        while (x < contentRight) {
            canvas.drawLine(x, contentTop, x, contentBottom, gridPaint)
            x += gridSize
        }

        // 绘制横线
        var y = contentTop
        while (y < contentBottom) {
            canvas.drawLine(contentLeft, y, contentRight, y, gridPaint)
            y += gridSize
        }
    }

    /**
     * 绘制纹理背景
     */
    private fun drawTextureBackground(
        canvas: Canvas,
        texturePath: String,
        pageLayout: PageLayoutData
    ) {
        try {
            val textureBitmap = loadBitmapSync(texturePath) ?: return
            val shader = BitmapShader(
                textureBitmap,
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )
            val paint = Paint().apply {
                setShader(shader)
                alpha = 40  // 纹理透明度
            }
            canvas.drawRect(
                0f, 0f,
                pageLayout.pageWidth,
                pageLayout.pageHeight,
                paint
            )
        } catch (e: Exception) {
            Timber.w(e, "纹理背景加载失败: $texturePath")
        }
    }

    /**
     * 创建字形绘制画笔
     */
    private fun createGlyphPaint(fontConfig: FontConfig): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
            isFilterBitmap = true
        }
    }

    /**
     * 预加载字形图片 - 使用协程并行加载
     */
    private suspend fun preloadGlyphImages(
        pageLayout: PageLayoutData,
        userGlyphs: Map<String, List<GlyphModel>>,
        fontConfig: FontConfig
    ) = coroutineScope {
        val allGlyphIds = mutableSetOf<String>()

        // 收集所有需要加载的字形ID
        for (line in pageLayout.lines) {
            for (glyphLayout in line.glyphs) {
                if (!glyphLayout.isFallback && userGlyphs.containsKey(glyphLayout.unicode)) {
                    // 动态字形切换：根据轮换计数器选择版本
                    val glyphId = if (fontConfig.enableDynamicGlyph) {
                        selectDynamicGlyph(glyphLayout.unicode, userGlyphs)
                    } else {
                        userGlyphs[glyphLayout.unicode]?.firstOrNull()?.getGlyphId()
                    }
                    glyphId?.let { allGlyphIds.add(it) }
                }
            }
        }

        // 过滤已缓存的
        val toLoad = allGlyphIds.filter { !glyphBitmapCache.containsKey(it) }

        // 分批并行加载（限制并发数）
        val semaphore = kotlinx.coroutines.sync.Semaphore(16)

        toLoad.map { glyphId ->
            async {
                semaphore.withPermit {
                    loadGlyphImage(glyphId, userGlyphs)
                }
            }
        }.awaitAll()
    }

    /**
     * 动态字形选择 - 同一字多次出现时轮换不同写法
     *
     * 实现OpenType随机替换效果：
     * - 维护每个unicode的轮换计数器
     * - 每次请求时返回下一个版本，循环往复
     * - 使同一文档中重复出现的字呈现不同形态，模拟真实手写变化
     */
    private fun selectDynamicGlyph(
        unicode: String,
        userGlyphs: Map<String, List<GlyphModel>>
    ): String? {
        val availableGlyphs = userGlyphs[unicode] ?: return null
        if (availableGlyphs.size <= 1) return availableGlyphs.firstOrNull()?.getGlyphId()

        // 原子操作：获取并递增计数器
        val currentIndex = glyphRotationCounters.getOrPut(unicode) { 0 }
        val selectedGlyph = availableGlyphs[currentIndex % availableGlyphs.size]
        glyphRotationCounters[unicode] = (currentIndex + 1) % availableGlyphs.size

        return selectedGlyph.getGlyphId()
    }

    /**
     * 异步加载单个字形图片
     */
    private suspend fun loadGlyphImage(
        glyphId: String,
        userGlyphs: Map<String, List<GlyphModel>>
    ) {
        // 查找字形对应的图片路径
        val glyphModel = userGlyphs.values.flatten().find { it.getGlyphId() == glyphId }
            ?: return

        try {
            val bitmap = loadBitmapAsync(glyphModel.imagePath)
            if (bitmap != null) {
                glyphBitmapCache[glyphId] = bitmap
            }
        } catch (e: Exception) {
            Timber.w(e, "字形图片加载失败: $glyphId")
        }
    }

    /**
     * 使用Coil异步加载Bitmap
     */
    private suspend fun loadBitmapAsync(path: String): Bitmap? = suspendCancellableCoroutine { continuation ->
        val request = ImageRequest.Builder(context)
            .data(path)
            .target(
                onSuccess = { drawable ->
                    val bitmap = drawable.toBitmap()
                    continuation.resume(bitmap) {}
                },
                onError = {
                    continuation.resume(null) {}
                }
            )
            .build()

        imageLoader.enqueue(request)
    }

    /**
     * 同步加载Bitmap（用于后备路径）
     */
    private fun loadBitmapSync(path: String): Bitmap? {
        // 先查缓存
        glyphBitmapCache[path]?.let { return it }

        return try {
            BitmapFactory.decodeFile(path)?.also {
                glyphBitmapCache[path] = it
            }
        } catch (e: Exception) {
            Timber.w(e, "Bitmap同步加载失败: $path")
            null
        }
    }

    /**
     * Drawable转Bitmap
     */
    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable) return bitmap

        val width = intrinsicWidth.coerceAtLeast(1)
        val height = intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }

    /**
     * 应用扫描滤镜 - 添加纸张纹理和边缘暗角
     */
    private fun applyScanFilter(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        // 1. 添加轻微噪点纹理
        val noisePaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            alpha = 15
        }

        // 使用简单的随机噪点（实际可用更复杂的纹理）
        val random = Random(42) // 固定种子保证一致性
        for (i in 0 until (width * height / 500).toInt()) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            val size = random.nextFloat() * 2 + 0.5f
            canvas.drawCircle(x, y, size, noisePaint)
        }

        // 2. 边缘暗角（Vignette）
        val vignetteColors = intArrayOf(
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            0x30000000,  // 轻微暗色
            0x60000000   // 更深的暗角
        )
        val vignettePositions = floatArrayOf(0f, 0.6f, 0.85f, 1f)

        val vignettePaint = Paint().apply {
            shader = RadialGradient(
                width / 2, height / 2,
                kotlin.math.max(width, height) * 0.7f,
                vignetteColors,
                vignettePositions,
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        }
        canvas.drawRect(0f, 0f, width, height, vignettePaint)

        // 3. 整体轻微模糊（模拟扫描仪分辨率限制）
        // 注：实际可用RenderScript或OpenGL实现更高效的模糊
    }

    /**
     * 清理缓存资源
     */
    fun clearCache() {
        glyphBitmapCache.values.forEach { it.recycle() }
        glyphBitmapCache.clear()
        glyphRotationCounters.clear()
        fallbackPerturbationCache.clear()
    }

    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        val totalSize = glyphBitmapCache.values.sumOf { it.byteCount.toLong() }
        return CacheStats(
            cachedGlyphs = glyphBitmapCache.size,
            cacheSizeBytes = totalSize,
            rotationCounters = glyphRotationCounters.size
        )
    }

    data class CacheStats(
        val cachedGlyphs: Int,
        val cacheSizeBytes: Long,
        val rotationCounters: Int
    )
}

/**
 * 排版计算引擎 - 协程后台计算排版参数
 *
 * 职责：
 * 1. 将纯文本解析为排版数据结构
 * 2. 为每个字符计算基准位置、随机扰动参数
 * 3. 动态字形版本选择
 * 4. 生成分页结果
 */
class LayoutComputationEngine {

    /**
     * 计算文档排版 - 在后台协程中执行
     *
     * @param textContent 纯文本内容（含换行符）
     * @param fontConfig 排版配置
     * @param userGlyphs 用户字库
     * @param pageWidth 页面宽度
     * @param pageHeight 页面高度
     * @return 完整排版结果
     */
    suspend fun computeDocumentLayout(
        textContent: String,
        fontConfig: FontConfig,
        userGlyphs: Map<String, List<GlyphModel>>,
        pageWidth: Float,
        pageHeight: Float
    ): DocumentLayoutResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        // 1. 解析段落结构
        val paragraphs = textContent.split("\n")

        // 2. 逐段落计算行分割
        val allLines = mutableListOf<LineLayoutData>()
        var globalCharIndex = 0

        for ((paraIdx, paragraph) in paragraphs.withIndex()) {
            val paraLines = breakParagraphIntoLines(
                paragraph = paragraph,
                paraIdx = paraIdx,
                fontConfig = fontConfig,
                userGlyphs = userGlyphs,
                contentWidth = pageWidth - fontConfig.marginLeftPx - fontConfig.marginRightPx,
                startCharIndex = globalCharIndex
            )
            allLines.addAll(paraLines)
            globalCharIndex += paragraph.length
        }

        // 3. 计算Y坐标并分页
        val pages = paginateLines(
            lines = allLines,
            fontConfig = fontConfig,
            pageWidth = pageWidth,
            pageHeight = pageHeight
        )

        // 4. 统计信息
        val totalChars = allLines.sumOf { it.glyphs.size }
        val fallbackChars = allLines.sumOf { line ->
            line.glyphs.count { it.isFallback }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Timber.d("排版计算完成: $totalChars 字, ${pages.size} 页, 耗时 ${elapsed}ms")

        DocumentLayoutResult(
            pages = pages,
            totalChars = totalChars,
            totalFallbackChars = fallbackChars,
            fontConfig = fontConfig
        )
    }

    /**
     * 将段落分割为多行
     */
    private fun breakParagraphIntoLines(
        paragraph: String,
        paraIdx: Int,
        fontConfig: FontConfig,
        userGlyphs: Map<String, List<GlyphModel>>,
        contentWidth: Float,
        startCharIndex: Int
    ): List<LineLayoutData> {
        val lines = mutableListOf<LineLayoutData>()
        val currentLineGlyphs = mutableListOf<GlyphLayoutData>()
        var currentX = fontConfig.marginLeftPx
        val baseY = fontConfig.marginTopPx + fontConfig.fontSizePx  // 初始Y，后续会调整
        var localCharIndex = 0

        for (char in paragraph) {
            val unicode = "U+${char.code.toString(16).uppercase().padStart(4, '0')}"

            // 检查字库中是否有该字
            val availableGlyphs = userGlyphs[unicode]
            val hasGlyph = availableGlyphs?.isNotEmpty() == true

            // 计算字符宽度（简化：使用固定宽度比例）
            val charWidth = if (hasGlyph) {
                fontConfig.fontSizePx  // 用户字形按字号等宽
            } else {
                fontConfig.fontSizePx * 0.9f  // 后备字体略窄
            }

            // 检查是否需要换行
            if (currentX + charWidth > contentWidth && currentLineGlyphs.isNotEmpty()) {
                // 保存当前行
                lines.add(
                    LineLayoutData(
                        lineIndex = lines.size,
                        paragraphIndex = paraIdx,
                        baseY = 0f,  // 后续统一计算
                        glyphs = currentLineGlyphs.toList(),
                        lineHeight = fontConfig.lineSpacingPx,
                        actualWidth = currentX - fontConfig.marginLeftPx
                    )
                )
                currentLineGlyphs.clear()
                currentX = fontConfig.marginLeftPx
            }

            // 生成随机扰动参数
            val seed = paraIdx * 100000L + localCharIndex
            val perturbation = if (fontConfig.enablePerturbation) {
                GlyphLayoutData.generateRandomPerturbation(seed)
            } else {
                PerturbationParams()
            }

            // 构建字形排版数据
            val glyphLayout = GlyphLayoutData(
                char = char.toString(),
                unicode = unicode,
                glyphId = if (hasGlyph) availableGlyphs!!.first().getGlyphId() else "fallback_$unicode",
                glyphImagePath = availableGlyphs?.firstOrNull()?.imagePath ?: "",
                offsetX = perturbation.offsetX,
                offsetY = perturbation.offsetY,
                rotation = perturbation.rotation,
                scale = perturbation.scale,
                baseX = currentX,
                baseY = baseY,
                glyphWidth = charWidth * perturbation.scale,
                glyphHeight = fontConfig.fontSizePx * perturbation.scale,
                isFallback = !hasGlyph,
                lineIndex = lines.size,
                charIndex = startCharIndex + localCharIndex,
                paragraphIndex = paraIdx
            )

            currentLineGlyphs.add(glyphLayout)
            currentX += charWidth * perturbation.scale + fontConfig.letterSpacingPx
            localCharIndex++
        }

        // 添加最后一行
        if (currentLineGlyphs.isNotEmpty()) {
            lines.add(
                LineLayoutData(
                    lineIndex = lines.size,
                    paragraphIndex = paraIdx,
                    baseY = 0f,
                    glyphs = currentLineGlyphs.toList(),
                    lineHeight = fontConfig.lineSpacingPx,
                    actualWidth = currentX - fontConfig.marginLeftPx
                )
            )
        }

        return lines
    }

    /**
     * 将行列表分页
     */
    private fun paginateLines(
        lines: List<LineLayoutData>,
        fontConfig: FontConfig,
        pageWidth: Float,
        pageHeight: Float
    ): List<PageLayoutData> {
        val pages = mutableListOf<PageLayoutData>()
        val contentHeight = pageHeight - fontConfig.marginTopPx - fontConfig.marginBottomPx

        var currentPageLines = mutableListOf<LineLayoutData>()
        var currentPageHeight = 0f

        for (line in lines) {
            if (currentPageHeight + line.lineHeight > contentHeight && currentPageLines.isNotEmpty()) {
                // 保存当前页
                pages.add(createPageData(pages.size, currentPageLines, fontConfig, pageWidth, pageHeight))
                currentPageLines = mutableListOf()
                currentPageHeight = 0f
            }

            // 更新行的实际Y坐标
            val updatedLine = line.copy(
                baseY = fontConfig.marginTopPx + currentPageHeight + fontConfig.fontSizePx
            )
            // 更新所有glyph的baseY
            val updatedGlyphs = updatedLine.glyphs.map { glyph ->
                glyph.copy(baseY = updatedLine.baseY)
            }
            currentPageLines.add(updatedLine.copy(glyphs = updatedGlyphs))
            currentPageHeight += line.lineHeight
        }

        // 添加最后一页
        if (currentPageLines.isNotEmpty()) {
            pages.add(createPageData(pages.size, currentPageLines, fontConfig, pageWidth, pageHeight))
        }

        return pages
    }

    private fun createPageData(
        pageIndex: Int,
        lines: List<LineLayoutData>,
        fontConfig: FontConfig,
        pageWidth: Float,
        pageHeight: Float
    ): PageLayoutData {
        return PageLayoutData(
            pageIndex = pageIndex,
            lines = lines,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            marginLeft = fontConfig.marginLeftPx,
            marginRight = fontConfig.marginRightPx,
            marginTop = fontConfig.marginTopPx,
            marginBottom = fontConfig.marginBottomPx
        )
    }
}
