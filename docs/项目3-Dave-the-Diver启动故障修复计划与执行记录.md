# 项目3：Dave the Diver 启动故障修复计划与执行记录

> 建立日期：2026-08-05  
> 当前状态：G1.1 已确认 UnityPlayer 检测无效；等待 T1.2 Unity 内部日志
> 测试对象：`D:\agent\Winlator\games\Dave the Diver\DaveTheDiver.exe`  
> 初始日志：`D:\agent\Winlator\logs\dave-the-diver\logs.txt`  
> 工作仓库：`D:\agent\Winlator\winlator_wzh_new`

## 一、目标与验收标准

本轮先解决“Dave the Diver 无法启动”，再进入项目3要求的性能分析。启动故障未闭环前，不把低帧率优化和兼容性修复混在同一轮实验中。

启动修复满足以下条件才算完成：

1. 游戏至少连续两次冷启动进入可交互主菜单，不以短暂出现窗口作为通过。
2. 日志能证明实际使用的 Wine、Box64 和图形组件版本，不能只依赖容器显示名称或日志文件名。
3. 已知的 `nsiproxy` 空指针路径不再造成 `c0000005`、SIGSEGV 或 X session 被动退出。
4. 最终修复进入 Winlator 内置 Wine/rootfs 或等价的可复现构建流程，不把“手工导入补丁包”当作项目最终实现。
5. 保留失败基线、成功日志、构建产物哈希、代码提交和真机回归记录。

启动闭环后，另行建立性能基线：固定场景记录平均 FPS、1% Low、帧时间、分辨率、渲染驱动、DX wrapper、Box64 预设、温度与功耗，再逐项优化。

## 二、当前基线

| 项目 | 当前事实 | 证据/备注 |
|---|---|---|
| Winlator 源码 | 当前实际开发仓库为 `winlator_wzh_new`，分支 `main` | `winlator-main` 是上游快照，子模块未完整初始化，不作为本轮修改目标 |
| 内置 Wine | 11.0，运行路径为 `/opt/wine` | Dave 日志中的搜索路径为 `rootfs/opt/wine`，不是可选 Wine 路径 |
| 内置 Wine 状态 | 当前 rootfs 的 `nsiproxy` 未包含项目2已验证的 `if_nameindex()` 空值保护 | 与补丁版模块哈希不同；现象与项目2未修复样本一致 |
| Box64 | `0.4.5-dev-372739d` | Dave 日志确认；当前默认版本 |
| Box64 预设 | Intermediate | `BIGBLOCK=2`、`STRONGMEM=0`、`WEAKBARRIER=2` |
| Unity 开关 | `BOX64_UNITYPLAYER=0` | Dave 日志多次确认；这是次级验证项，不在 T1 同时修改 |
| 游戏引擎 | Unity 2020.3.48f1，IL2CPP | 本地游戏文件静态检查 |
| 游戏包风险 | 当前文件包含替换过的 `UnityPlayer.dll` 和第三方 Steam 兼容层 | 若官方干净副本与当前副本结果不同，应归因于游戏文件，不修改 Winlator 去适配 DRM 绕过组件 |
| 初始日志 | 48,526 bytes，SHA-256 `9B688E03B964D960D81F37AEE579EE89901B32DF29A683588FD7C4861512E0B1` | 2026-08-05 09:38:22 |

### 2.1 初始失败序列

日志已经证明游戏进程被创建，而不是快捷方式或路径解析失败：

```text
18:27:04 Detected running wine with "DaveTheDiver.exe"
18:27:04 argv[1]="E:\Dave the Diver\DaveTheDiver.exe"
18:27:04 Parsing volatile metadata of file steamclient64.dll
18:27:06 Using .../opt/wine/lib/wine/x86_64-unix/dnsapi.so
18:29:03 X connection to :0 broken (explicit kill or server shutdown).
```

`dnsapi.so` 加载后约 117 秒没有新的游戏初始化记录，最后是 X session 被关闭。当前日志级别没有记录调用栈，所以这里是“高度吻合的工作假设”，不是仅凭这份日志就完成了根因证明。

