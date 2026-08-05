# 项目3：Dave the Diver 启动故障修复计划与执行记录

> 建立日期：2026-08-05  
> 当前状态：G0 计划基线已完成，等待 T1 真机单变量复测  
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
| P0 | 内置 Wine 11 的 `nsiproxy` 缺少 `if_nameindex()` 空值保护，阻塞网络初始化 | 使用 `/opt/wine`；停在 `dnsapi` 后；项目2存在相同平台条件和已验证补丁 | 只替换为补丁 Wine；若仍停在相同位置且无新进展，则降级该假设 |
| P1 | 当前游戏文件中的第三方 Steam/Unity 替换层在 Wine/Box64 下阻塞 | `steamclient64.dll` 在停止前加载，`UnityPlayer.dll` 不是原始文件 | 使用来源合法、校验完整的干净游戏副本对照 |
| P2 | Box64 Unity 专用策略被关闭导致 IL2CPP 初始化不兼容 | 日志为 `BOX64_UNITYPLAYER=0`，源码默认值被 Winlator 显式覆盖 | 在补丁 Wine 不变时只设置 `BOX64_UNITYPLAYER=1` |
| P3 | Box64 Intermediate 的内存模型不适合该 Unity 版本 | 当前为 Intermediate | 在前项无效后只改 Stability |
| P4 | Unity 图形后端或 DX wrapper 不兼容 | 当前尚未看到 Unity 图形初始化证据 | 先越过早期停点，再用 `-force-gfx-direct` 和图形矩阵验证 |

## 四、分阶段测试与实现计划

### G0：计划与证据基线

- [x] 通读工作区、项目3任务说明、Winlator 启动链与项目2修复记录。
- [x] 分析初始 Dave 日志并确定单变量测试顺序。
- [x] 校验现有补丁 Wine 包路径、大小和 SHA-256。
- [x] 建立本执行文档。
- [x] 提交并推送计划基线。

### T1：补丁 Wine 单变量复测（当前轮）

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

### G1：T1 证据归档与决策

- [ ] 将初始日志和 T1 日志归档到 `archive/runtime-logs/dave-the-diver/`，记录大小与 SHA-256。
- [ ] 在本文追加实际 Wine 路径、关键事件计数、可见现象和结论。
- [ ] 若 T1 通过，进入 G2；若失败，按新停点更新假设表和 T2 变量。
- [ ] 提交并推送该证据节点。

### G2：把最小修复集成到内置 Wine

仅在 T1 证明补丁有效后执行：

1. 从已验证补丁产物提取所需 `nsiproxy` 模块，确认版本、架构、权限和哈希。
2. 替换 `app/src/main/assets/rootfs.tzst` 中内置 Wine 11 的对应文件；不改 Wine 版本标识、Box64 或图形组件。
3. 重新打包后进行路径安全、符号链接、执行位和解包逐文件差异校验。
4. 构建调试 APK，执行 `zipalign` 与 APK 签名验证并记录 SHA-256。
5. 提交并推送源码、构建说明与哈希；APK 超过 GitHub 普通对象限制时只记录本地路径和哈希，不直接提交大文件。

### T2：内置修复 APK 真机回归

使用全新容器选择内置 Wine，日志必须重新显示 `/opt/wine/`。连续冷启动两次并进入主菜单，以证明成功来自新内置 rootfs，而不是手机里残留的可选 Wine。通过后将日志归档、更新本文并提交推送。

### T3：次级兼容性分支（仅当前置阶段未闭环时启用）

按以下顺序每轮只改一个变量：

1. 补丁 Wine + `BOX64_UNITYPLAYER=1`。
2. 保持上一轮 Wine，Box64 预设改为 Stability。
3. 保持 Wine/Box64，启动参数增加 `-force-gfx-direct`。
4. 使用来源合法、文件校验完整的干净游戏副本对照。
5. 增加诊断日志：Box64 日志级别 2；Wine 通道 `timestamp,pid,tid,+seh,+loaddll,+process,+thread,+nsi`；Unity 参数 `-logFile "E:\Dave the Diver\Player.log"`。

