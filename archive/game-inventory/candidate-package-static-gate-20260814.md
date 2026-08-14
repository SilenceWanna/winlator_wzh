# 项目3任务1候选游戏静态准入记录（2026-08-14）

本记录只描述本地文件的静态检查结果。检查期间没有运行游戏 EXE，也没有把商业游戏文件或第三方修改组件复制到仓库。

## 结论

| ID | 本地目录 | 静态结论 | 任务1处理 |
|---|---|---|---|
| T1-MGSVGZ | `D:\agent\Winlator\games\MetalGearSolidVGroundZeroes` | 存在 ALI213 Steam 模拟层及相关配置，不是干净发行副本 | `BLOCKED-PACKAGE`，不执行真机测试 |
| T1-SIFU | `D:\agent\Winlator\games\SIFU Deluxe Edition` | 存在 RUNE Steam 模拟配置、重命名原 DLL 和第三方分发标记；当前 Steam API DLL 签名为 `HashMismatch` | `BLOCKED-PACKAGE`，不执行真机测试 |

这两项不能记为 Winlator 启动失败或游戏兼容性失败。Steam API 替换层会改变授权、存档路径、用户身份、网络与覆盖层初始化，足以独立造成黑屏、闪退或存档异常。

## T1-MGSVGZ

- 目录规模：195 个文件，`3,172,889,448` bytes；压缩包 SHA-256：`06F712FC201D2827921E05462372BE22F86343795067F72476E11FF4E0BF12C0`。
- 正式入口：`MgsGroundZeroes.exe`，PE32+ x86-64 GUI，SHA-256：`A5E02DC1B9C81AB2688B21C449B616B7B7ECD08F0978CFA0D04DA5BDEA45F842`。
- 图形路径：入口导入 `dxgi.dll` 与 `d3d11.dll`，符合 64 位 D3D11/Fox Engine 测试目标。
- 包体风险：根目录存在 `SteamConfig.ini`、`ali213.bin`、`ali213\ali213.dll` 和 `Profile\VALVE`；配置包含多种 Steam 模拟存档类型和 Steam stub 处理选项。
- 关键指纹：`SteamConfig.ini` 为 `441953C91E3F3F87ED683CE442079B0CBA133422229C9A9598A792650F2BC9D4`，`ali213.bin` 为 `FF4A5880BC29F2A035DCBCE5CE67C6420680D4A0124CF0007AA221BB7C5D5A83`，`ali213\ali213.dll` 为 `2315268210E82398C640B6967AB45B1BF21C4869F9AE86AA5246DBEB4503A365`。
- 决策：架构和 API 适合作为候选，但当前包未通过来源完整性门槛，不生成首轮 Winlator 配置。

## T1-SIFU

- 目录规模：293 个文件，`31,313,000,338` bytes；压缩包 SHA-256：`1A9E70EDDB40546133696288F15AD7D30C766F11A16323C300915323193C8DF5`。
- 启动入口：根目录 `Sifu.exe` 为 64 位引导程序；实际游戏程序为 `Sifu\Binaries\Win64\Sifu-Win64-Shipping.exe`，PE32+ x86-64 GUI，SHA-256：`E427D1D39E6534C070B844E6CF901D5A7A9AD71B4E521FA5F0227225DEB85CB9`。
- 引擎与图形路径：目录结构为 Unreal Engine 4；Shipping 程序导入 D3D11/D3D12、DXGI、Steamworks、PhysX 与音频组件。
- 包体风险：Steamworks 目录存在 `steam_emu.ini`、`steam_api64.rne` 和替换后的 `steam_api64.dll`；配置含 `RUNE` 存档路径及 `[Crack]` 段，目录中另有第三方分发标记。
- 关键指纹：`steam_emu.ini` 为 `89BAB38C6ED1FD817A6A183CCE8C2A1457F43A32A88A785169220421B3F5B0B9`，`steam_api64.dll` 为 `51425709B07D703F2B675828520C8D38AF2D014F40D175695BC299C32B37F685`，`steam_api64.rne` 为 `728D0D3A8DF8E5BEA3A5FE5BDBE826F2A3F9FB6A397BC8A3AC716C3E2CE9C944`。当前 DLL 的 Authenticode 检查显示 Valve 签名元数据但文件哈希不匹配。
- 决策：引擎覆盖有价值，但当前包未通过来源完整性门槛，不生成首轮 Winlator 配置。

## 恢复准入的条件

1. 使用用户合法账户通过官方客户端重新下载或执行文件完整性校验，并输出到新的干净目录；不要在当前目录上手工删改模拟层文件。
2. 原爆点新目录不得再出现 `ali213*`、模拟层 `SteamConfig.ini` 或 `Profile\VALVE`。
3. 师父新目录不得再出现 `steam_emu.ini`、`steam_api64.rne` 或第三方分发标记；Steam API 文件不得为 `HashMismatch`。
4. 新目录准备好后先重复静态准入。通过后才生成唯一的 Winlator 首轮容器配置并开始真机测试。
