# 蚯蚓.手书修仙传 | 项目Wiki | 版本: 0.8.1 | 日期: 2026-06-11

## 1. 产品一句话
个人笔迹数字孪生修仙游戏。用户扫描历史手写稿，通过五行消消乐校对构建Git版本字库，生成手写风格文档。

## 2. 目标用户
- 种子：学生群体（8-22岁，作业/日记/作文/手稿等）
- 付费：成年人（22-60岁，情书/家书/工作笔记/手稿等）

## 3. 核心玩法（五行消消乐）
- 上滑：入库（消除）
- 下拉：待定
- 长按：质心扩展（方圆透出底稿+拖动对齐）
- 双层卡片：未触碰显示预切字，触碰后透出底稿

## 4. 技术架构（四层）
Layer1: PaddleOCR DBNet行检测
Layer2: return_word_box初框（仅ROI引导，不直接抠图）
Layer3: OpenCV局部分割（ROI内连通域+分水岭）
Layer4: 人机协同校对（消消乐交互）

## 5. 当前阶段
- [x] Phase1: PRD+架构书（已冻结，PRD-v1.0，架构书_v1.0）
- [x] Phase2: 原型设计（已冻结，UI/IXD定义_v0.1）
- [x] Phase3: 开发（已冻结，算法模块Python版已验证）
- [x] Phase4-1: 导航联调（已冻结，AppNavigation完成）
- [x] Phase4-2: TFLite模型集成与Android端编译（**编译通过，基础架构就绪**）
  - [x] build.gradle.kts + libs.versions.toml 已修复（OpenCV 4.5.3 + Timber 5.0.1 Maven依赖）
  - [x] network/（ApiContract.kt + ApiService.kt）@Serializable已移除
  - [x] model/（GlyphModel.kt + GlyphLayoutData.kt）@Serializable已移除，Room兼容性已修复
  - [x] data/local/（GlyphEntity.kt + ExportHistoryEntity.kt + SourceDocumentEntity.kt）Room兼容性已修复
  - [x] GlyphStatistics.kt 已创建
  - [x] core/ 依赖已补配（OpenCV + Timber）
  - [x] ml/ 已重建（FiveElementValues.kt + TFLiteHelper.kt + 占位模型）
  - [x] GlyphItem.kt 已恢复完整版（11字段，character/wuXingValues）
  - [x] **Cursor修复：HiltWorkerFactory依赖缺失**（补充 work-runtime-ktx + hilt-work + hilt-compiler）
  - [x] **Cursor修复：OpenCVLoader.initLocal → initDebug()**（4.5.3.0 Maven版API差异）
  - [x] **Cursor修复：20+处Unresolved reference批量修复**（HomeScreen navController、LibraryViewModel包名/字段、ProofreadScreen/DoubleLayerCard/OutputScreen/RealmScreen/ScanScreen/WuXingAwakening跨模块引用）
  - [x] **Trae修复：Hilt @Inject架构补全**（HandwritingRenderEngine等5个核心类添加@Inject；新建GlyphCacheImpl.kt；新建DatabaseModule.kt）
  - [x] **Trae修复：Kotlin语法错误**（LibraryViewModel Lambda参数it→currentState；BreakthroughAnimation包名修正）
  - [x] **Trae修复：IDE环境调优**（嵌入式JDK配置；禁用Clangd Support插件；构建缓存清理）
  - [x] **千问诊断：.gitignore缺失导致AS编译暴躁**（补充Android/Kotlin标准模板）
  - [x] **千问方案：Ubuntu私有Git中心**（receive.denyCurrentBranch updateInstead配置）
  - [ ] **TFLite模型真实推理未实测**（当前Mock兜底运行）
  - [ ] **OCR引擎（PaddleOCR）未集成**（已注释，降低编译复杂度）
- [x] Phase5: 真机冒烟测试（**首测通过 — 华为Mate30可见全部页面**）
  - [x] 编译通过（BUILD SUCCESSFUL，Debug构建33秒）
  - [x] APK安装成功
  - [x] 全部页面可见（无崩溃、无白屏）
  - [ ] 相机调用未通
  - [ ] 相册调用未通
  - [ ] 扫描/识别流程未通
  - [ ] 页面跳转存在异常
