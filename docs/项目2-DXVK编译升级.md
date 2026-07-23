# 项目2（组件编译篇）：DXVK 交叉编译与升级 2.4.1 → 3.0.2

> 记录把 D3D9/10/11→Vulkan 转换层 DXVK 从 winlator 内置的 2.4.1 升级到上游最新 3.0.2 的完整过程。这是项目2 第二个成功升级的组件，也是踩坑最多、最能体现"交叉编译工具链必须自洽"教训的一个。

## 一、DXVK 与 box64 的根本不同

- box64 是 **Linux ELF**（在 rootfs 里跑），用 glibc 交叉编译器（`aarch64-linux-gnu-gcc`）。
- DXVK 是 **Windows DLL**（在 wine 里被加载），必须用 **MinGW-w64 交叉编译器**产出 PE 格式 DLL，且要 32/64 位各一套。
- 构建系统：DXVK 用 **meson + ninja**，还需 **glslangValidator** 编译 shader。

## 二、目标与结构

- 版本：2.4.1（MAJOR_DXVK）→ **3.0.2**。MINOR_DXVK（1.10.3，给老 Vulkan 设备）保留不动。
- tzst 包结构（对照原 dxvk-2.4.1.tzst）：`system32/`=64位 DLL、`syswow64/`=32位 DLL，各含 `d3d8/d3d9/d3d10core/d3d11/dxgi.dll`（注意 Windows 命名怪癖：system32 放 64 位，syswow64 放 32 位）。

## 三、踩坑全过程（工具链地狱）

这是本组件最有价值的记录——**交叉编译的头文件、编译器、库必须来自同一套自洽工具链，任何混搭都会在链接或运行阶段崩**。依次踩了 5 个坑：

1. **WSL 自带 meson 0.61 太老**：DXVK 3.0.2 子项目 dxbc-spirv 要求 meson ≥ 1.0 → pip 升级到 1.11。
2. **WSL mingw-w64 8.0.0 头文件太旧**：缺 `d3d11on12.h`（DXVK 3.x 新需求）→ 编译中断。
3. **补头方案失败（关键教训）**：把 Windows llvm-mingw 的头文件（含 d3d11on12.h）用 `-isystem` 塞给 WSL 的 mingw-w64 gcc → 所有 .cpp 编译通过，但**链接时 `undefined reference to nanosleep64`**。原因：llvm-mingw 的头声明了 `nanosleep64`（其 UCRT 运行时有），但链接用的是 WSL mingw-w64 的库（没这个符号）。**头和库不同源 → 链接失败**。
4. **整套 Windows llvm-mingw 拷进 WSL 失败**：拷过去发现 bin 里是 `.exe`（Windows 二进制），Linux 的 WSL 根本不能执行。
5. **WSL 下载 Linux llvm-mingw 不通**：WSL 能 git clone（github.com），但下载 GitHub release 资产（objects.githubusercontent.com）超时/0 字节。

## 四、成功方案：Windows 原生编译

最终放弃 WSL，改在 **Windows 原生**用完整自洽的工具链编译：
- **编译器**：llvm-mingw（winget 装的 `MartinStorsjo.LLVM-MinGW.UCRT` 20260616），`x86_64-w64-mingw32-clang.exe` / `i686-w64-mingw32-clang.exe`——头+编译器+库同源，无混搭问题。
- **构建**：meson（pip）+ ninja（Android SDK 自带）。
- **shader**：glslangValidator（winget 装 `KhronosGroup.VulkanSDK` 1.4.350，`C:\VulkanSDK\1.4.350.0\Bin`）。

meson cross file（`build-win64-llvm.txt` / `build-win32-llvm.txt`）指向 llvm-mingw 的 clang 工具，关键内容：
```
[binaries]
c = '.../bin/x86_64-w64-mingw32-clang.exe'
cpp = '.../bin/x86_64-w64-mingw32-clang++.exe'
ar = '.../bin/llvm-ar.exe'
strip = '.../bin/llvm-strip.exe'
windres = '.../bin/x86_64-w64-mingw32-windres.exe'
[host_machine]
system = 'windows'
cpu_family = 'x86_64'  # win32 版为 'x86'
```
编译命令（PATH 需含 llvm-mingw bin + VulkanSDK Bin + ninja + meson）：
```
meson setup build.win64 --cross-file build-win64-llvm.txt --buildtype release
ninja -C build.win64
# win32 同理用 build-win32-llvm.txt
```

## 五、产物与打包

win64 + win32 各 5 个 DLL 全部链接成功：

| DLL | win64 | win32 |
|---|---|---|
| d3d8 | 1.96MB | 2.03MB |
| d3d9 | 7.08MB | 7.37MB |
| d3d10core | 0.53MB | 0.54MB |
| d3d11 | 7.77MB | 8.09MB |
| dxgi | 5.33MB | 5.54MB |

用 python zstandard 按 `system32/`(win64) + `syswow64/`(win32) 结构打包成 `dxvk-3.0.2.tzst`（8.55MB）。

## 六、集成

1. `dxvk-3.0.2.tzst` 放入 `app/src/main/assets/dxwrapper/`，删除旧 `dxvk-2.4.1.tzst`（MINOR 版 dxvk-1.10.3 保留）。
2. 改 `DefaultVersion.MAJOR_DXVK` 2.4.1 → 3.0.2。
3. 确认无硬编码 "2.4.1" 残留。
4. APK 重编成功（158.2MB），验证内含 dxvk-3.0.2.tzst。

## 七、验证

- 编译打包：BUILD SUCCESSFUL。
- 待真机验证：容器 DX Wrapper 选 DXVK，启动 D3D 游戏，`Documents/Winlator/logs.txt` 里 DXVK 版本应显示 3.0.2，游戏图形正常。

## 八、总结教训

**交叉编译的黄金法则：头文件 + 编译器 + 运行库必须是同一套工具链，不能混。** 本次绕的所有弯路（补头链接失败、拷 .exe、下载不通）本质都是违反这条法则或环境限制。最终 Windows 原生 llvm-mingw 一套到底才成功。这条经验适用于后续所有 Windows DLL 组件（如 VKD3D）。
