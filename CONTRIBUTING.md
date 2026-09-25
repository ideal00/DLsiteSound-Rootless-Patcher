# Contributing

欢迎提交 Issue 和 Pull Request。

## 基本原则

- 不要提交、镜像或附带 DLsiteSound 原始 APK / split APK。
- 不要把个人签名密钥、JKS、密码、GitHub Secrets 提交进仓库。
- 修改补丁逻辑时，保持 LSPatch 与 DLsiteFloat 的上游版本/commit 明确可追溯。
- 修改构建脚本后，确保 `Build APK` workflow 通过。
- APK 相关改动应通过 `scripts/verify_built_apk.py`。
- 新增对某个 DLsiteSound 版本的“已验证”标记前，应先完成真机测试。

## 本地准备

```bash
./scripts/prepare.sh
cd work/LSPatch
./gradlew :manager:buildRelease
```

当前补丁器通过 overlay 方式复用 LSPatch，不建议直接复制一份长期 fork 后手工维护。

## License

提交到本项目的代码按 GPL-3.0 发布。第三方代码保留各自版权和许可证声明。
