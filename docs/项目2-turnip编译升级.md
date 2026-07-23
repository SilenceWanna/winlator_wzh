# 项目2（组件编译篇）：turnip 交叉编译与升级 26.1.0 → 26.1.5

> 记录把 Adreno GPU 的开源 Vulkan 驱动 turnip（Mesa `libvulkan_freedreno.so`）从 26.1.0 升级到 26.1.5 的过程。这是项目2 **最复杂**的组件——依赖链最长、踩坑最多。

## 一、关键发现：内置 turnip 是 glibc/X11 构建，不是 Android/Bionic

解包内置 `turnip-26.1.0.tzst` 并 `readelf` 其 `.so`，NEEDED 依赖是 **glibc + X11**：`libc.so.6`、`libstdc++.so.6`、`ld-linux-aarch64.so.1`、`libdrm.so.2`、`libxcb-*`、`libX11-xcb`。ICD 的 `library_path` 指向 `/data/data/com.winlator/files/rootfs/lib/libvulkan_freedreno.so`（glibc rootfs 内）。

**这意味着不能用 Android NDK 编**（NDK 产出 Bionic `.so`，在 glibc rootfs 里跑不了——与 box64 的 ANDROID/ANDROID_GLIBC 同类陷阱）。必须用 **aarch64 glibc 交叉工具链 + platform=x11**。

## 二、包结构

`system32`/`syswow64` 不适用（这是 Linux `.so`）。结构：
- `./usr/lib/libvulkan_freedreno.so`
- `./usr/share/vulkan/icd.d/freedreno_icd.aarch64.json`（`library_path` 必须改成 winlator rootfs 路径）

## 三、依赖链（逐层踩坑，这是本组件的核心记录）

turnip 编译需要一整套 host 工具 + arm64 交叉 sysroot，依次解决了 6 个障碍：

1. **arm64 交叉 sysroot**：WSL Ubuntu 22.04（amd64）`dpkg --add-architecture arm64` + 加 `ports.ubuntu.com` 的 arm64 源（现有源限定 `[arch=amd64]`，否则 apt 报错）+ 装 `libdrm-dev:arm64 libxcb*-dev:arm64 libx11-*-dev:arm64` 等。
2. **g++ 交叉**：初次只装了 gcc 交叉，Mesa 需 C++ → 装 `g++-aarch64-linux-gnu`。
3. **glslang ≥ 12.2**：turnip 强制 `with_bvh`（freedreno vulkan 隐含），需 glslang 编内部 shader。WSL 仓库版 11.8 太旧 → 从源码编 glslang 15.1.0（还得先 `pip install --upgrade cmake` 到 4.x，因 glslang 15 需新 cmake）。
4. **flex/bison**：Mesa 词法/语法生成需要 → `apt install flex bison`。
5. **pkg-config（交叉）**：meson 找 arm64 的 xcb/drm 需要 `aarch64-linux-gnu-pkg-config` → `apt install pkg-config`。
6. **编译器版本 + 架构冲突（最麻烦）**：
   - gcc-11（jammy 自带 amd64→aarch64 交叉）编 Mesa 26.1 的 C++ 报 `std::pair incomplete type`——**gcc 11 太旧**。
   - 想换 gcc-13，但 Ubuntu 22.04 **没有 amd64→aarch64 的 gcc-13 交叉版**（PPA 只有 arm64 原生版，装上是 arm64 二进制，x86_64 上 `not executable`）。
   - **解法：改用 clang**。clang 单一 host 二进制、`--target=aarch64-linux-gnu` 交叉，无 gcc 版本/架构问题，Mesa 官方支持。连 `ar`/`strip` 也换成 **llvm-ar/llvm-strip**（因为 `aarch64-linux-gnu-ar` 也被 arm64 变体污染），组成**全 llvm 自洽工具链**。

## 四、成功配方

