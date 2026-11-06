# EhViewer Revived 开发约定

本文件集中维护开发规则、经维护者确认的经验及构建入口。不要散建交付记录、验证报告或临时开发文档。测试结果放在 Actions 日志、Artifact 和 Release 说明中。每轮结束清理本轮生成的临时文件、过期安装包及临时测试材料。

长程任务或包含大量子任务时，每完成一个阶段或关键节点，都须在本文件的临时进度章节记录已完成事项、当前状态、关键提交/Actions 链接和下一步，便于中断后恢复。任务全部完成后删除整个临时进度章节，仅保留本条持久规则；记录不得包含密钥或密码。

验证优先放到 GitHub Actions 或同类 CI；本地仅执行必要的开发操作、私钥签名和难以交由 CI 的定向诊断。按本轮改动选择检查范围，UI、签名和构建变更优先验证布局、构建、签名及安装，不重复运行无关功能或业务逻辑检查。历史发布验证流程不构成每轮必须重跑全部场景的要求。

## 基线与发布

- 功能源自 `Hycnyl/EhViewer` 的 `v1.17.8`，对应清理后的提交 `92923877ef671e33aa56f69688daa46af6e59ec6`；新仓库独立起始，不继承旧 Git 历史。四种语言 README 继承旧仓库最新的用户文档与完整素材引用，保留 LICENSE、NOTICE 和源码版权说明。
- 应用名保持 `EhViewer`，正式包名保持 `moe.hycnyl.ehviewer`。当前正式版本为 `1.17.12` / `180087`，上个正式版为 `1.17.11` / `180086`，接续旧版 `1.17.8` / `180083` 和 `1.17.9` / `180084`。后续沿用版本体系，版本码递增。
- 任何实际影响用户的改动完成后，除非维护者明确禁止，都须提升 versionName/versionCode，使用 GitHub Actions 发布正式 Release；提供 universal、arm64-v8a、armeabi-v7a、x86_64、x86 五种 APK，并附简短变更说明与 SHA-256 校验和。不得使用 `[skip ci]` 绕过这条规则。
- README 保持简洁，变更通常只在“变更”章节简要记录；允许修正仓库链接、安装要求和迁移说明。不得随意重写其余内容或丢失截图、素材和致谢。
- 单轮只改一个目标不是硬性限制。缓存导出、下载恢复和归档格式继承 1.17.8 的当前行为，维护者没有将其固化为不可修改的长期规则。
- 原初始化未继承旧测试和基准模块。维护者本轮明确要求恢复 Benchmark、Baseline Profile 自动生成与合并，并新增离线阅读器采集及独立 FrameTimingMetric 场景；按新需求维护这些有效检查，其他历史一次性测试材料仍不继承。

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
- 当前连接本地上传 Release 附件返回 HTTP 401。将上述七个公开文件打包成 ZIP，通过 Git Data blobs API 交接（不创建分支或提交），以 `signed_blob` 和 `bundle_sha256` 输入启动 `release.yml`；Actions 先校验包内容、来源与签名，再上传草稿。APK 的 `source_commit` 固定为实际构建提交，工作流本身可以在其后的提交中修正。
- 旧 1.17.9 已使用同一张新证书，可以直接覆盖安装。旧 1.17.8 及更早版本使用不同签名：Android 13+ 可先安装旧仓库的 1.17.9，再升级 Revived；Android 8–12 须先导出备份、卸载旧版、再安装并导入。设置/数据库备份不包含全部图片；卸载前，须把备份、下载和缓存导出文件复制到应用私有目录（如 Android/data/moe.hycnyl.ehviewer）之外，确认后再卸载。新装后重新授权下载目录并导入备份。

## 构建入口

