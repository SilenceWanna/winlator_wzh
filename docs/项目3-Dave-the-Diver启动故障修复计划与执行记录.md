# 项目3：Dave the Diver 启动故障修复计划与执行记录

> 建立日期：2026-08-05  
> 当前状态：Dave 启动、键盘输入与 G3-P2 鼠标回归均已闭环；长按无振动及性能 CSV 采样不足作为后续工具问题保留，不阻塞项目3任务1；Palworld 当前无法测试；用户已选择方案2，将原爆点和师父作为精确包体指纹限定样本继续测试，当前进入 T1-MGSVGZ-A
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
| 内置 Wine 状态 | G2 已把项目2验证的 `nsiproxy` 空值保护集成进 APK 资产；G2.2 强制递增 rootfs 版本以刷新手机缓存；G2.3 在容器入口增加 rootfs 完整性门槛 | 新 rootfs SHA-256 为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`；等待 G2.3 刷新后的 T2 真机确认 |
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
| P0（第一层已确认） | 原内置 Wine 11 的 `nsiproxy` 缺少 `if_nameindex()` 空值保护，阻塞网络初始化 | T0 停在 `dnsapi`；T1 只换补丁 Wine 后越过该点并继续加载 `GameAssembly.dll`、Vulkan 和音频 | 已由 T1 确认并在 G2 集成；等待 T2 验证内置路径 |
| P1（当前样本已确认） | Box64 Unity 专用策略被关闭会使当前样本在启动后期不稳定 | T1.1 证明该开关不能单独解决 DXVK 黑屏；值为 0 的两轮均在 114–115 秒进入崩溃处理路径，值为 1 的 A2/B 两轮均越过该点 | 最终配置保留 `BOX64_UNITYPLAYER=1`；正式发行版仍需独立验证 |
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
| T1.10B 六分钟冷启动成功 | `archive/runtime-logs/dave-the-diver/T1.10B-final-cold-start-success.txt` | 261,900 | `BE3EC59B04295BECAA54CFA5B1754585E616F7665CB72ED0E42D584E5C04BD22` |
| T1.10B Unity 成功日志 | `archive/runtime-logs/dave-the-diver/T1.10B-Player-final-success.log` | 8,975 | `63DCB211A8DA0F43A731261E0176698BD1F088C9BC8F6852233837D865883BD3` |

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

### G1.10：第二次冷启动与六分钟稳定性验收（已完成）

- [x] T1.10B Winlator 日志共 2,921 行、261,900 bytes，Unity 日志共 185 行、8,975 bytes。
- [x] Dave 实际 argv 为 `-logFile Player-T1.10-B.log`；`BOX64_UNITYPLAYER=1`、`Detected UnityPlayer.dll`、`BOX64_UNITY=1`、WineD3D GLSL/OpenGL 和 Intermediate 均按最终配置生效。
- [x] Dave 从 `11:32:47` 持续记录到 `11:39:40`，观测窗口约 413 秒（6 分 53 秒）；全程只有启动时带 `--attach` 的 CrashHandler，没有第二个 CrashHandler、`c0000005`、SIGSEGV 或 Unhandled page fault。
- [x] Unity 再次完成 D3D11 11.1、版本、资源、存档、CursorManager、Steamworks 回调和后续资源卸载，与 A2 成功路径一致。
- [x] 本轮日志结束时没有 X session 关闭记录，因此只证明六分钟持续运行而不单独证明退出方式；两次独立冷启动和稳定性验收已经满足，进入 G2 内置 Wine 集成。

### G2：把最小修复集成到内置 Wine

T1 已证明补丁解决第一层阻塞，T1.6 已证明 WineD3D 解决第二层黑屏，T1.9/T1.10A/T1.10A2/T1.10B 已完成 Box64 Unity 专用策略的失败重复、成功回切和六分钟复验。G2 已按以下范围完成内置集成：

1. 从已验证补丁产物提取所需 `nsiproxy` 模块，确认版本、架构、权限和哈希。
2. 替换 `app/src/main/assets/rootfs.tzst` 中内置 Wine 11 的对应文件；不改 Wine 版本标识、Box64 或图形组件。
3. 重新打包后进行路径安全、符号链接、执行位和解包逐文件差异校验。
4. 构建调试 APK，执行 `zipalign` 与 APK 签名验证并记录 SHA-256。
5. 提交并推送源码、构建说明与哈希；APK 超过 GitHub 普通对象限制时只记录本地路径和哈希，不直接提交大文件。

### G2.1：内置 rootfs 集成与 APK 构建（静态完成）

- [x] 从已归档的 Wine 修复包 `archive/artifacts/wine-packages/wine-11.0-final-nsiproxy-nullguard-winlator-custom-addon.tar.xz` 提取 `opt/wine/lib/wine/x86_64-unix/nsiproxy.so`；文件大小为 `65,200` bytes，SHA-256 为 `75E61DB13D2EF1929C085F94AB46605C2C4EBAF0BBB6102CA1F88C6D36BE2F93`。
- [x] 原始 `app/src/main/assets/rootfs.tzst` 大小为 `79,228,257` bytes，SHA-256 为 `58A65A477703E443B34A99D660DD62089B0116A0D17999BD6C093A37CF3FDA40`；仅替换 `./opt/wine/lib/wine/x86_64-unix/nsiproxy.so`，替换计数为 1。
- [x] 使用保留 tar 元数据的 zstd 重打包流程生成新 rootfs：成员数仍为 `4,154`，源/目标元数据摘要相同；目标文件保持 `0755`、`root:root`、mtime `0`。新 rootfs 大小为 `79,289,327` bytes，SHA-256 为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`。
- [x] `./gradlew.bat --no-daemon clean :app:assembleDebug` 构建成功。清理构建后的 APK 大小为 `206,814,205` bytes，SHA-256 为 `926E9DEB7E5530DFEE1BCCE6C0A532A102373E4C636AB00C9B6C98AF75E1C15C`。
- [x] `zipalign -c 4` 通过，APK 签名验证通过（V2）；签名证书 SHA-256 为 `B6396F6CD549475DEC0893AC0CAB0E03770F403FB37679BAE02418A492270B07`。
- [x] 从 APK 抽出的 `assets/rootfs.tzst` 大小和 SHA-256 与源码资产完全一致，确认修复模块已进入 APK，而不是只修改了本地源码文件。
- [x] 构建候选 APK 已归档到 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-builtin-nsiproxy-fix.apk`；APK 不进入 Git，便于后续真机安装和哈希核对。

### G2.2：rootfs 版本门槛修复（已完成）

- [x] T2-A 失败日志共 781 行、49,772 bytes，SHA-256 为 `F73377FE1CAC73824AE4FC1A95E497A271418B66DEFDD78C33F8ACA03519BF88`，已归档为 `archive/runtime-logs/dave-the-diver/T2-A-builtin-rfs22-no-refresh.txt`。
- [x] T2-A 实际使用 `/data/user/0/com.winlator/files/rootfs/opt/wine/`，环境为 `BOX64_UNITYPLAYER=1`，参数完整传入 `-logFile Player-T2-A.log`；加载 `nsiproxy.so` 后仍出现 `err:nsi:poll_events bind failed, errno 13`，没有生成 Unity `Player.log`。
- [x] 代码复核确认 `RootFSInstaller.installIfNeeded()` 只在 `rootFS.getVersion() < LATEST_VERSION` 时重新解压资产；G2 首版仍为 `LATEST_VERSION = 22`，因此 APK 更新不会刷新已存在的应用级 `/data/.../files/rootfs`。
- [x] 将 `RootFSInstaller.LATEST_VERSION` 升为 `23`，让安装 G2.2 APK 后自动重装 rootfs；该操作不删除 `opt/installed-wine`，但会重置容器 rootfs 版本标记。
- [x] G2.2 clean APK 重新构建成功，大小为 `206,814,205` bytes，SHA-256 为 `BCCC878EA10F4D04D9BAE47139CFC9335E055421C79D7788BBF9CEF746191B44`；`javap` 确认编译后的 `installIfNeeded()` 使用版本 `23`，`zipalign` 与 V2 签名验证通过。
- [x] 候选 APK 路径保持不变：`D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-builtin-nsiproxy-fix.apk`；安装后必须等待“安装系统文件”完成，再新建容器进行 T2-A 刷新后复验。

### G2.3：阻止未完成 rootfs 进入 `starting up`（已完成）

- [x] 用户反馈安装 G2.2 后无法进入容器，界面停在 `starting up`，且没有 Winlator/Wine/Box64 日志；这说明故障发生在 Wine 进程启动前，不能用游戏日志判断 `nsiproxy` 是否已生效。
- [x] 复查启动顺序确认 `XServerDisplayActivity` 原先直接显示 `PreloaderDialog` 并启动环境，没有检查 rootfs 是否有效或版本是否达到 `LATEST_VERSION`；在系统文件异步安装、旧版本 rootfs 或半解压状态下会产生“假启动”状态。
- [x] 在容器启动入口增加门槛：`RootFS.find(this)` 无效或版本低于 `RootFSInstaller.LATEST_VERSION`（当前为 `23`）时，提示“安装系统文件”并立即结束该 Activity，不创建容器运行环境，也不显示无限期 `starting up`。
- [x] 门槛只负责阻止错误时序，不改变 Wine、Box64、图形驱动或 Dave 参数；rootfs 完整且版本为 `23` 时，后续启动路径保持原样。
- [x] G2.3 clean APK 构建成功，大小为 `206,814,253` bytes，SHA-256 为 `8CB87C3CBE013649F59F909F8E59A3F616A9E6CA74AD6A9927B7EEF409EDE5CC`；`zipalign -c 4` 通过，APK V2 签名验证通过。
- [x] G2.3 APK 内嵌 `assets/rootfs.tzst` 大小为 `79,289,327` bytes，SHA-256 为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`；候选文件已替换为同一路径。

### G2.4：`starting up` 阶段可观测性与启动失败自愈（已完成）

- [x] G2.3 真机仍停在 `starting up`；本地日志目录没有比 T2-A 更新的导出文件，说明现有日志无法覆盖 Wine/Box64 进程创建前的 Java 后台初始化。
- [x] 代码路径重新收敛：显示 `starting up` 后，后台线程还会同步 Wine 系统文件、DX wrapper、图形驱动和音频，最后才创建 XEnvironment 与 guest 进程；任一步骤抛出未捕获异常都会留下永久对话框。
- [x] 发现第二条静默失败路径：Box64 解压结果未校验却立即保存 `current_box64_version`；若首次解压失败，后续不会重试。`ProcessHelper.exec()` 同时吞掉进程创建异常并返回 `-1`，因此不会产生进程日志或结束回调。
- [x] 为每个启动阶段写入独立的 `Documents/Winlator/startup.log`，并同步写入 Winlator 调试日志；捕获后台异常后关闭 `starting up`、返回上一界面并提示启动日志路径。若发生阻塞而非异常，日志最后一条 `BEGIN` 即为未返回的阶段。
- [x] Box64 文件不存在时强制重试解压，仅在文件存在、大小非零且具有执行权限后保存当前版本；guest 进程创建返回 `-1` 时抛出明确错误，由启动阶段日志收口。
- [x] `./gradlew.bat --no-daemon clean :app:assembleDebug` 成功；G2.4 APK 大小为 `206,816,529` bytes，SHA-256 为 `791605F6231AE041E2832298E079840EE60E49758F9B1E8B37FB3919E8B60E34`。`zipalign -c 4` 和 APK V2 签名验证通过，编译字节码包含启动阶段日志与 Box64 校验分支。
- [x] G2.4 APK 内嵌 rootfs 大小仍为 `79,289,327` bytes，SHA-256 仍为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`，未引入新的 Wine/rootfs 变量。
- [x] 新产物独立归档为 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G2.4-startup-diagnostics-box64-retry.apk`；创建前检查目标不存在，未覆盖 G2.3 或其他 APK。

### G2.5：压缩包文件父目录创建修复（已完成，等待真机复验）

