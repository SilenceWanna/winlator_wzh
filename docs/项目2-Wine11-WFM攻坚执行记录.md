# 项目2：Wine 11 / WFM 攻坚执行记录

> 执行日期：2026-07-24
>
> 结论：原先“wfm.exe 调用 Wine 11 中缺失 API”的判断不成立。同一个 wfm.exe 在 WSL 原生 Wine 11 下可正常加载并持续运行；Android 崩溃只出现在 box64 路径，且异常 RIP 落在指令中间，box64 dynarec 是当前第一嫌疑。已实现 WFM 单程序解释器模式、winefile 兜底，并按无 ADB 条件产出内置 Wine 11 的单 APK 真机候选版。

## 一、原逆向结论的纠正

从 `rootfs_patches.tzst`、Wine 11 APK 和回退 APK 提取的 wfm.exe 完全一致：

```
SHA-256 AF66D756D319CD4FD482CC28E3206B786B05234CCE53F7133960D8D2AFA8601F
```

PE 导入表解析结果：

| IAT RVA | 导入 API |
|---|---|
| `0x20af8` | `USER32!LoadCursorW` |
| `0x20b00` | `USER32!LoadIconW` |
| `0x20b30` | `USER32!RegisterClassExW` |
| `0x20bd0` 起 | `libcdio.dll` 导入 |

这些地址位于 `.idata` 的静态 IAT，由 PE loader 填充，不是 wfm.exe 通过 `GetProcAddress` 写入的动态槽。因此原计划 B1 不可能找到预期的“槽写入点”。

Android 日志中的 RIP `0x14000268A` 也不是合法指令边界：它位于 `0x140002687` 开始的一条 7 字节 `mov` 指令中间。该指令只是把寄存器写入 `.bss`，与“读取 NULL”不相符。这使 box64 dynarec 的异常地址映射或代码翻译成为首要嫌疑。

Wine 10.10 和 11.0 的 `user32.spec` 都导出上述三个 API；相关 `cursoricon.c` 没有 API 移除。

## 二、WSL 原生分流结果

环境：

- `/root/wine110/bin/wine --version`：`wine-11.0`
- prefix：从 `/root/wp11_pattern` 复制到 `/tmp/wfm-wine11-prefix`
- WFM/libcdio/winhandler：从当前 `rootfs_patches.tzst` 提取
- 显示：WSLg `DISPLAY=:0`

完成 prefix 初始化后运行：

```bash
WINEPREFIX=/tmp/wfm-wine11-prefix \
WINEDEBUG=+seh,+loaddll DISPLAY=:0 \
/root/wine110/bin/wine C:\\windows\\wfm.exe
```

结果：

- `wfm.exe`、Wine 11 USER32/SHELL32/COMCTL32 和 `libcdio.dll` 全部加载成功。
- WFM 持续运行到外部 25 秒超时，没有 `Unhandled page fault`，也没有 `0x14000268A`。
- 证明 wfm.exe 并非天然绑定 Wine 10.10；Android 故障面收敛为 Wine 11 + box64/Android 运行路径。

## 三、实现方案

### 3.1 Wine 11 打包方式

带双侧 server 目录补丁的 Wine 11 同时保留两种打包方式。

独立可安装包：

```
../../artifacts/wine-packages/wine-11.0-winlator-custom.tar.xz
SHA-256 B9BCC2CE2E2A5987E42C4D59C88CA31B68FB0363CF2042D015C4F39AE274D8B4
```

归档共 2532 个 `./opt/wine/` 条目，保留 ELF 可执行位和符号链接。硬校验：

- `ntdll.so` 含 `%s/.wineserver/server-%llx-%llx`
- `wineserver` 含 `%s/.wineserver`
- 64/32 位 `winefile.exe` 均存在

`WineInstaller.findWineVersionAsync` 现在最多向下解开 8 层单目录包装，因此可以直接识别 `opt/wine/bin/wine`，同时避免异常归档造成无限下钻。

