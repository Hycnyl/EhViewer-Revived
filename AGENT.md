# EhViewer Revived 开发约定

本文件集中维护开发规则、经维护者确认的经验及构建入口。不要散建交付记录、验证报告或临时开发文档。测试结果放在 Actions 日志、Artifact 和 Release 说明中。每轮结束清理本轮生成的临时文件、过期安装包及临时测试材料。

长程任务或包含大量子任务时，每完成一个阶段或关键节点，都须在本文件的临时进度章节记录已完成事项、当前状态、关键提交/Actions 链接和下一步，便于中断后恢复。任务全部完成后删除整个临时进度章节，仅保留本条持久规则；记录不得包含密钥或密码。

验证优先放到 GitHub Actions 或同类 CI；本地仅执行必要的开发操作、私钥签名和难以交由 CI 的定向诊断。按本轮改动选择检查范围，UI、签名和构建变更优先验证布局、构建、签名及安装，不重复运行无关功能或业务逻辑检查。历史发布验证流程不构成每轮必须重跑全部场景的要求。

## 基线与发布

- 功能源自 `Hycnyl/EhViewer` 的 `v1.17.8`，对应清理后的提交 `92923877ef671e33aa56f69688daa46af6e59ec6`；新仓库独立起始，不继承旧 Git 历史。四种语言 README 继承旧仓库最新的用户文档与完整素材引用，保留 LICENSE、NOTICE 和源码版权说明。
- 应用名保持 `EhViewer`，正式包名保持 `moe.hycnyl.ehviewer`。当前开发版本为 `1.17.12` / `180087`，上个正式版为 `1.17.11` / `180086`，接续旧版 `1.17.8` / `180083` 和 `1.17.9` / `180084`。后续沿用版本体系，版本码递增。
- 任何实际影响用户的改动完成后，除非维护者明确禁止，都须提升 versionName/versionCode，使用 GitHub Actions 发布正式 Release；提供 universal、arm64-v8a、armeabi-v7a、x86_64、x86 五种 APK，并附简短变更说明与 SHA-256 校验和。不得使用 `[skip ci]` 绕过这条规则。
- README 保持简洁，变更通常只在“变更”章节简要记录；允许修正仓库链接、安装要求和迁移说明。不得随意重写其余内容或丢失截图、素材和致谢。
- 单轮只改一个目标不是硬性限制。缓存导出、下载恢复和归档格式继承 1.17.8 的当前行为，维护者没有将其固化为不可修改的长期规则。
- 全部旧测试、测试数据和基准测试模块不继承；已有 baseline-prof.txt 与 startup-prof.txt 是应用性能资源，继续保留。根据新变更的实际风险添加必要验证，不复活历史测试包袱。

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

发布验证按本轮改动选择场景。UI、签名、构建和 Target 迁移默认使用 API 26、33、37.2：包含 WebView 的 google_apis 镜像，Android 17 使用 google_apis_ps16k 并检查真实页大小；每次新建 AVD，分配 4096M RAM / 512M heap。验证设备端 APK SHA-256、文件式 universal/匹配 ABI 安装、上个正式版本覆盖安装和启动。UI 使用独立的 archive-ui 工作流渲染实际组件，支持指定 APK 源码与更新验证夹具，避免测试修正触发重复原生构建。只有涉及相关业务功能的改动，才增加离线 CBZ、设置迁移等对应检查。临时数据不进入源码或 Release。

本托管环境先执行 `source /workspace/.ehviewer-env/activate.sh`，其中设置已固定的 JDK、SDK、Gradle/Cargo 缓存与本地 GNU m4 1.4.20。m4 是 Nettle 汇编的构建依赖；CI 检查并安装它。本地无 sudo，m4 安装于私有工具目录。Nettle 的源码包含预生成文件，CMake 必须保留其解包时间戳（DOWNLOAD_EXTRACT_TIMESTAMP TRUE）。

## 临时进度：压缩包下载 UI、Target 37 与 universal 签名修复

