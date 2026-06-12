# TFLite 模型输入输出规格文档

> **Agent-A（算法工程师）交付**  
> 日期: 2026-06-11 | 版本: 1.0 | 对应 Task 1: 端侧五行特征模型  
> 目标读者: Agent-D（Android端集成）

---

## wuxing_feature_extractor.tflite

### 基本规格

| 属性 | 值 | 说明 |
|------|-----|------|
| **文件名** | `wuxing_feature_extractor.tflite` | assets/models/ 下 |
| **模型架构** | MobileNetV3-Small(0.5x) + FC融合层 | 128维视觉 + 8维方向 → 5维 |
| **量化方式** | INT8, per-channel | 全整数量化 |
| **目标大小** | `< 1.5 MB` | 当前估算 ~986 KB |
| **推理延迟** | `< 50 ms` | Mate30 目标（实际待测） |

### 输入

```
Shape:  [1, 64, 64, 1]
Type:   float32
Range:  [0.0, 1.0]   （像素值 / 255.0 归一化）
通道:   灰度单通道    （RGBA 转灰度: Gray = 0.299R + 0.587G + 0.114B）
尺寸:   64 × 64       （输入图任意尺寸，先缩放到 64×64）
```

**Android 预处理代码示例：**

```kotlin
// 1. 缩放到 64×64
val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 64, 64, true)

// 2. 转灰度
val grayBitmap = toGrayscale(scaledBitmap)

// 3. 提取像素 + 归一化
val pixels = IntArray(64 * 64)
grayBitmap.getPixels(pixels, 0, 64, 0, 0, 64, 64)

val floatInput = FloatArray(64 * 64)
for (i in 0 until 64 * 64) {
    val gray = pixels[i] and 0xFF  // 取蓝色通道（灰度图R=G=B）
    floatInput[i] = gray / 255.0f
}

// 4. 转为 [1, 64, 64, 1] 的 4D 数组
val inputArray = Array(1) { Array(64) { Array(64) { FloatArray(1) } } }
for (y in 0 until 64) {
    for (x in 0 until 64) {
        inputArray[0][y][x][0] = floatInput[y * 64 + x]
    }
}
```

### 输出

```
Shape:  [1, 5]
Type:   float32
Range:  [0.0, 100.0]   （Sigmoid * 100）
含义:   [wood, fire, earth, metal, water]
```

| 索引 | 字段名 | 含义 | 高值特征 |
|------|--------|------|----------|
| 0 | `wood` | 木 | 竖长横短，舒展流畅 |
| 1 | `fire` | 火 | 粗重顿笔，锋芒外露 |
| 2 | `earth`| 土 | 方正对称，稳重厚实 |
| 3 | `metal`| 金 | 转折锐利，棱角分明 |
| 4 | `water`| 水 | 连笔圆润，连贯流畅 |

**Android 后处理代码示例：**

```kotlin
val outputArray = Array(1) { FloatArray(5) }
interpreter.run(inputArray, outputArray)

val wood  = outputArray[0][0]
val fire  = outputArray[0][1]
val earth = outputArray[0][2]
val metal = outputArray[0][3]
val water = outputArray[0][4]

// 构建 FiveElementValues
val fiveElementValues = FiveElementValues(
    wood = wood,
    fire = fire,
    earth = earth,
    metal = metal,
    water = water
)

// 判断是否 Mock（全为 0 或未初始化）
val isMock = fiveElementValues.isEmpty()
```

---

## print_filter.tflite

### 基本规格

| 属性 | 值 | 说明 |
|------|-----|------|
| **文件名** | `print_filter.tflite` | assets/models/ 下 |
| **模型架构** | MobileNetV3-Small(0.5x) + FC二分类头 | 共享 Backbone |
| **量化方式** | INT8 | 全整数量化 |
| **目标大小** | `< 500 KB` | 当前估算 ~650 KB |
| **推理延迟** | `< 30 ms` | Mate30 目标（实际待测） |

### 输入

```
Shape:  [1, 64, 64, 1]
Type:   float32
Range:  [0.0, 1.0]   （同 wuxing_feature，灰度归一化）
```

> **复用预处理**：与 wuxing_feature_extractor 完全相同的预处理流程，可在 Android 端复用 `bitmapToFloatArray()` 函数。

### 输出

```
Shape:  [1, 1]
Type:   float32
Range:  [0.0, 1.0]   （Sigmoid 输出）
含义:   isPrint 置信度
阈值:   > 0.5 判为印刷体（拒绝），≤ 0.5 判为手写体（通过）
```

**Android 后处理代码示例：**

```kotlin
val outputArray = Array(1) { FloatArray(1) }
interpreter.run(inputArray, outputArray)

val confidence = outputArray[0][0]
val isPrint = confidence > 0.5f

val result = PrintFilterResult(
    isPrint = isPrint,
    confidence = confidence,
    isMock = false
)

// UI 提示
if (isPrint) {
    Toast.makeText(context, "检测到印刷体，请手写", Toast.LENGTH_SHORT).show()
}
```

---

## 与现有 TFLiteHelper.kt 的接口对齐

当前 `TFLiteHelper.kt` 已实现以下接口：

```kotlin
// 五行觉醒（现在返回 Mock，替换模型后返回真实值）
suspend fun awakenWuxing(bitmap: Bitmap): FiveElementValues

// 印刷体过滤（现在返回 Mock，替换模型后返回真实值）  
suspend fun filterPrint(bitmap: Bitmap): PrintFilterResult

// 模型是否可用
fun isModelAvailable(): Boolean
```

**替换步骤**：
1. 将 `.tflite` 文件放入 `app/src/main/assets/models/`
2. 删除占位文件（0字节的同名文件）
3. `TFLiteHelper.initialize()` 自动加载真实模型
4. `isModelAvailable()` 返回 `true`
5. 所有调用代码**无需修改**

---

## 模型推理流程图

```
[Bitmap 任意尺寸]
    ↓
[缩放到 64×64]
    ↓
[转灰度图]
    ↓
[归一化 /255.0] ─────────────────────────┐
    ↓                                      │
[wuxing_feature_extractor.tflite]    [print_filter.tflite]
    ↓                                      ↓
[FiveElementValues 5维]          [PrintFilterResult 1维]
[wood/fire/earth/metal/water]     [isPrint confidence]
    ↓                                      ↓
[UI 五行雷达图展示]                 [>0.5 拒绝/≤0.5 通过]
```

---

## 文件放置路径

```
app/src/main/assets/models/
    ├── wuxing_feature_extractor.tflite   ← 五行模型（<1.5MB）
    ├── print_filter.tflite               ← 印刷体模型（<500KB）
    └── （删除原有0字节占位文件）
```

---

## 依赖版本（与项目一致）

```toml
# gradle/libs.versions.toml
tflite = "2.14.0"           # TensorFlow Lite
```

```kotlin
// app/build.gradle.kts
implementation("org.tensorflow:tensorflow-lite:${libs.versions.tflite.get()}")
```

---

*文档版本: 1.0 | 如有问题请联系 Agent-A（算法工程师）*
