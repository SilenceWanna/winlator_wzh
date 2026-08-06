# 项目3：Dave the Diver 启动故障修复计划与执行记录

> 建立日期：2026-08-05  
> 当前状态：T1.10A2 已在 `BOX64_UNITYPLAYER=1` 下越过重复闪退点并正常运行；等待 T1.10B 六分钟冷启动验收
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
| Unity 开关 | 当前样本需要 `BOX64_UNITYPLAYER=1` | 值为 0 的 T1.9/T1.10A 均在 114–115 秒闪退；值为 1 的 T1.10A2 越过该点且正常结束 |
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
| P1（因果回切已支持，待重复性确认） | Box64 Unity 专用策略被关闭会使当前样本在启动后期不稳定 | T1.1 证明该开关不能单独解决 DXVK 黑屏；值为 0 的两轮均在 114–115 秒进入崩溃处理路径，回切为 1 的 T1.10A2 越过该点 | 最终配置保留 1；T1.10B 六分钟冷启动确认重复性 |
| P2a（未支持） | Unity 多线程渲染路径导致首帧前等待 | T1.4 已在实际 argv 中启用 `-force-gfx-direct`；`Player.log` 停止位置与 T1.3 相同，仍未进入场景 | 已完成当前验证；不再单独修改 Unity 渲染线程参数 |
| P2b（第二层已确认） | DXVK 的 D3D11-to-Vulkan 路径在设备创建后、首帧前发生等待 | T1.3-T1.5 使用 DXVK/Turnip 时均停在 Odin Serializer 后；T1.6 只改 WineD3D 后进入资源加载、场景切换和教程任务 | 已由 T1.6 功能路径确认；后续回退无关开关并建立最终配置 |
| P3（已排除） | Box64 Intermediate 的内存模型不适合该 Unity 版本 | T1.5 已启用 Stability：`BIGBLOCK=0`、`STRONGMEM=2`、`WEAKBARRIER=0`；可见结果和 Unity 停点不变 | 已完成；恢复/保留 Stability 均不再作为主变量 |
| P4（非充分原因/范围风险） | 当前游戏文件中的第三方 Steam/Unity 替换层在 Wine/Box64 下阻塞 | 本地目录存在 `tenoke.ini`、`UnityPlayer.tnk` 和额外 `steamclient64.dll`，但相同文件在 T1.6 WineD3D 下成功运行 | 不能再把它判为本次黑屏的充分原因；最终结论仅覆盖当前样本，正式发行版仍需独立验证 |

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
| T1.2 损坏的快捷方式命令 | `archive/runtime-logs/dave-the-diver/T1.2-invalid-shortcut-command.txt` | 30,272 | `05B25941D43F9A95DA9CABB796D3A46B486955290D3106957910155B3DD7A8C6` |
| T1.3 快捷方式修复后仍黑屏 | `archive/runtime-logs/dave-the-diver/T1.3-shortcut-fixed-black-screen.txt` | 41,132 | `AF78F6162F09DD5DC37FC44BC65531E76F02DE605EC291084AC5C90E460A0012` |
| T1.3 Unity 内部日志 | `archive/runtime-logs/dave-the-diver/T1.3-Player.log` | 1,119 | `B033E0A9425CF8D8C2781FD380C3AA22C6A5C3FA2F865F25732E0278A2C26AB5` |
| T1.4 `-force-gfx-direct` 黑屏 | `archive/runtime-logs/dave-the-diver/T1.4-force-gfx-direct-black-screen.txt` | 41,400 | `F231EBDC34E3A8366687944589E951F8EAEB555A29FAD506067F84DC837CABF5` |
| T1.4 Unity 内部日志 | `archive/runtime-logs/dave-the-diver/T1.4-Player-force-gfx-direct.log` | 1,071 | `796B971A6134F2E9EB273D81F25C98BCF7BE6C8F8619B2EBBB6C8C10DC3F46B90` |
| T1.5 Stability 黑屏 | `archive/runtime-logs/dave-the-diver/T1.5-stability-force-gfx-direct-black-screen.txt` | 41,400 | `C2B63BC390346929E5A8B827FDCCC737A83183085F73D2CBE983BEC396A208C9` |
| T1.5 Unity 内部日志 | `archive/runtime-logs/dave-the-diver/T1.5-Player-stability-force-gfx-direct.log` | 1,071 | `977C783F47895A65B9BAB16744DCF90002095D9BA1B5F4438C80D09E23150752` |
| T1.6 WineD3D 成功 | `archive/runtime-logs/dave-the-diver/T1.6-wined3d-stability-success.txt` | 270,759 | `4FA3731A4D05F2739398C72BFD536B6489C850A5D1A8D2990555839058BD7A37` |
| T1.6 Unity 成功日志 | `archive/runtime-logs/dave-the-diver/T1.6-Player-wined3d-stability-success.log` | 11,374 | `3AF7129BF6B633FE098977FFE866D763B277D7CC852C485337305A38F6392CFF` |
| T1.7 WineD3D 无直连参数成功 | `archive/runtime-logs/dave-the-diver/T1.7-wined3d-no-force-gfx-direct-success.txt` | 274,957 | `E18E98F1BB1588389F761D99828294D8D970AA88CB92ACF88791EA741C73A64` |
| T1.7 Unity 成功日志 | `archive/runtime-logs/dave-the-diver/T1.7-Player-wined3d-no-force-gfx-direct-success.log` | 8,972 | `FA153783B48F6FB0019609C461A41D1736C2646751808A6302D87276F4256B6D` |
| T1.8 WineD3D + Intermediate 成功 | `archive/runtime-logs/dave-the-diver/T1.8-wined3d-intermediate-success.txt` | 273,565 | `B3C7BEB8793D6D862A7CF332418DEE161B83C82C53C2936ACBCF866D41D48313` |
| T1.8 Unity 成功日志 | `archive/runtime-logs/dave-the-diver/T1.8-Player-wined3d-intermediate-success.log` | 9,097 | `2ACCD29F8C16F429757C1BFF66B7DE8AD66BE3D530545794CAEB363F83F5ED87` |
| T1.9 默认 UnityPlayer 开关闪退 | `archive/runtime-logs/dave-the-diver/T1.9-wined3d-intermediate-unity-default-crash.txt` | 281,510 | `FAD9EEDC8AE7B9A86C5CEE00F88BBF7B55FDAC557DB2AFF87CA18E2C3119E9E8` |
| T1.9 Unity 闪退日志 | `archive/runtime-logs/dave-the-diver/T1.9-Player-wined3d-intermediate-unity-default-crash.log` | 9,595 | `E8090C4D3CC082ACA16E3833046A125C444FE938CFD0A968FFB914C3E765E3B4` |
| T1.10A 未生效的 UnityPlayer 覆盖重复闪退 | `archive/runtime-logs/dave-the-diver/T1.10A-invalid-unity-override-repeat-crash.txt` | 270,687 | `4717FC0FDBC2A4ABDC51B844BD8637435AC8D58DBA959A503D9EE20DE7B14786` |
| T1.10A Unity 重复闪退日志 | `archive/runtime-logs/dave-the-diver/T1.10A-Player-invalid-unity-override-repeat-crash.log` | 8,855 | `5424886C11FC03A207640A0A3B1E11BAE06FBBAD6E7B2E25CB1F1D55C67CAAF4` |
| T1.10A2 UnityPlayer 回切成功 | `archive/runtime-logs/dave-the-diver/T1.10A2-final-unity-on-success.txt` | 268,540 | `30D2C12D3EB92875A14D77EF09230631E9FEFAB002DD31F337A858ADEF339CD3` |
| T1.10A2 Unity 成功日志 | `archive/runtime-logs/dave-the-diver/T1.10A2-Player-final-unity-on-success.log` | 8,973 | `B777F9E4C5238DEEDC8258A1849F7C7387BAC1BF2D8C963BF10A35CAA4315A8F` |

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

