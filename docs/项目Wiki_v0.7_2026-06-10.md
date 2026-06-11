# 蚯蚓.手书修仙传 | 项目Wiki | 版本: 0.7 | 日期: 2026-06-10

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
- [x] Phase4-1: 导航联调（已冻结，AppNavigation/AppNavigation完成）
- [ ] Phase4-2: TFLite模型集成与Android端编译（进行中 — **系统性代码质量修复，各Agent回修中**）
  - 子状态：build.gradle.kts + libs.versions.toml 已由Agent-Arch修复（v1.1）
  - 子状态：network/（ApiContract.kt + ApiService.kt）@Serializable已移除，LibraryLevel改为String
  - 子状态：model/（GlyphModel.kt + GlyphLayoutData.kt）@Serializable已移除，Room兼容性已修复
  - 子状态：data/local/（GlyphEntity.kt + ExportHistoryEntity.kt + SourceDocumentEntity.kt）Room兼容性已修复
  - 子状态：GlyphStatistics.kt 已创建（原缺失）
  - **子状态：core/ 依赖缺失（OpenCV + Timber），待Agent-Arch补配**
  - **子状态：ml/ 仍隔离，待Agent-A确认无@Serializable后移回**
- [ ] Phase5: 真机冒烟测试（待Phase4-2编译通过后启动）
- [ ] Phase6: 内测与迭代

## 6. 关键约束
- 设备：华为Mate30（麒麟990），Android 10
- 开发：iMac/MacAir + Android Studio + HBuilderX
- 包体积：<50MB
- 网络：国内环境，优先端侧
- 包名：com.ew.handscript
- GitHub/本地项目名：ew-handscript-android
- 模型大小上限：30MB（实际TFLite模型约1.6MB，INT8量化）

## 7. 已确认决策（截至2026-06-10）

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

### 7.5 依赖版本（已锁定）
- Android Gradle Plugin: 8.5.0
- Kotlin: 1.9.24
- Compose BOM: 2024.02.00
- TFLite: 2.14.0
- Hilt: 2.50
- Room: 2.6.1
- Paddle-Lite: 已注释（暂不启用，降低编译复杂度）
- **OpenCV: 待Agent-Arch确认配置方式（本地模块 or Maven）**
- **Timber: 待Agent-Arch补充依赖**

## 8. Agent协作规范（v0.7 — 重大更新：系统性质量门禁）

### 8.1 全员角色分工矩阵

| 角色 | 代号 | 核心职责 | 关键交付物 | 绝对禁止 |
|---|---|---|---|---|
| **Owner/创始人** | **Owner** | **项目决策、资源协调、跨Agent信息路由、本地环境操作** | **决策确认、分诊单转发、真机测试报告、Wiki更新** | **不直接编写业务代码（Kotlin/Python）** |
| **Kimi总控窗口** | **Kimi** | **编译诊断中心、进度同步、规范制定、上下文续杯管理** | **分诊单、关门总结、Wiki更新、协作SOP** | **不直接生成修复代码（不输出.kt/.py代码块）** |
| 产品架构师 | Agent-P | PRD、修仙体系世界观、经济系统数值平衡 | PRD v1.0、灵石汇率表、境界经验曲线 | 不碰代码 |
| 技术架构师 | Agent-Arch | 五行算法映射、灵石数据结构、老设备兼容、依赖管理 | 技术架构书、Room Schema、依赖版本锁定、依赖映射表 | 不碰UI/业务逻辑代码 |
| UI设计师 | Agent-UI | 修仙风格界面、爆屏特效、境界突破动画 | UI设计稿、配色方案（修仙色系） | 不碰交互逻辑 |
| 交互设计师 | Agent-IXD | 五行消消乐手势、质心扩展、天劫关卡交互 | 交互流程图、状态机、手势映射表 | 不碰视觉样式 |
| 算法工程师 | Agent-A | 五行属性分类器、金字招牌检测、灵根觉醒 | Python算法包、TFLite模型、Mock数据 | 不碰Android业务代码 |
| Android开发 | Agent-D | 消消乐Compose、双层卡片、境界系统、本地数据库 | 完整Android项目（Kotlin） | 不碰算法模型训练 |
| 后端工程师 | Agent-B | 字体生成流水线、V2云端API预留 | Python脚本、Docker配置 | 不碰Android端 |
| QA测试 | Agent-Q | 消消乐流程、爆屏性能、Mate30兼容性 | 测试报告、Bug清单 | 不碰代码修复 |
| 文档/合规 | Agent-W | 修仙世界观文案、操作手册、时间戳存证 | Wiki更新、README、路演PPT | 不碰技术决策 |

