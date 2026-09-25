# Changelog

## 0.2.0 — 2026-09-25

- DLsiteFloat 改为从固定 v2.1.0 源码 commit 构建，不再嵌入上游预编译 APK。
- 新增 Rootless Controls v1：
  - 播放 / 暂停
  - 上一轨 / 下一轨
  - 前进 / 后退 10 秒
  - 可拖动进度条
  - 当前时间 / 总时长
  - 5 秒自动隐藏控制栏
  - 窗口锁定
  - 窗口位置与大小持久化
  - ⚙ 内联外观设置面板
  - 字幕面板透明度 20%–100%
  - 控制栏透明度 20%–100%
  - 字幕字号 13–24sp
  - 外观设置持久化并实时生效
- 内嵌模块验证改为检查增强控制类与外观设置键，而不是固定 APK SHA-256。


## 0.1.0

- 首个原型。
- 固定使用 LSPatch v1.2 build 487 的补丁引擎。
- 内置 DLsiteFloat v2.1.0（构建时下载并校验）。
- 自动读取已安装 DLsiteSound 的 base + split APK 路径。
- 默认 Integrated mode + signature bypass level 2。
- 明确的签名冲突/卸载数据丢失确认。
- 安装完成后提供悬浮窗权限入口。
- 不包含 DLsiteSound APK。
