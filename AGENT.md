# EhViewer Revived 开发约定

本文件集中维护开发规则、经维护者确认的经验及构建入口。不要散建交付记录、验证报告或临时开发文档。测试结果放在 Actions 日志、Artifact 和 Release 说明中。每轮结束清理本轮生成的临时文件、过期安装包及临时测试材料。

长程任务或包含大量子任务时，每完成一个阶段或关键节点，都须在本文件的临时进度章节记录已完成事项、当前状态、关键提交/Actions 链接和下一步，便于中断后恢复。任务全部完成后删除整个临时进度章节，仅保留本条持久规则；记录不得包含密钥或密码。

验证优先放到 GitHub Actions 或同类 CI；本地仅执行必要的开发操作、私钥签名和难以交由 CI 的定向诊断。按本轮改动选择检查范围，UI、签名和构建变更优先验证布局、构建、签名及安装，不重复运行无关功能或业务逻辑检查。历史发布验证流程不构成每轮必须重跑全部场景的要求。

## 基线与发布

- 功能源自 `Hycnyl/EhViewer` 的 `v1.17.8`，对应清理后的提交 `92923877ef671e33aa56f69688daa46af6e59ec6`；新仓库独立起始，不继承旧 Git 历史。四种语言 README 继承旧仓库最新的用户文档与完整素材引用，保留 LICENSE、NOTICE 和源码版权说明。
- 应用名保持 `EhViewer`，正式包名保持 `moe.hycnyl.ehviewer`。当前正式版本为 `1.17.16` / `180091`，上个正式版为 `1.17.15` / `180090`，接续旧版 `1.17.8` / `180083` 和 `1.17.9` / `180084`。后续沿用版本体系，版本码递增。
- 任何实际影响用户的改动完成后，除非维护者明确禁止，都须提升 versionName/versionCode，使用 GitHub Actions 发布正式 Release；提供 universal、arm64-v8a、armeabi-v7a、x86_64、x86 五种 APK，并附简短变更说明与 SHA-256 校验和。不得使用 `[skip ci]` 绕过这条规则。
- README 保持简洁，变更通常只在“变更”章节简要记录；允许修正仓库链接、安装要求和迁移说明。不得随意重写其余内容或丢失截图、素材和致谢。
- 单轮只改一个目标不是硬性限制。缓存导出、下载恢复和归档格式继承 1.17.8 的当前行为，维护者没有将其固化为不可修改的长期规则。
- 原初始化未继承旧测试和基准模块。维护者随后明确要求恢复 Benchmark、Baseline Profile 自动生成与合并，并新增离线阅读器采集及独立 FrameTimingMetric 场景；按新需求维护这些有效检查，其他历史一次性测试材料仍不继承。

## 兼容性与组件选择

- 正式版以 Min SDK 26、Target SDK 36 为兼容基准，允许提高编译 SDK。尽可能保留低版本 Android 兼容性。
- 工具链优先采用可兼容的新版稳定组件；UI 及相关组件可以采用 Alpha/Beta/预览版。数据库、网络、缓存、编解码等功能相关组件优先稳定性。
- 组件升级若出现局部兼容问题可修复；若需大范围改变应用行为，停止该构建尝试，先回退到可兼容的最高版本并向维护者说明、询问。

## 经确认继承的经验

- 文件导出先暂存、校验长度/CRC，再提交。SAF 不支持原子重命名时使用未完成标记。失败清理仅限本轮未提交产物，不删除源文件或已经安全提交的结果。（参考旧 `docs/CACHE_RESCUE.md`，经维护者确认）
- 优先兼容现有数据库和设置键。阅读缓存沿用 `read_cache_size_2` 的 Int MB 配置，先转 Long 再计算字节；不因改变容量而迁移或清空缓存，复用原有重启生效流程。（参考旧 `docs/DELIVERY-1.15.3.md`，经维护者确认）
- 如实区分静态检查、编译、JVM 测试、模拟器与真机/SAF 验证。未实际执行不得标记通过，构建通过不能替代功能验收。（参考旧交付记录，经维护者确认）
- 新增文案优先完整覆盖英文和简繁中文，校对格式占位符及回退；其他已有语言尽量同步。（参考旧 `docs/DELIVERY-1.15.3.md`，经维护者确认）