### 8.2 Owner（创始人）工作规范

**Owner是项目唯一的人类决策节点，承担"路由器+执行器"职能。**

#### 8.2.1 核心职责
1. **决策确认**：对所有Agent提交的待决策项（DXX）进行最终拍板
2. **跨Agent信息路由**：将分诊单、接口契约、依赖变更等信息从总控窗口转发给对应Agent的原始对话窗口
3. **本地环境操作**：在iMac/Android Studio/终端中执行编译、安装、真机测试
4. **文档上传**：将本地文件（tree截图、报错截图、代码文件）上传至Kimi对话窗口
5. **Wiki同步**：每轮关键决策或修复完成后，更新本Wiki文档
6. **对话管理**：在对话临近上限时触发"关门总结"，新开窗口时上传核心文档+前序链接

#### 8.2.2 标准工作流程（SOP）
```
[Agent交付] → [Owner接收] → [本地验证/测试] → [发现问题] → [上传截图+上下文至Kimi总控]
                                                                    ↓
[Owner转发分诊单给责任Agent] ← [Kimi输出分诊单] ← [Kimi诊断]
        ↓
[Agent自修复并重新交付] → [Owner本地验证] → [通过则更新Wiki，不通过则下一轮分诊]
```

#### 8.2.3 Owner绝对禁止事项
- ❌ **不直接编写Kotlin/Python业务代码**（包括但不限于：修复泛型错误、修改ViewModel、调整Compose UI）
- ❌ **不直接修改Agent交付的代码文件**（即使是简单的sed替换，也应要求Agent自修复）
- ❌ **不替Agent做技术决策**（如"这个类应该叫XX"应交由Agent-D/Arch判断）
- ❌ **不跳过Kimi总控直接跨Agent修改**（如发现UI问题直接找Agent-UI修改代码，应先经总控诊断）

#### 8.2.4 Owner常用指令模板
```text
【决策确认】
待决策项 DXX：确认选择方案A，理由：...

【分诊转发】
@Agent-D 请查收错误分诊单（附Kimi诊断结论）...

【本地验证报告】
编译结果：BUILD SUCCESSFUL / BUILD FAILED
真机型号：华为Mate30
测试项：拍照→九宫格→双层卡片→输出预览
截图：见附件

【Wiki更新】
本轮变更：...
更新章节：...
```

### 8.3 Kimi总控窗口（K2.6思考/快速模型）工作规范

**Kimi是项目的"AI项目经理+编译诊断中心+规范守护者"，不承担任何代码产出职能。**

#### 8.3.1 核心职责
1. **编译诊断中心**：接收Owner上传的报错截图/日志，定位根因，判定责任Agent
2. **分诊单生成**：输出标准化的错误分诊单，由Owner转发给责任Agent
3. **进度同步**：汇总各Agent工作状态，更新项目阶段与阻塞项
4. **规范制定与守护**：制定并维护Agent协作SOP、交付自检清单、关门总结模板
5. **上下文续杯管理**：在对话临近上限时主动提醒，输出关门总结作为新窗口背景材料
6. **Wiki维护**：根据Owner提供的信息，协助生成Wiki更新内容

#### 8.3.2 标准工作流程（SOP）
```
[Owner上传报错/日志] → [Kimi分析Build Output/代码结构] → [定位根因]
                                                              ↓
[输出关门总结] ← [对话临近上限] ← [Kimi输出分诊单] ← [判定责任Agent]
        ↓
[Owner转发给新窗口/Agent] ← [Owner确认分诊单] ← [Kimi等待Owner反馈]
        ↓
[下一轮诊断或关闭阻塞项]
```

