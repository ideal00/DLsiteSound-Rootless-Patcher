# 真机测试指南

当前测试目标：**DLsiteSound 2.19.0 (573)** + DLsiteFloat 2.1.0 + Integrated LSPatch。

## 测试前

1. 从官方渠道安装 DLsiteSound。
2. 确认 DLsiteSound 可以正常启动、登录和播放。
3. 如果 DLsiteSound 内有重要离线下载，请先自行备份/重新下载准备。官方版与修补版签名不同，首次安装修补版通常需要卸载官方版，Android 会清除其本地应用数据。
4. CI 测试包仅为 `arm64-v8a`。

## 测试步骤

1. 安装 `DLsiteSound-Rootless-Patcher-arm64-ci.apk`。
2. 打开补丁器。
3. 检查首页：
   - DLsiteSound 应被识别；
   - 2.19.0 (573) 应显示“已验证版本”；
   - 应显示“基础包 + N 个分包”；
   - DLsiteFloat 应显示 v2.1.0。
4. 点“生成无 Root 字幕版”。
5. 观察阶段是否依次经过：
   - 读取 APK
   - 签名准备
   - 重写清单
   - 注入加载器
   - 嵌入 DLsiteFloat
   - 处理 Split APK
   - 写入并签名
6. 修补完成后点“安装修补版”。
7. 如果系统尚未授予“允许来自此来源安装应用”，补丁器应该只打开权限页，**此时不得先卸载 DLsiteSound**。开启权限后返回，再点一次安装。
8. 如果检测到签名冲突，会出现“确认卸载并安装”。只有确认后才应触发 Android 的卸载确认。
9. 安装成功后点“开启‘显示在其他应用上层’”，确认打开的是 **DLsiteSound** 的权限页，而不是补丁器。
10. 打开 DLsiteSound，选择一个确定有官方字幕的音轨，测试：
    - 悬浮字幕按钮是否出现；
    - 字幕是否跟随播放进度；
    - 拖动/缩放是否正常；
    - 换轨后字幕是否更新；
    - 关闭/重开 DLsiteSound 后是否仍正常。

## 预期限制

- 无 Root 方案只修补 `jp.co.eisys.dlsitesound`。
- **悬浮字幕**是目标功能。
- **状态栏字幕**需要 SystemUI 作用域，不属于本项目的无 Root 目标。
- 其他 DLsiteSound 版本目前属于“未验证”，不代表一定不兼容。

## 报 Bug 时请提供

- Patcher 版本，例如 `0.1.0-alpha.1`
- DLsiteSound 版本名和 versionCode
- Android 版本
- 手机厂商/系统，例如 ColorOS、HyperOS、One UI、AOSP
- “基础包 + N 个分包”显示的 N
- 失败发生在哪个阶段
- 屏幕上的完整错误文本
- 是否已允许安装未知应用
- 是否已允许 DLsiteSound 显示在其他应用上层

**不要上传、附加或公开分享 DLsiteSound 原始 APK / split APK。**

如果问题发生在修补阶段，可以附补丁器日志；提交前请检查并删除不希望公开的路径或个人信息。
