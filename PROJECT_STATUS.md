# Project status

当前是 **v0.1 源码原型**，核心做法是直接复用 LSPatch v1.2 的 `ApkPatcher`、split 处理、签名和 `PackageInstaller` 流程，而不是重新实现 patch engine。

## 已完成

- 单用途 Quick Patcher Activity
- 检测 `jp.co.eisys.dlsitesound`
- 自动读取 `sourceDir + splitSourceDirs`
- 对 2.19.0(573) 标记为已验证
- 构造固定的 Integrated PatchRequest
- 自动嵌入 DLsiteFloat v2.1.0
- SHA-256 校验模块 APK
- 调用 LSPatch PatchJobHost 修补/安装
- 对不同签名安装先显示数据清除警告
- Overlay permission 快捷入口
- GitHub Actions 构建
- Release 源码归档
- 阻止源码仓库误带 DLsiteSound APK

## 需要真机验证

由于当前执行环境不能运行 Android/Gradle 联网构建，也不能模拟 Android PackageInstaller，本原型需要在 GitHub Actions 编译并在真实 Android 设备上进行第一次验证。

重点验证：

1. `QuickPatchActivity.kt` 是否与 pinned LSPatch v1.2 API 完全编译通过。
2. 2.19.0(573) 的 `base + 3 splits` 是否显示正确。
3. patch 输出是否仍是 4 个 APK。
4. 系统卸载确认后，`PatchJobHost.install(uninstallFirst=true)` 是否能顺利继续安装。
5. 新安装的 DLsiteSound 是否显示 DLsiteFloat 的悬浮字幕按钮。

若 CI 出现编译错误，只应根据 pinned upstream API 做小修，不应退回重新实现 patch engine。