由于真机无法连接 ADB，最终测试候选版把 Wine 11 的 `rootfs.tzst`、`container_pattern.tzst` 和 `common_dlls.json` 直接集成进 APK。主 Wine 版本改为 11.0，rootfs 迁移号为 `LATEST_VERSION=22`、`UPDATE_WINEPREFIX_VERSION=21`，覆盖安装后会自动替换主 Wine 并迁移已有主 Wine 容器。

### 3.2 WFM 的 box64 单程序解释器模式

Wine 11+ 容器进入纯桌面时仍默认启动 WFM，但自动给 `/etc/config.box64rc` 追加：

```ini
[wfm.exe]
BOX64_DYNAREC=0
```

该设置只作用于 wfm.exe；游戏、Wine、DXVK/VKD3D 仍使用 dynarec，不牺牲游戏性能。

可用容器环境变量覆盖：

| 变量 | 值 | 行为 |
|---|---|---|
| `WINLATOR_WFM_INTERPRETER` | `1` | WFM 使用解释器（Wine 11 默认） |
| `WINLATOR_WFM_INTERPRETER` | `0` | WFM 恢复 dynarec，用于复现对照 |
| `WINLATOR_DESKTOP_SHELL` | `wfm` | 使用 WFM |
| `WINLATOR_DESKTOP_SHELL` | `winefile` | 使用 Wine 同源 winefile 兜底 |

这两个 Winlator 内部变量在启动 guest 前会移除，不会传给 Wine 或游戏。

### 3.3 代码改动

- `XServerDisplayActivity`：桌面 shell 选择、Wine 11 WFM 解释器默认策略。
- `GuestProgramLauncherComponent`：按容器选项生成 per-exe box64rc，并剔除内部变量。
- `WineInfo`：增加版本下限判断。
- `WineInstaller`：支持多层包装的 Wine 归档。
- `EnvVarsView`：把两个诊断/兜底变量加入可选项。

## 四、构建产物

单 APK 主攻版（内置 Wine 11，WFM 默认走解释器）：

```
../../artifacts/apks/wine11/app-debug-wine11-wfm-interpreter-integrated.apk
SHA-256 F78360FBB1AEA470638A7D5EC89A404CF971386DFC1C0E7EE4A4E6A98F5C6DFA
```

保数据回退版（内置 Wine 10.10，rootfs 迁移号 23）：

```
../../artifacts/apks/wine10/app-debug-wine10.10-rollback-r23.apk
SHA-256 486DACA7C504CA7051256B359EF2CDE9B1CCEDFD7682738C7D8DE9DBAD380A45
```

回退版的迁移号高于 Wine 11 候选版的 22，因此测试失败后覆盖安装即可强制恢复 Wine 10.10，无需卸载应用或清除容器数据。

独立 Wine/双版本实验版仍保留：

```
../../artifacts/apks/wine11/app-debug-wine11-dual-wine.apk
SHA-256 994841751FC3F1F60424EA102B0BE7AEC2EF968E555461553B994894FB865513
```

Gradle `assembleDebug` 已通过。从最终单 APK 重新提取的三个 Wine 资产与 Wine 11 基准哈希完全一致；最终 DEX 中已验证包含 `wfm.exe`、`winefile.exe` 和两个内部切换变量。

## 五、真机验证矩阵

本轮执行时 ADB 没有连接设备，改用“APK 手工传手机、应用日志回传”完成以下矩阵：

1. 把 `app-debug-wine11-wfm-interpreter-integrated.apk` 发送到手机，直接覆盖安装，不卸载旧版。
2. 首次启动等待系统文件重新安装完成；不要在解压过程中强退。
3. 不加环境变量进入原容器桌面：预期 WFM 正常，box64 日志显示 wfm.exe 使用解释器。
4. 加 `WINLATOR_WFM_INTERPRETER=0`：预期复现原 NULL page fault，用于确认 dynarec 根因。
5. 加 `WINLATOR_DESKTOP_SHELL=winefile`：预期 Wine 11 同源文件管理器正常，作为必达兜底。
6. 从 Winlator 首页快捷方式启动游戏：确认不经过 WFM，box64 dynarec 保持开启。
7. 回归星露谷和 d3d9/d3d11/d3d12 测试程序。