判断边界：若干净副本能启动而当前副本不能，Winlator 侧不实现针对第三方 DRM/注入组件的绕过；记录兼容边界后转为验证正式游戏发行版本。

### G3：启动闭环与项目3性能阶段

- [ ] 连续两次冷启动通过，完成日志与 APK 归档。
- [ ] 输出根因、修复、验证矩阵、残余风险和复现步骤。
- [ ] 建立固定场景性能基线和单变量优化矩阵。
- [ ] 完成项目3报告并提交、推送最终节点。

## 五、用户当前需要执行的 T1 步骤

1. 把下列文件传到手机：

   `D:\agent\Winlator\winlator_wzh_new\archive\artifacts\wine-packages\wine-11.0-final-nsiproxy-nullguard-winlator-custom-addon.tar.xz`

2. 打开 Winlator 的“设置 -> Wine 版本”。保留 `Wine 11.0-rc3`、`Wine 11.0-rc5` 和 `Wine 11.0-f11`；如果已经存在无后缀的可选 `Wine 11.0`，只移除这个旧正式版条目，因为新包使用同一标识，不能并存。
3. 导入上述 `.tar.xz`，等待 Wine Configuration 和文件安装完成，中途不要强退。
4. 新建一个容器并明确选择导入的无后缀 `Wine 11.0`。不要复用旧 prefix。其余配置逐项照抄本次失败容器，特别是 Box64 `0.4.5-dev-372739d` 和 Intermediate 预设。
5. 为同一份 `DaveTheDiver.exe` 创建快捷方式。本轮不要增加任何环境变量或启动参数，也不要改图形驱动、DX wrapper、分辨率或游戏文件。
6. 启动后至少等待 120 秒。若进入主菜单，停留约 30 秒后正常退出；若仍无窗口，等待满 120 秒再退出。
7. 导出 Winlator 日志，保存到电脑：

   `D:\agent\Winlator\logs\dave-the-diver\T1-patched-wine11-20260805.txt`

8. 同时反馈一种可见结果：`进入主菜单`、`黑屏`、`无窗口`、`闪退` 或 `Winlator 会话退出`，以及从点击启动到该现象的大致秒数。

拿到 T1 日志后的第一项检查是 Wine 路径。若日志仍为 `/opt/wine/`，该轮属于选错 Wine，不进入技术结论；若为 `/opt/installed-wine/wine-11.0/`，才继续比较 `dnsapi`、`if_nameindex`、Unity 加载和退出序列。

## 六、进度记录

### 2026-08-05：G0 建立计划

- 工作区与代码启动链已完成首轮阅读，当前实际修改目标确认为 `winlator_wzh_new`。
- 初始日志确认 Winlator 已正确创建 Dave 游戏进程，排除快捷方式未执行。
- 失败停点收敛到 `dnsapi` 加载后的早期初始化阶段；当前内置 Wine 未包含项目2已验证的 `nsiproxy` 空值保护。
- 已确定 T1 只切换补丁 Wine，禁止同轮调整 Box64、Unity 或图形设置。
- G0 文档节点已纳入本次提交并推送；下一动作是等待用户执行 T1，收到日志后立即追加 G1 证据和下一轮计划。

## 七、Git 关键节点

| 节点 | 推送内容 | 触发条件 | 状态 |
|---|---|---|---|
| G0 | 初始诊断、执行计划、T1 操作 | 本文复核完成 | 已完成（2026-08-05） |
| G1 | 失败/成功日志归档、T1 结论、下一轮计划 | 收到并分析 T1 日志 | 待执行 |
| G2 | 内置 Wine/rootfs 最小修复、构建验证、APK 哈希 | T1 证明补丁有效 | 待执行 |
| G3 | 两次冷启动回归、最终报告、性能阶段入口 | 内置修复真机通过 | 待执行 |

所有节点推送到 `origin/main`（`https://github.com/SilenceWanna/winlator_wzh.git`）。提交前先检查工作树和远程差异，不覆盖用户改动；游戏本体、临时解包目录和超大 APK 不进入 Git。
