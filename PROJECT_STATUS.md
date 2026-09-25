# Project status

核心无 Root 修补链路已经在真实 Android 设备上完成端到端验证。

## 已验证

- 检测 `jp.co.eisys.dlsitesound`
- 读取 `base.apk + splitSourceDirs`
- DLsiteSound 2.19.0 (573) / base + 3 splits
- LSPatch Integrated mode + signature bypass level 2
- 完整四 ABI loader
- 修补输出
- 签名冲突识别
- 系统卸载官方版
- 卸载返回后继续安装修补版
- 应用重启后恢复 patched outputs
- 修补版安装
- DLsiteFloat 悬浮字幕无 Root 正常使用

## 当前开发：Rootless Controls v1

构建流程已经由“下载官方 DLsiteFloat APK”切换为：

```text
固定 DLsiteFloat v2.1.0 commit
→ 应用最小源码 patch
→ 覆盖新增 PlayerControlBridge / PlaybackControlsView
→ CI 编译模块
→ 静态验证控制类存在
→ 嵌入 LSPatch Quick Patcher
```

新增悬浮窗能力：

- 播放 / 暂停
- ±10 秒
- 上一轨 / 下一轨
- SeekBar + 当前时间 / 总时长
- 控制层 5 秒自动隐藏
- 锁定窗口
- 窗口位置 / 大小跨进程持久化

## 下一步真机回归

1. 控制栏点击不触发窗口拖动。
2. 锁定后禁止移动 / 缩放，但控制键仍可用。
3. 上一轨 / 下一轨同步 DLsiteSound 自身 UI 与字幕。
4. SeekBar 拖动时不会被实时进度抢回。
5. 进程重建后窗口几何与锁定状态恢复。
