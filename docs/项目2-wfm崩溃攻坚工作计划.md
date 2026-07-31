# 项目2（Wine 攻坚续篇）：wfm.exe 崩溃攻坚工作计划与执行状态

> 目标：让内置 Wine 升级到 11.0 后能正常进桌面。上一轮（见 `项目2-Wine升级10.10-11.0.md`）已确认 wine 11.0 本体可跑（双侧 server_dir 补丁生效、能进桌面），唯一障碍是 brunodev 闭源的桌面文件管理器 `wfm.exe`（为 10.10 编）在 11.0 下崩溃，故先回退了 10.10。本计划规划下一步：先攻方案 B（逆向定位崩溃点做针对性 workaround），打不通则降级方案 A（换 wine 自带开源 shell）。
>
> **状态：已完成第一轮执行（2026-07-24）。** 静态复核推翻了原方案 B1 的 IAT/GetProcAddress 假设；WSL 原生 Wine 11 已证明同一个 wfm.exe 可以持续运行，问题现已收敛到 Android/box64 路径。实现、产物和真机矩阵见 `项目2-Wine11-WFM攻坚执行记录.md`。当前仓库仍保留 Wine 10.10 作为稳定内置版，Wine 11 改为可选安装版。

## 一、已定位的崩溃证据（逆向已取得）

### 1.1 崩溃现象
真机日志：`wine: Unhandled page fault on read access to 0x0 at address 0x14000268A`，随后 `X connection broken`，整个桌面 session 退出。发生在进桌面后、`explorer /desktop=shell` 通过 `winhandler.exe` 拉起 `wfm.exe` 作桌面内容时。

### 1.2 wfm.exe PE 特性
- PE32+ x86-64，**符号已 strip**（调试信息在外部 PDB，不可得）→ 逆向无函数名，靠地址/指令推断。
- **开启 ASLR**（`DllCharacteristics=0x160`：`DYNAMIC_BASE + HIGH_ENTROPY_VA`）。但 winlator 下 box64 用 `WINEPRELOADRESERVE` 把主模块固定在 `ImageBase=0x140000000`（日志 `0x140000000` prereserve 佐证），故本次崩溃地址 `0x14000268A` 的 RVA = `0x268A`，能对上静态反汇编。
- 导入 DLL：ADVAPI32 / COMCTL32 / GDI32 / KERNEL32 / msvcrt / ole32 / SHELL32 / USER32 / **libcdio.dll**（第三方光驱库，随 `rootfs_patches.tzst` 附带 `libcdio.dll`）。
- 来源：`rootfs_patches.tzst` 的 `./home/xuser/.wine/drive_c/windows/wfm.exe`（276992 bytes），`applyGeneralPatches` 首启解压。

### 1.3 崩溃点反汇编（RVA 0x268A 附近）
```asm
140002687:  mov    %rsi,0x1cbb2(%rip)        # 0x14001f240   ; 存 rsi
14000268e:  mov    0x1e46b(%rip),%rsi        # 0x140020b00   ; ← rsi = *[0x140020b00]  (崩溃点)
...
1400026cf:  call   *%rsi                                     ; 调用该指针
...
1400026e1:  call   *0x1e411(%rip)            # 0x140020af8   ; 附近另一间接调用
14000271d:  call   *0x1e40d(%rip)            # 0x140020b30
```
**判读**：`0x140020b00`、`0x140020af8`、`0x140020b30` 是一组**全局函数指针槽**（`.data`，RVA ~0x1f000–0x20000）。wfm 从 `0x140020b00` 读指针到 rsi，稍后 `call *%rsi`。崩溃是**读到 NULL**（对 `0x0` 的读访问）→ 空指针。

**最可能根因**：这组槽是 wfm 启动时用 `GetProcAddress` 从某 DLL 动态解析的 API 入口。某 API 在 wine 11.0 **改名/移除/未导出** → `GetProcAddress` 返回 NULL → wfm 未判空直接用 → 崩溃。候选来源：`libcdio.dll`（第三方，版本敏感）、COMCTL32、SHELL32（wine 11.0 变动较大）。

> **乐观信号**：崩溃是干净的"NULL 指针"而非内存踩踏，说明是**单个 API 解析失败**，非 wfm 整体 ABI 不兼容。若定位到可 override/可替换的 API，workaround 成本可控。

## 二、方案 B：逆向定位 + 针对性 workaround

### 阶段 B1：确定 `0x140020b00` 槽对应哪个 API（关键，决定成败）
1. **反汇编找写入点**：全量反汇编 wfm.exe，搜所有写 `0x140020b00`/`0x140020af8`/`0x140020b30` 的指令（`mov %rax, xxx(%rip)` 目标 RVA 落在这些槽）。找到给这些槽赋值的代码块——通常是一段连续的 `LoadLibrary + GetProcAddress` 序列。
2. **提取 API 名字符串**：`GetProcAddress` 第二参数（API 名）通常是 `.rdata` 字符串，`lea xxx(%rip),%rdx` 指向它。顺赋值点上游找该字符串即 API 名。
3. **确定来源 DLL**：同序列的 `LoadLibrary`/`GetModuleHandle` 参数（DLL 名字符串）确定 API 来自哪个 DLL。
4. 工具：`x86_64-w64-mingw32-objdump -d`（已验证可用）+ `-s -j .rdata` 导字符串段人工比对。无 IDA/Ghidra 时靠 objdump + 交叉引用手工分析。