### G1.2：修复快捷方式路径重复引号（已完成）

- [x] 从 T1.2 日志确认游戏进程没有创建，失败发生在 `winhandler` 参数解析阶段。
- [x] 修复 `Shortcut.java`：移除 `.desktop` 的 `Exec=wine "..."` 可执行路径最外层引号，再交给启动命令重新转义。
- [x] 使用 Temurin JDK 17.0.20 完成 `compileDebugJavaWithJavac` 和 `assembleDebug`。
- [x] 完成 APK 对齐、V2 签名、签名证书一致性和 SHA-256 校验。
- [x] 归档 T1.2 日志并形成 T1.3 真机复测步骤。

候选 APK：

```text
D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-shortcut-quote-fix.apk
Size: 208635197 bytes
SHA-256: 4E3B812551D1767B5B7792536A652A36CD76CA38091C4FF41F2B3535E707298A
Signature: APK Signature Scheme v2, 1 signer
Signer SHA-256: B6396F6CD549475DEC0893AC0CAB0E03770F403FB37679BAE02418A492270B07
```

签名证书与上一版 `app-debug-wine11-default-box64-0.4.5-dev.apk` 完全一致，可以直接覆盖安装而不清除应用数据。

### G1.3：快捷方式修复回归与 Unity 初始化定位（已完成）

- [x] T1.3 Winlator 日志重新出现 `Detected running wine with "DaveTheDiver.exe"`，证明快捷方式引号修复生效。
- [x] `-logFile Player.log` 被完整传给游戏并生成 Unity `Player.log`。
- [x] Unity 已创建 D3D11 11.1 设备，识别 Turnip Adreno 725，并完成新输入系统、XInput 和触摸输入初始化。
- [x] 日志没有 `c0000005`、SIGSEGV、Unhandled page fault 或 X connection broken；可见结果仍为黑屏。
- [x] 将当前停点收敛到 `threaded=1` 的 Unity 渲染路径或 Odin Serializer 之后的游戏内部初始化，形成 T1.4 单变量测试。

### G1.4：`-force-gfx-direct` 无行为改善，转向 Box64 Stability（已完成）