- [x] T2-A3 `startup.log` 共 3,306 bytes，SHA-256 为 `32A31039DDE40684DDC2AC9F68CFA6F736E6543BEC25ADADB1CACB2F0D868223`；Winlator 日志共 2,113 bytes，SHA-256 为 `BFAF548272EAE80B72FAD6C65C6180C6B4928DDFB36E7E5DEFEE3E6628FBF50F`。
- [x] 两份日志已分别归档为 `archive/runtime-logs/dave-the-diver/T2-A3-startup-box64-parent-missing.log` 和 `archive/runtime-logs/dave-the-diver/T2-A3-winlator-box64-parent-missing.txt`，后续导出不覆盖本轮证据。
- [x] 日志证明 rootfs 版本为 `23`，`setup_wine_system_files`、图形驱动和音频阶段均完成；失败点为 `setup_x_environment` 中 Box64 解压，目标 `/data/user/0/com.winlator/files/rootfs/usr/local/bin/box64` 不存在。
- [x] 静态检查 `app/src/main/assets/box64/box64-0.4.5-dev-372739d.tzst` 确认压缩包只有 `usr/local/bin/box64` 一个文件成员，没有 `usr/`、`usr/local/`、`usr/local/bin/` 目录条目。
- [x] 修改 `TarCompressorUtils.extract()`：写入普通文件或符号链接前创建父目录，并在父路径无法创建时返回失败；该修复覆盖 Box64、图形驱动、DX wrapper 和其他同格式资产。
- [x] clean 构建完成，增量 `:app:assembleDebug` 再次明确返回 `BUILD SUCCESSFUL`；G2.5 APK 大小为 `206,816,497` bytes，SHA-256 为 `A89417627D46FDEE4A494C517620490617FC83E51A06E765D139E7D85FF29D0A`。
- [x] `zipalign -c 4`、APK V2 签名、编译字节码父目录分支和内嵌 rootfs 均验证通过；rootfs SHA-256 保持 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`。
- [x] G2.5 使用全新文件名 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G2.5-tar-parent-directory-fix.apk` 归档；创建前验证目标不存在，未覆盖任何历史 APK。
- [x] T2-A4 真机确认 Box64 `0.4.5 372739d` 成功解压并启动，`setup_x_environment` 完成且记录 `STARTUP COMPLETE`；内置 Wine、Unity、WineD3D 和游戏资源初始化均继续执行。
- [x] T2-A4 没有 `c0000005`、SIGSEGV、Unhandled page fault、X 连接中断或启动阶段失败。Winlator 日志有从 `10:03:26` 到 `10:07:32` 的运行输出，用户确认游戏成功启动；该轮计为第一次冷启动通过，不替代第二次冷启动和 6 分钟稳定性复验。
- [x] T2-A4 三份日志分别归档为 `T2-A4-startup-g2.5-success.log`、`T2-A4-winlator-g2.5-success.txt` 和 `T2-A4-player-g2.5-success.log`；大小依次为 `1,324`、`290,897`、`6,266` bytes，SHA-256 依次为 `33D2FB424997F87A0065B463FCB86BFB89BA8F4D59E28610C467E64139E203BA`、`15A6F1C18F6EDC76261268989552EF5D9378F10B05EE9AF99108411DE2D168A8`、`A30A858C5F01B70599DE96250B6705DE8026A47BEB113F0D176C5ACDAE4F2114`。

### APK 产物保留规则

- 从 G2.4 起，每个 APK 使用包含节点号和用途的唯一文件名，已存在文件视为只读产物，不执行覆盖。
- G2.3 已补存为 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G2.3-rootfs-startup-guard.apk`，大小 `206,814,253` bytes，SHA-256 为 `8CB87C3CBE013649F59F909F8E59A3F616A9E6CA74AD6A9927B7EEF409EDE5CC`。
- 新节点的文档必须同时记录文件名、大小和 SHA-256；通用文件名 `app-debug-project3-builtin-nsiproxy-fix.apk` 仅作为 G2.3 历史产物保留，不再更新。

### T2：内置修复 APK 真机回归

1. 覆盖安装 G2.5 APK `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G2.5-tar-parent-directory-fix.apk`，确认安装包 SHA-256 为 `A89417627D46FDEE4A494C517620490617FC83E51A06E765D139E7D85FF29D0A`；不要卸载应用。
2. 首次打开 APK 后等待系统文件安装完成；必须看到安装过程结束后再继续。不要在安装过程中启动容器。如果从桌面快捷方式启动时立即返回并提示“安装系统文件”，这是 G2.3 门槛生效，应回到主界面等待安装完成。
3. 创建全新容器并选择内置 Wine `/opt/wine`；不要复用已经导入补丁 Wine 的旧容器，避免旧模块残留污染结论。
4. Dave 快捷方式保持 `WineD3D`、Box64 `Intermediate`、`BOX64_UNITYPLAYER=1`，不添加 `-force-gfx-direct`，沿用当前已验证的 Turnip、分辨率和游戏路径。
5. 刷新后第一轮冷启动使用 `-logFile Player-T2-A4.log`，进入可交互主菜单后继续观察至少 6 分钟；导出 Winlator 日志、`Player-T2-A4.log` 和 `Documents/Winlator/startup.log`。
6. 若再次停在 `starting up`，最多等待 2 分钟后结束 Winlator，直接从手机 `Documents/Winlator/` 复制 `startup.log`；该文件启动时即创建，不依赖 Wine/Box64 成功启动或 Winlator 日志导出。
7. 成功日志应出现 `if_nameindex failed, errno 13` 后继续加载 Dave/图形模块，而不是停在旧的 rootfs 早期路径；通过后再进行 T2-B。

### T2-B：G2.5 第二次冷启动与稳定性确认（已完成）

1. 保持 G2.5 APK、container 6 和 T2-A4 全部配置不变，完全退出 Winlator 后重新冷启动。
2. 只把 Unity 日志文件名改为 `-logFile Player-T2-B.log`；不得修改 WineD3D、Turnip/Zink、Box64 `Intermediate` 或 `BOX64_UNITYPLAYER=1`。
3. 进入可交互主菜单后连续运行至少 6 分钟，期间不要切换配置或主动结束进程。
4. 结束后导出 `startup.log`、Winlator `logs.txt` 和 `Player-T2-B.log`；T2-B 通过后完成内置修复的启动闭环并进入 G3 性能基线。

T2-B 结果：`startup.log` 再次记录 `STARTUP COMPLETE`；Winlator 日志从 `10:17:52` 持续输出至 `10:24:43`，共 6 分 51 秒；Unity 日志进入 `Tutorial_Mission01`。三份日志均无崩溃特征，T2-B 通过。

### T3：兼容性分支与最小成功集回退

按以下顺序每轮只改一个变量：

1. T1.4 只增加 Winlator/Unity 已提供的 `-force-gfx-direct`，验证多线程渲染路径；该变量已验证无行为改善。
2. T1.5 保留 `-force-gfx-direct`，只把 Box64 预设改为 Stability；该变量已验证无行为改善。
3. T1.6 保持其他设置，只把 DX wrapper 从 DXVK 改为 WineD3D；该变量已成功使游戏进入教程任务。
4. T1.7 保持 WineD3D 和 Stability，只移除 `-force-gfx-direct`；该变量已验证不是成功必需项。
5. T1.8 保持 WineD3D 和无额外图形参数，只把 Box64 从 Stability 恢复为 Intermediate；已验证成功。
6. T1.9 删除用户设置的 `BOX64_UNITYPLAYER=1`，验证 Winlator 默认 `BOX64_UNITYPLAYER=0`；该轮在约 114 秒后闪退，强烈表明默认值不适用于当前样本，仍需回切复验排除偶发崩溃。
7. T1.10A2 正确保存 `BOX64_UNITYPLAYER=1` 后完成有效冷启动并越过重复闪退点；T1.10B 保持配置不变并持续记录 6 分 53 秒，最终最小成功集确认完成。

判断边界：若干净副本能启动而当前副本不能，Winlator 侧不实现针对第三方 DRM/注入组件的绕过；记录兼容边界后转为验证正式游戏发行版本。

### G3：启动闭环与项目3性能阶段

- [x] 连续两次冷启动通过，完成日志与 APK 归档。
- [x] 输出启动故障根因、修复、验证矩阵、残余风险和复现步骤。
- [ ] 建立固定场景性能基线和单变量优化矩阵。
- [ ] 完成项目3报告并提交、推送最终节点。

### G3-P0：性能基线采样计划（工具已完成，等待真机采样）

1. 保持 T2-B 成功配置作为性能基线：`1280x720`、Turnip/Zink、WineD3D、Box64 `Intermediate`、`BOX64_UNITYPLAYER=1`、container 6。
2. 修改前的 `FrameRating` 只每约 500 ms 计算并显示瞬时 FPS，不保留样本，不能可靠计算平均 FPS、1% Low 和帧时间分布；因此不以目测 HUD 数字作为项目3基线。
3. [x] 采样入口确定为容器 HUD `Full` 模式：它本来就显示 FPS、RAM 和 CPU，开启采样不会改变游戏运行参数；`Disabled/Simple` 模式不创建采样文件。
4. [x] `FrameRating` 已增加 CSV 采样：记录相对单调时间、近似帧时间、窗口 FPS、RAM 已用字节数和 CPU 最大频率；文件名含毫秒时间戳且存在时递增后缀，不覆盖既有结果。
5. [x] Activity 正常销毁时写入摘要行，包含平均 FPS、1% Low FPS、平均帧时间和 P99 帧时间；采样过程约每 500 ms 刷新，进程被系统直接杀死时已落盘的原始样本仍可用。
6. 首个固定场景使用已验证可达的 `Tutorial_Mission01`，预热 60 秒后采样 180 秒；基线完成后再按单变量顺序比较 WineD3D 设置、Box64 预设和分辨率。
7. 性能采样工具未改变 Wine、Box64、图形驱动或游戏参数。最终 APK 为 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G3-P0-frame-sampling-r1.apk`，大小 `208,688,282` bytes，SHA-256 `37BF9EA5759A1B9271D81764462A31F2018A176A87625E2D60610926B6DE1F95`。首次候选包 `app-debug-project3-G3-P0-frame-sampling.apk` 仍保留，未被 r1 覆盖。

### G3-P0-A：首次真机输入与采样诊断（已完成）

1. 用户确认游戏可以实际游玩，但触屏控制中绑定 `W/S/A/D` 后点击按钮无反应。本地游戏 `globalgamemanagers` 明确包含 `Horizontal: A/D` 和 `Vertical: S/W`，排除 Dave 不支持键盘方向键位。
2. Winlator `BUTTON` 元素被按下时会遍历该元素的所有绑定，并对每个绑定同时调用 `injectKeyPress`；松开时再同时释放。因此，若 `W/S/A/D` 被绑到同一个按钮，游戏会同时收到上下左右，水平与垂直输入都抵消；这是当前最高概率原因，不是 Box64 或 WineD3D 故障。即使是单键按钮，键也只在手指按住期间保持；过快点按可能在 Unity 两次输入轮询之间完成，因此方向对照必须持续按住。
3. Winlator 的 `D_PAD`/`STICK` 默认四向顺序是上、右、下、左，对应 `W/D/S/A`，它们会根据触摸方向只激活当前方向，不应按 `W/S/A/D` 的文字顺序填入四向槽位。
4. 本轮导出的 `frame-rating-20260807-151057-952.csv` 只有 97 bytes，SHA-256 `1CFF822F37E13924B131458E32EA5246A8761D85FDF9118C96027EA113AA3F6A`；文件只含版本行和表头，无帧样本、`# window_reset` 和 `# summary`，不能作为性能基线。空 CSV 是采样窗口识别/更新回调的独立问题，不足以证明键盘焦点丢失。
5. 下一步先用一个 `D_PAD` 执行最小输入对照；若 D-pad 仍无效，再用 Winlator 屏幕键盘单独按 `W` 区分“控制配置问题”与“X11 窗口焦点/键盘注入问题”。未完成该对照前不修改 Wine、Box64 或图形设置。