若主攻版无法进入应用或系统文件安装失败，直接覆盖安装 `app-debug-wine10.10-rollback-r23.apk`，不要卸载应用。

建议开启 box64 日志级别 2，并启用 Wine `seh,module,loaddll` 通道，保留三组日志进行最终归因。

## 六、Wine 11 游戏回归诊断（2026-07-30）

桌面与 WFM 已能长期稳定运行，鼠标和悬浮关机按钮正常。随后针对 Stardew Valley 1.5.6 黑屏完成以下排查：

- `d3d9`、`d3d11`、`d3d12` 独立测试均成功创建设备并完成渲染初始化，不能复现游戏黑屏。
- 自编译的 64 位 WGL 探针在 Turnip/Zink 上完成 867 帧，`SwapBuffers` 867 次全部成功；Wine 日志也记录了 867 次 `win32u_wglSwapBuffers` 和 `x11drv_surface_swap`。Mesa 的 `Window ... has no colormap!` 警告在该成功用例中同样出现，因此不是根因。
- Stardew Valley 能加载 .NET 5.0.7、MonoGame DesktopGL 3.8 和 `opengl32`，建立 GL 4.6 compatibility / zink / Turnip 上下文，但只有 `x11drv_surface_flush`，始终没有首次 `SwapBuffers`。故黑屏发生在游戏提交第一帧之前，不是 Wine 11 的 WGL 交换实现失效。
- Goldberg `steam_settings/disable_networking.txt` 在可执行的 E 盘路径上验证后现象不变，排除 Steam 网络等待为主因。D 盘测试出现的 `System.Collections.dll ... noexec filesystem?` 属于 D 盘挂载属性，不计入游戏兼容结论。
- Box64 `Stability` 预设已实际生效（`BIGBLOCK=0`、`STRONGMEM=2`、`WEAKBARRIER=0`），黑屏停点不变。
- 进一步用快捷方式环境变量强制 `BOX64_DYNAREC=0`。日志确认 Stardew 及其 Wine 进程均收到该值；解释器从 16:45:51 启动游戏，到 16:50:36 建立 GL 上下文，之后仍只有 55 次 surface flush、0 次 swap，最终停在网络库加载之后。由此基本排除 Box64 dynarec 和内存屏障策略。
- 日志中的 `e0434352 / 0x80070002` 与 `e06d7363` 均被上层处理，没有未处理异常或进程退出；它们目前只能说明启动流程内存在文件探测和 C++ 异常，不能单独作为崩溃根因。

下一步按单变量原则新建全新的 Wine 11 容器，使用当前内置 `container_pattern.tzst` 生成干净 prefix，并保持游戏文件、Turnip/Zink 和 Box64 Stability 不变。若新容器仍黑屏，再建立 Wine 10.10 同游戏基线；不要直接先装迁移号 23 的回退 APK，因为回退后需要迁移号至少 24 的 Wine 11 APK 才能再次覆盖升级。

### 6.1 全新 Wine 11 prefix 对照

新建而非复制的 Wine 11 容器仍复现。容器内双击程序后表面上没有窗口，但日志证明 `Stardew Valley.exe` 已启动：

- 游戏进程使用 Box64 dynarec（`BOX64_DYNAREC=1`）；唯一的 `BOX64_DYNAREC=0` 属于 WFM，符合 per-exe 策略。
- 18:30:08 启动 Stardew，18:30:46 初始化 GL 4.6 / Zink / Turnip，随后建立三个 GL 上下文。
- 仍是 55 次 `x11drv_surface_flush`、0 次 `win32u_wglSwapBuffers`、0 次 `x11drv_surface_swap`，且没有 `Unhandled`。
- 游戏成功生成首次运行的 `startup_preferences`（`timesPlayed=1`、1280x720），说明进程已越过配置目录创建阶段。

因此旧 Wine 10.10 prefix 的升级残留不是根因。桌面内没有可见窗口与快捷方式启动时显示黑屏只是宿主桌面/前台呈现差异，游戏内部停点一致。