- [x] T1.4 Winlator 日志共 629 行、41,400 bytes，SHA-256 为 `F231EBDC34E3A8366687944589E951F8EAEB555A29FAD506067F84DC837CABF5`；Unity 日志为 1,071 bytes，SHA-256 为 `796B971A6134F2E9EB273D81F25C98BCF7BE6C8F8619B2EBBB6C8C10DC3F46B90`。
- [x] Dave 实际 argv 明确包含 `-logFile Player-T1.4.log -force-gfx-direct`；工作目录、补丁 Wine、Box64 版本和 `BOX64_UNITYPLAYER=1` 均保持正确。
- [x] T1.4 `Player.log` 不再打印 T1.3 的 `GfxDevice ... threaded=1`，说明参数已被 Unity 接收；但 D3D11 11.1、Turnip Adreno 725、输入初始化和 Odin Serializer 停止位置没有实质变化，仍为黑屏。
- [x] 结论：多线程渲染开关不是当前可见故障的充分原因；下一轮保持 T1.4 参数，只切换 Box64 Stability。

### G1.5：Box64 Stability 无行为改善，转向 DX wrapper 对照（已完成）

- [x] T1.5 Winlator 日志共 629 行、41,400 bytes，SHA-256 为 `C2B63BC390346929E5A8B827FDCCC737A83183085F73D2CBE983BEC396A208C9`；Unity 日志共 27 行、1,071 bytes，SHA-256 为 `977C783F47895A65B9BAB16744DCF90002095D9BA1B5F4438C80D09E23150752`。
- [x] Dave 进程确认 Stability 全部生效：`BIGBLOCK=0`、`FASTNAN=0`、`STRONGMEM=2`、`WAIT=0`、`WEAKBARRIER=0`；Wine、Box64 版本、Unity 开关和启动参数保持不变。
- [x] T1.5 与 T1.4 的 Unity 日志除 `UnloadTime` 从 `43.550800 ms` 变为 `30.718300 ms` 外逐行一致，仍在 Odin Serializer 启用非对齐内存访问后结束。
- [x] 结论：P3 排除；T1.6 保留 Stability 和现有参数，只把 DX wrapper 从 DXVK 改为 WineD3D，完成 D3D11 翻译路径对照。

### G1.6：WineD3D 首次成功进入游戏（已完成）

- [x] 用户确认 T1.6 成功启动并进入游戏；Winlator 日志共 3,001 行、270,759 bytes，Unity 日志共 226 行、11,374 bytes。
- [x] WineD3D 激活证据完整：日志显示 `Using the GLSL shader backend`、`Using the OpenGL renderer` 和原生 `libGL.so.1`；不再经过 T1.3-T1.5 的 DXVK/winevulkan 成功路径。
- [x] Unity Renderer 从 DXVK 样本的 `Turnip Adreno 725` 变为 WineD3D 暴露的 `NVIDIA GeForce GTX 480`，D3D11 feature level 仍为 11.1。
- [x] Unity 越过原 Odin 停点，打印版本 `v1.0.2.1270.steam`、LogoManager、Addressables 资源加载、Steam 回调、多次 `CoChangeSceneAsync`，并启动 `Tutorial_Mission01`。
- [x] 建立最小成功集回退顺序：T1.7 移除 `-force-gfx-direct`；成功后 T1.8 恢复 Intermediate；再验证默认 UnityPlayer 开关与两次冷启动。

### G1.7：移除 `-force-gfx-direct` 后仍成功（已完成）

- [x] 用户确认 T1.7 仍成功；Winlator 日志共 3,033 行、274,957 bytes，Unity 日志共 185 行、8,972 bytes。
- [x] Dave 实际 argv 只包含 `-logFile Player-T1.7.log`，不再包含 `-force-gfx-direct`；WineD3D 仍打印 GLSL/OpenGL backend，Box64 Stability 和 `BOX64_UNITYPLAYER=1` 均保持生效。
- [x] Unity 日志继续完成 D3D11 11.1、WineD3D Renderer、版本打印、LogoManager、SoundConfigManager、存档转换和 Steamworks 回调，没有回到 Odin 停点或出现显式崩溃。
- [x] 结论：`-force-gfx-direct` 从成功配置中移除；下一轮只恢复 Box64 Intermediate，评估性能更好的预设是否同样可用。

### G1.8：恢复 Box64 Intermediate 后仍成功（已完成）

- [x] 用户确认 T1.8 成功；Winlator 日志共 3,021 行、273,565 bytes，Unity 日志共 186 行、9,097 bytes。
- [x] Dave 进程确认 Intermediate 生效：`BIGBLOCK=2`、`FASTNAN=1`、`STRONGMEM=0`、`WAIT=1`、`WEAKBARRIER=2`；实际 argv 仍只有 `-logFile Player-T1.8.log`。
- [x] WineD3D 继续使用 GLSL/OpenGL，Unity 完成版本、Logo、资源、存档转换和 Steamworks 回调，没有回到 DXVK 黑屏停点。
- [x] 结论：Stability 不是成功必需项；成功配置收敛为“补丁 Wine + WineD3D + Intermediate + `BOX64_UNITYPLAYER=1`”。T1.9 只移除 UnityPlayer 用户覆盖。

### G1.9：恢复默认 UnityPlayer 开关后闪退（已完成）