### G3-P0-B：跨游戏键盘注入诊断（已完成）

1. [x] Dave 的 D-pad 和 Winlator 屏幕键盘均无效；用户另用《星露谷物语》对照后仍无效，排除单个游戏键位、Dave 场景状态和单个触屏配置。
2. [x] 两条输入路径的公共链路确认为 `XServer.injectKeyPress()` → `Keyboard.setKeyPress()` → `InputDeviceManager.onKeyPress()` → X11 `KeyPress`。后三个输入核心类从仓库初始 Winlator 11.1 导入后未改动，不是 G2/G3 回归。
3. [x] 当前代码会在“焦点窗口为空”、“焦点窗口未订阅 `KEY_PRESS/KEY_RELEASE`”或“目标窗口被禁用”时静默返回；现有 Winlator/Player 日志不记录这些状态，无法在不增加观测的情况下区分根因。
4. [x] 已增加会话级唯一 `input-events-*.log`，记录窗口 map/focus、虚拟绑定 press/release、XServer 注入、键盘去重、X11 事件目标和丢弃原因；每行立即刷新，即使异常退出也能保留已写入证据。
5. [x] 本轮未改变焦点选择和事件分发行为。诊断 APK 已以 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G3-P0-B-input-trace.apk` 唯一文件名归档，大小 `206,821,237` bytes，SHA-256 `0A916DECCB023316E192476E118F1732F48F9D8E3BD2C925C2830205CCABF1F4`。

### G3-P0-C：WineD3D 窗口前台化对照（已完成，假设否定）

1. G3-P0-B 大日志记录星露谷主窗口 `id=29360133`、`class=stardew valley.exe`：`mapped/viewable/enabled/renderable/application/keyPress/keyRelease` 均为 `true`，X11 焦点从映射后一直指向该窗口。
2. 日志共记录 39 次 `key_press_sent` 和 39 次 `key_release_sent`，`no_focus`、`focus_not_listening`、`target_disabled` 计数均为 0。D-pad `W` 在 `145734–145904 ms` 持续约 170 ms，屏幕键盘也产生了 `keycode=25, keysym=119` 并发送到星露谷窗口。
3. 该窗口唯一异常属性是 `surface=false`。当前 `DesktopHelper.setFocusedWindow()` 只在 `window.isSurface()` 为真时调用 `WinHandler.bringToFront()`；WineD3D 应用窗口虽已取得 X11 焦点，却可能没有同步为 Wine 内部 Win32 前台窗口。
4. 侧栏“活动窗口”列表点击条目会不经 `isSurface()` 条件，直接调用同一 `bringToFront(className, handle)`；因此它是对“Win32 前台未同步”假设的无代码单变量验证。
5. 用户按要求从“活动窗口”手动选中 `Stardew Valley` 后，D-pad `W` 仍然无效。该操作已经无条件调用 `WinHandler.bringToFront(className, handle)`，因此否定“X11 已聚焦但 Wine 内部 Win32 前台窗口未同步”假设；不修改 `DesktopHelper` 的 `isSurface()` 条件。

### G3-P0-D：WinHandler 键盘直通对照（已完成，直通仍无效）

1. [x] 仓库已有 `WinHandler.keyboardEvent(byte vkey, int flags)` 和 `KEYBOARD_EVENT=11` 协议，但此前没有调用方；鼠标与游戏手柄已通过同一 WinHandler 通道发送到 Wine，键盘却只走 X11 core event。
2. [x] 已从 `rootfs_patches.tzst` 单独提取来宾端 `winhandler.exe`（SHA-256 `E378CF2534620F67B8665B1E4EF91E8AE8422A0D5F5F5A6C22F879E556EF3D39`）并反汇编。它读取 UDP `buffer[1]` 作为 `bVk`、`buffer+2` 作为 32 位 `dwFlags`，调用 `USER32.keybd_event(bVk, 0, dwFlags, 0)`；协议含义确认是标准 Windows 虚拟键，按下 flags 为 `0`，释放为 `KEYEVENTF_KEYUP=0x0002`。
3. [x] 独立 G3-P0-D 诊断实现保留原 X11 路径，只把键盘去重后真正接受的 `W` press/release 额外送到 WinHandler，分别使用 `VK_W=0x57` 和 flags `0/2`；其他按键不走直通，避免尚未验证时扩大双输入影响。
4. [x] 输入日志新增 `winhandler_init_received`、`winhandler_keyboard_queued`、`winhandler_keyboard_packet sent=<true|false>`，以及 `not_initialized/no_handler` 丢弃原因，可区分来宾握手、Java 排队和 UDP 发送状态。
5. [x] 新 APK 已以唯一文件名 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G3-P0-D-vk-w-dual-route.apk` 归档，大小 `206,821,829` bytes，SHA-256 `37D5A4EDFF758A377EADE8E08CB9633015A31FAC247A39687BB8F3A8F0F5C2B2`；未覆盖历史 APK。
6. [x] Java 编译与 clean 构建完成，随后增量 `:app:assembleDebug` 明确返回 `BUILD SUCCESSFUL`；zipalign 通过，V2 签名有效且 signer=1。APK 内 `rootfs.tzst` 仍为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`，`rootfs_patches.tzst` 仍为 `70C25EDCBAFB71D7D3855FAF39E582ED5C6709F3AFCFB2F21C675D5FEB65E02C`。
7. [x] 星露谷真机 `W` 仍无效。日志在 `2864 ms` 收到 WinHandler 握手，共记录 27 组去重后的 `W` press/release、54 次 `winhandler_keyboard_queued` 和 54 次 `winhandler_keyboard_packet sent=true`，丢弃计数为 0；同一批 27 组 X11 `KeyPress/KeyRelease` 也全部发送。根因已越过 Android 输入、X11 core event 发送和 WinHandler UDP 发送，继续扩展虚拟键映射没有依据。

### G3-P0-E：X11 焦点通知修复（已完成，单独修复仍无效）

1. [x] 本项目 `WindowManager.setFocus()` 仅修改 Java 侧 `focusedWindow`，`revertFocus()` 也只替换字段；事件目录没有 X11 事件码 9/10 的 `FocusIn/FocusOut` 实现。现有日志中的 X11 “焦点正常”只能证明服务端内部目标正确，不能证明 Wine 收到焦点切换。
2. [x] Wine 10.10 `winex11.drv/event.c` 明确注册 `FocusIn`/`FocusOut` 处理器；`X11DRV_FocusIn()` 在正常模式下调用 `NtUserSetForegroundWindow(hwnd)`。因此缺少 X11 焦点通知可以同时解释：core `KeyPress` 已送达 Wine 的 X11 窗口，但 Wine 内部前台/键盘状态未切换；`winhandler.exe` 的 `keybd_event` 也投递不到游戏。
3. [x] 已按 X11 wire protocol 实现固定 32-byte `FocusIn` 和 `FocusOut`：事件码 `9/10`、detail、sequence、event window、mode 和 23-byte padding；本轮只使用 `NotifyNormal`，不引入 grab 行为。
4. [x] `WindowManager.setFocus()` 现在只在焦点真正变化时按顺序发送旧窗口 `FocusOut` 和新窗口 `FocusIn`，detail 会区分 ancestor/inferior/nonlinear/none；`revertFocus()` 复用同一路径，窗口关闭或隐藏时不再只改内部字段。
5. [x] 输入日志增加每个窗口的 `focusChange` 订阅状态，并记录 `focus_notify` 的旧/新窗口、out/in detail 以及两端是否订阅，真机可直接判断 Wine 是否接收该类事件。
6. [x] Java 编译、clean 构建和增量 `:app:assembleDebug` 已完成，增量构建明确返回 `BUILD SUCCESSFUL`。编译字节码及最终 `classes.dex` 均包含事件码 `9/10`、23-byte padding、`FocusIn/FocusOut` 和 `focus_notify` 标记。
7. [x] 唯一 APK 已归档为 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G3-P0-E-x11-focus-notify.apk`，大小 `206,823,469` bytes，SHA-256 `6180E52628596C9BB407ECE44DB1E7AB0F0BAD246EB2E95DB574791AEE4F44E2`。zipalign 通过，V2 签名有效且 signer=1；APK 内 rootfs 与 rootfs patch 哈希均未变化。
8. [x] 星露谷真机 `W` 仍无效。游戏窗口明确为 `focusChange=true`，从 explorer 切换到游戏时生成 `FocusOut(INFERIOR)` / `FocusIn(ANCESTOR)` 且 `outSelected/inSelected` 均为 true；两组 `W` 的 X11 press/release 和 4 个 WinHandler 包也全部发送。该结果证明事件生成与订阅成立，但不能证明 Wine 处理器实际消费了事件。

### G3-P0-F：`A` 键 Wine 消费层跟踪（已完成，双通道仍无效）

1. [x] 按用户要求，从本节点起方向输入对照统一改为 `A`，不再要求测试 `W`；Android/X11 keycode 为 `38`，Windows vkey 为 `VK_A=0x41`。
2. [x] Wine 10.10 `X11DRV_KeyEvent()` 会依次执行 X keycode → keysym/vkey/scan code 转换、`update_lock_state()` 和 `X11DRV_send_keyboard_input()`。现有 `input-events` 只能证明事件写入 Wine 的 X11 socket，无法证明上述转换是否成功。
3. [x] 已把 G3-P0-D 的单键 WinHandler 直通从 `KEY_W/VK_W` 改为 `KEY_A/VK_A`，仍在键盘去重后发送；其他按键没有增加直通。
4. [x] 已在容器和快捷方式环境变量合并后强制 `WINEDEBUG=-all,+event,+key,+keyboard,+input,+rawinput`，并在输入日志写入 `wine_debug_override`，无需再手工录入容易触发 `invalid name` 的执行参数。
5. [x] 唯一 APK 已归档为 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G3-P0-F-vk-a-wine-input-trace.apk`，大小 `206,823,569` bytes，SHA-256 `E6040A50185919301262911D27D3425A415E10DE7D1DC87AB649DDA1F2C031DA`。clean/增量构建成功，zipalign、V2 签名、dex 标记和 rootfs 哈希均通过验证。
6. [x] 星露谷真机 `A` 仍无效；两组 X11 press/release 和四个 `VK_A` WinHandler 包均成功发送且无丢弃。但日志没有任何 `wine_trace`，代码复核确认原因是 Wine 调试偏好关闭时 `ProcessHelper` 把 stdout/stderr 重定向到 `/dev/null`，故本轮不能据此判断 `X11DRV_KeyEvent` 是否执行。

### G3-P0-G：自动捕获 Wine 输入跟踪（已完成，定位错误扫描码）

1. [x] G3-P0-F 输入日志证明 `KEY_A=38` 与 `VK_A=65` 各完成两组 press/release；焦点窗口仍为星露谷，X11、WinHandler 均无发送或初始化丢弃。
2. [x] 确认 G3-P0-F 的诊断缺口：仅覆盖 `WINEDEBUG` 不会让 `ProcessHelper.debugCallbacks` 变为非空；当设置页未启用 Wine/Box64 日志时，guest stdout/stderr 会被合并后写入 `/dev/null`。
3. [x] 已在 guest 启动前注册专用输出回调，把 Wine `key/keyboard/input/rawinput` 通道行自动写入同一份 `input-events`，不依赖设置页开关、日志菜单或手工参数。
4. [x] 已去掉噪声较大的 `event` 通道，限制自动记录通道和单行长度；日志增加 `wine_trace_capture` 与 `wine_trace` 标记，可直接核对 `X11DRV_KeyEvent`、keycode 38、`VK_A=0x41`、scan `0x1e` 及后续输入路径。
5. [x] 新 APK 已归档为 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G3-P0-G-auto-wine-input-trace-a.apk`，大小 `206,823,485` bytes，SHA-256 `E1F327D14C1EE33B1B91FF6CF72F0892C770FFBFD9F7A88679345C95FC542883`。Java 编译、clean/增量构建成功，zipalign、V2 签名、dex 标记和 rootfs 哈希均通过验证。
6. [x] 自动日志完整出现 `X11DRV_KeyEvent` 与 `X11DRV_send_keyboard_input`。两组 A 均转换为 `VK_A=0x41`，但 scan 为错误的 `0x78`；初始化日志明确显示 `assigning scancode 78 to unidentified keycode 38 (NoSymbol)`，根因转入 XServer 键盘映射响应。