2026-04-11，GameNative 合并了针对 Stardew Valley Steam/GOG 版本的专用 Wine 修复（PR #1156）：设置 `WINEDLLOVERRIDES=icu=n`，用于解决 ICU 导致的启动问题。该修复与当前“CoreCLR 和 GL 已初始化、首帧前停止”的症状高度一致。下一轮仅添加此变量验证，不同时修改窗口配置。

### 6.2 ICU 修复结果与 Wine 10.10 可选基线包

`WINEDLLOVERRIDES=icu=n` 已实际进入 Stardew 进程环境：同一新容器的 Box64 `Counted Env var` 从 81 增加到 82。结果仍严格相同：55 次 surface flush、0 次 swap、相同数量的已捕获异常，因此该上游修复不适用于当前 Stardew 1.5.6 包，或不是本次停点的主因。

为避免安装迁移号 23 的回退 APK 覆盖当前 Wine 11 rootfs 和所有主 Wine 容器，已从经过真机验证的回退 APK 提取 Wine 10.10，并制作成 Winlator `Settings -> Wine Version` 可安装的独立版本包：

```
../../artifacts/wine-packages/wine-10.10-winlator-custom-addon.tar.xz
SHA-256 64B3F4EAF1E68A91BBFF2F9B99C2351A0FBEE36EED96E5A84A4923424C0B4F44
```

校验结果：

- 来源回退 APK SHA-256 与记录一致：`486DACA7C504CA7051256B359EF2CDE9B1CCEDFD7682738C7D8DE9DBAD380A45`。
- XZ 完整性检查通过；解包后包含 1662 个普通文件和 14 个符号链接。
- `wine`、`wineserver` 保持 0755；Wine Vulkan/OpenGL 的 i386/x86_64 模块齐全。
- `ntdll.so` 和 `wineserver` 均保留 brunodev 的 Android server 目录补丁；版本标记为 `wine-10.10`。
- 原目录与重新解包目录的全文件 SHA-256 聚合值一致：`e374ae8544213ca51b92fdd1eb981c07b8ba59209e33fd86632074a45537ceee`。

下一步在当前 Wine 11 APK 内安装该可选 Wine，随后新建 `Wine Version = Wine 10.10` 的容器运行同一份 E 盘游戏。该测试不修改或迁移现有 Wine 11 容器。

### 6.3 Wine 管理入口修复

测试 APK 中没有显示 `Settings -> Wine Version` 的原因已定位：`settings_fragment.xml` 默认把 `LLWineInstallation` 设为 `gone`，而 `SettingsFragment` 仅在全局 `MainActivity.DEBUG_MODE` 为 `true` 时才将其显示。当前构建的 `DEBUG_MODE=false`，因此 debug APK 同样看不到该入口。

已移除 Wine 管理区对全局调试模式的可见性依赖，未开启全局 `DEBUG_MODE`，避免连带改变调试覆盖层和资源解包行为。新构建产物：

```
../../artifacts/apks/wine11/app-debug-wine11-wine-manager.apk
SHA-256 440CCF27574513DD7204185416D02A6DE7595D66623D8A86952B4DA632AA2C93
```

APK 校验结果：ZIP 对齐通过；包名 `com.winlator`，版本 `11.1 (28)`；V2 签名有效，签名证书 SHA-256 与上一 Wine 11 测试版一致，可直接覆盖安装。

### 6.4 Wine 10.10 可选容器无法进入桌面

首次安装 Wine 10.10 可选版本后，新建容器无法进入桌面。真机日志 `logs_stardew_wine10_clean.txt` 的关键错误为：

```text
system.reg is not a valid registry file
user.reg is not a valid registry file
wine: '/data/user/0/com.winlator/files/rootfs/home/xuser/.wine' is a 32-bit installation, it cannot support 64-bit applications.
```

该日志同时确认了以下事实：

