# 项目2（组件升级篇）：内置 Wine 升级 10.10 → 11.0（尝试与回退）

> 记录把 Winlator 内置主 Wine 从 `10.10-custom` 升级到 `11.0` 的全过程。Wine 是所有组件里**耦合面最广**、也是**唯一一个不能靠换预编译包解决、必须改源码重编**的组件。本次经历了"换 Kron4ek 预编译包→真机开机失败→源码定位强绑定→双侧打补丁重编→进桌面但 wfm.exe 崩溃→回退"的完整曲折。

## ⚠️ 最终结论：技术上编译成功，但因 wfm.exe 强绑定而回退到 10.10

wine 11.0 本身**成功编译、打补丁、能启动**（进了桌面），但 winlator 的桌面文件管理器 **`wfm.exe`（brunodev 闭源、为 wine 10.10 编译）在 wine 11.0 下空指针崩溃**（`Unhandled page fault on read access to 0x0 at 0x14000268A`），导致进桌面后整个 X session 断开。

- `wfm.exe` 来自 `rootfs_patches.tzst`（`applyGeneralPatches` 首启解压），是 winlator 默认桌面 shell，无源码、无法重编。
- 排除了 libXinerama 缺失（10.10 也缺、照跑）。
- **决策**：wine 升级到 11.0 与 brunodev 闭源桌面 shell 强绑定不兼容，收益（版本号）不值得硬碰。**回退内置 10.10**，其余四个组件（box64/DXVK/VKD3D/turnip）的升级保留。
- **回退操作**：三资产从备份恢复（rootfs/container_pattern/common_dlls），`WineInfo.MAIN_WINE_VERSION=10.10`，`RootFSInstaller.LATEST_VERSION` 提到 **21**（必须 > 被污染设备上已写入的 20，否则 `installIfNeeded` 不会重新解压回 10.10），`UPDATE_WINEPREFIX_VERSION=20`。

> **本文档保留完整技术记录**：wine 11.0 的编译方法、双侧 server_dir 补丁、打包坑，对未来重试（如换开源桌面 shell 或 brunodev 出 11.x custom wine 后）仍有价值。下方 §一~§九 是尝试过程的完整记录。

## 零、结论先行（TL;DR）


- 内置 wine 是 **x86_64 WoW64** ELF，由 box64 在手机上翻译执行（不是 aarch64）。
- **不能直接换通用预编译包**（如 Kron4ek）：winlator 是 Android 应用沙箱，根 `/tmp` 不可写，而通用 wine 把 wineserver 的 socket 目录硬编码在 `/tmp/.wine-<uid>` → 开机即 `mkdir /tmp/.wine-xxxx: Permission denied`。
- brunodev 的内置 10.10 是**改过源码的定制版**：把 wineserver 目录改到 rootfs 内可写路径。这就是"强绑定"。
- 修复 = 从 wine 11.0 源码打补丁（**client 端 `ntdll/unix/server.c` + server 端 `server/request.c` 两处都要改**），让 server 目录落在 `WINEPREFIX` 内，然后交叉/原生编译成 x86_64 WoW64。

## 一、第一次尝试：换 Kron4ek 预编译包（失败，但暴露了强绑定）

初始判断：wine 是 x86_64 WoW64 预编译包，换个新版即可。选 Kron4ek `wine-11.0-amd64-wow64.tar.xz`。

做了完整的源码耦合分析和运行时依赖核对（这部分结论仍然有效，见 §三），确认"依赖层面干净可换"，于是：整树替换 `/opt/wine`、重生成 container_pattern、改版本常量、打包 APK。

**真机结果：开机失败。** 日志核心（去噪后）：
```
wineserver: mkdir /tmp/.wine-10492: Permission denied
```
box64/wine/wineserver 全部正常加载（ntdll/libdl/libpthread 都 OK），**不是依赖问题**——是 wineserver 建 socket 目录的路径 `/tmp/.wine-10492` 在 Android 应用沙箱里不可写。

## 二、根因定位：wineserver 目录的"强绑定"（三重验证）

