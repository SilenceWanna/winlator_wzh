# Winlator 工作归档

本目录保存项目 1/2/3 研发过程中的可复现材料，服务于跨主机继续开发和研究。源码仍位于仓库本身；`winlator-main`、`winlator_wzh_new` 两个工作目录不复制到这里。

## 目录

- `project-documents/`: 练习项目文档和工作区布局说明。
- `build-logs/`: Android、组件和 WSL 构建日志。
- `runtime-logs/`: 真机图形、Stardew Valley 和 Dave the Diver 运行日志。
- `log-imports/`: 按日期保存的增量日志导入；每个快照包含原始路径、大小、SHA-256 和归档位置清单。
- `game-inventory/`: 本地游戏目录的非分发清单；只记录顶层条目、文件数、大小和本地压缩包校验值，不包含游戏数据。
- `test/`: 图形探针和游戏测试文件。
- `artifacts/wine-packages/`: Wine 10/11 基线、失败对照和最终补丁包。
- `artifacts/box64/`: Box64 0.4.5-dev 安装包归档。
- `artifacts/SHA256SUMS.txt`: 本归档文件的 SHA-256 清单。

## 未纳入 Git 的材料

- `games/`：完整商业游戏文件和未完成下载文件，约 10 GB，不是构建依赖，也不作为本开源研究仓库的可分发内容。只归档文件清单、校验值、运行配置和测试结论。
- `work-cache/`：临时解包目录、嵌套源码 clone 和中间产物，可由源码、补丁和日志重建。
- `.idea/`：本机 IDE 配置。
- `artifacts/source-archives/winlator-main.zip`：被排除源码仓库的重复压缩包。
- `artifacts/apks/`：历史 APK 约 2.9 GB，且单个文件超过 GitHub 普通 Git 限制。最终 APK 的路径、大小和哈希记录在项目文档中，适合作为远端 Release 附件而不是 Git 对象。

## 日志归档规则

1. 用户导出新日志后，先分析并使用唯一文件名保留原始证据，不覆盖此前的失败或成功样本。
2. 在仓库根目录运行 `powershell -ExecutionPolicy Bypass -File scripts/archive-development-logs.ps1 -SnapshotId <唯一节点名>`。脚本扫描工作区的 `logs/`、`build-logs/` 和 `test/` 日志，检查常见凭据与 GitHub 单文件上限风险。
3. 已按内容保存过的文件只在 `MANIFEST.csv` 中引用已有归档；尚未保存的内容复制到 `archive/log-imports/<节点名>/`。同一轮中即使内容相同，不同原始路径也保留各自记录。
4. 提交前检查生成清单、`git diff --check` 和远程差异；日志、分析结论与下一步计划在同一个关键节点提交并推送到 `origin/main`。
5. 日志若包含令牌、密码、个人路径中的隐私数据或其他敏感内容，必须先停止归档并人工核查，不得绕过扫描直接上传。

## 当前推荐产物

```text
APK: app-debug-wine11-default-box64-0.4.5-dev.apk
SHA-256: DD06BB1676D1AEF1DA6E3CFD778CF4C716C8CEFA4C433398BA84CC545C02809D

Wine addon: wine-11.0-final-nsiproxy-nullguard-winlator-custom-addon.tar.xz
SHA-256: A81595373E6C3FDAC8C33BEAE8BB1FDB7B7744DA63AF094E6BBECF2BD4A72765
```
