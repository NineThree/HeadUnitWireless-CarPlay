# 进入投屏后空白画面：golf.3

本轮依据：golf-wireless-20261005-201421.txt、golf-wireless-20261005-201532.txt 和 IMG_7541。报告首行是 golf.2，系统 Android4.2.2 / API17。

## 已确定的失败阶段

报告中的 iAP2 authentication accepted、wireless handoff ready 和 type-110 screen stream 表明这次已完成手机认证与无线交接。视频接收持续进行，统计未记录向前的序列缺口；显示统计一直是 shown=0fps。

普通 MTK AVC 解码器启动报 IllegalStateException，随后 Google AVC 解码器虽然启动，却很快也报错。照片中的内容区为空白，与没有输出帧一致。报告记录了少量触摸发送，但不能据此确认 iPhone 界面的实际操作成功。

旧报告没有异常消息、具体失败操作或 SPS profile/level，因此尚不能确定驱动为什么拒绝这路视频。不能将故障归结为触摸屏损坏，也不能仅凭无线连接成功认定视频兼容。

## 当前验证的兼容点

此前检测工具在普通 MTK 解码器上能够输出800x480测试视频。它使用视频轨道给出的格式；投屏代码还额外设置 max-input-size=8MiB 和 priority=0。

[Android4.2.2 的 ACodec 源码](https://android.googlesource.com/platform/frameworks/av/+/android-4.2.2_r1/media/libstagefright/ACodec.cpp)表明 max-input-size 会提高输入端口的每个缓冲区大小，并调用厂商的端口参数接口。大缓冲区是本轮优先验证的差异；真实驱动的错误状态仍需新版报告确认。这不是已经实机证实的唯一原因。

golf.3 在 API17–20 使用基础视频格式，让驱动自行选择输入缓冲区；仅在 API23 以后设置 priority。每个候选解码器得到独立的格式与 SPS/PPS 缓冲区，避免前一候选的失败影响后续初始化。H.264、614x360、30fps 和无线协议路径保持本轮测试条件。

## 报告与回归验证

新增报告包括 AVC 参数集长度、profile/constraints/level、解码器名称、失败操作（create/configure/start/dequeue-input/queue-input/dequeue-output 等）、异常消息和第一条成功入队的输入大小。不会导出实际视频帧内容。

五项回归直接执行实际视频工作线程，在 RobolectricSDK29 提供的 Android 对象上选择代码的 SDK17 分支。前三项分别模拟拒绝大缓冲区的驱动、失败候选消耗初始化缓冲区、以及输出查询异常；修改前分别出现硬件无法启动、回退数据为空、错误缺少操作位置的断言失败。另两项覆盖旧式输入缓冲区的成功入队和超大首帧报告；后者修改前因缺少帧大小和容量而失败。这些是兼容逻辑验证，不是 API17 解码器实机模拟，也没有验证视频性能。

后续实机结果：21:31与21:36导出的golf.3报告和IMG_7545确认MTK解码成功、可见CarPlay，用户确认基本功能正常。最新会话未记录解码异常，输入缓冲区由驱动分配为1MiB。这支持本轮基础格式修复有效，但不能单独证明此前错误只由max-input-size引起。用户反馈的轻微卡顿/拖影、低清晰度与顶部栏遮挡转入golf.4；见DISPLAY_FIX.md。持续运行和新画质仍需实车验收。