#### 8.3.3 Kimi绝对禁止事项（红线）
- ❌ **不直接生成修复代码**（不输出 `.kt` / `.py` / `.gradle` 代码块供Owner直接粘贴）
- ❌ **不输出sed/grep等全局替换命令**（这类命令应由Agent在其原始对话中生成）
- ❌ **不替Agent做技术实现决策**（如"这个类应该继承XX"应交由Agent-D/Arch判断）
- ❌ **不跨Agent修改其他Agent的交付物**（如发现Agent-D的代码有UI问题，应分诊给Agent-UI，而非直接修改）
- ❌ **不假设本地环境状态**（所有诊断必须基于Owner上传的实际截图/日志/tree）

#### 8.3.4 Kimi输出物标准格式
```text
【诊断结论】
根因：...
影响范围：...
责任Agent：...

【分诊单】（可直接复制转发）
...

【下一步建议】
1. Owner转发分诊单给Agent-XX
2. Agent自修复后附带【交付自检】
3. Owner本地验证后反馈

【Wiki更新建议】
建议更新章节：...
建议记录决策：...
```

### 8.4 信息流转图（项目协作拓扑）

```
                    ┌─────────────────┐
                    │   Owner（人类）   │
                    │  决策+路由+执行   │
                    └────────┬────────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ▼                ▼                ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
    │ 本地环境      │ │ Kimi总控窗口  │ │ 各Agent对话窗 │
    │ iMac/Android │ │ 诊断+规范+进度 │ │ P/Arch/D/A...│
    │ Studio/终端  │ │ 同步+上下文管理│ │ 代码产出     │
    └──────────────┘ └──────────────┘ └──────────────┘
            │                │                │
            │                │                │
            └────────────────┴────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │   项目Wiki.md    │
                    │  唯一事实来源    │
                    └─────────────────┘
```

**流转规则：**
- 所有Agent交付物 → Owner本地验证 → 通过则入库，不通过则上传至Kimi总控
- Kimi诊断结论 → 分诊单 → Owner转发 → 责任Agent原始对话窗口
- 关键决策/变更 → Owner确认 → Kimi协助更新Wiki → 所有新窗口启动时加载
- **禁止Agent之间直接对话或修改对方代码**（必须通过Owner+Kimi总控路由）

### 8.5 编译错误处理 SOP（标准作业程序）
1. **Owner本地验证**：执行编译/测试，发现问题保留首行红字截图
2. **上传至Kimi总控**：附上报错截图 + 当前代码tree + 最近操作记录
3. **Kimi诊断**：定位根因、判定责任Agent、生成【错误分诊单】
4. **Owner转发**：将分诊单完整复制，发送至责任Agent的原始对话窗口
5. **Agent自修复**：在原始对话窗口内修正代码，重新交付完整文件
6. **Agent交付自检**：交付时必须附带【交付自检】清单
7. **Owner本地验证**：粘贴新代码，重新编译/测试
8. **循环或关闭**：通过则更新Wiki，不通过则回到步骤2

### 8.6 交付自检清单（强制）
任何Agent交付Kotlin/Python代码前，必须在对话末尾附加：
```
【交付自检】
□ 无 << 双尖括号泛型错误（Kotlin）
□ 无未闭合的括号/引号/花括号
□ 包名统一为 com.ew.handscript（非 com.eruca）
□ 无未使用的 import（避免编译警告堆积）
□ 若修改 build.gradle.kts，已声明所有版本号与BOM对齐
□ 已检查无未解析的引用（Unresolved reference）
□ 模型/算法代码已验证输入输出Shape匹配
□ 本次交付为完整文件（非diff/patch），可直接复制粘贴替换
```