- [ ] Phase6: 功能迭代与算法实测（待启动）
  - [ ] TFLite真实模型替换占位文件
  - [ ] PaddleOCR集成与行检测实测
  - [ ] OpenCV局部分割实测
  - [ ] 字体生成流水线（Agent-B Python脚本）与Android端集成
  - [ ] 相机/相册权限与调用链打通
  - [ ] 双层卡片交互（质心扩展+方圆切割）实测
- [ ] Phase7: 内测与发布

## 6. 关键约束
- 设备：华为Mate30（麒麟990），Android 10
- 开发：iMac + Android Studio + HBuilderX
- **AI协作平台**：Kimi（规划+代码生成）→ 千问（基础设施诊断+方案设计）→ Cursor（依赖修复+API对齐）→ Trae（架构补全+AS环境调优）
- 包体积：<50MB
- 网络：国内环境，优先端侧
- 包名：com.ew.handscript
- GitHub/本地项目名：ew-handscript-android
- 模型大小上限：30MB（实际TFLite模型约1.6MB，INT8量化）

## 7. 已确认决策（截至2026-06-11）

### 7.1 产品决策
- 产品中文名：蚯蚓.手书修仙传
- 英文名：Eruca HandScript: Cultivation Saga
- 简称：EHS
- MVP排除：联网宗门/区块链/天道功德榜
- 印刷体自动拒绝
- 输出分辨率：300dpi（印刷级）
- MVP不加任何水印
- 用户协议：首次弹窗+设置可查

### 7.2 经济系统
- D01: 灵石（已定）
- D03: 字体美化度默认80%原迹+20%美化，用户可配置
- D04: MVP相克惩罚仅扣经验，不扣灵石
- D06: 炼字炉=3同五行字合成1金字候选
- 灵石初始值：新用户赠送100灵石
- 相克惩罚：仅扣经验（不扣灵石），触发心魔干扰块

### 7.3 交互与UI
- 境界命名：统一为"炼气期/筑基期/金丹期..."，格式为"炼气期·三层"
- 字块（Glyph Block）：80dp×80dp，纯汉字，消消乐网格用
- 卡片（Glyph Card）：104dp×120dp，含标签，瀑布流用
- 双层卡片：未触碰显示预切字，触碰后透出底稿（方圆切割）
- 五行颜色：木#4CAF50/火#F44336/土#795548/金#FFD700/水#1E88E5
- 状态用边框色，属性用左上角小徽章，避免一个卡片两种绿色

### 7.4 技术决策
- 字体生成流水线：本地Python脚本（Potrace+fontTools），不依赖云端
- Agent-B输入：单字PNG（透明背景）+ 元数据JSON
- Agent-B输出：TTF字体文件 + 字体元数据JSON
- TFLite模型：wuxing_feature_extractor.tflite ≈ 986KB, print_filter.tflite ≈ 650KB
- 端侧推理优先，模型不存在时返回Mock五行数据兜底
- 后端/字体流水线：暂不集成进Android MVP，独立Python脚本运行
- **OpenCV配置**：Maven依赖 `com.quickbirdstudios:opencv:4.5.3.0`（已验证Mate30兼容）
- **OpenCV API注意**：该Maven版仅提供 `initDebug()`，**无 `initLocal()`**（较新版本API），初始化时必须使用 `initDebug()`
- **Timber配置**：Maven依赖 `com.jakewharton.timber:timber:5.0.1`
- **WorkManager配置**：Maven依赖 `androidx.work:work-runtime-ktx` + `androidx.hilt:hilt-work` + `androidx.hilt:hilt-compiler`（kapt），用于HiltWorkerFactory
- **.gitignore**：已配置标准Android/Kotlin模板（解决AS编译暴躁问题）
- **gradle.properties**：已配置JVM堆内存、并行编译、非传递性R类