## 签名与迁移

- 正式证书 SHA-256：`67afb9dab5d2234ed83bc3dd8c13faaeadf4940be0a6749e0aafb9ae9e8e143f`。正式包使用这张证书的 V2 和 V3 签名；不使用旧公开私钥，不携带旧轮换链。不生成临时替代证书，密钥缺失时恢复维护者备份。
- 密钥、密码与备份不得进入 Git、日志、Artifact 或 Release。当前本地保管目录为 `/workspace/.ehviewer-revived-signing`，目录权限 0700，文件 0600；工作区或环境删除前必须由维护者保有独立备份。
- 维护者允许在连接具备 Actions Secrets 权限时将密钥存入 Secrets；当前连接实测管理 Secrets 返回 HTTP 403，因此当前流程只能本地签名，不上传私钥。
- Actions 构建未签名 APK；用 `tools/release.py sign` 在本地校验构建来源、证书与文件后签名。仅回交签好的 APK、校验和及公开构建信息到私有 Release 草稿，Actions 再验证并公开发布。
- 本地 CLI 凭据失效时，可使用已授权的 GitHub 连接器完成源码提交；受限的远程收尾可交由最小权限临时 Actions，完成后删除流程文件。不得打印或转存访问令牌。
- 当前连接本地上传 Release 附件返回 HTTP 401。将上述七个公开文件打包成 ZIP，通过 Git Data blobs API 交接（不创建分支或提交），以 `signed_blob` 和 `bundle_sha256` 输入启动 `release.yml`；Actions 先校验包内容、来源与签名，再上传草稿。APK 的 `source_commit` 固定为实际构建提交，工作流本身可以在其后的提交中修正。
- 连接器限制单次请求大小时，将公开 ZIP 按每片不超过 3 MiB 原始数据分块，使用 `ehviewer-release-parts-v1` 清单交接；`signed_blob` 为清单 Git SHA，`bundle_sha256` 仍是原 ZIP 的 SHA-256。Release CI 逐片核对长度和 Git SHA，组合后复核完整 ZIP 的两种摘要及七文件集合，继续执行来源和签名验证。
- CI 为已构建的历史 SHA 创建新草稿时，可先保留一个指向该 SHA 的临时来源分支头，避免 GitHub 对非分支顶端目标的 403；发布后删除该分支，正式标签始终指向真实 APK 构建提交。
- 旧 1.17.9 已使用同一张新证书，可以直接覆盖安装。旧 1.17.8 及更早版本使用不同签名：Android 13+ 可先安装旧仓库的 1.17.9，再升级 Revived；Android 8–12 须先导出备份、卸载旧版、再安装并导入。设置/数据库备份不包含全部图片；卸载前，须把备份、下载和缓存导出文件复制到应用私有目录（如 Android/data/moe.hycnyl.ehviewer）之外，确认后再卸载。新装后重新授权下载目录并导入备份。

## 构建入口

