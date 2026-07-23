# 项目2（组件编译篇）：VKD3D-Proton 交叉编译与升级 2.14.1 → 3.0.1

> 记录把 D3D12→Vulkan 转换层 VKD3D-Proton 从 winlator 内置的 2.14.1 升级到上游最新 3.0.1 的过程。因为 DXVK 已把 Windows llvm-mingw 工具链跑通，本组件复用同一套流程，一次成功、无新坑。

## 一、VKD3D-Proton 简介

- 作用：把 Windows **Direct3D 12** 调用转换为 Vulkan（DXVK 管 D3D9/10/11，VKD3D 管 D3D12）。
- 仓库：`HansKristian-Work/vkd3d-proton`（与 wine 自带的 vkd3d 不同，这是 Proton 分支，游戏兼容性更好）。
- 同为 Windows DLL，用 MinGW-w64 交叉编译。

## 二、目标与结构

- 版本：2.14.1 → **3.0.1**（`DefaultVersion.VKD3D`）。
- tzst 包结构（对照原 vkd3d-2.14.1.tzst）：`system32/`(64位) + `syswow64/`(32位)，各含 **`d3d12.dll` + `d3d12core.dll`**（比 DXVK 少，只有 D3D12 两个）。

## 三、与 DXVK 编译的差异点

复用 DXVK 的 Windows llvm-mingw + meson + glslang 工具链，但 VKD3D 有两个额外注意点：

1. **需要 `widl`（IDL 编译器）**：VKD3D 的 cross file 有 `widl-mingw-tools-fallback` 字段，用于编译 D3D12 的 IDL 接口定义。**llvm-mingw 自带 `x86_64-w64-mingw32-widl.exe`**，满足需求（meson.build:73 `find_program('widl', required:false)`，找不到会用 cross file 的 fallback）。
2. **嵌套子模块 `dxil-spirv`**：VKD3D 3.x 依赖 dxil-spirv（DXIL→SPIR-V，用于 DX12 shader）。`--recurse-submodules` 首次没拉全（只有 .git），需 `git submodule update --init --recursive` 补全（2199 文件）。

## 四、编译步骤

1. 克隆 + 递归子模块：
```
git clone --depth 1 --branch v3.0.1 --recurse-submodules https://github.com/HansKristian-Work/vkd3d-proton
git submodule update --init --recursive   # 确保 dxil-spirv 拉全
```
2. cross file（`build-win64-llvm.txt` / `build-win32-llvm.txt`）指向 llvm-mingw clang，**额外加 widl 行**：
```
[binaries]
c = '.../x86_64-w64-mingw32-clang.exe'
cpp = '.../x86_64-w64-mingw32-clang++.exe'
ar = '.../llvm-ar.exe'
strip = '.../llvm-strip.exe'
widl-mingw-tools-fallback = '.../x86_64-w64-mingw32-widl.exe'
[host_machine]
system = 'windows'
cpu_family = 'x86_64'   # win32 版为 'x86', 用 i686-* 工具
```
3. 编译（PATH 含 llvm-mingw bin + VulkanSDK Bin + ninja）：
```
meson setup build.win64 --cross-file build-win64-llvm.txt --buildtype release
ninja -C build.win64
# win32 同理
```

## 五、产物与打包

win64 + win32 各 2 个 DLL：

| DLL | win64 | win32 |
|---|---|---|
| d3d12 | 0.43MB | 0.37MB |
| d3d12core | 6.00MB | 5.67MB |

python zstandard 按 `system32/`(win64) + `syswow64/`(win32) 打包成 `vkd3d-3.0.1.tzst`（3.33MB）。

## 六、集成

1. `vkd3d-3.0.1.tzst` 放入 `app/src/main/assets/dxwrapper/`，删旧 `vkd3d-2.14.1.tzst`。
2. 改 `DefaultVersion.VKD3D` 2.14.1 → 3.0.1。
3. 无硬编码残留。
4. APK 重编成功（161.6MB），含 vkd3d-3.0.1.tzst。

## 七、验证

- 编译打包：BUILD SUCCESSFUL。
- 待真机验证：容器 VKD3D 设为 3.0.1，跑 D3D12 程序，DXVK/vkd3d 日志正常、图形正确。

## 八、小结

VKD3D 是继 DXVK 之后第二个成功升级的 Windows DLL 组件，**证明 Windows llvm-mingw 工具链方案可复用于所有 DLL 类组件**。相比 DXVK，只多了 widl（llvm-mingw 自带）和 dxil-spirv 子模块两个点，无新的工具链障碍，一次编译通过。后续若还有 DLL 组件（如 d8vk/dxwrapper 类）可沿用此流程。