### G3-P0-H：修复 X11 `GetKeyboardMapping` 响应（已完成，单独修复仍不足）

1. [x] Wine 10.10 的 US 键盘表规定 A 的扫描码为 `0x1e`；G3-P0-G 实际得到 `0x78`，可解释依赖 scan code/DirectInput 的游戏不响应，而 `VK_A` 仍看似正确。
2. [x] `KeyboardRequests.getKeyboardMapping()` 声明 `keysyms-per-keycode=2`，却只返回 `count` 个 keysym、reply length 也只写 `count`，并且非最小 keycode 请求的数组起点没有乘 2；`Keyboard.keysyms` 同样只按 keycode 数量分配，协议数据被截断。
3. [x] backing array 已扩为 `(MAX_KEYCODE-MIN_KEYCODE+1) * KEYSYMS_PER_KEYCODE`；回复长度、起始索引和写入数量全部按 `KEYSYMS_PER_KEYCODE` 计算，保持现有 X keycode 编号和键位表不变，并校验非法请求范围。
4. [x] 已保留 G3-P0-G 自动 Wine 跟踪与 `A` 双通道；新日志应直接验收 `keycode 38 converted to vkey 0x41 scan 1e`，不再把 `0x78` 当作可接受结果。
5. [x] 新 APK 已归档为 `D:\agent\Winlator\artifacts\apks\project3\app-debug-project3-G3-P0-H-x11-keyboard-mapping-a.apk`，大小 `208,697,326` bytes，SHA-256 `43FC44152EFF0AF9347645AABD1B6BB55A44A0A7258BDD8F14D57CFBA4816A6E`。Java 编译、增量 `assembleDebug`、zipalign、V2 签名、dex 标记和 rootfs 哈希均通过验证。
6. [x] 真机日志中 9 次 `keyboard_mapping_reply` 均为 `first=8,count=248,keysyms=496`，确认核心协议修复已经执行；两组 A 的 X11 与 WinHandler press/release 也均无丢弃。
7. [x] Wine 仍把 4 个 A 事件转换为 `scan=0078`，初始化日志继续出现 `assigning scancode 78 to unidentified keycode 38 (NoSymbol)`。代码复核确认 Wine 的扫描码初始化使用 XKB API，而当前 Java XServer 没有注册 `XKEYBOARD` 扩展，因此核心映射正确仍不足以提供扫描码。

### G3-P0-I：Box64 客体侧 XKB 核心映射兼容层（已完成）

1. [x] 新增可复现构建的 x86_64 ELF 兼容库，只接管 Wine 初始化依赖的 `XkbKeycodeToKeysym` 与 `XkbTranslateKeySym`；按当前 XServer 的核心两级键盘映射返回 keysym，不改 Android 触控、X11 事件投递或 WinHandler 协议。
2. [x] 将兼容库作为独立 APK asset，在每次 X 环境启动前复制到当前 rootfs，并通过 `BOX64_LD_PRELOAD` 仅注入 x86_64 客体进程；保留已有 preload 值，避免污染 Android ARM64 宿主进程。
3. [x] 增加安装路径、文件大小和 preload 环境标记；继续保留 A 键 X11/WinHandler 双通道和自动 Wine trace，以便一次真机日志同时确认补丁执行与最终扫描码。
4. [x] 完成 ELF 架构/导出符号验证、函数级测试、Java 编译、APK 构建、zipalign、V2 签名、dex 标记及 rootfs 哈希验证；生成新的唯一 APK，绝不覆盖 G3-P0-H 或更早构建。
5. [x] 星露谷真机日志中 A 共 16 个 press/release 事件全部为 `vkey 0x41 scan 1e`，错误 `scan 0x78` 为 0；用户确认输入有效。Dave 对照中 D/S 为标准 `scan 20/1f`，游戏随后持续读取 Raw Input，用户同样确认实际操作有效。

### G3-P1：WineD3D 性能采样修复与低扰动基线 APK（进行中）

1. [x] 复核首次空 CSV 与两份成功输入日志：WineD3D 的星露谷、Dave 应用窗口和子窗口全部 `surface=false`；旧采样入口只接受 surface 窗口，因此从未设置 `frameRatingWindowId`，不是游戏没有渲染帧。
2. [x] 扩展性能窗口选择：继续优先真正的 surface 窗口/子窗口；不存在 surface 时，选择尺寸达到屏幕门槛且 `isApplicationWindow()` 的可渲染 WineD3D 窗口。帧计数仍来自该窗口 `Drawable` 的更新回调，GLX 更新会触发同一回调。
3. [x] CSV 升级为 version 2，增加选中窗口的 id、名称、class、surface 状态、warmup/measure 阶段和相对时间；窗口切换/取消映射时先写分段摘要，不混合旧窗口数据。
4. [x] 删除 G3-P0-D/F/G 临时诊断行为：取消 A 键 WinHandler 双发、强制 `WINEDEBUG` 通道和自动 Wine trace 回调；保留通用 X11 核心映射、焦点通知及 XKB 兼容层，降低基线测试的日志与输入开销。
5. [x] 增加 60 秒预热、180 秒固定测量、手动长按重置和 `measurement_complete` 摘要；Java 编译、APK 构建、zipalign、V2 签名、dex 标记和 rootfs 哈希检查均通过，使用新的唯一 APK 文件名，未覆盖 G3-P0-I 或历史性能 APK。
6. [ ] 基线 A 固定为 Dave `1280x720`、Turnip/Zink、WineD3D GL、CSMT 开、Strict Shader Math 开、Box64 `Intermediate`、`BOX64_UNITYPLAYER=1`。进入 `Tutorial_Mission01` 后预热 60 秒，再以相同路线游玩 180 秒并正常退出。
7. [ ] 基线有效后按单变量顺序测试：关闭 Strict Shader Math；Box64 `Performance`；降低到 `960x544`。每轮保持同一场景、路线和时长，启动失败、画面错误或 1% Low 明显退化即回退，不同时改多个变量。

### G3-P2：HUD 触摸穿透回归修复（进行中）

1. [x] G3-P1 唯一新增的交互改动是在 `FrameRating` 根视图注册长按监听器；该视图通过 `rootView.addView(frameRating)` 覆盖整个 `FLXServerDisplay`，内部布局也是 `match_parent`。设置根视图 `longClickable=true` 后，全屏触摸在到达下层 `TouchpadView` 前被消费，因此手指拖动不能再驱动鼠标。
2. [x] 将长按监听器和 `longClickable` 仅绑定到实际可见的 `LLFPSPanel`。HUD 外区域恢复向下分发，FPS 小区域仍可长按重置性能采样；不修改 `TouchpadView`、XServer 指针、Wine、XKB 或游戏输入链。
3. [x] Java 编译和完整 `assembleDebug` 均通过；生成新的唯一 APK `app-debug-project3-G3-P2-hud-touch-pass-through-r1.apk`，未覆盖 G3-P1 或更早构建。zipalign 与 V2 签名验证通过。
4. [x] 真机确认 HUD 外鼠标恢复、Dave 触屏按键有效，日志记录 48 次 key press、48 次 key release 和 58 次 pointer focus 事件；长按没有振动，但它不影响鼠标/键盘兼容性。CSV 在容器仍打开时导出，没有 `session_end` 或 `window_restarted`，因此不能据此判定长按回调；提示与立即落盘问题转入后续性能采样工具修复，不阻塞任务1。

## 五、用户当前需要执行的步骤

1. G3-P2 已通过，不再重复 Dave 输入回归。
2. Duckov A-D 对照已完成并停止继续扩大变量；Palworld 当前无法测试。原爆点与师父按方案2进入 `PACKAGE-SCOPED` 测试，静态风险保留但不再阻断。
3. 当前只执行任务1文档中的 T1-MGSVGZ-A。使用独立容器和固定基线，不修改游戏文件；完成后先导出并分析全部日志，再开始师父。

## 六、进度记录

### 2026-08-05：G0 建立计划

- 工作区与代码启动链已完成首轮阅读，当前实际修改目标确认为 `winlator_wzh_new`。
- 初始日志确认 Winlator 已正确创建 Dave 游戏进程，排除快捷方式未执行。
- 失败停点收敛到 `dnsapi` 加载后的早期初始化阶段；当时内置 Wine 未包含项目2已验证的 `nsiproxy` 空值保护。
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

### 2026-08-06：G1.10B 六分钟稳定性验收通过

- T1.10B Winlator 日志共 2,921 行、261,900 bytes，SHA-256 为 `BE3EC59B04295BECAA54CFA5B1754585E616F7665CB72ED0E42D584E5C04BD22`；Unity 日志共 185 行、8,975 bytes，SHA-256 为 `63DCB211A8DA0F43A731261E0176698BD1F088C9BC8F6852233837D865883BD3`。
- Dave 从 `11:32:47` 持续到 `11:39:40`，共约 413 秒。UnityPlayer 检测、WineD3D 和 Intermediate 均按最终配置生效，只有启动时的附加型 CrashHandler。
- 日志没有第二个 CrashHandler、`c0000005`、SIGSEGV、Unhandled page fault 或 X session 异常终止；Unity 内部路径与 A2 一致。A2/B 已满足两次独立冷启动和稳定性验收。
- 当前样本的最小成功集确定为：补丁 Wine 11（`nsiproxy` 空值保护）+ WineD3D + Box64 Intermediate + `BOX64_UNITYPLAYER=1`，不需要 `-force-gfx-direct` 或 Stability。
- 下一阶段 G2 只把已验证的 Wine 模块集成进内置 rootfs；WineD3D 与 UnityPlayer 作为快捷方式级兼容配置保留，避免未经更广泛回归就改变全局默认。

### 2026-08-06：G2 内置 rootfs 集成与 APK 构建完成

- 已将已验证的 `nsiproxy.so` 空值保护模块单文件替换进 `app/src/main/assets/rootfs.tzst`，成员数和 tar 元数据摘要保持不变，目标文件执行权限仍为 `0755`。
- 新 rootfs SHA-256 为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`；干净构建命令 `./gradlew.bat --no-daemon clean :app:assembleDebug` 成功。
- APK SHA-256 为 `926E9DEB7E5530DFEE1BCCE6C0A532A102373E4C636AB00C9B6C98AF75E1C15C`，`zipalign` 和 V2 签名验证通过；从 APK 抽出的 rootfs 与源码资产哈希一致。
- 首次增量构建因旧 asset 残留导致 APK 物理大小异常，已废弃该 APK 并改用 clean build；后续只使用上述干净构建产物。
- 候选 APK 已放入本地 `artifacts/apks/project3/`，下一步转入全新内置 Wine 容器的 T2 两轮冷启动回归。

### 2026-08-06：T2-A 暴露 rootfs 缓存未刷新

- T2-A 日志明确使用内置 `/opt/wine`，但仍在加载 `nsiproxy.so` 后打印 `poll_events bind failed, errno 13`，并在启动早期停止；未生成 `Player-T2-A.log`。
- 复查启动代码发现，rootfs 资产不是每次创建容器时重新解压，而是由 `RootFSInstaller.installIfNeeded()` 按 `.winlator/.rfs_version` 与 `LATEST_VERSION` 判断。G2 首版未递增版本号，故不能把这轮结果解释为修复模块已经在手机端生效后仍失败。
- T2-A 日志已归档；该轮作为“版本门槛缺失”的失败证据，不计入 G3 的内置修复回归。

### 2026-08-06：G2.2 递增 rootfs 版本并重建 APK

- `RootFSInstaller.LATEST_VERSION` 从 `22` 升为 `23`，安装 G2.2 APK 后会自动清理并重新解压 `assets/rootfs.tzst`；`opt/installed-wine` 按现有逻辑保留。
- clean 构建和静态校验通过；G2.2 APK SHA-256 为 `BCCC878EA10F4D04D9BAE47139CFC9335E055421C79D7788BBF9CEF746191B44`，内嵌 rootfs SHA-256 仍为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`。
- 下一步先执行 T2-A2：安装后等待系统文件安装完成，再新建内置 Wine 容器并观察 `if_nameindex failed, errno 13` 后是否继续加载游戏与图形模块。