- 构建：`./gradlew :app:assembleDefaultRelease --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`。正式构建没有 Gradle signingConfig，不需要签名凭据。
- 打包：`python3 tools/release.py package --input app/build/outputs/apk/default/release --output delivery`。
- 本地签名：`python3 tools/release.py sign --input <Actions未签名产物目录> --output <新输出目录> --vault /workspace/.ehviewer-revived-signing`。设置 ANDROID_HOME；使用已安装的固定版 Build Tools。
- 公开产物复核：`python3 tools/release.py verify --input <已签名产物目录>`。来源提交、版本、包名、SDK、原生 ABI、ZIP CRC、16 KiB ZIP 对齐、证书及 V2/V3 均须匹配。
- Release CI 使用 API 35 离线场景执行 `:app:generateBaselineProfile -PbuildAbis=x86_64`，检查两套阅读器覆盖后再组装全部正式 ABI。生成文本存于 Git 忽略的 `app/src/main/generated/baselineProfiles/`，正式包须包含非空 `assets/dexopt/baseline.prof` 和 `.profm`。开发构建默认不启动模拟器，需要自动采集时显式 `-PgenerateBaselineProfile=true`。
- 维护者明确要求快速发布、工具链及核心阅读路径未变时，可通过 build.yml 的 `reuse_profile_run` 复用最新正式版成功构建中的 Profile 文本；CI 核对来源、构建配置和 Artifact 摘要后，按当前 DEX 重新编译。release.yml 的 `quick_release=true` 将设备检查缩为 API 33 安装/升级并省略可选 UI 套件，保留五包构建、来源、签名及公开下载完整性验证；默认流程仍自动重新采集并使用常规检查范围。
- Baseline Profile 1.5 会在 finalizeDsl 中复制原 Release manifest；benchmark/nonMinified 变体的专用 overlay 必须在其后配置。测试内容只进入独立 `.benchmark` 包，正式 APK 不含夹具入口。采集端 `Rule.collect` 的 `filterPredicate` 必须排除整个夹具包，不能仅依赖 consumer filter：Startup 规则可能在后续合并时重新进入 Baseline。
- 帧测试按六个独立矩阵作业执行，每场景使用全新 AVD、关闭 Wi-Fi/移动网络并加载固定本地内容，以 CompilationMode.Full 各测量三次；版本/依赖/内容/构建配置一致后再对比。runtime-tracing 1.13 使用 Tracing 2.0/tracing-wire；仅在 benchmarkRelease 显式 -PtraceCompose=true 时引入（performance.yml 的 trace_compose 选项）。Macrobenchmark 1.5 会自动连接此依赖，但其写出在软件模拟器中复现偶发 60 秒超时，默认 Frame 测量仅用标准系统 Perfetto，详细 Compose 追踪按需开启。不要启用仅对应旧 tracing-perfetto 的 perfettoSdkTracing 开关。高开销的重组状态读写追踪只用于必要定向诊断。
- ProfileInstaller 保持官方最新稳定版 1.4.1（V015_S 向前兼容）；发布设备检查实际调用安装 receiver，并要求成功转码/写入 Profile。帧数据附原始 JSON/Perfetto，软件渲染模拟器不得被描述为真机 FPS 或硬件 GPU 验证。
- APK 根目录的标准 Kotlin 工具元数据由 `GenerateKotlinToolingMetadataTask` 从实际 KGP、Gradle 和 Java 编译配置生成，通过 `ScopedArtifact.JAVA_RES` 随每个变体打包，供 LibChecker 等工具识别。Java 字段表示 compileOptions 的源码/字节码兼容级别。


## 当前工具链与升级取舍

版本经官方 Maven 元数据、SDK 仓库及上游 Release 核对后固定，禁止使用动态版本。工具链和 UI 的升级不作为修改数据库、网络协议或用户文件格式的理由。