### 2.2 与项目2已知故障的关联

项目2已经取得一组可重复的因果证据：Android 环境中 `if_nameindex()` 会因权限返回 NULL；未修复的 Wine 11.0 `nsiproxy` 随后解引用空指针，而回移 Wine 上游 `08dbf01a` 的空值保护后，日志记录 `if_nameindex failed, errno 13` 并继续运行。补丁版在 Box64 0.4.3 和 0.4.5-dev 下都通过了真机游戏回归。

Dave 当前恰好使用未修复的内置 Wine，并停在 `dnsapi` 初始化之后。因此第一优先级不是调整图形驱动，而是用同一个补丁 Wine 做单变量对照。

## 三、假设优先级

| 优先级 | 假设 | 当前支持证据 | 证伪方式 |
|---|---|---|---|
| P0（第一层已确认） | 内置 Wine 11 的 `nsiproxy` 缺少 `if_nameindex()` 空值保护，阻塞网络初始化 | T0 停在 `dnsapi`；T1 只换补丁 Wine 后越过该点并继续加载 `GameAssembly.dll`、Vulkan 和音频 | 已由 T1 功能路径确认；后续仍需集成进内置 rootfs |
| P1（已排除） | Box64 Unity 专用策略被关闭导致 IL2CPP/Unity 初始化不兼容 | T1.1 确认 `BOX64_UNITYPLAYER=1`、`Detected UnityPlayer.dll`、`BOX64_UNITY=1`，但时间线和黑屏无改善 | 已完成；不再把该开关作为主修复 |
| P2（当前） | Unity 图形后端或 DX wrapper 不兼容，或首帧创建前发生 Unity 内部等待 | T1/T1.1 均加载 `GameAssembly.dll`、`winevulkan.so`，没有渲染器、swap 或首帧完成证据 | 先取得 Unity `Player.log`，再用 `-force-gfx-direct` 和图形矩阵验证 |
| P3 | Box64 Intermediate 的内存模型不适合该 Unity 版本 | 当前为 Intermediate | 在前项无效后只改 Stability |
| P4 | 当前游戏文件中的第三方 Steam/Unity 替换层在 Wine/Box64 下阻塞 | `steamclient64.dll` 和替换过的 `UnityPlayer.dll` 均参与当前启动链 | 使用来源合法、校验完整的干净游戏副本对照 |

## 四、分阶段测试与实现计划

### G0：计划与证据基线

- [x] 通读工作区、项目3任务说明、Winlator 启动链与项目2修复记录。
- [x] 分析初始 Dave 日志并确定单变量测试顺序。
- [x] 校验现有补丁 Wine 包路径、大小和 SHA-256。
- [x] 建立本执行文档。
- [x] 提交并推送计划基线。

### T1：补丁 Wine 单变量复测（已完成）

唯一变量：从未修复的内置 Wine 11 切换到已验证的补丁版可选 Wine 11。Box64、预设、图形驱动、DX wrapper、分辨率、游戏文件、环境变量和启动参数全部保持不变。

测试包：

```text
archive/artifacts/wine-packages/wine-11.0-final-nsiproxy-nullguard-winlator-custom-addon.tar.xz
Size: 54689068 bytes
SHA-256: A81595373E6C3FDAC8C33BEAE8BB1FDB7B7744DA63AF094E6BBECF2BD4A72765
```

通过信号：

- 日志搜索路径为 `/opt/installed-wine/wine-11.0/`，不能仍是 `/opt/wine/`。
- `dnsapi` 之后出现 `if_nameindex failed, errno 13`，并继续加载 Unity/图形模块或进入主菜单。
- 不出现 `c0000005`、SIGSEGV、`nsiproxy.so+...` 空指针和被动 X session 退出。

失败也必须保留日志。若越过 `dnsapi` 后停在新位置，说明 P0 修复有效但还有第二个阻塞点，应按第一个新异常继续分析，不能回到已经证伪的旧停点反复改参数。