### 2.1 二进制对比
`readelf`/`strings` 对比内置 10.10 与 Kron4ek 11.0 的 wineserver 和 **ntdll.so**：
- **内置 10.10 ntdll.so** 里 server 目录硬编码是 `/data/data/com.winlator/files/rootfs/tmp/.wine-%u/server-%llx-%llx`（rootfs 内，可写）。
- **Kron4ek 11.0 ntdll.so** 是通用构建，硬编码 `/tmp/.wine-%u/server-%llx-%llx`（Android 根 /tmp，不可写）。

→ brunodev 打了 patch 把 server 目录重定向进 rootfs。**这不在 wineserver 二进制里，而在 ntdll（client 端）和 request.c（server 端）**。

### 2.2 wine 源码逻辑（`dlls/ntdll/unix/server.c` 的 `init_server_dir`）
```c
static const char *init_server_dir( dev_t dev, ino_t ino ) {
    char *dir = NULL;
#ifdef __ANDROID__  /* there's no /tmp dir on Android */
    asprintf( &dir, "%s/.wineserver/server-%llx-%llx", config_dir, ...);  // 落在 WINEPREFIX 内
#else
    asprintf( &dir, "/tmp/.wine-%u/server-%llx-%llx", getuid(), ...);      // 落在 /tmp
#endif
    return dir;
}
```
wine **本就有** `__ANDROID__` 分支把 server 目录放进 `config_dir`（=WINEPREFIX），但 Kron4ek 通用构建没启用它。`config_dir` 即 WINEPREFIX（winlator 设在 `<rootfs>/home/xuser/.wine`，可写），并作为 `WINECONFIGDIR` 导出。

### 2.3 为何不能二进制 patch
想直接改 ntdll.so 里的字符串把 `/tmp/...` 换成 rootfs 路径——不行：
- winlator 式绝对路径（66 字节）比原串（30 字节）长，原地替换会溢出覆盖后续数据。
- 想切到 `__ANDROID__` 分支的短格式 `%s/.wineserver/...`（31 字节），但它用 `config_dir`（指针 `%s`）而非 `getuid()`（整数 `%u`），传参类型不同，只改串会崩。

→ **只能改源码重编。**

## 三、依赖与兼容核对（第一次尝试时做的，结论仍有效）

按 box64/turnip 的教训，动手前 `readelf` 对比 wine 11.0 与内置 10.10 的 ELF 依赖：

| 项目 | wine 10.10(内置基线) | wine 11.0 | rootfs 是否满足 |
|---|---|---|---|
| 强制 NEEDED 库 | libc/libm/libgcc_s/libX11/libXext | +`libdl.so.2`、`libpthread.so.0` | 全在 ✅ |
| 最高 GLIBC 符号版本 | — | `GLIBC_2.25` | rootfs 提供到 `2.39` ✅ |
| libstdc++ | — | 不需要 | ✅ |
| 硬编码 DLL（d3d 系列/字体/wordpad/explorer） | — | 全部提供 ✅ | ✅ |
| Vulkan 加载点 | winex11.so dlopen `libvulkan.so.1` | 同左 | rootfs 有 turnip 提供的 loader ✅ |

**依赖层面确实干净**——问题不在依赖，而在 §二 的 server 目录路径。

## 四、源码耦合分析（换 wine 要联动的全部位置）

### 4.1 路径模型（版本无关）
`RootFS.java:20` `winePath="/opt/wine"` 硬编码；启动链 `box64 <winePath>/bin/wine …` 只认单个 WoW64 `bin/wine`。新包解压到 `/opt/wine` 且含 `bin/wine`+`lib/wine/{x86_64-windows,i386-windows,x86_64-unix}` 即可，路径层零改动。

### 4.2 版本常量（已改）
| 常量 | 原值 | 新值 |
|---|---|---|
| `WineInfo.MAIN_WINE_VERSION` | `10.10` | `11.0` |
| `RootFSInstaller.LATEST_VERSION` | `19` | `20` |
| `RootFSInstaller.UPDATE_WINEPREFIX_VERSION` | `16` | `19` |