| 组件 | 1.17.8 基线 → Revived | 取舍 |
| --- | --- | --- |
| JDK | 本环境 21.0.9 → 21.0.13+9 | 使用有安全修复的 JDK 21；保持 JVM 目标和 Android 脱糖兼容 |
| Gradle / AGP | 9.2.1 / 8.13.2 → 9.8.2 / 9.5.0 | 稳定版；同步迁移 AGP 内建 Kotlin 和 KMP DSL，固定 Wrapper SHA-256 |
| Kotlin / KSP | 2.3.0 / 2.3.4 → 2.4.21 / 2.3.13 | 稳定版；Compose 编译插件与 Kotlin 同版本 |
| 编译 SDK / Build Tools | 36 / 36.0.0 → 37.2 / 37.0.0 | 采用新版构建能力；应用 Min 26、Target 36，避免额外 Target 行为迁移 |
| NDK / CMake | 29.0.14206865 / 3.22.1 → 30.0.16248370 / 3.31.6 | 稳定原生工具链；CMake 使用最新 3.x，避免 4.x 移除旧策略导致的额外依赖迁移 |
| Rust | nightly-2025-11-15 → nightly-2026-11-03 | 保留项目需要的 nightly 功能，固定日期、四种 Android target 及 rust-src |
| Compose Multiplatform / BOM alpha | 1.9.3 / 2025.12.00 → 1.13.0-beta02 / 2026.10.01 | UI 采用成套预览版本；Material 3 对应 Jetpack 1.5.0-beta02 |
| Adaptive / Activity / Biometric | 1.2.0 / 1.12.1 / 1.4.0-alpha04 → 1.4.0-alpha01 / 1.14.0-alpha03 / 1.4.0-alpha07 | 按维护者偏好使用 UI 预览能力 |
| Compose Preference / MaterialKolor / Telephoto | 2.1.0 / 4.0.5 / 0.18.0 → 2.2.0 / 5.0.1 / 0.19.0 | 更新设置、色彩和缩放组件；MaterialKolor 6 改模块与色彩引擎，本轮选兼容旧接口的 5.x；Telephoto Maven 最高版本号不代表当前发布线；上游最新正式 Release 为 0.19.0。1.0 Alpha 接口不匹配，已停止该尝试，经维护者确认采用 0.19.0，不重构阅读器 |
| AppCompat / Core / Browser / WebKit | 1.7.1 / 1.17.0 / 1.9.0 / 1.14.0 → 1.8.0 / 1.19.1 / 1.10.0 / 1.18.0 | 稳定版 Android 支持组件 |
| Room / DataStore / Work / Paging / Lifecycle | 2.8.4 / 1.2.0 / 2.11.0 / 3.3.6 / 2.10.0 → 2.8.5 / 1.2.1 / 2.12.0 / 3.5.3 / 2.11.0 | 功能组件采用稳定版，保持应用 schema 与设置键；DataStore 1.3.0 在 Android 16 首次读取设置时出现等待，回退至相邻稳定维护版 1.2.1，通过 API 26/33/36 启动验证及 API 33 覆盖升级、设置保留检查 |
| Coil / OkHttp / Okio / Ktor | 3.3.0 / 5.3.2 / 3.16.4 / 3.3.3 → 3.6.3 / 5.5.1 / 3.18.2 / 3.6.1 | 同主版本稳定更新 |
| Coroutines / Serialization / Datetime / IO | 1.10.2 / 1.9.0 / 0.7.1 / 0.8.2 → 1.11.0 / 1.12.0 / 0.8.0 / 0.9.2 | 固定稳定版本；不采用兼容旧日期类型的特殊分支 |
| Arrow / moko-resources / AboutLibraries / Spotless | 2.2.1.1 / 0.25.2 / 13.1.0 / 8.1.0 → 2.3.0 / 0.27.1 / 15.3.0 / 8.11.0 | 采用支持新版 Kotlin/AGP 的稳定组件 |
| libarchive / XZ / Corrosion | 3.8.4 / 5.8.1 / 0.6 → 3.8.11 / 5.8.4 / 0.6.1 | 原生稳定修复版，保留项目原有补丁 |
| Baseline Profile / Macrobenchmark / UI Automator | 恢复 → 1.5.0 / 1.5.0 / 2.4.1 | 固定稳定版；Release 自动采集，阅读器使用本地内容，帧测量和追踪按场景独立执行 |
| 保留的依赖 | XMLUtil 0.91.3、WebP 1.6.0、Nettle 3.10.2、Rust 运行依赖锁文件 | XMLUtil 1.x 涉及编解码 API 迁移；其余保持原生 FFI 与解析格式兼容；TL 例外固定至同一 FooIbar fork 的 `051705e41fcc140bcb6affcca687589333976296`，上游提交仅同步 3 处 SIMD 方法名以适配新 nightly，未改变解析逻辑 |

