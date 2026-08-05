# Winlator 工作归档

本目录保存项目 1/2/3 研发过程中的可复现材料，服务于跨主机继续开发和研究。源码仍位于仓库本身；`winlator-main`、`winlator_wzh_new` 两个工作目录不复制到这里。

## 目录

- `project-documents/`: 练习项目文档和工作区布局说明。
- `build-logs/`: Android、组件和 WSL 构建日志。
- `runtime-logs/`: 真机图形、Stardew Valley 和 Dave the Diver 运行日志。
- `test/`: 图形探针和游戏测试文件。
- `artifacts/wine-packages/`: Wine 10/11 基线、失败对照和最终补丁包。
- `artifacts/box64/`: Box64 0.4.5-dev 安装包归档。
- `artifacts/SHA256SUMS.txt`: 本归档文件的 SHA-256 清单。

## 未纳入 Git 的材料

- `games/`：完整商业游戏文件和未完成下载文件，约 10 GB，不是构建依赖。
- `work-cache/`：临时解包目录、嵌套源码 clone 和中间产物，可由源码、补丁和日志重建。
- `.idea/`：本机 IDE 配置。
- `artifacts/source-archives/winlator-main.zip`：被排除源码仓库的重复压缩包。
- `artifacts/apks/`：历史 APK 约 2.9 GB，且单个文件超过 GitHub 普通 Git 限制。最终 APK 的路径、大小和哈希记录在项目文档中，适合作为远端 Release 附件而不是 Git 对象。

## 当前推荐产物

```text
APK: app-debug-wine11-default-box64-0.4.5-dev.apk
SHA-256: DD06BB1676D1AEF1DA6E3CFD778CF4C716C8CEFA4C433398BA84CC545C02809D

Wine addon: wine-11.0-final-nsiproxy-nullguard-winlator-custom-addon.tar.xz
SHA-256: A81595373E6C3FDAC8C33BEAE8BB1FDB7B7744DA63AF094E6BBECF2BD4A72765
```