- 容器实际选择了 `/opt/installed-wine/wine-10.10`，不是误用主 Wine 11。
- Wine 10.10 的 `bin/wine`、`ntdll.so` 和 `wineserver` 均成功由 Box64 启动；本地检查 `bin/wine` 与 `lib/wine/x86_64-unix/wine` 均为 ELF64。
- `wineserver` 已正常进入 `esync: up and running`，没有 `/tmp/.wine-* Permission denied`，双侧 Android server 目录补丁仍然有效。
- 失败发生在 Zink/Turnip 初始化之前，因此与图形驱动设置无关。

根因是上游可选 Wine 安装器在 `winecfg.exe` 退出后立即压缩 `.wine`，没有等待 `wineserver` 将注册表完整写回，也没有检查注册表头或 `#arch`。因此部分写入的 `system.reg`、`user.reg` 被制作成 `container-pattern-10.10.tzst`，后续所有新容器都会重复解压同一个坏模板。压缩工具本身还会吞掉 `IOException` 并返回 `void`，进一步允许部分归档被当作成功。

此外，回退 APK 的原始 `container_pattern.tzst` 只保存基础 prefix；主 Wine 容器创建流程会另外从 Wine 目录注入 `system32` 和 `syswow64` 公共 DLL，而可选 Wine 分支原先没有这一步。若直接把回退模板作为可选模板使用，会形成第二个缺 DLL 风险。

修复内容：

- Wine 10.10 安装前先展开回退 APK 中经过真机验证的 win64 模板作为初始化种子。该模板的 `system.reg`、`user.reg` 均具有有效 `WINE REGISTRY Version 2` 文件头与 `#arch=win64`。
- `winecfg.exe` 退出后改为执行 `wineserver -k -w`，等待服务端完成注册表写回后再压缩。
- 压缩前强制校验 `system.reg` 与 `user.reg`：文件非空、文件头正确且前 32 行包含 `#arch=win64`；不满足时安装失败并清理 `preinstall`，不会留下可选 Wine 或模板。
- `TarCompressorUtils.compress` 改为返回成功状态，文件读取、符号链接或归档写入失败会向上层传播并删除部分归档。
- 为 Wine 10.10 带入回退 APK 对应的 `common_dlls.json`；新建 10.10 容器时从该 Wine 自身目录补齐 703 个 `system32` 和 691 个 `syswow64` 文件。本地逐项核对缺失数为 0。
- 公共 DLL 注入改为检查每个复制结果，任一文件失败就取消并清理容器；同时核对内置 Wine 11 的 711 个 `system32` 和 712 个 `syswow64` 文件，缺失数同样为 0。
- 版本探测只在 `wine64` 确认为 ELF64 时才优先执行，否则回退到已确认的 ELF64 `wine`，避免“校验一个二进制、执行另一个错误架构二进制”。

构建过程中还遇到旧 Android Gradle Plugin 7.2.2/R8 与 JDK 21 的兼容问题：`minifyDebugWithR8` 在 `ALSAClient$DataType.class` 上触发内部空指针。切换到此前成功构建所用的 JBR 17.0.8.1 后，`compileDebugJavaWithJavac` 与 `assembleDebug` 均通过。该问题属于构建工具链，不是应用源码或 ALSA 回归。

最终测试 APK：

```text
../../artifacts/apks/wine11/app-debug-wine11-wine10-prefix-fix.apk
SHA-256 3B3D5C51609DD8B0490623B953CF21E859F9685F7D8D151EBF058D993B652029
```

APK 校验结果：ZIP 对齐通过；包名 `com.winlator`，版本 `11.1 (28)`；V2 签名有效且证书 SHA-256 仍为 `b6396f6cd549475dec0893ac0cab0e03770f403fb37679bae02418a492270b07`，可覆盖安装。APK 内 `assets/wine/container_pattern-10.10.tzst` 长度为 7,399,363 字节，SHA-256 为 `8AE3A4FEE33E86DA26826395650BB07C6F49CE94629EA4B9442BC633B6B8CA33`。

