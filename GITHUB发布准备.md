# GitHub 发布记录

公开仓库：<https://github.com/NineThree/HeadUnitWireless-CarPlay>

## 发布内容

- Git 仓库发布完整 Android 源码、GPLv3 许可证、第三方声明、构建说明、GitHub Actions 工作流和安装说明。
- APK `HeadUnitWireless-CarPlay-0.2.7-universal.1.apk` 单独作为 GitHub Release 附件发布，不加入 Git 源码提交。
- APK 内含可提取的实验性配件认证证书和私钥。项目安全说明、第三方声明、Release 描述和 [`安装说明.md`](安装说明.md) 均披露了来源和复用风险；这是维护者明确选择公开分发的材料。
- Git 源码树检查禁止 APK、Android 签名材料和私钥文件；`.gitignore` 忽略 APK，CI 运行 `scripts/check_public_tree.py`。

## 安装说明

用户可按 [`安装说明.md`](安装说明.md) 在 Android 4.2 及以上车机安装 APK、配置热点与蓝牙，并核对 SHA-256。

## 发布状态

本地准备与 GitHub 远程发布由本次任务执行。GitHub Release 使用 `0.2.7-universal.1` 标签；附件为上述 APK，SHA-256 为 `9c7b484fefeaf7aa0d38643758fc0ce0029c2fbdba3705f72c328bb553db8ddd`。