### 8.7 错误分诊单模板
```markdown
【错误分诊单】
━━━━━━━━━━━━━━━━━━━━
错误现象：BUILD FAILED / Could not load module <Error module>
根因定位：系统性双尖括号泛型语法错误（List<< / StateFlow<< / Map<<）
影响范围：15个 Kotlin文件（附清单）
责任Agent：Agent-D（Android端开发）
━━━━━━━━━━━━━━━━━━━━
【要求】
1. 请在你的原始对话窗口内，对你交付的所有文件进行语法自检
2. 全局搜索 << 替换为 <
3. 重新输出修正后的完整文件（不要只给diff）
4. 输出前附加【交付自检】声明
━━━━━━━━━━━━━━━━━━━━
【附件】
- 错误截图：Build Output首行红字
- 受影响文件树
```

### 8.8 对话管理与关门总结
- **关门总结机制**：当对话临近上限或Owner发送"关门总结"指令时，Kimi输出结构化总结（窗口角色、核心决策、待办/阻塞、下一步建议）
- **新窗口启动**：新对话必须上传核心文档（PRD/架构书/Wiki）+ 前序对话链接
- **Wiki同步**：每轮关键决策或修复完成后，Owner需同步更新本Wiki

### 8.9 【新增】编译前系统性扫描脚本（v0.7）

**Owner在移回任何隔离代码前，必须先运行此脚本，一次性发现所有问题。**

```bash
#!/bin/bash
# pre_compile_scan.sh
# 运行方式：cd ~/Projects/ew-handscript-android && bash pre_compile_scan.sh

echo "=== 1. 扫描 @Serializable ==="
grep -rn "@Serializable" app/src/main/java/ | grep -v "//" | grep -v "ApiService.kt"

echo "=== 2. 扫描 Room Entity 的 List/Map 字段 ==="
find app/src/main/java/ -name "*.kt" -exec grep -l "@Entity" {} \; | while read f; do
    echo "File: $f"
    grep -n "List<<\|Map<<" "$f" | grep "val\|var"
done

echo "=== 3. 扫描 Room Entity 的 System.currentTimeMillis() ==="
find app/src/main/java/ -name "*.kt" -exec grep -l "@Entity" {} \; | while read f; do
    grep -n "System.currentTimeMillis()" "$f" | grep "val\|var"
done

echo "=== 4. 扫描 DAO 引用的自定义类 ==="
grep -rn "data class\|class " app/src/main/java/ | grep -v "import" | grep -v "interface"

echo "=== 5. 扫描外部依赖 import ==="
grep -rn "^import " app/src/main/java/ | grep -v "android\|androidx\|kotlin\|java\|javax" | sort | uniq

echo "=== 6. 扫描 << 双尖括号 ==="
grep -rn "<<" app/src/main/java/ | grep -v "//" | grep -v "import" | grep -v "kotlinx"
```

### 8.10 【新增】Agent交付质量门禁（v0.7）

**任何Agent交付代码前，必须完成以下自检，未通过直接退回，不进入Owner视野。**

| 门禁项 | 检查方式 | 未通过后果 |
|--------|----------|-----------|
| 无 @Serializable | `grep -c "@Serializable" == 0` | 退回 |
| 无 @SerialName | `grep -c "@SerialName" == 0` | 退回 |
| Room Entity 无 List/Map | `grep -c "List<<\|Map<<" == 0` | 退回 |
| Room Entity 无 System.currentTimeMillis() | `grep -c "System.currentTimeMillis()" == 0` | 退回 |
| 所有 import 的类都存在 | `find` 确认 | 退回 |
| 所有 DAO 返回的类都有文件 | `find` 确认 | 退回 |
| 无 << 非法字符 | `grep -c "<<" == 0`（非注释/非泛型） | 退回 |
| 依赖映射表已提供 | Arch 必须提供 | 退回 |

### 8.11 【新增】最大排查轮次限制（v0.7）

| 轮次 | 动作 |
|------|------|
| 1-2 | 正常排查 |
| 3 | 触发预警，Kimi 建议暂停，输出系统性扫描报告 |
| ≥4 | **强制停止**，所有 Agent 回修，Kimi 输出系统性扫描报告 |

**2026-06-09 实际排查 9 轮，严重违反此机制，已复盘并写入规范。**

## 9. 当前阻塞与待办（2026-06-10）