真机复测必须先删除失败的 Wine 10.10 容器，再在 `Settings -> Wine Version` 移除旧 Wine 10.10，随后用同一份 `wine-10.10-winlator-custom-addon.tar.xz` 重新安装。旧 `container-pattern-10.10.tzst` 已经损坏，单纯覆盖 APK 或新建容器不会替换它。重新安装后新建 Wine 10.10 容器，继续使用 Zink、原 Turnip 和 Box64 Stability，不添加 ICU、dynarec 或其他环境变量；先确认能进入桌面，再运行同一份 E 盘 Stardew Valley。

### 6.5 Wine 10.10 安装第二阶段返回 Settings

真机覆盖安装上述版本后，Wine 10.10 安装在 Wine Configuration 点击 `OK` 后显示 `Starting up`，随后直接返回 Settings，Wine 版本没有安装成功。手机未生成新的可导出日志；原 UI 的成功与失败路径都会重启回 Settings，因此该现象本身无法区分 `wineserver -k -w`、注册表校验或文件移动中的具体失败点。

重新评估安装结构后确认：这份 Wine 10.10 已经有与其同源、从稳定回退 APK 提取并校验的 win64 container pattern，再让手机启动 X Server、运行 winecfg、等待 wineserver，然后把 prefix 重新压缩一遍既重复又引入竞态。直装任务若放在 `XServerDisplayActivity` 内，还会与 Activity 自己的 guest launcher 和退出/重启生命周期竞争。

第二次修订：

- 对定制 Wine 10.10 的 `bin/wine`、`bin/wineserver`、`x86_64-unix/ntdll.so` 做 SHA-256 指纹校验，只有三项均与已验证包一致才启用直装。
- 指纹匹配后直接在 Settings 后台复制已验证模板，并事务移动 Wine 目录；不再进入 `Starting up`、Wine Configuration 或 X Server。
- 模板复制完成后从归档重新读取 `system.reg` 与 `user.reg`，确认有效注册表头与 `#arch=win64` 后才提交安装。
- 指纹不匹配的其他 Wine 10.10 构建仍使用通用 winecfg + `wineserver -k -w` + 注册表校验流程，不会错误套用该模板。
- 安装完成回调显式切回 Android 主线程，再关闭进度框、显示错误或重启 Settings。

直装修订测试 APK：

```text
../../artifacts/apks/wine11/app-debug-wine11-wine10-direct-install.apk
SHA-256 AC50B87395745A142476517F5ED60C9441B1100E3AF22607C07748ADE21061AA
```

### 6.6 Wine 10.10 成功基线与 Wine 版本回归定界

直装修订版在真机上安装 Wine 10.10 成功，新建容器可正常进入桌面，并成功启动与 Wine 11 测试相同的 E 盘 Stardew Valley 1.5.6。成功日志为 `logs/stardew-valley/logs_stardew_wine10_clean.txt`，长度 652860 字节，SHA-256 为 `EFBEF935E9839C043BC9C10C1A42895D7EFB0E1681331C3B1159D8DC1ED07BDC`。

日志对照结果：

- Wine 10.10 于 12:45:26 启动游戏，12:46:05 进入 WGL context flush，12:46:19 首次进入 `x11drv_swap_buffers`。日志期间共有 215 次 swap、2206 次 context flush，到 12:48:10 仍在持续提交画面。
- Wine 11 于 18:30:08 启动同一游戏，18:30:47 开始 surface flush，到日志结束仅有 55 次 `x11drv_surface_flush`、51 次 context flush，始终为 0 次 `win32u_wglSwapBuffers` 和 0 次 `x11drv_surface_swap`。
- 成功的 Wine 10.10 日志同样有 16 条 `e0434352`、8 条 `e06d7363`，数量与 Wine 11 完全一致，且后续正常出帧；这两类已捕获异常可确定为非致命探测路径。
- Wine 10.10 成功路径里 `set_swap_interval` 警告出现 2420 次，因此该警告也不是黑屏原因。
- Wine 11 日志末尾有一条 `0058` 线程的 `handle_syscall_fault c0000005`，而 Stardew 主线程标识为 `00dc`；它不是游戏主线程的未处理异常，不能用来解释游戏黑屏。
- Wine 10.10 在首帧前继续加载 `crypt32`、`netapi32`、`secur32`、`kerberos` 等组件，Wine 11 日志则停在 `dnsapi` / `ws2_32` 附近。这是一个可追踪的分歧点，但当前日志通道不足以判定是网络调用阻塞还是其他初始化状态未满足。