- [x] 用户确认 T1.9 闪退；Winlator 日志共 3,099 行、281,510 bytes，Unity 日志共 196 行、9,595 bytes。
- [x] 单变量生效：Dave 进程保持补丁 Wine、WineD3D 和 Intermediate，实际 argv 只有 `-logFile Player-T1.9.log`，环境明确为 `BOX64_UNITYPLAYER=0`，且不再出现 `Detected UnityPlayer.dll` 或 `BOX64_UNITY=1`。
- [x] 本轮不是启动早期失败：Unity 已完成 D3D11 11.1/WineD3D 初始化，并继续打印版本、LogoManager、SoundConfigManager、存档转换、CursorManager 和 Steamworks 回调。
- [x] Dave 于 `10:16:18` 启动；`10:18:12` 第二次创建 `UnityCrashHandler64.exe`，其参数不再包含初始监控进程使用的 `--attach`，与约 114 秒后的可见闪退一致。日志未打印 `c0000005`、SIGSEGV、Unhandled page fault 或可用崩溃地址，因此不推断具体故障指令。
- [x] 结论：`BOX64_UNITYPLAYER=1` 不能单独修复 DXVK 黑屏，但 T1.9 已使它成为当前 WineD3D 成功路径的首要稳定性变量。T1.10A 仍为 0，只是重复验证，不能作为“开关为 1 仍闪退”的证据。

### G1.10A：UnityPlayer 覆盖未生效，重复默认值闪退（已完成）

- [x] 用户反馈 T1.10A 仍在运行一段时间后闪退；Winlator 日志共 3,006 行、270,687 bytes，SHA-256 为 `4717FC0FDBC2A4ABDC51B844BD8637435AC8D58DBA959A503D9EE20DE7B14786`；Unity 日志共 183 行、8,855 bytes，SHA-256 为 `5424886C11FC03A207640A0A3B1E11BAE06FBBAD6E7B2E25CB1F1D55C67CAAF4`。
- [x] Dave 进程仍显示 `BOX64_UNITYPLAYER=0`，没有 `Detected UnityPlayer.dll` 或 `BOX64_UNITY=1`；因此用户环境变量没有进入实际容器启动环境，本轮不是 T1.10 的有效 A 测试。
- [x] 运行层和时序与 T1.9 重复：补丁 Wine、WineD3D、Intermediate 生效；Dave 于 `10:59:19` 启动，`11:01:14` 第二次启动不带 `--attach` 的 `UnityCrashHandler64.exe`，约 115 秒后闪退。Unity 同样完成 D3D11、版本、资源、存档和 Steamworks 初始化。
- [x] 结论：该样本进一步重复验证默认值 0 的闪退，但尚未验证 `BOX64_UNITYPLAYER=1`。下一轮先确认快捷方式设置被保存，再执行 T1.10A2。

### G1.10A2：UnityPlayer 回切后越过闪退点（已完成）

- [x] 用户确认游戏成功启动且未闪退；Winlator 日志共 2,978 行、268,540 bytes，Unity 日志共 185 行、8,973 bytes。
- [x] Dave 进程明确为 `BOX64_UNITYPLAYER=1`，并出现 `Detected UnityPlayer.dll` 和 `BOX64_UNITY=1`；实际 argv 只有 `-logFile Player-T1.10-A2.log`，说明回切变量正确生效。
- [x] 补丁 Wine、WineD3D GLSL/OpenGL 和 Intermediate 均保持不变；Unity 完成 D3D11 11.1、版本、资源、存档、CursorManager、Steamworks 回调及后续资源卸载。
- [x] Dave 从 `11:18:22` 运行到 `11:21:26`，日志覆盖约 184 秒，越过默认值 0 两轮稳定发生在 114–115 秒的闪退点；全程只有启动时带 `--attach` 的 CrashHandler，没有第二个崩溃处理进程。
- [x] 末尾 `X connection to :0 broken` 与用户主动结束本次正常运行一致。由于本轮不足计划中的 5 分钟，T1.10B 改为进入可操作画面后计时 6 分钟，作为最终重复性与时长验收。

### G2：把最小修复集成到内置 Wine

T1 已证明补丁解决第一层阻塞，T1.6 已证明 WineD3D 解决第二层黑屏，T1.9/T1.10A/T1.10A2 已完成 Box64 Unity 专用策略的失败重复与成功回切。先完成 T1.10B 六分钟冷启动验收，再执行内置集成：

1. 从已验证补丁产物提取所需 `nsiproxy` 模块，确认版本、架构、权限和哈希。
2. 替换 `app/src/main/assets/rootfs.tzst` 中内置 Wine 11 的对应文件；不改 Wine 版本标识、Box64 或图形组件。
3. 重新打包后进行路径安全、符号链接、执行位和解包逐文件差异校验。
4. 构建调试 APK，执行 `zipalign` 与 APK 签名验证并记录 SHA-256。
5. 提交并推送源码、构建说明与哈希；APK 超过 GitHub 普通对象限制时只记录本地路径和哈希，不直接提交大文件。

### T2：内置修复 APK 真机回归

使用全新容器选择内置 Wine，日志必须重新显示 `/opt/wine/`。连续冷启动两次并进入主菜单，以证明成功来自新内置 rootfs，而不是手机里残留的可选 Wine。通过后将日志归档、更新本文并提交推送。

### T3：兼容性分支与最小成功集回退

按以下顺序每轮只改一个变量：

