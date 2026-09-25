# DLsiteSound Rootless Subtitle Patcher

面向 Android 的 **无 Root DLsiteSound 字幕补丁器**。

它基于 [JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch) v1.2 的补丁引擎，在用户自己的手机上读取已经安装的 DLsiteSound（`base.apk + split APKs`），以 **Integrated mode** 嵌入 [DLsiteSoundFloatingSubtitle](https://github.com/ariinyume/DLSiteSoundFloatingSubtitle) v2.1.0，然后交给 Android 安装器安装。

> **本项目不包含、不下载、不分发 DLsiteSound APK。** 用户必须自行从官方渠道安装 DLsiteSound。本项目仅对用户设备上已有的 APK 副本进行本地修补。

## 已验证组合

- DLsiteSound: **2.19.0 (573)**
- LSPatch: **v1.2 / build 487**
- DLsiteFloat: **v2.1.0**
- Android: LSPatch 要求 Android 9+
- 模式: Integrated
- Signature bypass: Level 2

其他 DLsiteSound 版本会显示“未验证”，默认仍允许高级用户尝试。

## 用户使用流程

1. 从官方渠道安装 DLsiteSound。
2. 安装本项目构建出的 `DLsiteSound-Rootless-Patcher.apk`。
3. 打开补丁器，确认它检测到了 DLsiteSound 以及完整 split 数量。
4. 点 **“生成无 Root 字幕版”**。
5. 补丁完成后点 **“安装修补版”**。
6. 如果提示签名冲突，补丁器会明确提示：卸载官方版会清除 DLsiteSound 本地数据。用户确认后才继续卸载和安装。
7. 安装完成后，在补丁器中打开 DLsiteSound 的“显示在其他应用上层”权限，然后启动 DLsiteSound 测试字幕。

无 Root 模式只修补 DLsiteSound 本身，因此支持 **悬浮字幕**；需要 Hook `com.android.systemui` 的状态栏字幕不属于本项目目标。

## 构建

本仓库采用“overlay + 固定上游版本”的方式，避免把第三方源码手工复制一份后长期漂移。

```bash
./scripts/prepare.sh
cd work/LSPatch
./gradlew :manager:buildRelease
```

`prepare.sh` 会：

1. 克隆 LSPatch **固定 commit `0dc50f42503711b14f5e2bb217c2bdd6321ce5be`**（v1.2）。
2. 初始化其 submodules。
3. 覆盖本项目的 Quick Patcher UI。
4. 下载 DLsiteFloat v2.1.0，并校验 SHA-256。
5. 修改 Manager 的 launcher 与 applicationId，使其成为独立补丁器。

也可以直接运行 GitHub Actions 中的 **Build APK**。

## 稳定签名

CI 在没有签名 Secrets 时可以生成测试 APK，但不同 CI 运行生成的默认 debug 签名不适合作为长期更新渠道。

公开发布稳定版本前，在 GitHub Repository Secrets 配置：

- `KEY_STORE`：JKS 文件的 Base64
- `KEY_STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

然后用 Release workflow 构建。请永久保存这把发布密钥，否则未来版本无法覆盖升级。

## 为什么不直接发布“修补好的 DLsiteSound.apks”

因为那会重新分发 DLsiteSound 自身的 APK。这个项目只发布补丁器和开源源码，不携带 DLsiteSound 的专有程序文件。

## 开源许可

本项目为 GPL-3.0。LSPatch 与 DLsiteFloat 也均为 GPL-3.0。详见 `THIRD_PARTY.md`。

GitHub Actions 同时上传 **combined-source**，包含实际参与该次 APK 构建的 LSPatch 完整源码（含 submodules）、DLsiteFloat v2.1.0 源码、overlay 和许可证，方便满足对应源码分发要求。