- 构建：`./gradlew :app:assembleDefaultRelease --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`。正式构建没有 Gradle signingConfig，不需要签名凭据。
- 打包：`python3 tools/release.py package --input app/build/outputs/apk/default/release --output delivery`。
- 本地签名：`python3 tools/release.py sign --input <Actions未签名产物目录> --output <新输出目录> --vault /workspace/.ehviewer-revived-signing`。设置 ANDROID_HOME；使用已安装的固定版 Build Tools。
- 公开产物复核：`python3 tools/release.py verify --input <已签名产物目录>`。来源提交、版本、包名、SDK、原生 ABI、ZIP CRC、16 KiB ZIP 对齐、证书及 V2/V3 均须匹配。
- Release CI 使用 API 35 离线场景执行 `:app:generateBaselineProfile -PbuildAbis=x86_64`，检查两套阅读器覆盖后再组装全部正式 ABI。生成文本存于 Git 忽略的 `app/src/main/generated/baselineProfiles/`，正式包须包含非空 `assets/dexopt/baseline.prof` 和 `.profm`。开发构建默认不启动模拟器，需要自动采集时显式 `-PgenerateBaselineProfile=true`。
- Baseline Profile 1.5 会在 finalizeDsl 中复制原 Release manifest；benchmark/nonMinified 变体的专用 overlay 必须在其后配置。测试内容只进入独立 `.benchmark` 包，正式 APK 不含夹具入口。
- 帧测试按六个独立场景执行，版本/依赖/内容/构建配置一致后再对比。runtime-tracing 1.13 使用 Tracing 2.0/tracing-wire；仅在 benchmarkRelease 显式 -PtraceCompose=true 时引入（performance.yml 的 trace_compose 选项）。Macrobenchmark 1.5 会自动连接此依赖，但其写出在软件模拟器中复现偶发 60 秒超时，默认 Frame 测量仅用标准系统 Perfetto，详细 Compose 追踪按需开启。不要启用仅对应旧 tracing-perfetto 的 perfettoSdkTracing 开关。高开销的重组状态读写追踪只用于必要定向诊断。
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
| 保留的依赖 | XMLUtil 0.91.3、WebP 1.6.0、Nettle 3.10.2、Rust 运行依赖锁文件 | XMLUtil 1.x 涉及编解码 API 迁移；其余保持原生 FFI 与解析格式兼容；TL 例外固定至同一 FooIbar fork 的 `051705e41fcc140bcb6affcca687589333976296`，上游提交仅同步 3 处 SIMD 方法名以适配新 nightly，未改变解析逻辑 |