### 7.5 依赖版本（已锁定）
- Android Gradle Plugin: 8.5.0
- Kotlin: 1.9.24
- Compose BOM: 2024.02.00
- TFLite: 2.14.0
- Hilt: 2.51.1
- Room: 2.6.1
- OpenCV: 4.5.3.0（Maven，com.quickbirdstudios）
- Timber: 5.0.1
- WorkManager: 已配置（work-runtime-ktx + hilt-work + hilt-compiler）
- Paddle-Lite: 已注释（暂不启用，降低编译复杂度）

## 8. 平台协作规范（v0.8.1 — 新增跨平台交转机制）

### 8.1 平台分工矩阵（终局版）

| 平台 | 核心职责 | 准入条件 | 退出条件 | 绝对禁止 |
|------|---------|---------|---------|---------|
| **Kimi K2.6** | 长程规划、PRD/架构书、全量代码生成、多Agent协调 | 项目启动、需求冻结、代码生成期 | 编译阻塞≥3轮或真机调试启动 | 不直接调试Gradle/不读取AS状态 |
| **千问** | 基础设施诊断、.gitignore/AS配置、工具链选型、开放性问题 | IDE环境异常、Git方案设计 | 方案确认后 | 不生成业务代码 |
| **Cursor** | IDE内代码调试、依赖修复、API对齐、配置优化 | 代码已入库、首次编译失败 | 额度耗尽或真机测试启动 | 不替代Kimi做架构设计 |
| **Trae** | IDE内架构补全、Hilt注入完善、AS环境调优、真机测试辅助 | Cursor额度耗尽或架构层阻塞 | 项目完成 | 同Cursor |
| **Android Studio** | 真机部署、日志查看、性能分析 | 编译通过后 | 项目完成 | 不替代AI做代码生成 |
| **Owner（人类）** | 决策确认、信息路由、本地操作、Wiki更新 | 全程 | 全程 | 不直接编写业务代码 |

### 8.2 六阶段项目SOP（推荐机制）

#### 阶段0：创意孵化（Kimi单窗口，1-2天）
- 交付物：产品概念一句话、目标用户画像、核心玩法定义
- 退出条件：创始人确认"这就是我要做的"

#### 阶段1：双冻结+基础设施（Kimi多Agent + 千问，2-3天）
- 交付物：PRD v1.0 + 技术架构书 v1.0 + UI/IXD定义 v0.1
- 冻结物：PRD、架构书、技术选型（依赖版本锁定）
- 关键动作：
  - 生成`libs.versions.toml`完整版（含所有依赖，包括边缘依赖如OpenCV/Timber/WorkManager）
  - 生成`.gitignore`（Android/Kotlin标准模板）
  - 生成`gradle.properties`（JVM堆内存、并行编译、非传递性R类）
  - 配置Ubuntu私有Git中心（receive.denyCurrentBranch updateInstead）
  - 全局搜索替换旧包名（如com.eruca→com.ew）
  - 检查并禁用AS冲突插件（如Clangd Support）
- 退出条件：Owner签字确认冻结

#### 阶段2：全量代码生成（Kimi多Agent并行，3-5天）
- 交付物：完整项目所有代码文件
- 关键动作：Agent交付前执行【交付自检】（8项门禁）；Owner执行`pre_compile_scan.sh`；**不编译**（只验证文件完整性+语法门禁）
- 退出条件：所有文件入库、扫描通过

#### 阶段3：首次编译与配置（交转Cursor + 千问，1-2天）
- 交付物：`BUILD SUCCESSFUL` + 可安装APK
- 工具：Cursor + 千问（环境诊断）+ Android Studio
- 关键动作：
  - 由IDE内AI解决Gradle配置、依赖下载、缓存清理
  - 由千问诊断.gitignore/AS配置等基础设施问题
  - 遵循"AI全局感知修改"原则：禁止手动修改build.gradle.kts，必须由AI IDE读取上下文后修改
  - 首次编译前执行：`rm -rf app/build && ./gradlew clean --no-build-cache`
- 退出条件：`./gradlew assembleDebug`成功

