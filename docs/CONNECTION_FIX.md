# 连接页重试问题：golf.2

用户在2026年10月5日提供四份无线故障报告和 IMG_7537 照片。报告中的程序版本均为 0.2.7-golf.1，系统为 Android 4.2.2 / API17。原始报告仅保存在本机排障目录，不随源码公开。

## 报告确认的停止位置

应用已安装启动；Android 蓝牙接口存在且已开启，有一个已配对设备，手机已选择。本地认证提供器可初始化，但这不等于 iPhone 已认证。

早期日志提示热点未开启；后续日志已经成功检测到 ap0 IPv4 热点，并显示 `wireless AirPlay listener attached ... port=7000`，紧接着报 `bind failed: EADDRINUSE (Address already in use)`。最新报告包含38次该失败记录。停止点在服务发现启动，早于蓝牙 RFCOMM / iAP2 握手；不能据此认定手机认证或视频解码失败。

## 原因与改动

JmDNS 3.6.3 原实现调用带已绑定地址的 MulticastSocket 构造器。Android 4.2.2 的该构造器先由父类绑定、后设置 SO_REUSEADDR：有已有可共享的 mDNS 服务时仍可能失败。报告没有保留系统端口拥有者和完整异常栈，因此不宣称已识别具体占用进程。

参考：[Android 4.2.2 MulticastSocket 源码](https://android.googlesource.com/platform/libcore/+/android-4.2.2_r1/luni/src/main/java/java/net/MulticastSocket.java)、[原始 JmDNS 3.6.3 源码包](https://repo.maven.apache.org/maven2/org/jmdns/jmdns/3.6.3/jmdns-3.6.3-sources.jar)。

修复保留同版全部协议实现和热点接口绑定，只将套接字创建改为“未绑定 → 开启端口共享 → 绑定”。任何后续接口/组播初始化失败均关闭新套接字。通过本地源码模块替换原二进制依赖，APK只包含一份实现。增加服务发现启动/成功日志，以及网络发现失败的明确提示。

## 本机验证与边界

回归测试加载真实 JmDNS 实现，仅模拟 Android 4.2 的构造器顺序，对真实已占用 UDP 端口进行测试。原实现复现 BindException，修复后通过；组播加入失败的端口释放和连接页提示也有回归测试。完整测试共212项通过，静态检查没有错误。

这证明本地重现的问题已修正，不代表完整车机无线连接成功。需要覆盖更新 golf.2 后重启车机，开启热点再连接，确认能否进入蓝牙与 iPhone 许可阶段；如仍卡住，导出新版报告。花屏和连续稳定运行仍需实车验证。

后续结果：用户20:14与20:15导出的 golf.2 车机报告已确认手机认证、热点交接及视频接收，原来的连接页阻塞已越过。新故障是解码器报错、显示帧率为零；golf.3 的调查与验收限制见 VIDEO_FIX.md。