核对入口：[AGP 9.5](https://developer.android.com/build/releases/agp-9-5-0-release-notes)、[Kotlin 发布](https://kotlinlang.org/docs/releases.html)、[Compose 兼容性](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)、[Material 3 发布](https://developer.android.com/jetpack/androidx/releases/compose-material3)、[libarchive](https://github.com/libarchive/libarchive/releases/tag/v3.8.11)、[XZ](https://github.com/tukaani-project/xz/releases/tag/v5.8.4)。

UI 模块保留 `allWarningsAsErrors`，仅用 `-Xwarning-level=DEPRECATION:warning` 将仍受支持的旧 API 弃用提示保留为日志警告，不隐藏它们；类型、ABI、其他编译警告仍阻止构建。预测返回、搜索和输入等兼容层保留现有交互语义，后续迁移新 API 时独立验证手势与输入行为。

发布验证按本轮改动选择场景。UI、签名和构建默认使用 API 26、33、36；Target 37 的候选将现代设备检查切换至 API 37.2。使用包含 WebView 的 google_apis 镜像，现代设备使用 google_apis_ps16k 并检查真实页大小；每次新建 AVD，分配 4096M RAM / 512M heap。验证设备端 APK SHA-256、文件式 universal/匹配 ABI 安装、上个正式版本覆盖安装和启动。UI 使用独立的 archive-ui 工作流渲染实际组件，支持指定 APK 源码与更新验证夹具，避免测试修正触发重复原生构建。只有涉及相关业务功能的改动，才增加离线 CBZ、设置迁移等对应检查。临时数据不进入源码或 Release。

本托管环境先执行 `source /workspace/.ehviewer-env/activate.sh`，其中设置已固定的 JDK、SDK、Gradle/Cargo 缓存与本地 GNU m4 1.4.20。m4 是 Nettle 汇编的构建依赖；CI 检查并安装它。本地无 sudo，m4 安装于私有工具目录。Nettle 的源码包含预生成文件，CMake 必须保留其解包时间戳（DOWNLOAD_EXTRACT_TIMESTAMP TRUE）。

## 临时进度：Profile、性能追踪与交互动画

- 目标 1.17.13 / 180088，当前正式版 1.17.12，尚未创建新 Release。保持 EhViewer / moe.hycnyl.ehviewer、Min 26 / Target 36 / Compile 37.2、正式 V2+V3 证书。
- 产品已完成：移除主抽屉/右侧栏动态模糊，保留遮罩与滑动；GalleryList 两种布局及 Webtoon 单层 48 dp 限幅、绘制阶段读取状态、临界阻尼与拖动中断连续；Pager 拖动/点击/滑块复用单一吸附弹簧；修正 Material 空间位移误用 effects 参数。图片分辨率、检测、缓存和预取未改。Compose 保持成套 1.13.0-beta02。
- Benchmark 已恢复：Macrobenchmark / Baseline Profile 1.5.0、UI Automator 2.4.1；四个 Profile、六个独立 FrameTimingMetric、可选冷启动 None/Partial 对照。24 页本地 CBZ、120 本地收藏、24 页完整预览与详情，图片像素检查；夹具仅独立 .benchmark 变体包含。列表手势在内容区，失败记录 PNG/XML/logcat。Release 自动采集合并，开发默认不启动模拟器。
- 最近完整 Profile [42084560170](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42084560170)（源码 8f37bc504b04023afbfc34df8a6cbca528617e79）四个生成测试实际全部通过，生成 38,783 条 Baseline 和 29,257 条 Startup 规则；八个 Frame/Startup 在此任务为预期 skipped。随后隔离检查发现 14 条 BenchmarkInitializer/OfflineThumbs 夹具规则，因此尚未组装正式 APK。Artifact 13444802728，SHA-256 fc3037356bc6c6f0616047da77a768ac04d4e0c984e146830b7cb4eb7377a15c。
- 已核对插件官方源码：Startup 规则不使用 consumer filter，复制合并又把它们并回 Baseline。修复在 BaselineProfileRule.collect 的 filterPredicate 中统一排除 Lcom/hippo/ehviewer/benchmark/，让四个场景的原始输出先过滤；保留最终隔离检查，并扩展 APK DEX 检查到整个夹具包。此修改已包含于 7cd4e5032b06b95ca69b3f5a7c4e30b1b234e361，完整构建验证正在进行。
- 六场景两组 Frame 已全部成功：[优化 42088477421](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42088477421)（46fd3c6f63b882e2b22b5f8410347234a914a7cf，Artifact 13444384682，SHA d288b8d08cfdcf51b6289c55823c0d49637b7687b5971399690cbe85299a4b7b）；[原行为 42088480537](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42088480537)（a5acc471b49fd75a873fb885aa2e3fb044870470，Artifact 13444299579，SHA 77fc3f8cbe9551de2467b38b17f97b367040b2ba8af59a71f3259ccfed2e3059）。只恢复九个 UI 文件形成控制，依赖/版本/离线内容/Pixel 6 配置一致；每场景三次，CompilationMode.Full。
- 本次 CI 的 frameDurationCpuMs P95 原行为→优化：侧栏 737.433→171.242；列表 130.132→131.277；网格 200.366→241.176；导航 234.149→254.546；Pager 121.913→125.381；Webtoon 123.627→123.788。侧栏长帧显著减少，其他场景未证实一致提升，网格存在波动/回退，不能宣称全面提速。原始 JSON、每迭代 Perfetto 和 SQL 摘要已下载核对，最终结果写 Release，不能将软件渲染 CI 描述为真机 FPS。
- 实际 Perfetto：侧栏长帧主要是主线程 postAndWait 与渲染着色器编译；阅读器/缩略图 ImageDecoder 在 DefaultDispatcher，网格有明显硬件 Bitmap 分配/上传等待（240x356 缩略图也可达数百 ms），未据此降低图片质量或猜测性改缓存。详细 Compose tracing 的旧采集出现随机 60 秒 FLUSH_TRACES_GET_PATH 超时；已改 -PtraceCompose=true / performance.yml trace_compose 按需启用，默认标准系统 Perfetto，两组当前成功结果采用相同默认配置。
- 定向交互 [42067072084](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42067072084) / job 127384046956 的四项实际通过（限幅且不 Stretch、回位中断、普通滚动、侧栏锚点），源码 6b24b49。回弹静止时现直接绘制内容，省去零位移下的额外裁剪/变换；benchmark tracing 的 app/build.gradle 配置也有改变，将为新候选重新执行这四项 CI；不运行无关业务测试。
- 当前已合并并推送 main，候选实际源码 7cd4e5032b06b95ca69b3f5a7c4e30b1b234e361。当前 [Build 42131493833](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42131493833)、[四项交互 42131493825](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42131493825)、[优化 Frame 42131496485](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/42131496485)；原行为 a5acc47 的同配置六场景可复用。签名 worktree /workspace/.ehviewer-revived-work/release-source 仍停在 af83923，签名前必须切至最终实际成功构建 SHA。不能用旧 8f37bc5 元数据冒充新构建。
- 下一步：等待当前完整 Profile+五 ABI 构建、四项交互和优化 Frame；既有同配置原行为 Frame 可复用；通过后从该成功源码取产物、本地签名、Actions 验证 API 26/33/36 文件安装、1.17.12 覆盖升级、16 KiB 与实际 ProfileInstaller 安装，再发布 1.17.13、复核五个公开下载。GitHub dispatch 可能 500/502，失败后先查是否已创建运行，避免重复。
- 私有 scratch /workspace/.ehviewer-revived-work 已恢复，final-profile/final-before/final-after 存当前有效证据；vault /workspace/.ehviewer-revived-signing 保持 0700/0600，禁止上传。最终保留成功 CI/Release 证据，删除本轮无效/过期 Artifact、临时分支/工作树、临时文件并移除整个进度章节；保留环境工具链、原仓库和密钥保管目录。

- 新候选 7cd4e50 的交互 CI 42131493825 / job 127608952231 已完成成功，实际 4 项全部通过。Artifact 13463014361，SHA-256 e45519fe025d3dfb88b31ec321f6557acf1e7d93ce394d1efd149a0c260920c5；签名 worktree 已同步至 7cd4e50。当前等待 Build 42131493833 / job 127608951558 与优化 Frame 42131496485 / job 127608957348，main 之后仅 AGENT.md 状态更新，不影响候选源码与已执行检查。

- 候选 7cd4e50 的优化 Frame 42131496485 / job 127608957348 已完成成功，六场景各三次，Artifact 13464328291，SHA e7cee4f3d25276590f6e81411caeb30e48e91a2ae32341ea8f8ebed3cb498e6f，已下载验证。P95 原行为 a5acc47→当前候选：侧栏 737.433→169.747；列表 130.132→134.630；网格 200.366→246.408；导航 234.149→227.066；Pager 121.913→133.012；Webtoon 123.627→128.269。不能宣称全面提速；静止绘制快路径未消除模拟器网格差异。
- 网格追踪进一步核对：每次 AndroidOwner:draw 平均耗时没有明显增加，measureAndLayout 的实际 Running 时间约从 1.18 ms/次增至 2.15 ms/次，仍小于大额渲染/Bitmap 分配上传等待；调度等待也上升。不据软件 GPU 下的差异加入推测性的图片策略或新的缓存层。发布说明应完整记录数据与 CI 边界。当前仍等 Build 42131493833 的新 producer 过滤生成与全部 ABI 组装。
