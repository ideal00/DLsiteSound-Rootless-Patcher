# DLsiteSound Rootless Subtitle Patcher

当前补丁器版本：**0.4.0**

面向 Android 的 **无 Root DLsiteSound 字幕补丁器**。

它基于 [JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch) v1.2 的补丁引擎，在用户自己的手机上读取已经安装的 DLsiteSound（`base.apk + split APKs`），以 **Integrated mode** 嵌入基于 [DLsiteSoundFloatingSubtitle](https://github.com/ariinyume/DLSiteSoundFloatingSubtitle) v2.1.2 固定源码构建的增强模块，然后交给 Android 安装器安装。增强模块加入悬浮播放控制，但不改变字幕抓取/同步的核心逻辑。

> **本项目不包含、不下载、不分发 DLsiteSound APK。** 用户必须自行从官方渠道安装 DLsiteSound。本项目仅对用户设备上已有的 APK 副本进行本地修补。

## 构建组合

- DLsiteSound: **2.19.0 (573)**
- LSPatch: **v1.2 / build 487**
- DLsiteFloat: **v2.1.2**（构建与静态验证通过；本次尚未真机回归）
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

### 悬浮窗权限

在 Integrated 模式中，DLsiteFloat 代码运行在 DLsiteSound 进程内，`FloatingWindowManager` 使用宿主 DLsiteSound 的 `Context` 创建 `TYPE_APPLICATION_OVERLAY` 窗口。因此最终需要授予“显示在其他应用上层”权限的是：

```text
jp.co.eisys.dlsitesound
```

而不是补丁器本身，也不需要额外安装一个独立的 DLsiteFloat App。安装修补版成功后，补丁器会提供快捷按钮直接打开 DLsiteSound 的悬浮窗权限页。

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
4. 克隆固定的 DLsiteFloat v2.1.2 源码 commit，并应用本仓库的增强 overlay。
5. 编译自定义 DLsiteFloat APK，静态验证播放控制与外观设置代码已打入。
6. 将增强模块嵌入 Manager，并修改 launcher/applicationId。

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

GitHub Actions 同时上传 **combined-source**，包含实际参与该次 APK 构建的 LSPatch 完整源码（含 submodules）、DLsiteFloat v2.1.2 源码、overlay 和许可证，方便满足对应源码分发要求。


## CI 测试包

`Build APK` 与正式 Release 都构建完整四 ABI 的 LSPatch loader：

- arm64-v8a
- armeabi-v7a
- x86
- x86_64

CI 产物名为 `DLsiteSound-Rootless-Patcher-full-ci.apk`。CI 包用于真机验证；正式 GitHub Release 仍要求仓库 Secrets 中配置稳定发布签名。


## 增强悬浮字幕 · Rootless Controls v4

默认采用“显示模式 / 编辑模式”双状态，避免悬浮面板长期遮挡其他应用。

**显示模式（默认）**

- 只显示当前字幕行，不显示大玻璃面板、关闭键、缩放手柄和播放控制栏
- 主字幕窗口使用 `FLAG_NOT_TOUCHABLE`，点击会穿透到下面的应用
- 使用独立的 36×48dp 控制柄（✎）；优先放在字幕窗上方，空间不足时移到窗外的其他位置
- 字幕窗口 alpha 设为 0.78，兼顾 Android 12+ overlay touch 安全限制
- 字体使用高反差阴影，不依赖屏幕截图或自动取色

**编辑模式**

点击边缘的 **✎** 进入，控制柄变为 **✓**：

- 恢复玻璃面板与窗口触摸
- 可拖动、缩放、关闭和锁定
- 播放 / 暂停
- 上一轨 / 下一轨
- 前进 / 后退 10 秒
- 可拖动进度条与当前时间 / 总时长
- **黑 / 亮**：开启或关闭补丁的黑屏防误触模式。黑屏触摸层盖住播放页，字幕窗和控制柄仍可操作；长按控制柄也可退出黑屏
- 在 ⚙ 外观设置中选择当前音轨的本地 `.lrc` 汉化字幕
- ⚙ 外观设置
- 字幕面板透明度：20%–100%
- 控制栏透明度：20%–100%
- 字幕字号：13–24sp
- 字体颜色预设：白 / 黄 / 青 / 绿 / 粉 / 黑
- 文字阴影根据字体亮度自动使用黑色或白色高反差阴影
- 窗口位置、尺寸、锁定状态和外观设置持久化

点 **✓** 退出编辑模式后，玻璃面板与操作控件立即消失，字幕重新进入点击穿透状态。

编辑模式下，播放控制栏占用独立空间，不再叠在歌词上。关闭悬浮字幕窗时黑屏触摸层会一并移除。黑屏模式由补丁的 **黑 / 亮** 按钮控制，不与 DLsite Sound 自带按钮联动；系统状态栏和导航手势仍由 Android 管理。

### 使用 Kikoeru.Extras 民间汉化

1. 在 ASMR.one 找到与正版作品对应的 RJ 编号，单独保存 `Kikoeru.Extras/Subtitles/zh/` 下当前音轨的 `.lrc` 文件到手机。
2. 在 DLsiteSound 播放该音轨。如果显示“无字幕”，长按播放页的字幕胶囊；如果已显示悬浮字幕，点 ✎ → ⚙ →“导入当前音轨的本地 LRC 字幕”。
3. 从系统文件选择器选中 `.lrc`。补丁会在 DLsiteSound 本地缓存此文件，并优先显示汉化字幕。以后播放同一音轨时会尝试自动恢复。

字幕内容只在导入时读取，本地播放和滚动不请求 ASMR.one。若该音轨无法取得稳定的媒体标识，重开后需要再选一次文件；不同音轨首次使用时也需要分别选择。这个功能不会自动从 ASMR.one 下载字幕。
