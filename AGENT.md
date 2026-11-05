# EhViewer Revived 开发约定

本文件集中维护开发规则、经维护者确认的经验及构建入口。不要散建交付记录、验证报告或临时开发文档。测试结果放在 Actions 日志、Artifact 和 Release 说明中。每轮结束清理本轮生成的临时文件、过期安装包及临时测试材料。

长程任务或包含大量子任务时，每完成一个阶段或关键节点，都须在本文件的临时进度章节记录已完成事项、当前状态、关键提交/Actions 链接和下一步，便于中断后恢复。任务全部完成后删除整个临时进度章节，仅保留本条持久规则；记录不得包含密钥或密码。

## 基线与发布

- 功能源自 `Hycnyl/EhViewer` 的 `v1.17.8`，对应清理后的提交 `92923877ef671e33aa56f69688daa46af6e59ec6`；新仓库独立起始，不继承旧 Git 历史。四种语言 README 继承旧仓库最新的用户文档与完整素材引用，保留 LICENSE、NOTICE 和源码版权说明。
- 应用名保持 `EhViewer`，正式包名保持 `moe.hycnyl.ehviewer`。当前版本为 `1.17.11` / `180086`，接续旧版 `1.17.8` / `180083` 和 `1.17.9` / `180084`。后续沿用版本体系，版本码递增。
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
| Room / DataStore / Work / Paging / Lifecycle | 2.8.4 / 1.2.0 / 2.11.0 / 3.3.6 / 2.10.0 → 2.8.5 / 1.2.1 / 2.12.0 / 3.5.3 / 2.11.0 | 功能组件采用稳定版，保持应用 schema 与设置键；DataStore 1.3.0 的 Android 16 首次读取等待正在以相邻稳定维护版 1.2.1 做兼容性复核 |
| Coil / OkHttp / Okio / Ktor | 3.3.0 / 5.3.2 / 3.16.4 / 3.3.3 → 3.6.3 / 5.5.1 / 3.18.2 / 3.6.1 | 同主版本稳定更新 |
| Coroutines / Serialization / Datetime / IO | 1.10.2 / 1.9.0 / 0.7.1 / 0.8.2 → 1.11.0 / 1.12.0 / 0.8.0 / 0.9.2 | 固定稳定版本；不采用兼容旧日期类型的特殊分支 |
| Arrow / moko-resources / AboutLibraries / Spotless | 2.2.1.1 / 0.25.2 / 13.1.0 / 8.1.0 → 2.3.0 / 0.27.1 / 15.3.0 / 8.11.0 | 采用支持新版 Kotlin/AGP 的稳定组件 |
| libarchive / XZ / Corrosion | 3.8.4 / 5.8.1 / 0.6 → 3.8.11 / 5.8.4 / 0.6.1 | 原生稳定修复版，保留项目原有补丁 |
| 保留的依赖 | XMLUtil 0.91.3、WebP 1.6.0、Nettle 3.10.2、Rust 运行依赖锁文件 | XMLUtil 1.x 涉及编解码 API 迁移；其余保持原生 FFI 与解析格式兼容；TL 例外固定至同一 FooIbar fork 的 `051705e41fcc140bcb6affcca687589333976296`，上游提交仅同步 3 处 SIMD 方法名以适配新 nightly，未改变解析逻辑 |