### 9.1 P0阻塞项
| 优先级 | 事项 | 责任方 | 状态 |
|---|---|---|---|
| P0 | core/ 依赖缺失（OpenCV + Timber） | Agent-Arch | 已分诊，待Arch补配依赖 |
| P0 | ml/ 目录确认无 @Serializable 后移回 | Agent-A | 已分诊，待A确认 |
| P0 | 各Agent交付代码需通过质量门禁 | 全员 | 新规范生效，待执行 |

### 9.2 P1待办
| 优先级 | 事项 | 责任方 | 依赖 |
|---|---|---|---|
| P1 | 连接Mate30真机Run，冒烟测试 | Owner | P0编译通过 |
| P1 | 全流程验证：拍照→九宫格切字→双层卡片校对→输出预览 | Owner | P0编译通过 |
| P1 | TFLite模型真机性能测试（麒麟990推理延迟） | Agent-A | P0编译通过 |

### 9.3 P2待办
| 优先级 | 事项 | 责任方 | 备注 |
|---|---|---|---|
| P2 | Mock版TFLiteHelper.kt算法逻辑确认 | Agent-A | 已与Agent-D接口对齐 |
| P2 | 字体生成Python脚本（Agent-B）与Android端集成方案 | Agent-Arch | V2再评估 |
| P2 | 回滚阈值决策（500字？1000字？） | Agent-P | V1再评估 |

## 10. 已知风险与规避

| 风险 | 影响 | 规避方案 | 状态 |
|---|---|---|---|
| LLM生成Kotlin泛型代码系统性缺陷（<<） | 编译崩溃 | 建立交付自检清单+Agent自修复 | 已规范，待执行 |
| Agent跨窗口上下文丢失 | 修复引入新错误 | 禁止总控窗口越权编码，强制原始窗口修复 | 已规范 |
| Mate30老设备性能（麒麟990+Android 10） | 推理延迟/卡顿 | TFLite INT8量化+GPU Delegate+模型降级Mock | 已规划 |
| 对话窗口超限导致上下文断裂 | 项目进度阻塞 | 关门总结机制+Wiki同步+核心文档云盘备份 | 已运行 |
| 多Agent代码风格不一致 | 维护困难 | 统一包名、命名规范、中文注释 | 已规范 |
| Owner过度介入编码 | 分工混乱、代码质量不可控 | Owner禁止事项清单+Kimi监督提醒 | 已规范 |
| Kimi越权生成修复代码 | 责任不清、引入新错误 | Kimi红线清单+仅输出分诊单 | 已规范 |
| Agent交付质量系统性崩盘 | 编译阻塞、Owner疲劳 | **质量门禁+最大排查轮次+系统性扫描脚本** | **v0.7新增** |
| kapt错误提示无行号 | 排查困难 | 系统性扫描脚本前置，二分法仅作辅助 | **v0.7新增** |
| 依赖缺失（OpenCV/Timber） | 编译失败 | Arch必须提供依赖映射表 | **v0.7新增** |

## 11. 项目路径与文件结构