### 2026-08-06：G2.3 增加 rootfs 启动门槛并重建 APK

- 用户新反馈为“无法进入容器，卡在 `starting up`，没有日志产出”；由于 Wine/Box64 尚未启动，本轮没有新增运行日志可归档。
- `XServerDisplayActivity` 增加 rootfs 有效性和版本检查：系统文件安装未完成时提示安装状态并结束 Activity，避免在异步解压或旧 rootfs 上创建假启动会话。
- G2.3 clean APK SHA-256 为 `8CB87C3CBE013649F59F909F8E59A3F616A9E6CA74AD6A9927B7EEF409EDE5CC`，大小 `206,814,253` bytes；内嵌 rootfs SHA-256 为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`。
- 下一步改用 G2.3 APK，先从 Winlator 主界面等待系统文件安装结束，再启动新建内置 Wine 容器；若入口立即提示“安装系统文件”，记录为门槛拦截而不是游戏启动失败。

### 2026-08-06：G2.4 收口 guest 启动前静默失败

- G2.3 真机依然停在 `starting up`，而本地没有新导出的 Winlator/Unity 日志；因此 G2.3 的版本门槛已通过，阻塞范围后移到 Java 后台初始化至 guest 进程创建之间。
- 新增独立 `Documents/Winlator/startup.log`，记录 rootfs/容器/驱动信息，以及 Wine 系统文件同步、DX wrapper、图形驱动、音频与 XEnvironment 的 BEGIN/END；异常时记录完整 Java 堆栈、关闭对话框并返回。
- 修复 Box64 首次解压失败仍写入版本缓存的问题：缺失或不可执行时每次重试，验证成功后才保存 `current_box64_version`；进程创建返回 `-1` 不再静默。
- G2.3 已补存为唯一文件名且哈希保持不变；G2.4 使用新的唯一文件名归档。后续 APK 一律只新增、不覆盖。
- G2.4 clean 构建及静态验证通过，APK SHA-256 为 `791605F6231AE041E2832298E079840EE60E49758F9B1E8B37FB3919E8B60E34`。下一步执行 T2-A3；无论成功、异常或阻塞，都应取得 `startup.log`。

### 2026-08-06：T2-A3 确诊 Box64 父目录缺失并完成 G2.5

- `startup.log` 将旧的永久 `starting up` 收敛为明确异常：所有 Wine/图形/音频准备步骤均在 4 秒内结束，随后 Box64 解压后目标文件仍不存在；Winlator 日志与独立启动日志结论一致。
- Box64 内置包本身存在且文件名与默认版本完全匹配，但 tar 只包含 `usr/local/bin/box64` 文件成员。通用解压器直接创建文件输出流，没有为缺失的 `usr/local/bin` 创建父目录，因而返回失败。
- `TarCompressorUtils.extract()` 现已在处理目录、普通文件和符号链接前统一确保父目录存在；Box64 的版本缓存仍由 G2.4 校验保护，G2.5 首次启动会自动重试失败的解压。
- 两份 T2-A3 日志已使用唯一文件名归档。G2.5 clean 构建、APK 对齐、V2 签名、字节码和内嵌 rootfs 校验通过，新 APK SHA-256 为 `A89417627D46FDEE4A494C517620490617FC83E51A06E765D139E7D85FF29D0A`。
- 下一步执行 T2-A4，预期 `startup.log` 越过 `setup_x_environment` 并出现 `STARTUP COMPLETE`，Winlator 日志开始产生 Box64/Wine 输出。

### 2026-08-07：T2-A4 首次内置修复冷启动成功

- 启动阶段从 `10:03:26` 开始并在同一秒完成，`startup.log` 明确记录 `END setup_x_environment` 和 `STARTUP COMPLETE`；G2.5 的父目录创建修复已在真机生效。
- Winlator 日志确认实际运行 Box64 `0.4.5 372739d`、内置 `/opt/wine`、WineD3D、Turnip/Zink、`BOX64_UNITYPLAYER=1`，执行参数完整传入 `Player-T2-A4.log`。
- 内置 `nsiproxy.so` 加载后出现两次预期的 `if_nameindex failed, errno 13`，但启动没有停止：随后加载 `UnityCrashHandler64.exe`、`GameAssembly.dll`、WineD3D/OpenGL，并进入 Unity D3D11、输入、Addressables、DLC 和网络初始化。
- 三份日志中均未发现 `c0000005`、SIGSEGV、Unhandled page fault 或 X 连接中断；用户确认游戏成功启动。该轮计为第一次冷启动成功。
- 日志已使用 T2-A4 唯一名称归档；下一步执行完全同配置的 T2-B，要求第二次进入可交互主菜单并持续至少 6 分钟。

### 2026-08-07：T2-B 六分钟第二次冷启动通过，启动故障闭环

- T2-B 再次使用 rootfs 版本 `23`、container 6、Turnip/Zink、WineD3D、ALSA、Box64 `0.4.5 372739d`、`BOX64_UNITYPLAYER=1` 和内置 `/opt/wine`；唯一参数变化是日志名 `Player-T2-B.log`。
- `startup.log` 在 `10:17:52` 完成全部阶段并记录 `STARTUP COMPLETE`。Winlator 日志持续至 `10:24:43`，可验证时长为 411 秒（6 分 51 秒）。
- `nsiproxy.so` 后两次 `if_nameindex failed, errno 13` 没有阻断启动；随后继续加载 Unity、GameAssembly 和 WineD3D。Unity 日志进入 `Tutorial_Mission01`，证明已进入游戏内容而不是只显示窗口。
- Winlator/Unity 日志中 `c0000005`、SIGSEGV、Segmentation fault、Unhandled page fault、X connection broken、Box64 extraction failed、NullReferenceException 和 AccessViolationException 计数均为 0。
- T2-B 日志已归档为 `T2-B-startup-g2.5-success.log`（1,324 bytes，SHA-256 `F0CBCD7AEDECF0A51846569FFB14BEB8E62D188FE7AA395CF019CC3B27DA4F7F`）、`T2-B-winlator-g2.5-success.txt`（290,988 bytes，SHA-256 `A90F0C6305F6409E0E7D5751D7EC480686E6D582D9C6AD8F57CA1BE389992831`）和 `T2-B-player-g2.5-success.log`（12,444 bytes，SHA-256 `00231601A81E7C9A778D86F230A52024FE17853FFDF60715D14FA0386C02A28A`）。
- 启动故障验收标准已经满足：G2.5 连续两次冷启动成功，第二次稳定超过 6 分钟。后续转入 G3-P0 性能采样，不再追加兼容性变量。

### 2026-08-07：G3-P0 CSV 性能采样工具完成

- 采样入口复用容器 HUD `Full` 模式；`Disabled` 和 `Simple` 不会创建 CSV，因此本轮唯一配置变化是将 HUD 设为 `Full`。
- `FrameRating` 按帧记录 `elapsed_ms`、`frame_time_ms`、`window_fps`、`ram_used_bytes` 和 `cpu_max_mhz`；约每 500 ms 刷新已写入数据，正常退出时写入整个窗口运行期的平均 FPS、1% Low、平均帧时间和 P99 帧时间摘要。本轮正式指标将从退出前最后 180 秒原始样本重算。
- 采样文件写入公共文档目录 `Documents\Winlator\performance\`，使用毫秒时间戳和碰撞后缀确保每次运行不覆盖旧样本。Activity 销毁时主动关闭写入器。
- `:app:compileDebugJavaWithJavac`、clean `:app:assembleDebug` 和最终增量 `:app:assembleDebug` 均成功；最终 r1 APK 已通过 zipalign 和 APK V2 签名校验，签名者数为 1。
- 最终 APK 以唯一文件名 `app-debug-project3-G3-P0-frame-sampling-r1.apk` 归档，大小 `208,688,282` bytes，SHA-256 `37BF9EA5759A1B9271D81764462A31F2018A176A87625E2D60610926B6DE1F95`；内置 `assets/rootfs.tzst` 大小 `79,289,327` bytes，SHA-256 仍为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`。
- 上一个候选 APK `app-debug-project3-G3-P0-frame-sampling.apk` 保留不动；r1 用于真机基线，两个文件都没有覆盖 G2.5 及更早 APK。

### 2026-08-07：G3-P0 首轮真机发现输入配置与空 CSV

- 用户已成功运行游戏并导出首份 CSV，但实际游玩时触屏 `W/S/A/D` 按钮无反应。
- 游戏资源证明 Dave 接受 `A/D` 水平和 `W/S` 垂直键盘输入；Winlator 源码证明普通 `BUTTON` 会同时按下其全部绑定。因此先验证“单按钮绑四键导致相反输入抵消”，不立即修改 Wine/Box64。
- 首份 CSV 仅含 97 bytes 表头，没有任何样本或摘要，本轮性能数据作废。该问题与触屏按键是两条独立诊断线，计划在输入最小对照后修复采样窗口识别。

### 2026-08-07：G3-P0-B 跨游戏输入跟踪 APK 完成

- Dave 的 D-pad/屏幕键盘和星露谷对照均无效，原“单按钮多绑定”假设被否定；根因范围收窄到共用 XServer/X11 键盘事件分发。
- 新增 `InputEventLogger`，每个 XServer 会话在 `Documents\Winlator\input\` 创建唯一日志；跟踪绑定、XServer 注入、键盘去重、窗口 map/focus、X11 目标和静默丢弃原因。本轮未改变焦点与分发行为。
- Java 编译和 clean `:app:assembleDebug` 成功；APK 已通过 zipalign、V2 签名、字节码关键字和内置 rootfs 校验。rootfs 大小仍为 `79,289,327` bytes，SHA-256 仍为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`。
- 新 APK `app-debug-project3-G3-P0-B-input-trace.apk` 大小 `206,821,237` bytes，SHA-256 `0A916DECCB023316E192476E118F1732F48F9D8E3BD2C925C2830205CCABF1F4`；归档前确认目标不存在，未覆盖 G3-P0 r1 或其他历史 APK。

### 2026-08-07：G3-P0-B 真机日志排除 X11 焦点与事件丢弃

- 两份日志已使用唯一名称归档：`G3-P0-B-input-trace-stardew-20260807.log`（230,565 bytes，SHA-256 `B3564D59A9A4E3C1708B8DF7668066B93B5D3280F7F1B8C2758AD141F5D684E9`）和 `G3-P0-B-input-trace-startup-only-20260807.log`（470 bytes，SHA-256 `23297807550FE357FD050F8155F527E632874CB076E4829DD1FA4BE72D58DA91`）。
- 星露谷窗口的 X11 焦点、事件订阅和 enabled 状态均正常；39 次 press 和 39 次 release 均已发送，三类预设丢弃计数均为 0。这否定“X11 焦点丢失”与“虚拟按键未到达 XServer”假设。
- 主窗口为 application window 但 `surface=false`，刚好绕过当前 `DesktopHelper` 中受 `isSurface()` 限制的 `bringToFront()`。下一步使用已有“活动窗口”菜单无条件调用 `bringToFront()`，作为 Win32 前台同步的单变量验证。

### 2026-08-07：G3-P0-C 手动前台化对照失败