该对照已同时排除游戏文件、E 盘挂载、Turnip/Zink、Box64 0.4.3 Stability 预设、旧 prefix 残留和 Steam 网络开关为主因。结论从“Wine 11 环境中的游戏兼容问题”收窄为“Wine 10.10 到 11.0 之间引入的版本回归”。Wine 11 的 WGL 探针可正常 swap，而 Stardew 在调用首次 SwapBuffers 之前就停住，所以现阶段不应直接将根因写成 X11 的 swap 实现错误。

下一阶段停止叠加 ICU、Box64 和窗口环境变量，改用 Wine 官方发布点做版本二分。优先构建 10.15、10.18、10.20 和 11.0-rc3，每个版本仅应用 Android `server_dir` 双侧补丁和现有编译约束，通过可选 Wine 容器运行同一份游戏。按结果继续缩小到首个失效的 Wine 发布点，再对该区间的 OpenGL/WGL、win32u/window 和 winsock/dns 变更做源码级二分。

### 6.7 Wine 10.18 二分包

首个中间版本选择 Wine 10.18，官方 tag 提交为 `1e998672`。独立源码工作树只修改 `dlls/ntdll/unix/server.c` 和 `server/request.c`，双侧强制使用 `<WINEPREFIX>/.wineserver`。可复用补丁已保存为 `wine_patches/winlator-server-dir.patch`。

构建与验证：

- configure 参数与 Wine 11 保持一致：`--enable-archs=i386,x86_64 --disable-tests --without-oss`；已确认产生 i386/x86_64 WoW64 目录，`SONAME_LIBVULKAN` 为 `libvulkan.so.1`。
- 首次 `make -j12` 因外层命令达到一小时超时被中止，日志无编译错误；保留增量对象后续跑 `make -j12` 成功，末尾为 `Wine build complete.`。
- `make install` 后体积约 1.5 GB。统一 strip 了 808 个 PE32、758 个 PE32+ 和 38 个 ELF，体积降至 470 MB；之后才排除 headers/manpages 生成 408 MB 运行时 staging。
- staging 包含 2511 个普通文件和 14 个符号链接；`i386-windows` 1058 个文件、`x86_64-windows` 1007 个文件、`x86_64-unix` 278 个文件。Wine Vulkan、OpenGL、X11、wined3d 以及 dnsapi/ws2_32/winhttp/crypt32/secur32 均保留。
- WSL 干净 prefix 的 `wineboot -i` 以 0 退出，实际创建 `.wineserver/server-*`，`system.reg` 和 `user.reg` 头均有 `#arch=win64`。后续无 X Server 的 `wineboot -u` 超过 10 分钟未退出，已终止该验证 wineserver，未将部分更新的 WSL prefix 打入安装包。
- 新 WoW64 prefix 的 `syswow64` 目录可为空；直接运行 `i386-windows/cmd.exe` 成功输出 `32BIT_OK` 并以 0 退出，证明 32 位 PE 从 Wine 的 `i386-windows` 目录加载，不是 i386 构建缺失。
- XZ 完整性、归档路径安全和解包后逐文件 `diff` 均通过；归档不含绝对路径、`..`、headers 或 manpages。

真机二分包：

```text
../../artifacts/wine-packages/wine-10.18-winlator-custom-addon.tar.xz
Size 56521976 bytes
SHA-256 C8DE32911703B8A91FBCB222D04F9153D0D67F35BA1C8FA6EC4D480AFBDA200A
```

该包在当前直装修订 APK 中不匹配 Wine 10.10 专用指纹，因此会故意走通用 `winecfg -> wineserver -k -w -> prefix 校验` 路径。安装成功后必须新建 `Wine Version = Wine 10.18` 容器，保持与 10.10 成功基线相同的 Zink/Turnip、Box64 Stability 和游戏文件，不添加环境变量。