### 4.3 预生成资产（重新生成）
- **`container_pattern.tzst`**：wineboot 过的 prefix 快照。必须用 11.0 重新 wineboot 生成。
- **`common_dlls.json`**：pattern 与 `/opt/wine` 逐字节相同的 builtin DLL 清单，随 pattern 一起重生成。

### 4.4 运行时自愈（决定 pattern 只需干净启动）
`applyGeneralPatches → WineUtils.applySystemTweaks`（`XServerDisplayActivity:1072`）在 rfsVersion 变化后首启重写 brunodev 定制（d3d DllOverrides/字体/7-Zip/x11 驱动）。所以 pattern 只需干净的 11.0 prefix，app 首启叠加定制。

### 4.5 老容器兼容（无误判）
`Container.saveData:308` 主 wine 容器不写 `wineVersion` 键；`loadData:315` 默认取当前 MAIN。故升级后老容器自动变 `wine-11.0-custom`，经 `wineboot -u` 迁移，不会误判为 add-on wine。

## 五、双侧补丁（本次最关键的教训）

wine 的 server 目录路径在**两处**独立构造，必须**同时**改，否则 client 和 server 各建各的路径，导致 client `chdir` 到 wineserver 没建的目录 → `No such file or directory`。

### 5.1 client 端：`dlls/ntdll/unix/server.c` 的 `init_server_dir`
```c
    /* Winlator patch: no writable /tmp in Android app sandbox; put server dir inside the wineprefix (config_dir) */
    asprintf( &dir, "%s/.wineserver/server-%llx-%llx", config_dir, ...);
```

### 5.2 server 端：`server/request.c` 的 `create_server_dir`（**第一次漏了这处**）
```c
    /* Winlator patch: put server dir inside the wineprefix (config_dir) */
    if (asprintf( &base_dir, "%s/.wineserver", config_dir ) == -1) fatal_error(...);
    create_dir( base_dir, &st2 );   // wineserver 真正建目录的地方
```

**踩坑复盘**：第一次只改了 client（ntdll），漏了 server（request.c 仍走 `/tmp/.wine-%u`）。现象是 wineboot 报 `chdir to <prefix>/.wineserver/server-xxx: No such file or directory`——client 想进 prefix 内的目录，但 wineserver 在 `/tmp` 建（或建失败）。补上 request.c 后，`.wineserver` 正确建在 prefix 内。

## 六、编译（x86_64 WoW64 原生构建）

WSL(Ubuntu 22.04, x86_64) 原生编译（wine x86_64 在 x86_64 host 上非交叉）：
- 工具链：gcc-11、**mingw-w64**（x86_64+i686，编 PE 部分）、flex/bison、freetype/x11/gnutls/pulse dev 库。
- **关键依赖 `libvulkan-dev`**：否则 configure 报 `Vulkan won't be supported`，`SONAME_LIBVULKAN` 宏缺失 → winevulkan 找不到 loader → DXVK/VKD3D 全废。装后 configure 探测到 `SONAME_LIBVULKAN "libvulkan.so.1"`（与 rootfs turnip 提供的 loader 一致）。
- configure：`--enable-archs=i386,x86_64`（新 WoW64 模式，产 `x86_64-unix` + 两个 PE 目录，与 Kron4ek 结构一致）`--disable-tests`。
- 硬校验：configure 后断言 Vulkan 已启用，不通过则中止（避免白编 30 分钟）。
- `make -j12`（约 30 分钟）→ `make install` 到 staging。

### 6.1 strip（体积从 1.5G → 501M）
`make install` 产物带完整调试符号（wined3d.dll 27MB）。strip 是安全的——PE DLL 和 wine 的 unix .so 都是普通 PE/PIE 库，**不是 box64 那种非 PIE 固定地址二进制**（那才不能 strip）。
- PE DLL：按架构用 `x86_64-w64-mingw32-strip` / `i686-w64-mingw32-strip`
- unix .so + bin/ELF：普通 `strip --strip-unneeded`
- strip 后 wined3d.dll 3.1MB（比 Kron4ek 的 4.3MB 还小），补丁字符串仍在（strip 不动 rodata）。

