# Third-party notices

## LSPatch

- Project: JingMatrix/LSPatch
- Upstream tag: v1.2
- Pinned commit: `0dc50f42503711b14f5e2bb217c2bdd6321ce5be`
- License: GNU GPL v3
- Source: https://github.com/JingMatrix/LSPatch

本项目对 LSPatch Manager 增加了单用途 Quick Patcher UI，并修改 launcher/applicationId。发布二进制时同时提供 combined-source。

## DLsiteFloat

- Project: ariinyume/DLSiteSoundFloatingSubtitle
- Upstream version: v2.1.2
- Pinned commit: `bfe938cee6016791b2ae34fb8269208a6713017b`
- License: GNU GPL v3
- Source: https://github.com/ariinyume/DLSiteSoundFloatingSubtitle/tree/v2.1.2

构建时从固定上游源码编译，不再下载官方 Release APK。本仓库通过 `dlsitefloat-overlay/` 与
`scripts/patch_dlsitesfloat.py` 增加 Rootless Controls v4（播放/暂停、seek、上下轨、进度条、
自动隐藏控制栏、窗口锁定、几何持久化、黑屏防误触、控制柄避开歌词和本地 LRC 字幕导入）。combined-source 包含实际参与构建的修改后源码。

网络适配回移自 [ariinyume/DLsiteFloat v2.4.4](https://github.com/ariinyume/DLsiteFloat/tree/v2.4.4)，
固定 commit `b339c65a8ec674cfa2c6aa6834d15a99957fad3a`，GPL-3.0。
`NetworkHook.java` 和 `Shape.java` 保留上游版权声明；回移脚本为 `scripts/import_upstream_network.py`。
本增强版未接入上游的新设置页、缓存预取与作品键缓存 API，保留原有本地 LRC 优先规则。

## DLsiteSound

DLsiteSound 属于其权利人。本项目**不包含、不镜像、不下载、不分发** DLsiteSound APK。补丁器仅读取用户设备上已经合法安装的副本并在本地生成修补版本。