```bash
# clang cross file (cross-aarch64-clang.txt)
[binaries]
c = ['clang', '--target=aarch64-linux-gnu']
cpp = ['clang++', '--target=aarch64-linux-gnu']
ar = 'llvm-ar'
strip = 'llvm-strip'
ld = 'ld.lld'
pkg-config = 'aarch64-linux-gnu-pkg-config'
[built-in options]
c_args = ['--target=aarch64-linux-gnu']
cpp_args = ['--target=aarch64-linux-gnu']
c_link_args = ['--target=aarch64-linux-gnu', '-fuse-ld=lld']
cpp_link_args = ['--target=aarch64-linux-gnu', '-fuse-ld=lld']
[host_machine]
system='linux'; cpu_family='aarch64'; cpu='armv8-a'; endian='little'

# meson: turnip-only glibc/x11 + kgsl(Android Adreno)
meson setup build-turnip --cross-file cross-aarch64-clang.txt \
  -Dplatforms=x11 -Dgallium-drivers= -Dvulkan-drivers=freedreno \
  -Dfreedreno-kmds=kgsl -Dbuildtype=release -Db_ndebug=true
ninja -C build-turnip
```
关键选项：`-Dvulkan-drivers=freedreno`（turnip）、`-Dfreedreno-kmds=kgsl`（Android Adreno 用 KGSL 内核接口，非 msm/drm）、`-Dplatforms=x11`（匹配内置 glibc/X11 变体）、`-Dgallium-drivers=`（空，不编 GL）。

## 五、产物、打包、集成

- 产物 `libvulkan_freedreno.so`（15.4MB → llvm-strip 后 14MB），`readelf` 确认 glibc/AArch64，依赖与内置一致（少了 libdrm，因 kgsl 不需要）。
- 打包 `turnip-26.1.5.tzst`（1.79MB）：`./usr/lib/libvulkan_freedreno.so` + ICD json（`library_path` 改为 `/data/data/com.winlator/files/rootfs/lib/libvulkan_freedreno.so`，文件名 `freedreno_icd.aarch64.json`）。
- 放入 `app/src/main/assets/graphics_driver/`，删 26.1.0，改 `DefaultVersion.TURNIP=26.1.5`。APK 重编成功（161.6MB）。

## 六、验证与关键修复（缺依赖导致 INCOMPATIBLE_DRIVER）

首次真机测试：桌面能开，但**游戏无法启动**，日志 `MESA: error: ZINK: vkCreateInstance failed (VK_ERROR_INCOMPATIBLE_DRIVER)`、`wine_vkCreateInstance res=-9`，D3D9/11/12 全部图形初始化失败。

**逐项排除**（对比内置能跑的 26.1.0 与我编的 26.1.5）：
- ICD 协商符号（`vk_icdNegotiateLoaderICDInterfaceVersion` 等）：**两者一致**，非符号问题。
- glibc：rootfs 有 2.39 ≥ 我需的 2.34。
- libstdc++：rootfs 有 `GLIBCXX_3.4.32` ≥ 我需的 3.4.29。
- api_version：改回 1.4.318 仍失败——非此因。

**真正根因**：`INCOMPATIBLE_DRIVER` 的本质是 **loader 无法成功 dlopen 驱动 .so**（Vulkan-Loader 规则 LDP_LOADER_1：加载驱动失败即返回此错）。对比 NEEDED 依赖发现——**我编的 26.1.5 依赖 `libxcb-xfixes.so.0`（内置 26.1.0 不依赖），而 winlator rootfs 里没有这个库** → dlopen 失败 → loader 拒绝。

**修复**：把 arm64 版 `libxcb-xfixes.so.0`（30KB）一并打进 turnip 的 tzst（`./usr/lib/`），解压到 rootfs 后依赖即满足。

**验证结果（通过）**：重编 APK 后真机测试——**游戏正常打开**，d3d9（`Direct3DCreate9 ok`/`CreateDevice ok`）、d3d11（device+RTV ok）、d3d12（`CreateDXGIFactory1 ok`→`D3D12CreateDevice ok`→`CreateCommandQueue ok`→`done`）全部成功，主日志零 vulkan 错误。turnip 成功升级到 **26.1.5**。

## 七、教训

- **turnip 的 glibc/X11 属性**决定了必须走 glibc 交叉而非 NDK——先 `readelf` 内置产物确认目标类型，再选工具链（与 box64 同一原则）。
- **Mesa 26.1 需要较新编译器**：gcc 11 不行，而 22.04 无 gcc-13 交叉 → **clang 是跨版本交叉的万能解**（单二进制 + --target）。
- **arm64 外部架构会污染 binutils**（ar/strip/readelf 被换成 arm64 版）→ 用 llvm 系工具（llvm-ar/strip/readelf）规避。
- **运行时依赖必须齐全**：新版组件可能引入内置版没有的 NEEDED 库（本次 `libxcb-xfixes.so.0`），rootfs 缺则 dlopen 失败、报 `INCOMPATIBLE_DRIVER`（看似 loader 问题，实为缺依赖）。**排查法**：`readelf -d` 对比新旧产物的 NEEDED 差集，rootfs 缺的库要么打进包、要么去掉该依赖。`VK_LOADER_DEBUG=all` 可打印 loader 的确切拒绝原因。