T1 结果：补丁 Wine 路径和 Box64 版本选择正确；游戏从 `dnsapi` 继续到 Unity、Vulkan 和音频初始化，没有旧异常，但可见结果为黑屏，因此 P0 第一层修复有效、游戏整体验收未通过。详细事件见进度记录。

### G1：T1 证据归档与决策（已完成）

- [x] 将初始日志和 T1 日志归档到 `archive/runtime-logs/dave-the-diver/`，记录大小与 SHA-256。
- [x] 在本文追加实际 Wine 路径、关键事件计数、可见现象和结论。
- [x] 确认 P0 第一层修复有效，但游戏仍黑屏；按新停点将 P1 UnityPlayer 检测设为下一变量。
- [x] 提交并推送该证据节点。

归档文件：

| 测试 | 文件 | 大小 | SHA-256 |
|---|---|---:|---|
| T0 内置 Wine 失败 | `archive/runtime-logs/dave-the-diver/T0-builtin-wine11-failure.txt` | 48,526 | `9B688E03B964D960D81F37AEE579EE89901B32DF29A683588FD7C4861512E0B1` |
| T1 补丁 Wine 黑屏 | `archive/runtime-logs/dave-the-diver/T1-patched-wine11-black-screen.txt` | 44,940 | `C521624EC395AF20B1F0C205313EB00D56C66BD1AE160BDC6EEDF9BD93BDCD58` |
| T1.1 UnityPlayer 检测黑屏 | `archive/runtime-logs/dave-the-diver/T1.1-unityplayer-black-screen.txt` | 45,763 | `873B719C44BD1404CCDFE96CE0552F5662EB9D0894E8F7C63CC0A861EDEBF108` |

### T1.1：启用 Box64 UnityPlayer 检测（已完成）

唯一变量：在 T1 的同一个补丁 Wine 容器中增加 `BOX64_UNITYPLAYER=1`。Box64 0.4.5 的该选项默认值是 1；检测到 `UnityPlayer.dll` 后会设置 `BOX64_UNITY=1`，对 Windows Unity 程序启用特殊检测代码。当前 Winlator 在 `GuestProgramLauncherComponent.addBox64EnvVars()` 中显式设为 0，但容器环境变量会在其后合并，可以覆盖该值。

通过信号：

- Dave 进程环境显示 `BOX64_UNITYPLAYER=1`。
- 日志出现 `Detected UnityPlayer.dll`，随后环境显示 `BOX64_UNITY=1`。
- 游戏进入主菜单；或者即使仍失败，也越过 T1 的最后进度并给出新的首个异常。

本轮禁止同时更改 Box64 预设、图形驱动、DX wrapper、分辨率、Wine、游戏文件和启动参数。

T1.1 结果：变量生效但无行为改善。日志在 `10:55:39` 明确出现 `Detected UnityPlayer.dll`，并把 `BOX64_UNITY` 设为 1；Dave 随后仍在约一分钟内黑屏，继续经过 `GameAssembly.dll`、`dnsapi.so`、`winevulkan.so` 和音频初始化，没有进入主菜单。P1 排除，下一轮只增加 Unity `Player.log` 输出，不同时改图形设置。

### G1.1：T1.1 证据归档与决策（已完成）

- [x] 核对 `BOX64_UNITYPLAYER=1`、`Detected UnityPlayer.dll` 和 `BOX64_UNITY=1` 均已生效。
- [x] 将 T1.1 日志归档并记录哈希。
- [x] 将 P1 从主因列表降级，进入 Unity 内部日志诊断。
- [x] 准备下一轮只增加 `-logFile` 的 T1.2 操作。

### G2：把最小修复集成到内置 Wine

T1 已证明补丁解决第一层阻塞。为避免在第二层黑屏未定位时反复构建 APK，先完成 T1.1 兼容性分流，再执行内置集成：

