# 通用车机无线 CarPlay（Android 4.2+）

基于 DiPlay-Legacy-Android v0.2.7 的实验性旧安卓车机适配版。最低 Android 4.2（API17），画质按实际投屏区域调整，最高请求 H.264／30帧。

## 致谢

特别感谢 [programmerguohuajing](https://github.com/programmerguohuajing) 开源并分享 [DiPlay-Legacy-Android](https://github.com/programmerguohuajing/DiPlay-Legacy-Android)。本项目基于其 v0.2.7 版本进行独立修改与适配，以支持较旧的 Android 车机。上游署名及许可证信息见[第三方声明](docs/THIRD_PARTY_NOTICES.md)。

**版本：**`0.2.7-universal.1`（版本号107）。本仓库包含完整源码、GPLv3许可证、第三方声明、安装与构建说明、GitHub问题模板及自动检查工作流。APK 作为单独的 [GitHub Release 附件](https://github.com/NineThree/HeadUnitWireless-CarPlay/releases/tag/0.2.7-universal.1) 发布，不进入 Git 源码历史。APK 含有可提取的实验性配件认证材料，详见 `docs/THIRD_PARTY_NOTICES.md`。

## 功能与限制

- 使用车机系统Wi-Fi热点与蓝牙无线连接iPhone。
- 均衡（80%）、流畅（60%）、清晰（100%），最高30帧。
- 已开启的Android4.2–5.1热点长期缺少网络接口时，可尝试恢复一次。
- 车机把方向盘信号交给Android应用时，可逐个学习实体按键。
- 保留可选Siri／麦克风路径。

仅凭Android4.2+不能保证不同车机的热点、蓝牙RFCOMM及视频解码兼容。通用版尚未在多种非高尔夫车机上验证。若固件不向普通应用传递实体按键事件，学习映射也无法补出信号。

## 安装

从[GitHub Releases](https://github.com/NineThree/HeadUnitWireless-CarPlay/releases/tag/0.2.7-universal.1)下载APK，并按[安装说明](安装说明.md)操作。安装后开启车机热点，在系统蓝牙设置中配对iPhone，再在应用内填写相同热点名称和密码并选择手机。iPhone需保持Wi-Fi与蓝牙开启。

完整步骤见[安装说明](docs/INSTALL.md)和[兼容边界](docs/RELIABILITY_PERFORMANCE.md)。

## 从源码构建

需要JDK25、Android SDK platform37、Build Tools36.0.0，项目包含Gradle9.5.0 wrapper。

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest \
  :mobile:lintGolfDebug :mobile:lintUniversalDebug \
  :mobile:assembleGolfDebug :mobile:assembleUniversalDebug
```

源码／Debug构建不含随附独立安装包所用的外部配件认证材料。独立版构建说明见[`docs/BUILD.md`](docs/BUILD.md)，需单独提供 `DIPLAY_AUTH_ASSETS_DIR`。仓库中没有该认证材料；请勿将它或Android签名文件提交到GitHub。

## 许可证与状态

保留上游GNU GPLv3，见[`LICENSE`](LICENSE)及[第三方声明](docs/THIRD_PARTY_NOTICES.md)。本项目是社区适配版，不代表Apple，也不声称通过Apple认证。

本版本315项自动测试与Release检查已在本地通过。热点恢复、实体按键送达、通话控制、长时间流畅度及其它车机兼容性仍待各自实车验证，见[验证记录](docs/VALIDATION.md)。

[English README](README.en.md) · [构建说明](docs/BUILD.md) · [安装说明](docs/INSTALL.md) · [安全政策](SECURITY.md)
