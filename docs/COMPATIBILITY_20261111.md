# 0.4.1 / DLsiteSound 2.23.1 验证记录

日期：2026-11-11，Asia/Shanghai。

## 本地测试产物

- 补丁器：`DLsiteSound-Rootless-Patcher-0.4.1.apk`，versionCode 6。
- 内嵌模块：`2.1.2-rootless-v5`，versionCode 1111；回移 v2.4.4 网络适配，并非完整升级上游设置/缓存体系。
- 四种 loader ABI：arm64-v8a、armeabi-v7a、x86、x86_64。
- 与旧 0.4.0 补丁器签名一致：SHA-256 `97bb5b6ba4a7e0da5e0b39e26059ccf2d2236547e61d16d9078bc28b6e63b1ee`。
- 补丁器 SHA-256：`638826386f2835e4c0c28f8d258a583cbeed38c5d6c631221fb157a757b4f33e`。
- 内嵌模块 SHA-256：`2df2c59104448dec3423619bf645faccca11de6f8a8d40fd2a2e3f667f22dfcd`。

正式 Release 由 CI 使用仓库稳定发布密钥重新构建，以上本地 APK 哈希与签名不用于标识正式产物。
正式文件请使用发布页的 `SHA256SUMS.txt` 校验。公开 v0.4.0 的发布证书为
`0d5cac533d7b69d34f2177e6ed15ee77a54d97c6c34c664c0917be8d7b4a143d`。

## 安装失败原因

此前提供的修复 APK 是 split 应用的基础包，不能单独安装。
原始 base 和此前修复包均在模拟器中返回：

```text
INSTALL_FAILED_MISSING_SPLIT: Missing split for jp.co.eisys.dlsitesound
```

原始 base 没有 `lib/` 原生库，并声明 `requiredSplitTypes="base__abi,base__density"`。
不能通过删除 split 要求来补出缺失的原生库。新版补丁器在设备上读取完整 `sourceDir + splitSourceDirs`。

## 测试输入与环境

- 雷电 14.0.7.4，新建独立实例 `DLsiteSound_Subtitle_Test`（index 2），Android 14。
- 原始完整包来自 [APKPure 2.23.1 下载页](https://apkpure.net/dlsite-sound/jp.co.eisys.dlsitesound/download/2.23.1)，仅作为本地测试输入，没有嵌入补丁器。
- 基础 APK SHA-256 与用户提供的原始 APK 相同：`53447f9b74a790900d58fb1c087be52bd5180231c665f7cdc2d35c380e622279`。
- 分包：`config.armeabi_v7a`、`config.en`、`config.mdpi`。
- 基础包与各分包原始签名均为 `e610426300883bfc317005a5783104656a83d6a7d3d70aa5ac1c9f1b26b1ea5f`。
- 原版完整安装成功。初次 ADB 非流式安装补丁器遇到模拟器 restorecon 错误；流式安装成功，旧 0.4.0 的非流式安装也有同样错误。

## 已验证

1. Gradle release 构建成功；静态 APK 检查通过，四 ABI 和 v5 内嵌模块存在，APK 签名校验通过。
2. 22 项 JVM 适配回归检查通过；从固定 v2.1.2 干净源码应用脚本及 overlay 后，模块 DEX 与交付模块一致。
3. 补丁器正确识别 `2.23.1 (633)`、`基础包 + 3 个分包` 和 `Rootless Controls v5`。
4. 点击生成后得到 4 个 APK，随后完整走完签名冲突提示、系统卸载确认和系统安装确认，界面显示“安装成功”。
5. 安装后仍为 `2.23.1 (633)`、ABI armeabi-v7a、3 个 split；基础和分包使用相同 LSPatch 签名。
6. 启动日志确认模块成功加载：

```text
Loaded module io.github.ariinyume.dlsitesoundfloat successfully.
build applicationId=io.github.ariinyume.dlsitesoundfloat versionName=2.1.2-rootless-v5
build channel ready: resp=wk.G body=wk.H peek=YES
okio chain resolved BY SHAPE: source=k request=L buffer=b clone=clone readByteArray=n
[DLsiteSoundFloat:HostEvents] hooked 2 Expo status getters
LSPatch bootstrap completed
```

## 旧修补版直接更新

使用用户提供的旧模块基础 APK，配合同版本且同签名的三个修补分包，覆盖到测试实例。
然后安装最终 0.4.1 补丁器，点击“更新字幕模块并重新生成”：

- 从内嵌原始 base 和三个 split 恢复来源，再重新修补；安装器显示“要更新此应用吗”，未要求卸载。
- 曾主动取消一次，补丁器准确显示取消错误，没有把已有旧修补版误报为安装成功；随后重试更新成功。
- 更新前后首次安装时间均为 `2026-11-11 09:08:58`，最后更新时间由 `09:17:26` 变为 `09:20:21`。
- 安装后的模块 SHA-256 与交付模块相同，原始 base SHA-256 也与用户原包相同，证明恢复重建未嵌套旧修补包。
- 分包数仍为 3。检查结果见本地 `analysis-20261111/module-update-verification.json`。

## 手机确认与模拟器限制

模拟器上原版 2.19.0、原版 2.23.1 和修补版 2.23.1 均出现：

```text
FATAL EXCEPTION: FinalizerWatchdogDaemon
java.util.concurrent.TimeoutException:
expo.modules.kotlin.jni.decorators.JSDecoratorsBridgingObject.finalize() timed out after 10 seconds
```

模拟器的验证范围是修补/安装/模块初始化及挂钩注册。用户随后在手机上确认“可以用了”，并要求推送新版本；据此将 2.23.1 (633) 标记为用户已验证可用。手机结果与模拟器检查分别记录，不以模拟器安装成功替代播放验证。

本地截图和完整日志位于工作区外层 `analysis-20261111/`，不包含在公开补丁器中。