## 七、验证（WSL 内 wineboot 代理真机）

x86_64 wine 在 WSL 原生可跑，用 `wineboot -i` 验证补丁：
- **`.wineserver/server-xxx` 成功建在 WINEPREFIX 内** ✅（补丁核心目标）
- wineboot 正常填充 prefix（587 sys32 + 620 syswow64 dll，1.4G）✅
- 首次超时是因 `wine.inf` 安装（rundll32 setupapi）耗时，给足 300s 后跑完，非崩溃。

## 八、打包与集成

### 8.1 重打包 rootfs.tzst（流式，只换 /opt/wine）
- 流式读原 rootfs，透传除 `/opt/wine/*` 外所有成员（保序/属性 root:root）。
- 追加带补丁的 wine-install 到 `./opt/wine/`，剔除 `include/`(63MB winegcc 头，运行时无用)+`share/man/`。
- **权限修复**：`x86_64-unix/wine` 是无扩展名 loader，第一次规则漏给可执行位（0644）。修正：源文件有 x 位 / bin/ 下 / .so / `wine`/`wine-preloader` 结尾 → 0755。
- zstd level 19，产出 79MB。

### 8.2 pattern + common_dlls
用带补丁 wine 在 WSL `wineboot` 生成，清 `dll*.tmp` 孤儿文件 + `.wineserver` 运行时目录（补丁把 socket 放这，属 host 运行时垃圾），复刻 `generateCompactContainerPattern`。产出 pattern 24MB / 361 文件、common_dlls(sys32=711,syswow64=712，含 wined3d/d3d11)。

### 8.3 集成
三资产入 `app/src/main/assets/`：`rootfs.tzst`(79MB)、`container_pattern.tzst`(24MB)、`common_dlls.json`(23KB)。版本常量按 §4.2 改。重编 APK。

## 九、教训沉淀

1. **不是所有组件都能换预编译包**：wine 与 winlator 的 Android 沙箱**强绑定**在 server 目录路径上。通用 linux 构建（Kron4ek）缺这个定制，必须改源码重编。
2. **强绑定要"同步修改绑定的部分"**：wine 的 server 目录在 client(ntdll) 和 server(request.c) **两处**独立构造，必须同时打补丁，只改一半会 `chdir ENOENT`。这是本次最深的坑。
3. **依赖核对 ≠ 能跑**：第一次 readelf 依赖全过、符号版本达标，仍开机失败——因为问题在运行时路径可写性，不在链接依赖。核对要覆盖"运行时环境假设"（可写目录、sandbox 限制），不只是 NEEDED/符号。
4. **libvulkan-dev 是隐性硬需求**：wine 靠它探测 `SONAME_LIBVULKAN` 编进 winex11，缺了 DXVK/VKD3D 全废——而这是 header/soname 探测，不影响运行时 ABI。
5. **strip 分二进制类型**：普通 PE/PIE 库可安全 strip（官方发布版也 strip）；只有 box64 那种非 PIE 固定地址二进制不能碰。
6. **WSL 是 x86_64 wine 的天然验证台**：x86_64 wine 原生可跑，`wineboot` 建 prefix 成功 = 补丁正确的强信号，不必每次上真机。
7. **打包顺序陷阱（真机第二次失败的根因）**：第一次补 request.c 前就打包了 rootfs，导致 APK 里 ntdll 带补丁、wineserver 却是旧的（无补丁）→ 真机仍 `mkdir /tmp/.wine-xxxx: Permission denied`。**必须在所有补丁 + `make install` 之后才打包**。定位靠"解开 APK 内 rootfs.tzst 直接 `strings` wineserver"——验证真实产物，不信中间态。
8. **`make install` 会覆盖 strip 过的文件**：增量重编后 `make install` 把全部 PE DLL 重新装一遍（未 strip），若只 strip 了个别文件，rootfs 会暴涨（247MB）。正确顺序：**先 `make install`，再全量 strip 整个 install 目录，最后打包**——三步串成一条命令保证顺序。