- 目标：三项任务合并发布 1.17.12 / 180087；新增“CI 优先、按改动选择验证范围”长期规则已写入本文件。上个正式版为 1.17.11 / 180086。
- 当前 APK 源码 `68034a243e7b9827b22055b66d9d4f5bf517efc7`，独立 worktree `/workspace/.ehviewer-revived-work/source` 已固定于此。构建 [41972981550](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/41972981550) 全部 17 个构建步骤成功；五种 APK 已完成本地 V2/V3 签名，经过来源、版本、SDK、CRC、ELF/ZIP 对齐、固定证书及独立内容摘要检查。
- UI 已完成：GP 的 k 后缀与数值共用 labelLarge，GP/C 使用同一跨列 FlowRow 对齐；BoxWithConstraints 给出有限单元宽度，避免 RollingNumber/LazyRow 不支持的固有尺寸测量；大字体或窄屏可换行。数值区域固定 LTR，按钮由固定宽度改最小宽度，解决截图发现的 RTL 数字倒排与按钮拆字。
- UI 验证成功：[41972981482](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/41972981482)，5 测试、0 跳过/失败/错误。已目检普通、大字体窄屏、RTL 截图；数字顺序有实际位置断言。Artifact `13390931513` 是本版界面证据。没有启动真实应用业务；只用了实际组件和独立 Application 的 Robolectric 渲染。
- Target 候选为 37，min 26 / compile 37.2 保留；已核对官方迁移要求，无 MessageQueue 私有字段修改、static final 写入或自建 LAN 服务，原生库来自只读安装目录。SDK 提供 android-37.2/google_apis_ps16k，可验证真实 Android 17。尚未宣称设备实测通过。
- 签名根因已定位至设备文件不同：用户 Android 13 / MIUI 14.0.14.0 的 universal SHA-256 为 `94ee382144d9097fa541d6ad0974a260898f625c14db805a65c72382ba8c4e22`；正式附件为 `f512338816c3ab254fb20b4701339463b0455c9ddabf30d410f2b38627356de9`。已解释需重新下载；损坏发生在下载、缓存还是复制环节尚不确定。
- 官方文件复核：[41970685302](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/41970685302) 五种 APK 的所有 V2/V3 SHA-512 摘要经独立实现重算及 Build Tools 34/36/37 全部验证通过。[41973369577](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/41973369577) 直接读取公开浏览器下载 URL，也得到正式 SHA-256。证书/算法无需更换；原固定 RSA-4096 身份继续使用。
- 发布流程已增加独立摘要校验、设备端 SHA-256、文件式 universal/ABI 安装、上版升级检查、公开下载 URL 的发布后哈希复核；本轮不执行离线阅读、数据库、下载等无关业务测试。UI 门禁核对实际 APK 源码的成功布局作业。
- 签名与交接完成：v1.17.12 草稿来源固定 68034a2；七个公开文件 ZIP SHA-256 为 `5dab52ddf5443d2cd05c8439d78bd927ba92fdd61a7fe698147106b7ca27ba36`，Git blob 为 `6ba7f26ea2a690d863bd39a1edcb7d134f08997e`。新版 universal SHA-256 为 `90f385c82dafe10d8169371094df2e0f89cd11f680dcabe0ded1e1f571b8c289`。私钥未上传。
- 最终验证/发布 [41974998089](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/41974998089) 已启动，工作流版本 4dba0d1，验证 API 26/33/37.2 安装、升级与 Target 启动；先核对精确源码的 UI 成功作业，全部通过才公开发布。若验证失败，草稿保留，修复验证流程时沿用固定 APK 源码。
- 首轮安装阶段：API 26 与 API 33 全部通过；API 33 确认 1.17.11 ABI、1.17.11 universal 和 1.17.12 universal 均能文件式安装，升级保留标记数据。API 37.2 的设备页大小为 16384，但 pm install 在系统 package 服务调用处返回 Broken pipe(32)，退出码 224；尚未进入应用启动，不能直接视为 Target 兼容失败。草稿未发布，系统日志 Artifact 为 `13392205148`，正在读取原因。
- 已确认 API 37.2 模拟器在安装前发生 SurfaceFlinger/mapper.ranchu 的 ReadColorBufferDma 断言崩溃，带动系统服务重启；与应用进程无关。按官方模拟器文档，将该镜像从已弃用的 swiftshader_indirect 改为受支持的 software 模式，做一次有针对性的兼容尝试。签名包保持不变，复用前轮已通过的 API 26/33；复用门禁核对前轮成功步骤及四个相同输入（Tag、源码、blob、ZIP SHA-256）。
- 重试 [41976148244](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/41976148244) 在复用门禁处被新版 gh 的 ANSI 日志输出保护中断，模拟器尚未运行；已为只在内存中解析的自有日志加上官方要求的 allow-escape-sequences 标志。该修正不改变 APK、签名或实际验证条件。
- 下一步：分析 API 37.2 系统日志，若是服务就绪/模拟器问题则只修正 CI 等待或镜像，不重建 APK；若 Target 本身有难以兼容的阻碍，按维护者授权回退 Target 36 后再构建。通过后复核发布并清理。
- 完成后：清理旧候选/失败或取消作业的临时 Artifact、本地 worktree、APK、日志与截图，仅保留本版有效 CI 证据；停止本地 Gradle，删除本临时章节。保留工具链缓存与私有签名目录。