1. T1.4 只增加 Winlator/Unity 已提供的 `-force-gfx-direct`，验证多线程渲染路径；该变量已验证无行为改善。
2. T1.5 保留 `-force-gfx-direct`，只把 Box64 预设改为 Stability；该变量已验证无行为改善。
3. T1.6 保持其他设置，只把 DX wrapper 从 DXVK 改为 WineD3D；该变量已成功使游戏进入教程任务。
4. T1.7 保持 WineD3D 和 Stability，只移除 `-force-gfx-direct`；该变量已验证不是成功必需项。
5. T1.8 保持 WineD3D 和无额外图形参数，只把 Box64 从 Stability 恢复为 Intermediate；已验证成功。
6. T1.9 删除用户设置的 `BOX64_UNITYPLAYER=1`，验证 Winlator 默认 `BOX64_UNITYPLAYER=0`；该轮在约 114 秒后闪退，强烈表明默认值不适用于当前样本，仍需回切复验排除偶发崩溃。
7. T1.10A2 正确保存 `BOX64_UNITYPLAYER=1` 后已完成一次有效冷启动并越过重复闪退点；T1.10B 保持配置不变，进入可操作画面后持续运行 6 分钟，随后进入 G2 内置 Wine 集成。

判断边界：若干净副本能启动而当前副本不能，Winlator 侧不实现针对第三方 DRM/注入组件的绕过；记录兼容边界后转为验证正式游戏发行版本。

### G3：启动闭环与项目3性能阶段

- [ ] 连续两次冷启动通过，完成日志与 APK 归档。
- [ ] 输出根因、修复、验证矩阵、残余风险和复现步骤。
- [ ] 建立固定场景性能基线和单变量优化矩阵。
- [ ] 完成项目3报告并提交、推送最终节点。

## 五、用户当前需要执行的 T1.10B 步骤

1. 保持当前已经生效的全部设置不变：补丁 Wine 11、WineD3D、Box64 `Intermediate`、`BOX64_UNITYPLAYER=1`、Turnip、分辨率及游戏文件均不改。
2. 重新打开快捷方式设置，确认 Environment Variables 中仍有 Name `BOX64_UNITYPLAYER`、Value `1`。
3. `Exec Arguments` 只改为：

   `-logFile Player-T1.10-B.log`