#### 阶段4：真机冒烟测试（AS+Trae，1-2天）
- 交付物：真机可运行、核心流程可走完
- 退出条件：首测通过（所有页面可见、无崩溃）
- 关键动作：记录所有阻塞点，更新Wiki"已知问题清单"

#### 阶段5：功能迭代与算法实测（Kimi+Cursor接力，持续）
- 交付物：功能完整版
- 工具：Kimi（算法/架构迭代）+ Cursor/Trae（调试）
- 关键动作：算法模块独立迭代→验证→转TFLite→Android集成

#### 阶段6：发布与运营（Owner主导）
- 交付物：应用商店上架、路演PPT、用户协议

### 8.3 质量门禁（跨平台通用版）

**Kimi阶段门禁**（Agent交付前必须自检）：
```
□ 无 << 双尖括号泛型错误
□ 无未闭合括号/引号/花括号
□ 包名统一（如 com.ew.handscript）
□ 无未使用 import
□ Room Entity 无 List/Map 字段
□ Room Entity 无 System.currentTimeMillis() 默认值
□ 所有 import 的类都存在（find确认）
□ 依赖映射表已提供（Arch必须提供，含WorkManager等边缘依赖）
□ .gitignore 已配置（Android/Kotlin标准模板）
□ gradle.properties 已配置（JVM堆内存、并行编译）
□ 所有 Repository/Engine 类有 @Inject 构造函数
□ 全局搜索无旧包名残留（如 com.eruca）
```

**Cursor/Trae阶段门禁**（编译前必须自检）：
```
□ rm -rf app/build && ./gradlew clean --no-build-cache
□ ./gradlew assembleDebug 成功
□ APK可安装到真机
□ 无启动崩溃（Crash）
□ 核心页面可见（无白屏/黑屏）
□ AS无插件冲突报错（如Clangd Support）
```

### 8.4 对话与上下文管理规范

1. **关门总结机制**：Kimi对话临近上限时，Owner发送"关门总结"，Kimi输出结构化总结
2. **Wiki为唯一事实来源**：任何新窗口启动必须上传Wiki+前序对话链接
3. **GitHub/私有Git中心为代码唯一事实来源**：本地修改后必须commit+push，避免多平台代码版本冲突
4. **跨平台接力日志**：Owner在Wiki中记录"当前使用平台"（如"2026-06-11 切换至Trae"），避免AI重复劳动

### 8.5 免费用户额度管理策略

| 平台 | 免费额度特点 | 使用策略 |
|------|-------------|---------|
| Kimi | 长文本强，对话轮次有限 | 集中用于规划+代码生成，不用于调试 |
| 千问 | 开放性强，基础设施诊断优 | IDE环境异常、.gitignore问题、Git方案设计 |
| Cursor | IDE内强，免费额度有限 | 仅用于编译阻塞和依赖修复 |
| Trae | 免费额度相对充足 | 接力Cursor，承担主力调试和架构补全 |

---

## 9. 当前阻塞与待办（2026-06-11）

### 9.1 P0阻塞项（功能未通）
| 优先级 | 事项 | 责任方 | 状态 | 备注 |
|---|---|---|---|---|
| P0 | 相机调用链打通 | Agent-D | 待启动 | 权限+CameraX+预览+拍照 |
| P0 | 相册调用链打通 | Agent-D | 待启动 | 权限+MediaStore+图片选择 |
| P0 | 扫描→九宫格切字流程 | Agent-D + Agent-A | 待启动 | 需PaddleOCR或OpenCV分割实测 |
| P0 | 页面跳转异常修复 | Agent-D | 待启动 | 首测发现的跳转问题 |

### 9.2 P1待办（算法与引擎）
| 优先级 | 事项 | 责任方 | 依赖 |
|---|---|---|---|
| P1 | TFLite真实模型替换占位文件 | Agent-A | 模型训练完成 |
| P1 | PaddleOCR集成与行检测实测 | Agent-A + Agent-D | 模型就位 |
| P1 | OpenCV局部分割（L3）实测 | Agent-D | 扫描流程通 |
| P1 | 字体生成Python脚本与Android集成 | Agent-B + Agent-Arch | V2评估 |