1. 从已验证补丁产物提取所需 `nsiproxy` 模块，确认版本、架构、权限和哈希。
2. 替换 `app/src/main/assets/rootfs.tzst` 中内置 Wine 11 的对应文件；不改 Wine 版本标识、Box64 或图形组件。
3. 重新打包后进行路径安全、符号链接、执行位和解包逐文件差异校验。
4. 构建调试 APK，执行 `zipalign` 与 APK 签名验证并记录 SHA-256。
5. 提交并推送源码、构建说明与哈希；APK 超过 GitHub 普通对象限制时只记录本地路径和哈希，不直接提交大文件。

### T2：内置修复 APK 真机回归

使用全新容器选择内置 Wine，日志必须重新显示 `/opt/wine/`。连续冷启动两次并进入主菜单，以证明成功来自新内置 rootfs，而不是手机里残留的可选 Wine。通过后将日志归档、更新本文并提交推送。

### T3：后续兼容性分支（仅 T1.1 未闭环时启用）

按以下顺序每轮只改一个变量：

1. 保持补丁 Wine 和 T1.1 设置，增加 Unity 参数 `-logFile "E:\Dave the Diver\Player.log"`，取得游戏内部初始化停点。
2. 根据 `Player.log` 只验证一个图形后端变量，优先使用 Winlator 已提供的 `-force-gfx-direct`。
3. 保持 Wine/图形设置，Box64 预设改为 Stability。
4. 使用来源合法、文件校验完整的干净游戏副本对照。
5. 仍无明确异常时增加 Box64 日志级别 2 和 Wine 通道 `timestamp,pid,tid,+seh,+loaddll,+process,+thread,+nsi`。

判断边界：若干净副本能启动而当前副本不能，Winlator 侧不实现针对第三方 DRM/注入组件的绕过；记录兼容边界后转为验证正式游戏发行版本。

### G3：启动闭环与项目3性能阶段

- [ ] 连续两次冷启动通过，完成日志与 APK 归档。
- [ ] 输出根因、修复、验证矩阵、残余风险和复现步骤。
- [ ] 建立固定场景性能基线和单变量优化矩阵。
- [ ] 完成项目3报告并提交、推送最终节点。

## 五、用户当前需要执行的 T1.2 步骤

1. 继续使用刚才 T1.1 的补丁 Wine 11 容器，不要新建容器，也不要移除 `BOX64_UNITYPLAYER=1`。
2. 为同一个 `DaveTheDiver.exe` 建立或编辑快捷方式，在执行参数中只增加：

   `-logFile "E:\Dave the Diver\Player.log"`

   不要加入 `-force-gfx-direct`、Stability 或其他参数。
3. 保持 Box64 `0.4.5-dev-372739d`、Intermediate、Wine、图形驱动、DX wrapper、分辨率和游戏文件不变。
4. 通过该快捷方式启动，黑屏后继续等待至少 120 秒。若进入主菜单，停留约 30 秒后正常退出。
5. 导出 Winlator 日志并保存到电脑：

   `D:\agent\Winlator\logs\dave-the-diver\T1.2-playerlog-20260805.txt`

6. 从游戏目录取回 `Player.log`。如果该文件没有生成，也要反馈“无 Player.log”，这说明停点早于 Unity 日志系统初始化或路径不可写。
7. 反馈可见结果和大致耗时。

## 六、进度记录

### 2026-08-05：G0 建立计划

- 工作区与代码启动链已完成首轮阅读，当前实际修改目标确认为 `winlator_wzh_new`。
- 初始日志确认 Winlator 已正确创建 Dave 游戏进程，排除快捷方式未执行。
- 失败停点收敛到 `dnsapi` 加载后的早期初始化阶段；当前内置 Wine 未包含项目2已验证的 `nsiproxy` 空值保护。
- 已确定 T1 只切换补丁 Wine，禁止同轮调整 Box64、Unity 或图形设置。
- G0 文档节点已纳入本次提交并推送；下一动作是等待用户执行 T1，收到日志后立即追加 G1 证据和下一轮计划。

### 2026-08-05：G1 补丁 Wine 越过第一阻塞点，出现第二层黑屏