```
~/Projects/ew-handscript-android/
├── app/
│   ├── build.gradle.kts          ← Arch定版（BOM 2024.02.00 + TFLite 2.14.0 + Hilt 2.50）
│   └── src/main/java/com/ew/handscript/
│       ├── HandCraftFontApp.kt
│       ├── core/
│       │   ├── render/HandwritingRenderEngine.kt
│       │   ├── render/LayoutComputationEngine.kt
│       │   ├── scan/ScanCorrectionEngine.kt
│       │   └── segmentation/GlyphSegmentationEngine.kt
│       ├── data/
│       │   ├── local/（Converters.kt, FontDatabase.kt, GlyphDao.kt, GlyphEntity.kt, SourceDocumentEntity.kt, ExportHistoryEntity.kt, GlyphStatistics.kt）
│       │   └── repository/GlyphRepository.kt
│       ├── ml/
│       │   ├── TFLiteHelper.kt     ← Agent-A交付（Mock兜底版）
│       │   └── WuXingResult.kt     ← 数据类，与Agent-A接口对齐
│       ├── model/
│       │   ├── GlyphItem.kt
│       │   ├── GlyphModel.kt
│       │   └── typeset/（FontConfig.kt, GlyphLayoutData.kt）
│       ├── network/（ApiContract.kt, ApiService.kt）
│       ├── ui/
│       │   ├── MainActivity.kt
│       │   ├── navigation/AppNavigation.kt
│       │   ├── theme/（Color.kt, Theme.kt, Type.kt）
│       │   └── screens/
│       │       ├── home/（HomeScreen.kt, HomeViewModel.kt）
│       │       ├── scan/（ScanScreen.kt, ScanViewModel.kt）
│       │       ├── proofread/（ProofreadScreen_Phase3.kt, DoubleLayerCard_Phase3.kt）
│       │       ├── verify/（VerifyUiState.kt, VerifyViewModel.kt）
│       │       ├── workspace/（WorkspaceUiState.kt, WorkspaceViewModel.kt）
│       │       ├── library/（LibraryUiState.kt, LibraryViewModel.kt）
│       │       ├── realm/（RealmScreen_Phase3.kt, FiveElementsPanel.kt, WuXingAwakening.kt, BreakthroughAnimation.kt）
│       │       ├── output/（OutputScreen.kt, HandwritingPreviewCanvas.kt）
│       │       └── settings/SettingsScreen.kt
│       └── di/DataModule.kt
├── docs/
│   ├── prd/PRD-v1.0-EHS.md
│   ├── arch/技术架构书_v1.0.docx
│   ├── ui/UI设计定义_蚯蚓手书修仙传.md
│   ├── ixd/EHS_五行消消乐_交互状态机定义_v0.1.md
│   ├── algorithm/ehs_algorithm/（Python算法模块）
│   ├── backend/font_pipeline/（Python字体流水线）
│   └── Prompt模板库.md
├── gradle/libs.versions.toml
└── 项目Wiki.md                    ← 本文件
```

## 12. 变更日志

| 版本 | 日期 | 变更内容 |
|---|---|---|
| 0.1 | 2026-05-25 | 项目启动，iRP命名体系定稿，产品概念确立 |
| 0.2 | 2026-06-07 | 多Agent分工启动（P/Arch/UI/IXD/A/B/D），PRD与架构书冻结 |
| 0.3 | 2026-06-08 | Phase4-1导航联调完成，进入Phase4-2模型集成 |
| 0.4 | 2026-06-08 | Android端代码大规模交付，编译阻塞修复中 |
| 0.5 | 2026-06-09 | 确立Agent协作规范（诊断-分诊-转诊机制）、交付自检清单、关门总结SOP；定位系统性<<编译错误；更新当前阻塞与风险矩阵 |
| 0.6 | 2026-06-09 | 补充Owner（创始人）与Kimi总控窗口的正式角色定义、工作规范、绝对禁止事项、信息流转图；定位network/目录为kapt崩溃触发器（ApiContract.kt引用不存在的LibraryLevel类 + kotlinx-serialization与kapt冲突）；Agent-Arch修复build.gradle.kts+libs.versions.toml（v1.1）；确立诊断-分诊-转诊协作机制 |
| **0.7** | **2026-06-10** | **重大复盘：9轮排查暴露系统性方法论缺陷；新增编译前系统性扫描脚本（pre_compile_scan.sh）；新增Agent交付质量门禁（8项强制检查）；新增最大排查轮次限制（≥4轮强制停止）；更新风险矩阵（新增Agent交付质量崩盘、kapt无行号、依赖缺失）；当前状态：core/依赖缺失待Arch修复，ml/待A确认，全员进入质量门禁阶段** |

---

> **下次更新触发条件**：
> 1. Agent-Arch补配OpenCV+Timber依赖且编译通过
> 2. Agent-A确认ml/无@Serializable且移回编译通过
> 3. 任何关键决策变更（DXX新增或修改）
> 4. 新阶段启动（Phase5或更高）
> 5. 协作规范执行中发现漏洞需修补

Owner：陈桂熙 | 总控窗口：Kimi K2.6 | 状态：core/依赖缺失待Arch修复，ml/待A确认，质量门禁生效 | 协作规范：v0.7 生效