### 9.3 P2待办（优化与扩展）
| 优先级 | 事项 | 责任方 | 备注 |
|---|---|---|---|
| P2 | 双层卡片交互（质心扩展+方圆切割）实测 | Agent-IXD + Agent-D | 需手势识别精调 |
| P2 | 五行消消乐游戏逻辑实测 | Agent-D | 需TFLite真实输出 |
| P2 | 回滚阈值决策（500字？1000字？） | Agent-P | V1再评估 |
| P2 | 性能优化（Mate30卡顿/发热） | Agent-D | 首测后评估 |

## 10. 已知风险与规避（v0.8.1更新）

| 风险 | 影响 | 规避方案 | 状态 |
|---|---|---|---|
| LLM生成Kotlin泛型代码系统性缺陷（<<） | 编译崩溃 | 质量门禁+扫描脚本 | 已规范 |
| Agent跨窗口上下文丢失 | 修复引入新错误 | 禁止总控越权，强制原始窗口修复 | 已规范 |
| Mate30老设备性能（麒麟990+Android 10） | 推理延迟/卡顿 | TFLite INT8量化+GPU Delegate | 已规划 |
| 对话窗口超限导致上下文断裂 | 项目进度阻塞 | 关门总结+Wiki同步+GitHub备份 | 已运行 |
| 多Agent代码风格不一致 | 维护困难 | 统一包名、命名规范、中文注释 | 已规范 |
| Owner过度介入编码 | 分工混乱 | Owner禁止事项清单+Kimi监督 | 已规范 |
| Kimi越权生成修复代码 | 责任不清 | 强化红线：仅输出分诊单 | 已规范 |
| Agent交付质量系统性崩盘 | 编译阻塞 | 质量门禁+最大排查轮次+扫描脚本 | 已规范 |
| kapt错误提示无行号 | 排查困难 | 系统性扫描前置 | 已规范 |
| 依赖缺失后置发现 | 编译失败 | Arch必须提供完整依赖映射表 | 已规范 |
| 跨平台调试信息损耗 | Owner疲劳、进度阻塞 | 平台交转机制：Kimi→Cursor/Trae | 已规避 |
| **.gitignore缺失导致AS编译暴躁** | 无明确错误但构建异常 | **阶段1强制生成.gitignore** | **已规避** |
| **OpenCV Maven版API与官方SDK不一致** | `initLocal`不存在 | **确认Maven社区打包版API范围** | **已规避** |
| **IDE插件冲突（Clangd Support）** | AS内部错误 | **项目启动时禁用冲突插件** | **已规避** |
| **Gradle构建缓存污染** | 编译失败但无明确错误 | **首次编译前清理缓存** | **已规避** |
| **Hilt注入架构不完整** | 核心类无法实例化 | **交付时验证所有Repository/Engine有@Inject** | **已规避** |
| **SDK版本选择（16KB对齐）** | 安装失败 | **targetSdk 34适配老设备，升35需评估** | **已规避** |

## 11. 项目路径与文件结构（v0.8.1）