- 用户可见结果：从桌面双击 `DaveTheDiver.exe` 后很快进入黑屏，未进入主菜单。
- T1 日志共 668 行，44,940 bytes，SHA-256 为 `C521624EC395AF20B1F0C205313EB00D56C66BD1AE160BDC6EEDF9BD93BDCD58`。
- 日志从头到尾使用 `/opt/installed-wine/wine-11.0/` 和 Box64 `0.4.5 372739d`，版本选择正确；Dave 进程保持 Intermediate 参数和 `BOX64_UNITYPLAYER=0`，符合单变量约束。
- `10:26:04` 创建 Dave 进程，`10:26:06` 加载 `dnsapi.so`，随后在 `10:26:07` 创建附加模式的 `UnityCrashHandler64.exe`，`10:26:08` 加载 `GameAssembly.dll`，`10:26:33` 加载 `winevulkan.so`、Vulkan、打印和音频模块，最后记录持续到 `10:26:38`。
- T1 没有 `c0000005`、SIGSEGV、Unhandled page fault 或 `X connection ... broken`。`UnityCrashHandler64.exe --attach` 是 Unity 的随进程崩溃收集器，单凭它被启动不能认定主进程已经崩溃。
- 当前日志级别没有打印 `if_nameindex failed`，因此不能从该字符串直接验签补丁；但相对于 T0 在 `dnsapi` 后静止 117 秒，T1 已确定越过旧停点并进入 Unity/Vulkan 初始化，足以确认 P0 是第一层阻塞且补丁有效。
- 游戏整体验收仍失败。当前首要变量转为 `BOX64_UNITYPLAYER=1`；若 T1.1 仍黑屏，下一轮先采集 Unity `Player.log`，不直接盲改图形驱动。

### 2026-08-05：G1.1 UnityPlayer 检测无效，转向 Unity 内部日志

- T1.1 日志共 688 行，45,763 bytes，SHA-256 为 `873B719C44BD1404CCDFE96CE0552F5662EB9D0894E8F7C63CC0A861EDEBF108`。
- 日志确认 `/opt/installed-wine/wine-11.0/`、Box64 `0.4.5 372739d` 和 `BOX64_UNITYPLAYER=1`；`10:55:39` 出现 `Detected UnityPlayer.dll`，随后打印 `BOX64_UNITY=1`。
- Dave 仍在不到一分钟内黑屏。`GameAssembly.dll`、`dnsapi.so`、`winevulkan.so`、Vulkan、打印和音频模块的加载顺序与 T1 基本一致，没有主菜单、swap 或首帧完成证据。
- `c0000005`、SIGSEGV、Unhandled page fault 和 X connection broken 仍未出现；`UnityCrashHandler64.exe --attach` 仍只能视为 Unity 的崩溃收集器启动，不能单独作为主进程崩溃证据。
- 结论：P1 已排除。当前问题收敛到 Unity 图形/窗口初始化或游戏内部等待；下一轮只增加 `-logFile` 生成 `Player.log`，保持所有运行参数不变。

## 七、Git 关键节点

| 节点 | 推送内容 | 触发条件 | 状态 |
|---|---|---|---|
| G0 | 初始诊断、执行计划、T1 操作 | 本文复核完成 | 已完成（2026-08-05） |
| G1 | 失败/成功日志归档、T1 结论、下一轮计划 | 收到并分析 T1 日志 | 已完成（2026-08-05） |
| G1.1 | T1.1 日志归档、UnityPlayer 变量验证、T1.2 计划 | 收到并分析 T1.1 日志 | 已完成（2026-08-05） |
| G2 | 内置 Wine/rootfs 最小修复、构建验证、APK 哈希 | T1.2/T3 完成兼容性分流 | 待执行 |
| G3 | 两次冷启动回归、最终报告、性能阶段入口 | 内置修复真机通过 | 待执行 |

所有节点推送到 `origin/main`（`https://github.com/SilenceWanna/winlator_wzh.git`）。提交前先检查工作树和远程差异，不覆盖用户改动；游戏本体、临时解包目录和超大 APK 不进入 Git。