- 用户从“活动窗口”手动选中 `Stardew Valley` 后立即测试 D-pad `W`，输入仍无效；该入口无条件调用 `WinHandler.bringToFront(className, handle)`。
- 结合 G3-P0-B 已确认的 X11 焦点、订阅和事件发送，现可排除触屏绑定未触发、X11 事件静默丢弃以及 Win32 前台窗口未同步三类原因。
- 下一步不修改焦点逻辑，转入 G3-P0-D：验证仓库中现成但未被调用的 `WinHandler.keyboardEvent()`，用 Windows 虚拟键直通路径绕过 Wine 的 X11 core keyboard 转换层。

### 2026-08-07：G3-P0-D `VK_W` 双通道诊断 APK 完成

- 静态检查 `rootfs_patches.tzst` 中的 `winhandler.exe`，确认 `KEYBOARD_EVENT=11` 最终调用 `USER32.keybd_event`，Android 端现有数据布局与来宾端读取偏移一致；按下/释放协议不再是推测。
- 在 `Keyboard` 的去重完成点增加仅针对 `KEY_W` 的 WinHandler 分支，继续发送原 X11 core event；在 `WinHandler` 记录初始化、未初始化丢弃、排队及 UDP 发送结果。
- Java 编译及 clean/增量 APK 构建完成，zipalign、V2 签名、编译字节码、rootfs 与 rootfs patch 哈希均通过验证。唯一 APK 为 `app-debug-project3-G3-P0-D-vk-w-dual-route.apk`，大小 `206,821,829` bytes，SHA-256 `37D5A4EDFF758A377EADE8E08CB9633015A31FAC247A39687BB8F3A8F0F5C2B2`。
- 下一步只在星露谷相同场景测试 D-pad `W`，并回收包含 `winhandler_*` 条目的最新输入日志；在结果明确前不扩展到其他虚拟键。

### 2026-08-10：G3-P0-D 真机日志证明双键盘通道均发送但仍无响应

- 日志已唯一归档为 `G3-P0-D-vk-w-dual-route-stardew-20260810.log`，大小 `313,240` bytes，SHA-256 `784E5A0BE28BEB71F80383DF7BE5D7F0CB52DCA5B2DAB442B9AFCBC95243D8D0`。
- WinHandler 在 `2864 ms` 完成握手；27 组去重后的 `W` press/release 对应 54 次排队和 54 次 UDP `sent=true`，没有直通丢弃。X11 同时发送 27 次 `KeyPress` 与 27 次 `KeyRelease`。
- 该证据否定“WinHandler 未初始化”“直通请求未排队/未发送”和“按键没有完成 release”三类原因；不再扩展 `A/S/D` 映射。
- 源码复核发现 XServer 从未实现或发送 X11 `FocusIn/FocusOut`。Wine 10.10 的 X11 驱动依赖 `FocusIn` 更新 Windows 前台窗口，故下一步 G3-P0-E 补齐标准焦点事件并保留 `W` 双通道进行单变量验证。

### 2026-08-10：G3-P0-E X11 焦点通知修复 APK 完成

- 新增标准 32-byte `FocusNotify` 序列化及事件码 9/10 的 `FocusIn/FocusOut`，`WindowManager.setFocus()` 现在在焦点变化时发送旧出新入事件，`revertFocus()` 也走同一路径。
- 日志新增 `focusChange=<true|false>` 与 `focus_notify`，可核对 Wine 窗口的订阅和通知生成；G3-P0-D 的 `VK_W` 双通道保持不变，因此本轮唯一新增行为是 X11 焦点通知。
- Java 编译、clean/增量 APK 构建、zipalign、V2 签名、字节码及最终 dex 检查均通过。APK 内 `rootfs.tzst` 仍为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`，`rootfs_patches.tzst` 仍为 `70C25EDCBAFB71D7D3855FAF39E582ED5C6709F3AFCFB2F21C675D5FEB65E02C`。
- 新 APK `app-debug-project3-G3-P0-E-x11-focus-notify.apk` 大小 `206,823,469` bytes，SHA-256 `6180E52628596C9BB407ECE44DB1E7AB0F0BAD246EB2E95DB574791AEE4F44E2`；归档前确认目标不存在，未覆盖历史 APK。

### 2026-08-10：G3-P0-E 真机仍无效，下一轮改测 `A`

- 日志已唯一归档为 `G3-P0-E-x11-focus-notify-stardew-20260810.log`，大小 `58,576` bytes，SHA-256 `B8E2746031D58C78260EA066E1D54187336DE605CE2C4C2151602D4989005E56`。
- explorer 与星露谷窗口均订阅 `FOCUS_CHANGE`；星露谷映射时生成旧窗口 `FocusOut(INFERIOR)` 和游戏 `FocusIn(ANCESTOR)`，两端 `Selected` 都为 true。两组 `W` X11 press/release 及 4 个 WinHandler UDP 包也全部发送，但游戏仍无响应。
- 结论收窄为“Android/XServer 已生成全部预期事件，但 Wine/game 是否消费未知”。G3-P0-F 将临时启用 Wine 键盘相关 trace，观察 `X11DRV_KeyEvent` 到 Windows input/rawinput 的链路。
- 用户要求后续测试改为 `A`；代码直通、文档步骤、日志检索条件和验收口径从本节点起全部同步改为 `KEY_A/VK_A`。

### 2026-08-10：G3-P0-F `A` 键 Wine 消费层跟踪 APK 完成

- 单键 WinHandler 诊断直通已从 `KEY_W/VK_W` 改为 `KEY_A/VK_A`；X11 core event 原路径保持不变，因此可继续核对两条通道。
- 运行环境最终合并点强制写入 `WINEDEBUG=-all,+event,+key,+keyboard,+input,+rawinput`，输入日志同步写入 `wine_debug_override`，避免再次依赖手工执行参数。
- Java 编译、clean/增量 APK 构建、zipalign、V2 签名和最终 dex 检查均通过。APK 内 `rootfs.tzst` 仍为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`，`rootfs_patches.tzst` 仍为 `70C25EDCBAFB71D7D3855FAF39E582ED5C6709F3AFCFB2F21C675D5FEB65E02C`。
- 新 APK `app-debug-project3-G3-P0-F-vk-a-wine-input-trace.apk` 大小 `206,823,569` bytes，SHA-256 `E6040A50185919301262911D27D3425A415E10DE7D1DC87AB649DDA1F2C031DA`；归档前确认目标不存在，未覆盖历史 APK。
- 下一次真机测试只在星露谷相同场景按 D-pad 左方向 `A`，并同时回收最新 `input-events` 与 Winlator/Wine 日志；不再测试 `W`。

### 2026-08-10：G3-P0-F `A` 键仍无效，Wine 输出未被捕获

- 最新输入日志 `input-events-20260810-134006-812.log` 大小 `72,259` bytes，SHA-256 `9EADDB6E6468F5F50BA602D2B2A76B185F1D403CB86A5F75E3864FA4BF39AF14`。
- 星露谷焦点和订阅状态保持正常；两组 `KEY_A=38` press/release 均发送，WinHandler 对应两组 `VK_A=65` flags `0/2`，四个 UDP 包全部 `sent=true`，drop 计数为 0，但角色仍无响应。
- 日志包含 `wine_debug_override`，却没有任何 `wine_trace`；目录中 `logs.txt` 仍为 8 月 7 日旧文件。源码确认调试偏好关闭时 `ProcessHelper` 将 guest 输出定向到 `/dev/null`，因此 G3-P0-F 没有取得 Wine 消费层证据。
- G3-P0-G 改为在应用内自动注册专用输出回调并写回 `input-events`，不再要求用户另外导出 Winlator/Wine 日志；测试按键继续使用 `A`。

### 2026-08-10：G3-P0-G 自动捕获 Wine 输入跟踪 APK 完成

- guest 启动前注册 `ProcessHelper` 输出回调；当 Wine/Box64 日志偏好关闭时，也会保留 Wine `key/keyboard/input/rawinput` 通道，过滤后写入当前 `input-events` 文件。Activity 销毁顺序调整为先停止 guest、移除回调，再关闭输入日志。
- 诊断变量从 `-all,+event,+key,+keyboard,+input,+rawinput` 收窄为 `-all,+key,+keyboard,+input,+rawinput`，并限制单行最大 2048 字节，减少无关事件干扰。
- 新 APK `app-debug-project3-G3-P0-G-auto-wine-input-trace-a.apk` 大小 `206,823,485` bytes，SHA-256 `E1F327D14C1EE33B1B91FF6CF72F0892C770FFBFD9F7A88679345C95FC542883`；归档前确认目标不存在，未覆盖历史 APK。
- 下一次真机只需按星露谷 D-pad 左方向 `A` 两次并导出最新 `input-events`；若出现 `X11DRV_KeyEvent`，继续分析 vkey/scan 和 Windows input/rawinput；若完全没有，进入 X event 投递/guest 事件循环修复分支。

### 2026-08-10：G3-P0-G 真机确认 Wine 消费但扫描码错误

- A 键测试日志 `input-events-20260810-142626-808.log` 已唯一归档，大小 `295,200` bytes，SHA-256 `AFCAEA4B477228846A61FA28C47FD5AB6021E014F7EC182E5D4DCE5555A6BD9D`；启动会话日志 `input-events-20260810-142418-730.log` 大小 `252,433` bytes，SHA-256 `235D1C01FD9913CC21018A7B007F264110087C4E8EDB32D983C3DB74E886F48D`。
- Wine 日志完整出现 `X11DRV_KeyEvent`、keysym `a`、`vkey 0x41` 和 `X11DRV_send_keyboard_input`；两组 press/release 均进入 Wine，但 scan 是 `0x78`。初始化阶段显示 keycode 38 的 X11 keysym 曾被识别为 `NoSymbol`，随后被分配备用扫描码 `0x78`。
- 该证据将根因从“事件未到达 Wine”收窄为“XServer `GetKeyboardMapping` 的 2-keysyms-per-keycode 响应被截断/错位”，G3-P0-H 已修复数组容量、reply length、起始索引和写入数量。

### 2026-08-10：G3-P0-H X11 键盘映射修复 APK 完成

- `Keyboard.keysyms` 现在为全部 248 个 keycode 分配两组 keysym；`GetKeyboardMapping` 按请求范围校验，并以 `count*2` 返回长度和数据，修正非 8 起始 keycode 的索引。
- 新 APK `app-debug-project3-G3-P0-H-x11-keyboard-mapping-a.apk` 大小 `208,697,326` bytes，SHA-256 `43FC44152EFF0AF9347645AABD1B6BB55A44A0A7258BDD8F14D57CFBA4816A6E`；归档前确认目标不存在，未覆盖历史 APK。
- 下一次真机只在星露谷相同场景按 D-pad 左方向 `A` 两次；验收首要条件是 Wine 日志出现 `keycode 38 converted to vkey 0x41 scan 1e`，随后再看游戏是否响应。

### 2026-08-10：G3-P0-H 真机确认核心映射完整但 XKB 缺失

- 真机日志 `input-events-20260810-151403-986.log` 已唯一归档为 `archive/runtime-logs/dave-the-diver/G3-P0-H-x11-keyboard-mapping-stardew-a-20260810.log`，大小 `268,990` bytes，SHA-256 `254ED3C6372A627CCC690EB85636A9D7E3F3B3B42A275DE5E9C7A90200391F83`。
- 日志出现 9 次 `keyboard_mapping_reply first=8,count=248,keysyms=496`，证明 H 的数组容量、回复长度和数据数量修复均已在真机执行。两组 A 产生 4 个 X11 `KeyPress/KeyRelease`、4 个成功 WinHandler 包及 4 个 Wine `X11DRV_KeyEvent`，没有事件丢失。
- 4 个 Wine 事件仍全部为 `vkey=0041 scan=0078`，没有 `scan=001e`；初始化仍报告 keycode 38 为 `NoSymbol` 并分配备用扫描码 `0x78`。原因是 Wine 扫描码布局通过 XKB API读取，而当前 XServer 未注册 `XKEYBOARD` 扩展。
- G3-P0-I 采用 Box64 客体侧 XKB 到核心映射的窄兼容层，避免在 Java XServer 中一次性实现完整 XKB wire protocol；验收目标保持为 A 的标准扫描码 `0x1e`。