```
~/Projects/ew-handscript-android/
├── app/
│   ├── build.gradle.kts          ← Arch定版（OpenCV+Timber+WorkManager Maven依赖）
│   └── src/main/
│       ├── java/com/ew/handscript/
│       │   ├── HandCraftFontApp.kt
│       │   ├── MainActivity.kt
│       │   ├── core/
│       │   │   ├── render/HandwritingRenderEngine.kt      ← @Inject已补（Trae）
│       │   │   ├── render/LayoutComputationEngine.kt       ← @Inject已补（Trae）
│       │   │   ├── render/GlyphCacheImpl.kt               ← 新建（Trae）
│       │   │   ├── scan/ScanCorrectionEngine.kt             ← @Inject已补（Trae）
│       │   │   └── segmentation/GlyphSegmentationEngine.kt  ← @Inject已补（Trae）
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── Converters.kt
│       │   │   │   ├── FontDatabase.kt
│       │   │   │   ├── GlyphDao.kt
│       │   │   │   ├── GlyphEntity.kt
│       │   │   │   ├── GlyphStatistics.kt
│       │   │   │   ├── SourceDocumentEntity.kt
│       │   │   │   └── ExportHistoryEntity.kt
│       │   │   └── repository/GlyphRepository.kt           ← @Inject已补（Trae）
│       │   ├── di/
│       │   │   ├── DataModule.kt
│       │   │   └── DatabaseModule.kt                      ← 新建（Trae）
│       │   ├── ml/
│       │   │   ├── FiveElementValues.kt     ← Agent-A交付（纯数据类）
│       │   │   └── TFLiteHelper.kt          ← Agent-A交付（Mock兜底版）
│       │   ├── model/
│       │   │   ├── GlyphItem.kt             ← 11字段完整版（character/wuXingValues）
│       │   │   ├── GlyphModel.kt
│       │   │   └── typeset/
│       │   │       ├── FontConfig.kt
│       │   │       └── GlyphLayoutData.kt
│       │   ├── network/
│       │   │   ├── ApiContract.kt
│       │   │   └── ApiService.kt
│       │   └── ui/
│       │       ├── MainActivity.kt
│       │       ├── navigation/AppNavigation.kt
│       │       ├── theme/（Color.kt, Theme.kt, Type.kt）
│       │       └── screens/
│       │           ├── home/（HomeScreen.kt, HomeViewModel.kt）
│       │           ├── scan/（ScanScreen.kt, ScanViewModel.kt）
│       │           ├── proofread/（ProofreadScreen_Phase3.kt, DoubleLayerCard_Phase3.kt）
│       │           ├── verify/（VerifyUiState.kt, VerifyViewModel.kt）
│       │           ├── workspace/（WorkspaceUiState.kt, WorkspaceViewModel.kt）
│       │           ├── library/（LibraryUiState.kt, LibraryViewModel.kt）
│       │           ├── realm/（RealmScreen_Phase3.kt, FiveElementsPanel.kt, WuXingAwakening.kt, BreakthroughAnimation.kt）
│       │           ├── output/（OutputScreen.kt, HandwritingPreviewCanvas.kt）
│       │           └── settings/SettingsScreen.kt
│       ├── assets/
│       │   └── models/
│       │       ├── wuxing_feature.tflite     ← 占位文件（待替换真实模型）
│       │       └── print_filter.tflite       ← 占位文件（待替换真实模型）
│       └── res/（Android标准资源目录）
├── docs/
│   ├── prd/PRD-v1.0-EHS.md
│   ├── arch/技术架构书_v1.0.docx
│   ├── ui/UI设计定义_蚯蚓手书修仙传.md
│   ├── ixd/EHS_五行消消乐_交互状态机定义_v0.1.md
│   ├── algorithm/ehs_algorithm/（Python算法模块）
│   ├── backend/font_pipeline/（Python字体流水线）
│   └── Prompt模板库.md
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── gradle.properties              ← 已配置（JVM堆内存、并行编译）
├── .gitignore                     ← 已配置（Android/Kotlin标准模板）
└── 项目Wiki.md                    ← 本文件
```

## 12. 基础设施（v0.8.1新增）

### 12.1 Ubuntu私有Git中心
- **主机**：ubuntu-lan（局域网Ubuntu Server）
- **配置**：`receive.denyCurrentBranch updateInstead`
- **用途**：iMac/iPad/Mate30多终端代码同步与备份
- **操作**：本地commit后push，其他终端pull更新

### 12.2 IDE环境配置
- **JDK**：Android Studio嵌入式JDK（`/Applications/Android Studio.app/Contents/jbr/Contents/Home`）
- **禁用插件**：`Clangd Support`（C/C++语言支持插件，与当前项目冲突）
- **构建缓存清理**：`rm -rf app/build && ./gradlew clean --no-build-cache`