### 阶段 B2：验证该 API 在 wine 11.0 的状态
1. 确定 API + DLL 后，查 wine 11.0 对应 PE（`x86_64-windows/<dll>`）是否导出：`objdump -p` 看导出表，或 grep wine 源码 `.spec`。
2. 对比内置 10.10 同名 DLL 是否导出——确认 11.0 是否移除/改名。
3. 若来源是 `libcdio.dll`，检查 rootfs 里的 libcdio 版本与 wfm 期望是否匹配。

### 阶段 B3：设计 workaround（按 B2 结论分支）
- **情况① API 改名**：wine 支持 DLL 转发/别名。容器注册表加 DllOverrides，或提供 shim DLL 转发旧名→新名。成本中。
- **情况② API 在 11.0 移除**：提供 shim DLL 实现该 API（若语义简单）。成本中高。
- **情况③ libcdio 版本不匹配**：换 wfm 期望的 libcdio.dll 版本进 rootfs_patches。成本低。**最好的情况**。
- **情况④ wfm 硬依赖 10.10 内部 ABI**（直接调 wine 私有结构）：无法外部绕过 → **判 B 打不通，转方案 A**。

### 阶段 B4：验证
用带补丁 wine 11.0 + workaround 跑 wfm（WSL 或真机），确认进桌面不崩。

### B 的止损判据
B1 投入 **2–3 轮**仍无法确定槽对应 API，或 B2 确认落到情况④ → 判 B 打不通，转 A。

## 三、方案 A：换 wine 自带开源 shell（兜底，低成本高确定性）

### 已确认前提
- wine 11.0 自带 `explorer.exe`(142KB) + `winefile.exe`(200KB)，**与 11.0 同源编译，无版本绑定**。
- shell 仅一处硬编码：`XServerDisplayActivity.java:980` `if (cmdArgs.isEmpty()) cmdArgs = "/dir C:\\windows \"wfm.exe\"";`——**仅纯桌面模式用**，启动游戏走第977行不经过它。

### 步骤
1. **改 shell 入口**：第980行 `wfm.exe` 换成 wine 自带 shell（`explorer.exe` 最接近桌面语义，或 `winefile.exe` 接近文件管理定位）。需实测哪个在 `winhandler.exe /dir ...` 链下正常。
2. **确认 winhandler 兼容**：`winhandler.exe` 负责把 shell 作桌面内容拉起，需确认它对 explorer/winefile 的参数传递正常；若它也硬绑 wfm 行为，可能需直接改启动命令绕过 winhandler。
3. **重集成 wine 11.0**：复用上一轮 WSL 编好的带补丁 wine 11.0（`$HOME/wine-install`，已 strip）——重打包 rootfs + 重生成 container_pattern（脚本都在），改版本常量（`MAIN_WINE_VERSION=11.0`、`LATEST_VERSION` 提到 22 覆盖回退版的 21）。
4. **真机验证**：进桌面（显示 explorer/winefile）→ 星露谷 → d3d9/11/12。
5. **通过后 push**。

### A 的代价与风险
- **代价**：失去 wfm 的 winlator 特色集成（右键 7-Zip/GPUInfo/TestD3D、光驱）。桌面文件管理器变通用款。
- **风险**：winlator 的桌面图标/快捷方式系统可能部分依赖 wfm 行为；explorer/winefile 作桌面 shell 时交互（双击启动 .desktop、右键菜单）可能与预期不符。需真机确认至少能启动游戏快捷方式。
- **确定性**：比 B 高得多——不涉闭源逆向，纯配置/替换 + 同源组件。

## 四、执行顺序与决策树

```
B1 定位 0x140020b00 对应 API
  ├─ 2-3 轮内定位成功 → B2 查 11.0 状态
  │    ├─ 情况①②③(可 override/shim/换库) → B3 workaround → B4 验证 → 成功则保留 wfm
  │    └─ 情况④(硬依赖 10.10 ABI) → 转方案 A
  └─ 2-3 轮定位失败 → 转方案 A

方案 A: 改 shell 入口 → 集成 wine 11.0 → 真机验证 → push
```

## 五、执行前待确认
1. B1 逆向若 objdump 手工分析太慢，是否允许装 Ghidra/radare2 到 WSL 加速？
2. 方案 A 若 explorer/winefile 作桌面 shell 交互不理想（如无法双击启动快捷方式），是否接受"桌面仅用于启动游戏、文件管理弱化"的降级？
3. 最终是"wine 11.0 + 开源 shell"与"wine 10.10 稳定版"双版本并存，还是 11.0 验证通过就完全替换？