核对入口：[AGP 9.5](https://developer.android.com/build/releases/agp-9-5-0-release-notes)、[Kotlin 发布](https://kotlinlang.org/docs/releases.html)、[Compose 兼容性](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)、[Material 3 发布](https://developer.android.com/jetpack/androidx/releases/compose-material3)、[libarchive](https://github.com/libarchive/libarchive/releases/tag/v3.8.11)、[XZ](https://github.com/tukaani-project/xz/releases/tag/v5.8.4)。

UI 模块保留 `allWarningsAsErrors`，仅用 `-Xwarning-level=DEPRECATION:warning` 将仍受支持的旧 API 弃用提示保留为日志警告，不隐藏它们；类型、ABI、其他编译警告仍阻止构建。预测返回、搜索和输入等兼容层保留现有交互语义，后续迁移新 API 时独立验证手势与输入行为。

发布验证按本轮改动选择场景。UI、签名和构建默认使用 API 26、33、36；Target 37 的候选将现代设备检查切换至 API 37.2。使用包含 WebView 的 google_apis 镜像，现代设备使用 google_apis_ps16k 并检查真实页大小；每次新建 AVD，分配 4096M RAM / 512M heap。验证设备端 APK SHA-256、文件式 universal/匹配 ABI 安装、上个正式版本覆盖安装和启动。UI 按改动选用 archive-ui 或 interaction-ui 工作流渲染实际组件，支持指定 APK 源码与更新验证夹具，避免测试修正触发重复原生构建。只有涉及相关业务功能的改动，才增加离线 CBZ、设置迁移等对应检查。临时数据不进入源码或 Release。

本托管环境先执行 `source /workspace/.ehviewer-env/activate.sh`，其中设置已固定的 JDK、SDK、Gradle/Cargo 缓存与本地 GNU m4 1.4.20。m4 是 Nettle 汇编的构建依赖；CI 检查并安装它。本地无 sudo，m4 安装于私有工具目录。Nettle 的源码包含预生成文件，CMake 必须保留其解包时间戳（DOWNLOAD_EXTRACT_TIMESTAMP TRUE）。

## 临时进度：1.17.17 Tooltip 与主题色彩

- 基线 1.17.16 / 180091，main=fa66030，工作区干净。当前任务只做 UI：原生 Plain Tooltip（长按/悬停、无障碍、保留点击）、配额恢复全程主题色、主题色彩扩展动态/预设/自定义；主题模式及全部无关设置保持原行为。计划 1.17.17 /180092。
- 已核对 FlClash v0.8.99（chen08209/FlClash，tag tree 68c71b8ef9b7486a224972eb371ff153c6b2de0f）的主题页、色板、预览与调色入口，仅参考预览/色板/自定义思路，用 Compose 实现，不移植 Flutter 代码或无关设置。已盘点约47处图标按钮及现有长按路径，下一步统一原生 Tooltip 包装与补齐简短本地化描述；保留画廊/阅读器既有长按手势。
- 继续 CI 优先且按 UI 精简验证：只做本轮配色/Tooltip 定向交互渲染及构建签名/API33安装升级，复用正式 1.17.16 Build 42341393511 的 Profile；不跑无关软件功能或性能测试。每个关键阶段更新本章，完成后删整章并清临时文件/来源分支。

- 实施完成：原生 IconTooltip（TooltipBox + PlainTooltip、非持久、非聚焦、原生上方/边缘定位），已覆盖36处图标按钮及动态 FAB/3处点击图标，补齐英/简繁/HK/日描述；保留原点击回调和图片/卡片长按区域，悬浮和行内按钮的父级对齐/偏移修饰符已移到提示锚点以保位置。配额移除警示色函数，保留真实用量与截断绘图，完全使用 Material 默认主题色。
- 新增独立配色区域：动态/八个预设/自定义；五种 Material 配色风格；配色预览；原生对话框十六进制输入及 HCT 滑块，确认保存、取消不保存。仅四个新增设置键，默认路径保留原壁纸方案；主题模式、纯黑、分类协调与原有列表/阅读/语言等设置未改。已添加本轮5个定向 UI 检查（明暗选择、调色确认/取消、长按和Hover），不跑旧无关UI或业务/性能套件。版本1.17.17 /180092，下一步格式整理并交CI构建/渲染。

- 必要格式整理已完成（app/core.ui spotlessApply），修正清理警示色时遗留的注释及包装表达式声明的格式问题；未运行本地软件/性能测试。ThemeScreen 差异仅新增一行 ThemeColorPreference，Settings 差异仅四个新配色键；默认壁纸与现有主题模式逻辑保留。下一步推送源码、只运行5项新 UI 检查和复用 Profile 的精简构建，并取消旧交互动画自动作业。

- 源码已提交并推送 `f046f63438912c053b27c87565fa04bd3fddfd92`，来源分支 release-source-1.17.17 与 Draft 440348791 固定此 SHA；签名工作树也固定此 SHA。[精简 Build 42817938376](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42817938376) 复用 1.17.16 的规则，[本轮5项 UI 42817858474](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42817858474) 并行；重复 Build/旧动画作业 42817858460、42817858521 已取消。下一步核对编译、长按/Hover 与颜色截图，成功后签名、quick_release 发布。

- 首轮定向 UI 的主代码编译发现两个 EhIcons.GoTo FAB 调用缺少新增提示 label，导致重载及 suspend 回调连带错误；已为主页/收藏跳转入口补齐 go_to 描述。仅补标签不改回调，候选 f046f63 不发布；下一步将来源分支/Draft/签名工作树同步新 SHA，并重跑同一精简构建及5项UI。

- 修正后的候选为 `c5473f5e8a90bf6a54a65edd9c9fe0df994942b6`，来源分支、Draft target 和签名工作树均同步此 SHA。[Build 42818460982](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42818460982) 与 [5项UI 42818456069](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42818456069) 已重启；旧 f046f63 的两轮产物不能发布，后续清理 obsoleteRuns=42817938376/42817858474 及取消的 42817858460/42817858521/42818438870。

- c5473f5 的主代码/签名元数据编译与规则复用已通过，Build 正组装五包；UI 作业129837503759执行5项，4项通过，Hover显示断言未过。长按提示不触发点击、明暗选择、自定义确认/取消均通过。先修正夹具为显式鼠标中心 Enter+Move，原生源码确认是 Mouse Enter 触发展示；仅重跑同5项UI，暂不改应用或重构手势。APK候选仍c5473f5，若验证为夹具问题不重复原生构建。

- 鼠标中心 Enter+Move 的检查已确认原生悬停能够显示 Tooltip；剩余失败位于窗口内原坐标发送合成 HOVER_EXIT 后的移除断言。改用真实的“指针移出锚点、仍留在窗口内”Mouse Move，避免 Android 为区分点击而延迟窗口级 Exit。仅夹具修正；应用继续使用原生输入/自动消失，无自定义手势或弹窗，APK源码仍c5473f5。明暗配色和调色对话框截图已查看，布局正常。

- 最终5项 UI [42820140116](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42820140116) 全部通过，XML 5/0/0，Artifact13766496168 摘要已核对；Hover 中心进入/移出锚点正确触发显示/消失，长按不触发点击及自动消失、明暗配色和调色确认/取消通过。三张配色/调色截图已查看。Build 42818460982 全成功，最终五包13766570749经Artifact摘要取回，本地正式V2+V3签名、来源/SDK/ABI/CRC/16KiB/Kotlin/Profile/独立签名摘要校验已通过。应用源码仍c5473f5；UI夹具revision0bc8ebb，不重复已完成的原生构建。下一步公开ZIP交接并API33精简发布。

- 已签名的公开七文件ZIP交接完成：manifest7e879604cd6c10d7675638536500aafda7d024e4，ZIP SHA-256=62f5640158f378d94d7dacb8a4033be113688e55a2c1560f9709f21608c9c0c2；私钥仅在vault。待清理旧Artifact3个：13765727497/13765943544/13765889127，最终Build/UI产物保留。下一步 quick_release=true API33 安装升级、ProfileInstaller及公开五包下载验证，成功后清理并更新正式基线。

- [正式发布 42821420510](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42821420510) 已启动，工作流revision3240f05，APK source=c5473f5，quick_release=true，仅API33。下一步确认prepare/verify(33)/publish均成功，补全Release实际结果，核对七附件摘要，删旧Artifact和临时来源分支、本地工作树/临时包及本章。

- 发布prepare作业129847623984已成功：CI收取七公开文件、按c5473f5复核五包来源及Build Tools36/37正式V2+V3签名通过。API33作业129847969996执行安装/启动、1.17.16→1.17.17覆盖升级/数据标记、ProfileInstaller；随后publish公开并下载校验五包。当前无待修应用问题。