### 12.3 构建脚本规范
- **禁止手动修改**：`build.gradle.kts`、`libs.versions.toml`
- **必须通过AI IDE修改**：Trae/Cursor读取全项目上下文后全局对齐修改
- **版本一致性**：toml、gradle、wrapper版本必须严格一致

## 13. 变更日志

| 版本 | 日期 | 变更内容 |
|---|---|---|
| 0.1 | 2026-05-25 | 项目启动，iRP命名体系定稿，产品概念确立 |
| 0.2 | 2026-06-07 | 多Agent分工启动（P/Arch/UI/IXD/A/B/D），PRD与架构书冻结 |
| 0.3 | 2026-06-08 | Phase4-1导航联调完成，进入Phase4-2模型集成 |
| 0.4 | 2026-06-08 | Android端代码大规模交付，编译阻塞修复中 |
| 0.5 | 2026-06-09 | 确立Agent协作规范（诊断-分诊-转诊机制）、交付自检清单、关门总结SOP |
| 0.6 | 2026-06-09 | 补充Owner与Kimi总控窗口正式角色定义、工作规范、绝对禁止事项、信息流转图 |
| 0.7 | 2026-06-10 | 重大复盘：9轮排查暴露系统性方法论缺陷；新增编译前扫描脚本、质量门禁、最大排查轮次限制；core/依赖缺失待Arch修复，ml/待A确认 |
| 0.8 | 2026-06-11 | 里程碑：Phase5首测通过（华为Mate30真机可见全部页面）；新增平台交转机制（Kimi→千问→Cursor→Trae→AS）；更新六阶段项目SOP；更新风险矩阵；明确Phase4-2部分完成 |
| **0.8.1** | **2026-06-11** | **补充Cursor/Trae/千问实战细节：HiltWorkerFactory依赖修复、OpenCV initDebug API差异、20+Unresolved reference批量修复、Hilt @Inject架构补全、DatabaseModule/GlyphCacheImpl新建、.gitignore缺失诊断、Ubuntu私有Git中心、IDE插件冲突、构建缓存清理、gradle.properties配置；新增"基础设施"章节；修正16KB兼容性描述（targetSdk34规避）** |

---

## 14. 交接说明（致后续AI协作者）

如果你是新接入的AI（Cursor/Trae/Claude/ChatGPT等），请知悉：

1. **本项目已冻结的文档**：PRD v1.0、技术架构书_v1.0、UI/IXD定义_v0.1、本Wiki v0.8.1 —— **开发期不得修改**
2. **当前代码状态**：编译通过（`BUILD SUCCESSFUL`，Debug构建33秒），APK可安装，但功能未全通（相机/相册/扫描/识别/跳转待修复）
3. **已知编译问题已解决**：
   - HiltWorkerFactory依赖（已补充work-runtime-ktx + hilt-work + hilt-compiler）
   - OpenCV API差异（使用initDebug()，非initLocal()）
   - Room兼容性（无List/Map字段，无System.currentTimeMillis()默认值）
   - @Serializable冲突（已移除）
   - Hilt注入架构（所有Repository/Engine已添加@Inject）
   - 构建缓存（已清理并配置--no-build-cache流程）
4. **请优先处理P0阻塞项**：相机调用链 → 相册调用链 → 扫描→九宫格流程 → 页面跳转修复
5. **所有代码修改请通过GitHub/私有Git中心同步**：本地commit+push后，Owner将仓库链接提供给你
6. **修改build.gradle.kts时必须遵循"AI全局感知修改"原则**：读取全项目上下文后修改，确保toml/gradle/wrapper版本一致
7. **若遇AS编译异常**：优先检查.gitignore是否完整、是否禁用Clangd Support插件、是否使用嵌入式JDK

---

> **时不我予，我予时光。**  
> 越过山丘，拾回自己。

**Owner**：陈桂熙 | **首任AI项目经理**：Kimi K2.6 | **当前调试平台**：Trae + Android Studio  
**地点**：辽宁省锦州市  
**状态**：Phase 5 首测通过，进入Phase 6功能迭代 | **协作规范**：v0.8.1 生效 | **GitHub/私有Git**：ew-handscript-android