核对入口：[AGP 9.5](https://developer.android.com/build/releases/agp-9-5-0-release-notes)、[Kotlin 发布](https://kotlinlang.org/docs/releases.html)、[Compose 兼容性](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)、[Material 3 发布](https://developer.android.com/jetpack/androidx/releases/compose-material3)、[libarchive](https://github.com/libarchive/libarchive/releases/tag/v3.8.11)、[XZ](https://github.com/tukaani-project/xz/releases/tag/v5.8.4)。

UI 模块保留 `allWarningsAsErrors`，仅用 `-Xwarning-level=DEPRECATION:warning` 将仍受支持的旧 API 弃用提示保留为日志警告，不隐藏它们；类型、ABI、其他编译警告仍阻止构建。预测返回、搜索和输入等兼容层保留现有交互语义，后续迁移新 API 时独立验证手势与输入行为。

发布验证使用 API 26、33、36 模拟器；API 26/33 采用包含 WebView 的 google_apis 镜像，模拟器分配 4096M RAM / 512M heap 并每次新建 AVD。API 33 执行 1.17.8 → 1.17.9 → Revived 的覆盖安装链，API 36 使用 google_apis_ps16k 镜像并核对实际页大小 16384。每台模拟器还打开临时生成的离线 CBZ，通过已知页面像素确认原生解包与图片显示；临时材料不进入源码或 Release。

本托管环境先执行 `source /workspace/.ehviewer-env/activate.sh`，其中设置已固定的 JDK、SDK、Gradle/Cargo 缓存与本地 GNU m4 1.4.20。m4 是 Nettle 汇编的构建依赖；CI 检查并安装它。本地无 sudo，m4 安装于私有工具目录。Nettle 的源码包含预生成文件，CMake 必须保留其解包时间戳（DOWNLOAD_EXTRACT_TIMESTAMP TRUE）。

## 临时进度：LibChecker Kotlin 构建信息与 1.17.11

- 已定位并修复：1.17.8 的 APK 有根目录 `kotlin-tooling-metadata.json`，1.17.10 缺失；AGP 内建 Kotlin 没有注册旧 KGP 的生成任务，其他 Kotlin 标记被沿用的精简规则排除。现在使用 Kotlin 官方模型与公开 JAVA_RES Artifact API，从真实配置生成 Kotlin 2.4.21 / Gradle 9.8.2 / Java source/target 21；不修改 R8 策略。
- 元数据生成、五种 ABI 打包与一致性检查已通过首个候选构建 [41946491046](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/41946491046)，旧候选源码 bdc1985。五包已通过 V2/V3、固定证书、最低 API 26、包信息、ABI、CRC 及 16 KiB 对齐验证；LibChecker 字段按其公开源码核对，未实际自动操作 LibChecker 界面。
- 首个候选的三轮设备验证 API 26/33 均通过，API 33 包含 1.17.8 → 1.17.9 → 1.17.10 → 1.17.11。API 36 仍停在启动页；[41950499857](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/41950499857) 的截图及反混淆 SIGQUIT 栈确认，主线程和五个 IO 线程在 PrefDelegate.getValue 的 runBlocking 中等待 DataStore 首次快照，无应用崩溃。禁止把该等待当作通过。
- 当前局部兼容候选：DataStore 1.3.0 → 1.2.1（原版为 1.2.0）。全部 DataStore 模块已确认解析为 1.2.1；两版原有八类 protobuf 字段编号一致，不改变设置键、文件位置或应用存储格式。此回退须设备实测才认定有效。
- 本地 JVM 格式检查通过：使用两版官方二进制库和隔离类加载器，验证全部八类设置值经 1.3.0 → 1.2.1 → 1.3.0 的 protobuf 往返，值、类型及中文/集合/字节数组不丢失。结果将写入 Release；一次性检查材料留在私有临时目录，完成后删除。
- 当前候选源码 `8a11e1a64a3aef4f3731bf85c8528afb5875dc64`，构建 [41953512288](https://github.com/Hycnyl/EhViewer-Revived/actions/runs/41953512288) 已通过 Kotlin 编译、AAR 元数据与格式检查，全部 17 个构建步骤成功。五种 APK 已完成本地签名，并通过真实 Kotlin 元数据一致性、来源、包信息、ABI、CRC、16 KiB 对齐、固定证书、V2/V3 和最低 API 26 验证。版本仍为未公开的 1.17.11 / 180086。
- 当前本地独立源码 worktree：`/workspace/.ehviewer-revived-work/source`，HEAD 固定为 8a11e1a。签名私钥仍在上文私有目录。v1.17.11 草稿即将更新为当前候选；设备矩阵全部通过前不得发布。
- 设备脚本保留有限界面等待和失败诊断；API 33 新增访客模式设置保留断言，先处理下载路径提示但不重新进入访客模式掩盖重置。三台设备均新增一次带已有设置的强制停止后冷启动，并保留离线 CBZ 解包/渲染、恢复前台和崩溃检查。
- 下一步：下载新构建产物、固定源码本地签名并核验，更新草稿来源与七个公开附件，再跑 API 26/33/36。只有全部通过才正式发布；最后清理临时产物并删除本章节。