4. 正常退出当前容器和 Winlator，并从 Android 最近任务中移除 Winlator；重新打开应用后从 Dave 快捷方式冷启动。
5. 进入可操作画面后再开始计时，连续操作或停留至少 6 分钟；不要在计时结束前主动关闭容器。完成后正常退出。
6. 导出 Winlator 日志为：

   `D:\agent\Winlator\logs\dave-the-diver\T1.10B-final-cold-start-20260806.txt`

   从 `E:\Dave the Diver\` 取回 Unity 日志并保存为：

   `D:\agent\Winlator\logs\dave-the-diver\T1.10B-Player-final.log`

7. 反馈从双击到可操作画面的耗时、6 分钟内是否稳定，以及是否能正常退出；无论成功或失败都保留两份日志。

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

### 2026-08-05：G1.2 T1.2 日志定位快捷方式重复引号并完成源码修复

- 用户使用 `-logFile "E:\\Dave the Diver\\Player.log"` 后收到 `invalid name`。导出的 T1.2 Winlator 日志共 466 行、30,272 bytes，SHA-256 为 `05B25941D43F9A95DA9CABB796D3A46B486955290D3106957910155B3DD7A8C6`。
- 日志没有 `Detected running wine with "DaveTheDiver.exe"`、`Detected UnityPlayer.dll` 或 `GameAssembly.dll`。`14:27:41` 的参数已经损坏：目录参数带多余首尾引号，文件名带多余尾引号，`-logFile` 前还有空字符串参数；`14:27:59` X session 退出。因此该样本不是 Dave 黑屏复现，而是游戏启动前的快捷方式命令失败。
- 源码根因为 `Shortcut.java` 从 `.desktop` 的 `Exec=wine "..."` 取路径时保留了可执行文件的包装引号，随后 `XServerDisplayActivity` 又对目录和文件名加引号。新增的 `Exec Arguments` 让原有隐患变成可见的错误拆分。
- 最小修复只剥离整个路径最外层成对双引号。Windows 文件名不允许双引号，因此不会损失合法路径字符；路径内部空格和反斜杠保持不变。
- Temurin JDK 17.0.20 下 `compileDebugJavaWithJavac` 和 `assembleDebug` 均成功。APK 通过 `zipalign -c -v 4` 和 `apksigner verify --verbose`，V2 签名有效且证书与上一版一致。
- T1.3 使用修复 APK、原补丁 Wine 容器和相对参数 `-logFile Player.log` 复测。只有日志重新出现 Dave 进程后，才继续分析 Unity `Player.log`。

### 2026-08-05：G1.3 快捷方式修复生效，黑屏收敛到 Unity 内部初始化

- T1.3 Winlator 日志共 624 行、41,132 bytes，SHA-256 为 `AF78F6162F09DD5DC37FC44BC65531E76F02DE605EC291084AC5C90E460A0012`；Unity `Player.log` 为 1,119 bytes，SHA-256 为 `B033E0A9425CF8D8C2781FD380C3AA22C6A5C3FA2F865F25732E0278A2C26AB5`。
- 启动参数已恢复正常：工作目录为 `E:\\Dave the Diver`，Dave 实际进程参数为 `E:\Dave the Diver\DaveTheDiver.exe -logFile Player.log`。`17:27:46` 出现 Dave 进程、`Detected UnityPlayer.dll` 和 `BOX64_UNITY=1`，证明 G1.2 修复通过真机回归。
- 游戏继续使用 `/opt/installed-wine/wine-11.0/`、Box64 `0.4.5 372739d`、Intermediate 和 `BOX64_UNITYPLAYER=1`；随后加载 `GameAssembly.dll`、`winevulkan.so` 和 Vulkan。Winlator 日志没有旧 `nsiproxy` 异常或新的显式崩溃。
- Unity 日志确认引擎为 2020.3.48f1，D3D11 feature level 11.1 设备创建成功，渲染器为 Turnip Adreno 725，VRAM 11,473 MB；新输入系统、XInput 和触摸输入均完成初始化。因此不能再把 GPU 未识别或 D3D11 设备创建失败作为当前主因。
- Unity 日志显示 `GfxDevice: creating device client; threaded=1`，最后记录 Odin Serializer 在 WindowsPlayer 上完成内存读取测试并启用非对齐读写，之后没有场景加载或首帧记录。下一轮 T1.4 只增加 `-force-gfx-direct`，验证 Unity 多线程渲染路径；若仍停在相同位置，再单独切换 Box64 Stability。

### 2026-08-05：G1.4 `-force-gfx-direct` 已生效但仍黑屏

- T1.4 实际参数为 `E:\\Dave the Diver\\DaveTheDiver.exe -logFile Player-T1.4.log -force-gfx-direct`，Winlator 日志共 629 行、41,400 bytes，Unity 日志共 27 行、1,071 bytes。
- `-force-gfx-direct` 已被传给 Dave；新 `Player.log` 不再出现 `GfxDevice: creating device client; threaded=1`，因此不能把本轮判定为参数未生效。`UnloadTime` 从 T1.3 的 `156.030300 ms` 降为 `43.550800 ms`，但这没有带来可见启动改善。
- T1.4 仍完成 D3D11 11.1、Turnip Adreno 725、输入系统和 Odin Serializer 初始化，日志同样在 Odin 启用非对齐内存访问后结束。Winlator 侧仍没有 `c0000005`、SIGSEGV、Unhandled page fault 或 X connection broken。
- 结论：P2 的多线程渲染开关未能解释黑屏；T1.5 保留 `-force-gfx-direct`，只将 Box64 预设从 Intermediate 切换为 Stability，验证 Box64 内存/屏障策略。

### 2026-08-05：G1.5 Stability 已生效但 Unity 停点完全不变

- T1.5 Winlator 日志共 629 行、41,400 bytes；Dave 实际 argv 为 `E:\\Dave the Diver\\DaveTheDiver.exe -logFile Player-T1.5.log -force-gfx-direct`。
- Stability 环境值在 Dave 和 UnityCrashHandler 进程中均正确生效：`BIGBLOCK=0`、`FASTNAN=0`、`STRONGMEM=2`、`WAIT=0`、`WEAKBARRIER=0`。日志没有出现 `c0000005`、SIGSEGV、Unhandled page fault 或 X connection broken。
- T1.5 Unity 日志仍为 27 行，与 T1.4 仅 `UnloadTime` 数值不同；D3D11 11.1、Turnip Adreno 725、输入初始化和 Odin Serializer 结束位置完全相同。因此 Box64 Stability 不构成修复。
- 本地游戏目录进一步确认包含 `tenoke.ini`（SHA-256 `21CACA1ED7E5D2731FD754D60DE5338554F15198AE5928542D57F30875035B82`）、`UnityPlayer.tnk` 和额外 `steamclient64.dll`。T1.6 只做 Winlator 自带 WineD3D 对照；若仍失败，停止针对该第三方替换层调参，转向正式发行版干净副本。

### 2026-08-05：G1.6 WineD3D 越过黑屏并进入教程任务

- 用户可见结果：游戏成功启动并进入可操作内容。T1.6 Winlator 日志共 3,001 行、270,759 bytes，SHA-256 为 `4FA3731A4D05F2739398C72BFD536B6489C850A5D1A8D2990555839058BD7A37`；Unity 日志共 226 行、11,374 bytes，SHA-256 为 `3AF7129BF6B633FE098977FFE866D763B277D7CC852C485337305A38F6392CFF`。
- T1.6 仅将 DX wrapper 从 DXVK 改为 WineD3D，其余保持 Stability、`BOX64_UNITYPLAYER=1`、`-force-gfx-direct` 和补丁 Wine。`18:13:28` 日志明确打印 GLSL shader backend 与 OpenGL renderer，证明对照变量生效。
- Unity 仍创建 D3D11 11.1 设备，但 Renderer 变为 WineD3D 默认的 `NVIDIA GeForce GTX 480`、VRAM 2,048 MB；随后越过此前所有失败样本共同停止的 Odin Serializer 位置。
- 成功日志继续出现 Build Version、LogoManager、SoundConfigManager、CursorManager、Steamworks 回调、Addressables 完成事件、`CoChangeSceneAsync` 和 `Tutorial_Mission01`，形成比“窗口可见”更强的功能路径证据。
- WineD3D 日志包含缺少部分 OpenGL 扩展、未实现 swap effect、`ensure_mta` 等警告，但游戏仍持续运行。这些当前归为兼容性噪声/残余风险，不作为启动失败；末尾 X connection broken 出现在用户正常结束会话后。
- 当前只完成一次成功运行，尚未满足连续两次冷启动验收。T1.7 先移除 `-force-gfx-direct`；若仍成功，再逐项恢复性能更好的 Box64 Intermediate 和默认 UnityPlayer 设置。

### 2026-08-05：G1.7 移除 `-force-gfx-direct` 后仍成功

- 用户可见结果：T1.7 仍成功进入游戏。Winlator 日志共 3,033 行、274,957 bytes，SHA-256 为 `E18E98F1BB1588389F761D99828294D8D970AA88CB92ACF88791EA741C73A64`；Unity 日志共 185 行、8,972 bytes，SHA-256 为 `FA153783B48F6FB0019609C461A41D1736C2646751808A6302D87276F4256B6D`。
- Dave 实际 argv 为 `E:\\Dave the Diver\\DaveTheDiver.exe -logFile Player-T1.7.log`，没有 `-force-gfx-direct`；WineD3D 仍使用 GLSL/OpenGL，证明 T1.6 的关键修复是 DX wrapper，而不是 Unity 直连渲染参数。
- Unity 继续打印 D3D11 11.1、`NVIDIA GeForce GTX 480`、版本 `v1.0.2.1270.steam`、LogoManager、资源加载、存档转换和 Steamworks 回调；未出现 `c0000005`、SIGSEGV 或 X connection broken。
- T1.7 使成功组合进一步收敛为“补丁 Wine + WineD3D + Stability + `BOX64_UNITYPLAYER=1`”，下一轮只恢复 Intermediate。

### 2026-08-06：G1.8 恢复 Intermediate 后仍成功

- 用户可见结果：T1.8 成功进入游戏。Winlator 日志共 3,021 行、273,565 bytes，SHA-256 为 `B3C7BEB8793D6D862A7CF332418DEE161B83C82C53C2936ACBCF866D41D48313`；Unity 日志共 186 行、9,097 bytes，SHA-256 为 `2ACCD29F8C16F429757C1BFF66B7DE8AD66BE3D530545794CAEB363F83F5ED87`。
- Dave 实际 argv 为 `E:\\Dave the Diver\\DaveTheDiver.exe -logFile Player-T1.8.log`；Intermediate 的五个关键环境值全部符合源码定义，WineD3D 仍明确打印 GLSL/OpenGL renderer。
- Unity 日志继续越过 Odin Serializer，完成 D3D11 11.1、版本、Logo、SoundConfigManager、存档转换、CursorManager 和 Steamworks 回调；没有 `c0000005`、SIGSEGV、Unhandled page fault 或 X connection broken。
- Stability 已从必要条件中排除。下一轮只删除 `BOX64_UNITYPLAYER=1` 用户覆盖，验证 Winlator 默认值 0 是否可用。

### 2026-08-06：G1.9 默认 UnityPlayer 开关导致启动后期闪退

- 用户可见结果：T1.9 闪退。Winlator 日志共 3,099 行、281,510 bytes，SHA-256 为 `FAD9EEDC8AE7B9A86C5CEE00F88BBF7B55FDAC557DB2AFF87CA18E2C3119E9E8`；Unity 日志共 196 行、9,595 bytes，SHA-256 为 `E8090C4D3CC082ACA16E3833046A125C444FE938CFD0A968FFB914C3E765E3B4`。
- `BOX64_UNITYPLAYER=0` 在 Dave 进程中明确生效，且没有 UnityPlayer 自动检测记录；WineD3D、Intermediate、补丁 Wine 和无额外图形参数均保持不变，满足单变量约束。
- Unity 日志已越过早先黑屏的 Odin Serializer 停点，完成版本、Logo、声音、存档、光标和 Steamworks 初始化。因此本轮是启动后期闪退，不是 DXVK 黑屏复发。
- 启动约 114 秒后出现第二个不带 `--attach` 的 `UnityCrashHandler64.exe`，可作为崩溃处理路径证据；现有日志没有异常地址或调用栈，具体崩溃指令仍未知。
- T1.8 成功与 T1.9 失败之间唯一配置差异是 UnityPlayer 开关；更早的 T1.6、T1.7 成功日志分别覆盖约 351 秒和 151 秒，且都只有启动时带 `--attach` 的 CrashHandler。该对照高度怀疑默认值 0 是闪退原因，但 T1.8 日志只覆盖约 109 秒，故最终配置先恢复 `BOX64_UNITYPLAYER=1`，再由有效的 T1.10A2/B 两次独立冷启动完成因果确认。

### 2026-08-06：G1.10A 覆盖变量未生效，重复默认值闪退

- 本轮文件名为 `logs.txt` 和 `Player-T1.10-A.log`，实际 Dave 参数为 `-logFile Player-T1.10-A.log`，但 Dave 进程的 `BOX64_UNITYPLAYER=0` 与 T1.9 完全一致。
- 日志共 3,006 行、270,687 bytes，SHA-256 为 `4717FC0FDBC2A4ABDC51B844BD8637435AC8D58DBA959A503D9EE20DE7B14786`；Unity 日志共 183 行、8,855 bytes，SHA-256 为 `5424886C11FC03A207640A0A3B1E11BAE06FBBAD6E7B2E25CB1F1D55C67CAAF4`。
- Dave 在 `10:59:19` 启动，`11:01:14` 启动第二个不带 `--attach` 的 CrashHandler；这与 T1.9 的约 114 秒闪退时序重复。该证据用于确认默认值路径可重复，不用于判断开关 1 的效果。
- 已查明正确保存边界：快捷方式设置中的 Environment Variables 由 `ShortcutSettingsDialog` 保存到快捷方式 `envVars`，运行时再由 `XServerDisplayActivity` 合并；只在新增变量子对话框中输入而未确认外层设置，不会进入下次启动。

### 2026-08-06：G1.10A2 UnityPlayer 回切成功

- 用户可见结果：游戏成功启动且未闪退。Winlator 日志共 2,978 行、268,540 bytes，SHA-256 为 `30D2C12D3EB92875A14D77EF09230631E9FEFAB002DD31F337A858ADEF339CD3`；Unity 日志共 185 行、8,973 bytes，SHA-256 为 `B777F9E4C5238DEEDC8258A1849F7C7387BAC1BF2D8C963BF10A35CAA4315A8F`。
- 回切信号完整：Dave 及其他 Box64 进程均为 `BOX64_UNITYPLAYER=1`，Dave 加载时检测到 `UnityPlayer.dll` 并设置 `BOX64_UNITY=1`；WineD3D 和 Intermediate 保持生效。
- Dave 运行日志覆盖约 184 秒，比两次默认值闪退点多约 69–70 秒，且只有一个带 `--attach` 的启动监控进程，没有第二个 CrashHandler。Unity 日志继续完成资源、存档、Steamworks 和资源卸载路径。
- 本轮支持“UnityPlayer 开关为必要但不充分条件”的因果判断：WineD3D 解决首帧黑屏，UnityPlayer 开关解决随后稳定发生的启动后期闪退。下一轮 B 保持相同配置，进入游戏后计时 6 分钟完成最终重复性验证。

## 七、Git 关键节点

| 节点 | 推送内容 | 触发条件 | 状态 |
|---|---|---|---|
| G0 | 初始诊断、执行计划、T1 操作 | 本文复核完成 | 已完成（2026-08-05） |
| G1 | 失败/成功日志归档、T1 结论、下一轮计划 | 收到并分析 T1 日志 | 已完成（2026-08-05） |
| G1.1 | T1.1 日志归档、UnityPlayer 变量验证、T1.2 计划 | 收到并分析 T1.1 日志 | 已完成（2026-08-05） |
| G1.2 | T1.2 日志归档、快捷方式引号修复、候选 APK | 定位 `invalid name` 的命令拼装根因 | 已完成（2026-08-05） |
| G1.3 | T1.3 双日志归档、快捷方式回归、Unity 初始化停点和 T1.4 计划 | 收到并分析 T1.3 Winlator/Unity 日志 | 已完成（2026-08-05） |
| G1.4 | T1.4 双日志归档、`-force-gfx-direct` 变量验证、T1.5 计划 | 收到并分析 T1.4 Winlator/Unity 日志 | 已完成（2026-08-05） |
| G1.5 | T1.5 双日志归档、Stability 变量验证、T1.6 WineD3D 计划 | 收到并分析 T1.5 Winlator/Unity 日志 | 已完成（2026-08-05） |
| G1.6 | T1.6 双日志归档、WineD3D 成功证据、两层根因和最小回退计划 | T1.6 成功进入游戏并取得完整日志 | 已完成（2026-08-05） |
| G1.7 | T1.7 双日志归档、移除 `-force-gfx-direct` 成功、T1.8 计划 | T1.7 成功进入游戏且参数未出现 | 已完成（2026-08-05） |
| G1.8 | T1.8 双日志归档、Intermediate 成功、T1.9 默认 Unity 开关计划 | T1.8 成功且 Intermediate 参数生效 | 已完成（2026-08-06） |
| G1.9 | T1.9 双日志归档、默认 UnityPlayer 开关闪退证据、T1.10 双冷启动计划 | T1.9 闪退且单变量生效 | 已完成（2026-08-06） |
| G1.10A | 未生效覆盖的双日志归档、默认值闪退重复证据、A2 保存步骤 | T1.10A 日志仍显示 `BOX64_UNITYPLAYER=0` | 已完成（2026-08-06） |
| G1.10A2 | 回切成功双日志、114–115 秒闪退点对照、B 轮六分钟计划 | A2 日志显示 UnityPlayer 1 且越过重复闪退点 | 已完成（2026-08-06） |
| G1.10 | 最终配置第二次冷启动日志与最小成功集确认 | T1.10B 进入可交互游戏后稳定运行至少 6 分钟 | 待执行 |
| G2 | 内置 Wine/rootfs 最小修复、构建验证、APK 哈希 | T1.10B 六分钟冷启动完成 | 待执行 |
| G3 | 两次冷启动回归、最终报告、性能阶段入口 | 内置修复真机通过 | 待执行 |

所有节点推送到 `origin/main`（`https://github.com/SilenceWanna/winlator_wzh.git`）。提交前先检查工作树和远程差异，不覆盖用户改动；游戏本体、临时解包目录和超大 APK 不进入 Git。