### 2026-08-10：G3-P0-I 兼容层 APK 完成

- 新增 `app/src/main/guest/xkb-core-fallback.c` 与构建脚本；独立函数级测试退出码为 `0`。生成库为 ELF64 x86-64、3024 bytes，导出 `XkbKeycodeToKeysym`、`XkbTranslateKeySym`，仅保留运行时所需的 `XKeycodeToKeysym` 未定义符号。
- 启动流程每次将 asset 复制为 rootfs `/usr/lib/libwinlator-xkb-core.so`，并写入 `BOX64_LD_PRELOAD`；日志会记录 `xkb_core_fallback installed` 与 `preload` 标记，未设置宿主 `LD_PRELOAD`。
- 新 APK `app-debug-project3-G3-P0-I-xkb-core-fallback-a.apk` 大小 `208,697,640` bytes，SHA-256 `C6EE05454DF4A48DC77CBCAC0E245E0A19DF8A73A4959A7B5EF994AAC55357F0`；目标归档前不存在，未覆盖 G3-P0-H 或更早 APK。
- APK v2 签名和 zipalign 通过；asset `xkb-core-fallback-x86_64.so` SHA-256 `B2D9ECB6CB0F05895C59FACCB195BE1641029A185C9371CFC1B666F955D83F4E`，rootfs 与 patches 哈希仍为 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`、`70C25EDCBAFB71D7D3855FAF39E582ED5C6709F3AFCFB2F21C675D5FEB65E02C`。
- H 真机日志已唯一归档为 `archive/runtime-logs/dave-the-diver/G3-P0-H-x11-keyboard-mapping-stardew-a-20260810.log`，大小 `268,990` bytes，SHA-256 `254ED3C6372A627CCC690EB85636A9D7E3F3B3B42A275DE5E9C7A90200391F83`。

### 2026-08-11：G3-P0-I 真机输入闭环并恢复性能阶段

- 星露谷成功日志已归档为 `archive/runtime-logs/dave-the-diver/G3-P0-I-xkb-core-fallback-stardew-input-success-20260811.log`，原文件 `input-events-20260811-105026-922.log` 大小 `1,446,660` bytes，SHA-256 `1B330B98129FD8E087BFD909148BD48FE8A1E3A537D6484453C3243303E2BB00`：A 的 16 个 Wine press/release 事件全部为 `scan 1e`，另有 W/S/D 分别为标准 `scan 11/1f/20`，没有 `scan 78`。
- Dave 成功日志已归档为 `archive/runtime-logs/dave-the-diver/G3-P0-I-xkb-core-fallback-dave-input-success-20260811.log`，原文件 `input-events-20260811-105430-263.log` 大小 `733,905` bytes，SHA-256 `272A797E789EF67B2069D8DC5E36493855F01AE7A88342741299E0B58C9E9857`：D、S 分别为 `scan 20`、`scan 1f`，X11 事件发送后出现 604 行 Raw Input 读取；用户确认游戏实际响应。
- 两轮都记录兼容库 `installed=true,size=3024`、`preload=true`，且所有应用窗口均为 `surface=false`。输入修复正式闭环；后续不再保留 A 单键双通道和强制 Wine trace，转入 G3-P1 低扰动性能基线。

### 2026-08-11：G3-P1 低扰动性能基线 APK 完成

- `FrameRating` 现在可选择没有 `_NET_WM_SURFACE` 属性的 WineD3D 应用窗口；计数仍来自该窗口 drawable 的实际更新回调，不使用 Android 屏幕刷新次数伪造 FPS。
- CSV 使用 `version=2`：自动预热 `60,000 ms`、测量 `180,000 ms`；在 HUD Full 模式下长按 FPS 区域可重新开始分段，测量结束后写入 `reason=measurement_complete`。摘要的 1% Low 由最慢 1% 帧的平均帧时间换算，避免对瞬时 FPS 取算术平均。
- 新 APK `app-debug-project3-G3-P1-performance-baseline-r2.apk` 大小 `208,697,768` bytes，SHA-256 `579F4B793B22F1CEC4B83E8ACCE6709C814E8202F2D816ABE205DE60E8AF6DB1`；目标归档前不存在，未覆盖历史构建。
- APK V2 签名、zipalign、dex 标记通过；XKB asset SHA-256 `B2D9ECB6CB0F05895C59FACCB195BE1641029A185C9371CFC1B666F955D83F4E`，rootfs 与 patches 哈希保持 `2419CAD38D3C3992827151CD7D46C18E19085375403D01C5478A1F9EC4D97CBD`、`70C25EDCBAFB71D7D3855FAF39E582ED5C6709F3AFCFB2F21C675D5FEB65E02C`。

### 2026-08-11：G3-P2 修复性能 HUD 截获全屏触摸

- 用户安装 G3-P1 后报告鼠标无法移动。代码差异定位到 `FrameRating` 根视图新增的长按监听器；全屏根视图变为 long-clickable 后位于 `TouchpadView` 上方，触摸事件被 HUD 层提前消费。该现象发生在 Android View 分发层，早于 XServer/Wine 日志链，不属于 Wine 鼠标配置或 Dave 游戏问题。
- 修复只把采样重置手势从全屏 `FrameRating` 移到可见的 `LLFPSPanel`，未改触摸板、X11、WinHandler、XKB、Wine 或性能采样统计逻辑。
- 新 APK `app-debug-project3-G3-P2-hud-touch-pass-through-r1.apk` 大小 `208,697,777` bytes，SHA-256 `776266F789536BE5DC35BBF303C8147F7210528F7F8016274E607531D7E7D67B`；目标归档前不存在，未覆盖 G3-P1。Java 编译、完整 APK 构建、zipalign 和 V2 签名验证通过，等待真机鼠标/按键/HUD 长按三项回归。
- 项目3任务1的后续覆盖计划已独立建立为 `docs/项目3-任务1-多游戏运行测试计划与执行记录.md`；G3-P2 通过后先测 `Escape from Duckov`，再测 `Palworld`，不在鼠标回归轮同时引入新游戏变量。
- 真机回归结果为鼠标恢复、按键有效、长按无振动。输入日志归档为 `archive/runtime-logs/dave-the-diver/G3-P2-hud-touch-pass-through-success-20260811.log`，大小 `1,174,319` bytes，SHA-256 `4D0BAEBB5388C11B73826E26F3003DAF21DC598956F2CB7290763C2E7F3F0F8E`；其中 XKB 安装/preload 均生效，Dave 窗口获得 48 次 press、48 次 release，另有 58 次 pointer focus 事件，与用户可见结果一致。
- 同轮 CSV 归档为 `archive/runtime-logs/dave-the-diver/G3-P2-hud-touch-pass-through-open-sampling-20260811.csv`，大小 `1,074` bytes，SHA-256 `4DC3D2A1561C9B28BADB7789979ABB5EEAC373503870B63E69EC73066DF212F9`。它已选中非 surface 的 Dave 窗口，证明 G3-P1 窗口识别生效；但约 15 分钟会话仅记录两条 Dave 更新，且容器未销毁时导出导致无 `session_end`。该文件不能作为性能基线，后续需把采样目标继续下沉到实际更新的 renderable 子窗口，并让手动重置标记立即 flush。
- G3-P2 的兼容性目标已完成。长按无振动仅保留为采样器交互反馈问题；当前暂停 G3 性能工具修正，按用户优先级进入项目3任务1的 Duckov 覆盖。

### 2026-08-11：T1-DUCKOV-A DXVK 黑屏归档

- Duckov 独立容器 ID 8 已完整执行 rootfs、图形、音频和 X 环境启动阶段，随后启动 `Duckov.exe` 并识别 `UnityPlayer.dll`；Steam API、FMOD、ALSA 与 Wine Vulkan 模块均已加载。用户可见结果仍为黑屏。
- 约 75 秒的 Winlator 日志没有访问异常、SIGSEGV、Unhandled exception、X 连接断开或显式进程退出，也没有 DXVK 建立设备或交换链的可辨认证据，因此分类为 `FAIL-BLACK`，不归为容器启动失败或闪退。
- 两份原始日志已使用唯一名称归档到 `archive/runtime-logs/duckov/`。主日志大小 `60,808` bytes、SHA-256 `DCC3727774199B6CC1A2652825EF14329FA474C99586A639CC56CF893B28BC38`；启动日志大小 `1,321` bytes、SHA-256 `84E2102A921A5FCEC9941303B5FA19B744480395FEAFCE7B3A08CB17C8B6E022`。
- 下一步复用同一容器，仅将 DXVK 3.0.2 切换为 WineD3D；另加相对 Unity `-logFile` 参数以获得引擎日志。如果 B 轮结果变化，后续用相同日志参数补做 DXVK 对照，避免把观测参数误判为修复变量。

### 2026-08-11：T1-DUCKOV-B WineD3D 黑屏归档

- B 轮配置和 argv 均按计划生效，约 5 分钟内仍为黑屏。Unity Player 日志证明 2022.3.62f2 已通过 WineD3D 创建 D3D11 feature level 11.1 设备，并完成程序集、输入、触摸与 PhysX 初始化；WineD3D 不是当前缺少图形设备的证据。
- WineD3D 对现代交换链特性的若干未实现警告保留为显示兼容风险。更靠近游戏逻辑的直接异常是 `SavesSystem` 与 `OptionsManager` 无法写入 `E:\Saves`，连续抛出两个 `DirectoryNotFoundException`，随后未进入可见菜单或场景。
- 原始 7z 不包含 Saves 条目，本地共享根目录也没有该目录。T1-DUCKOV-C 因此只创建空的 `E:\Saves`，验证游戏包首次运行未创建父目录是否为共同阻塞；B 轮仍记为 `FAIL-BLACK`。
- B 轮三份日志已归档：Winlator 日志 `164,866` bytes、SHA-256 `582BE36177E53ABB895338F8D6E4E600FDDE227A0DCE2C63CA306D57561A20DE`；启动日志 `1,324` bytes、SHA-256 `349522637F1F18BFBC57DF74E68F48165FF48B0D0FFF07C19E6414A9889F2FCB`；Player 日志 `4,052` bytes、SHA-256 `A0B685E2D0C24D7C480563C5896BCC4DA296E4149EB438895239F2B660C90257`。

### 2026-08-13：T1-DUCKOV-C 保存目录对照仍黑屏

- C 轮仍为黑屏，约 4 分 37 秒后由用户结束会话。Unity 与 WineD3D 的 D3D11 初始化结果和 B 轮一致，无运行中崩溃特征。
- 创建 `E:\Saves` 后，B 轮两条目录缺失异常消失，证明该目录是当前游戏包首次运行缺失的必要数据目录；但 Easy Save 3 随后在 `ES3.CreateBackup()` 中把空目录路径传给 `Directory.CreateDirectory()`，产生新的 `ArgumentException`。补目录推进了游戏脚本，但没有解除黑屏，故它不是充分根因。
- WineD3D 的 swap effect、frame-latency object 和 swapchain view 未实现警告仍存在。下一轮保留 Saves 与日志参数，只回切 DXVK 3.0.2，取得可与 C 轮比较的 Unity Player 日志；D 轮后停止继续扩大 Duckov 变量并转入 Palworld 覆盖。
- C 轮三份日志已归档：Winlator 日志 `165,410` bytes、SHA-256 `FB1B175BF6527F5375E8382205EF70096DE382483731AD2E8FDC096B49845508`；启动日志 `1,324` bytes、SHA-256 `D0F5DC64154C3A44190CE24E93633A5A868D0C9C1A850CAFC81B134241E5B8B6`；Player 日志 `2,551` bytes、SHA-256 `13C5EEADC7BB02B09E6F3E21E118C266086B64D59479AD809A1ABE17055A96D5`。

### 2026-08-13：T1-DUCKOV-D DXVK 受控复测仍黑屏

- D 轮只回切 DXVK 3.0.2，并保留 `E:\Saves`。Unity Player 日志确认 Turnip Adreno 725 的 D3D11 feature level 11.1 设备创建成功，D3D11 初始化层没有失败。
- `DirectoryNotFoundException` 不再出现，但 Easy Save 3 的 `ES3.CreateBackup()` 仍抛出空路径 `ArgumentException`；该停点与 C 轮一致。Winlator 日志没有运行中崩溃或 X 会话断开，分类为 `FAIL-BLACK`。
- A-D 结果共同说明：当前 Duckov 游戏包的保存/选项配置在 Winlator 映射路径下不兼容；现有证据不支持修改 Winlator 公共图形、Wine 或 Box64 代码。停止 Duckov 变量扩展，转入 Palworld 覆盖。
- D 轮三份日志已归档：Winlator 日志 `61,768` bytes、SHA-256 `B2977CDD3C3202BA4AC05FFC8702D82141B33420DB547793D2B4F56BA05873CF`；启动日志 `1,321` bytes、SHA-256 `2D66581606A454531A362D9FBFEFB6FCE33163AAB0E615A10E3C1B2785C7FC19`；Player 日志 `2,562` bytes、SHA-256 `66271A03CC1F3B0B7929474BE5587870996EB9369C1B269D8BC469862BD87DB4`。

### 2026-08-13：Palworld 替代候选与 PDF 归档

- 用户确认 Palworld 当前无法测试，并提供 17 页图片型候选游戏列表。PDF 已原样归档，大小 `2,326,954` bytes、SHA-256 `A504EED183B6E8ABC35562D59E0480E53D8E1337A1FD376CC82676AFB139E902`。
- 按“非 Unity、64 位、D3D11、体积较小、启动链简单”筛选，主测选为 Fox Engine 的《合金装备5：原爆点》；官方要求 DirectX 11、约 4 GB 存储。备用选为 Unreal Engine 的《师父》；最低 DirectX 11、约 22 GB。
- 候选 PDF 中存在非官方修改标签，测试只接受用户合法持有的干净 PC 副本作为可外推证据。下一步先下载解压但不运行，由仓库侧完成 PE、主程序、引擎和第三方 DLL 静态检查。

### 2026-08-13：日志归档机制固定

- 工作区运行、输入、性能、测试和构建日志完成全量哈希核对；本轮把远程尚未保存的当前内容导入日期化快照，并生成包含原始路径、大小、SHA-256、状态和归档位置的清单。
- 新增 `scripts/archive-development-logs.ps1`。后续每次日志导出与分析完成后必须运行该脚本；常见凭据扫描或 95 MiB 单文件保护检查失败时停止上传。
- 完整商业游戏文件不进入开源仓库。开发记录保留游戏清单与校验值、容器/组件配置、日志、现象、结论和下一步计划，以满足问题复查与结果复现。

### 2026-08-14：替代候选包静态准入阻断

- 原爆点与师父均完成了只读静态检查，没有运行 EXE。两者的主程序架构和图形 API 原本适合补充 Fox Engine/Unreal Engine 覆盖，但实际目录都不是干净发行副本。
- 原爆点存在 ALI213 Steam 模拟层；师父存在 RUNE `steam_emu.ini`、`[Crack]` 配置、重命名原 DLL 和签名哈希不匹配的 Steam API DLL。两者均标记为 `BLOCKED-PACKAGE`，不计入任务1第四个真机样本。
- 详细文件指纹写入 `archive/game-inventory/candidate-package-static-gate-20260814.md`，并生成 `local-games-20260814.csv`。下一步是用官方客户端取得/校验一个新目录，再做第二次静态准入；通过前不生成容器配置。

### 2026-08-14：用户选择方案2解除包体测试阻断

- 用户确认当前原爆点与师父目录符合本项目要求，并选择以现有文件集合继续研究。两个样本状态改为 `READY-PACKAGE-SCOPED`，静态发现的第三方修改事实及哈希继续保留。
- 当前包可以计入用户定义的任务1五游戏矩阵，但结论必须绑定已记录的 SHA-256，并带 `PACKAGE-SCOPED` 限定；不外推到其他副本或官方发行版。
- 执行顺序为原爆点 A 轮、日志归档与分析、师父 A 轮。当前只开放 T1-MGSVGZ-A，使用 G3-P2、独立容器、`1280x720`、Turnip/Zink、DXVK 3.0.2、ALSA、Box64 Intermediate、无环境变量和无启动参数。

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
| G1.10 | 最终配置第二次冷启动日志与最小成功集确认 | T1.10B 进入可交互游戏后稳定运行至少 6 分钟 | 已完成（2026-08-06） |
| G2 | 内置 Wine/rootfs 最小修复、构建验证、APK 哈希 | T1.10B 六分钟冷启动完成 | 已完成（2026-08-06） |
| G2.2 | 递增 rootfs 版本、归档 T2-A 失败日志、重建 APK | T2-A 证明应用级 rootfs 未因 APK 更新而刷新 | 已完成（2026-08-06） |
| G2.3 | 容器入口增加 rootfs 完整性门槛、重建 APK、更新 T2 操作步骤 | 用户反馈卡在 `starting up` 且无日志，确认需阻止安装竞争状态启动 | 已完成（2026-08-06） |
| G2.4 | 启动阶段独立日志、后台异常收口、Box64 解压失败重试、唯一 APK 归档 | G2.3 通过版本门槛后仍在 guest 进程创建前静默卡住 | 已完成（2026-08-06） |
| G2.5 | 归档 T2-A3 双日志、修复压缩包父目录创建、唯一 APK 归档 | T2-A3 证明 Box64 单文件 tar 因父目录缺失而无法解压 | 已完成（2026-08-06） |
| G2.5-A | 归档 T2-A4 三份成功日志、确认首次内置修复冷启动、制定 T2-B | G2.5 真机越过 Box64 解压并成功启动 Dave | 已完成（2026-08-07） |
| G2.5-B | 归档 T2-B 三份日志、确认 6 分 51 秒第二次冷启动、关闭启动故障 | T2-B 同配置进入 `Tutorial_Mission01` 且无崩溃特征 | 已完成（2026-08-07） |
| G3-P0 | FPS/帧时间 CSV 采样工具与固定场景基线 | 启动故障闭环后进入性能阶段 | 工具已完成，等待真机基线（2026-08-07） |
| G3-P0-A | 首份空 CSV 证据、单按钮多绑定诊断、D-pad/屏幕键盘对照 | 首轮真机游玩出现触屏方向无反应 | 已完成，跨游戏对照仍失败（2026-08-07） |
| G3-P0-B | X11 键盘事件全链跟踪、唯一诊断 APK、两份真机日志 | Dave/星露谷两款游戏的 D-pad/屏幕键盘均无效 | 已完成，X11 事件已发送且无丢弃（2026-08-07） |
| G3-P0-C | 活动窗口手动 `bringToFront()` 单变量对照 | WineD3D application window 的 `surface=false` 绕过自动前台同步 | 已完成，手动前台化仍无效（2026-08-07） |
| G3-P0-D | WinHandler 键盘协议核对、`VK_W` 双通道诊断 APK 与真机对照 | X11 core event 已发送但两款游戏均不响应 | 已完成，54 个直通包发送成功但仍无效（2026-08-10） |
| G3-P0-E | 标准 X11 `FocusIn/FocusOut`、焦点日志、唯一 APK 与真机对照 | Java 侧焦点变化从未通知 Wine X11 客户端 | 已完成，通知生成且被订阅但输入仍无效（2026-08-10） |
| G3-P0-F | 改测 `A`、Wine `event/key/input/rawinput` 跟踪、唯一 APK | Android/XServer 事件完整，Wine 消费状态未知 | 已完成，A 双通道仍无效且 guest 输出被丢弃（2026-08-10） |
| G3-P0-G | 自动捕获 Wine 键盘/input/rawinput 输出、唯一 APK | G3-P0-F 未获得 Wine 消费层日志 | 已完成，定位 A 被映射为错误 scan `0x78`（2026-08-10） |
| G3-P0-H | 修复 `GetKeyboardMapping` 长度/索引/容量、唯一 APK | Wine 收到 A 但使用错误 scan `0x78` | 已完成，核心映射完整但 XKB 仍返回 `NoSymbol`（2026-08-10） |
| G3-P0-I | x86_64 XKB 核心映射兼容层、Box64 注入、唯一 APK | H 真机仍把 A 分配为备用 scan `0x78` | 已完成，星露谷和 Dave 输入均有效（2026-08-11） |
| G3-P1 | WineD3D 非 surface 采样、移除输入诊断开销、唯一基线 APK | I 已闭环输入，旧性能 CSV 因未选中窗口为空 | 窗口选中已生效，但实际帧更新目标仍需修正；任务1后继续（2026-08-11） |
| G3-P2 | HUD 长按命中范围收缩到 FPS 面板、恢复触摸板事件下传、唯一 APK | G3-P1 根 HUD 全屏 long-clickable 导致鼠标无法移动 | 已完成，鼠标与按键真机回归通过（2026-08-11） |
| T1-DUCKOV-A | Duckov DXVK 黑屏日志归档、失败分类与 WineD3D 对照计划 | G3-P2 输入门槛通过后首轮新游戏覆盖 | 已完成，`FAIL-BLACK`，进入 B 轮（2026-08-11） |
| T1-DUCKOV-B | WineD3D 黑屏三日志归档、D3D11 初始化证据与缺失保存目录定位 | A 轮 DXVK 无首帧后执行图形封装层对照 | 已完成，`FAIL-BLACK`，进入 C 轮（2026-08-11） |
| T1-DUCKOV-C | 补齐 `E:\Saves`、归档新脚本异常并制定 DXVK 受控复测 | B 轮定位到保存父目录缺失 | 已完成，目录异常推进但仍 `FAIL-BLACK`（2026-08-13） |
| T1-DUCKOV-D | 保留 Saves 的 DXVK 复测、A-D 对照结论、停止 Duckov 变量扩展 | C 轮消除目录缺失但仍黑屏 | 已完成，游戏包保存配置兼容性候选（2026-08-13） |
| T1-GAME-SELECT | 候选 PDF 归档、Palworld 替代选型与干净副本门槛 | 用户确认 Palworld 当前无法测试 | 已完成，主测《原爆点》、备用《师父》（2026-08-13） |
| T1-PACKAGE-GATE | 两款候选目录静态检查、文件指纹、包体风险分类和干净副本计划 | 用户准备好原爆点与师父目录 | 已完成，两者均为 `BLOCKED-PACKAGE`（2026-08-14） |
| T1-PACKAGE-SCOPE | 方案2范围调整、五游戏矩阵和原爆点 A 轮唯一配置 | 用户确认当前两款包符合研究要求 | 已完成，两者改为 `READY-PACKAGE-SCOPED`（2026-08-14） |
| LOG-ARCHIVE | 全量日志哈希核对、增量快照、清单与持续归档脚本 | 用户要求日志作为开发记录持续推送 | 已完成（2026-08-13） |
| G3 | 性能基线、单变量优化矩阵和项目3最终报告 | G2.5-B 完成启动闭环 | 进行中（2026-08-07） |

所有节点推送到 `origin/main`（`https://github.com/SilenceWanna/winlator_wzh.git`）。提交前先检查工作树和远程差异，不覆盖用户改动；游戏本体、临时解包目录和超大 APK 不进入 Git。

从 2026-08-13 起，后续每份新导出的运行、输入、性能、测试和构建日志均在分析后运行 `scripts/archive-development-logs.ps1`：脚本以 SHA-256 复用已有归档、把新增内容写入唯一快照、生成完整来源清单，并在敏感信息或超大文件检查失败时中止。日志快照、分析结论和下一步计划必须在同一个关键节点提交并推送。
