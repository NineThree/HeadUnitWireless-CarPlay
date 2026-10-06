# golf.4：画质、全屏与视频合成

依据用户提供的golf-wireless-20261005-213134.txt、golf-wireless-20261005-213628.txt与IMG_7545照片。报告中包含先前版本的历史日志；本轮判断使用21点之后的golf.3会话。

## 当前能确认的结果

普通OMX.mtk.video.decoder.avc已启动并送显614×360 H.264视频。最新会话没有解码器启动/运行错误，第一输入缓冲区容量1MiB，AVC profile100/level30。照片展示可见CarPlay，用户确认连接和基本功能正常。多数统计接收与送显速率接近，动态内容可达30帧；有一次超过250ms积压恢复。

用户仍观察到轻微拖影和卡顿。统计包含静态画面，低收帧率不能独立证明无线丢包；“touch2frame”是触摸到下一次收到帧的时间，并非实测屏幕响应延迟。不能据现有材料确定拖影由网络、解码器还是面板造成。新版画质的性能仍需实车比较。

## 本轮修改

- 按用户选择，默认均衡画质为80%，1024×600全屏画布请求820×480、H.264、30帧。保留原60%流畅档（614×360），两档使用同一解码与连接路径。
- 独立保存画质和顶部避让。连接Profile读取选择，不再每次强制回到60%；从golf.3覆盖升级默认为均衡，不清空热点、身份或手机配对。
- 照片中的OEM顶部系统栏遮住首排图标。已核对本地AndroidX Core1.9.0：WindowInsetsControllerCompat在API20以下选择空的Impl，hide调用没有隐藏效果；原布局允许延伸到系统栏下方。API17–19现改用FLAG_FULLSCREEN与旧式systemUiVisibility。API17/18不隐藏导航栏，避免系统吞掉首次触摸；API19使用沉浸式导航栏隐藏。现代安卓分支保持WindowInsets。
- 若固件仍拒绝隐藏顶部栏，可手动开启64dp顶部避让。视频与触摸层采用相同上边距，屏幕尺寸协商依据剩余视频区域，归一化触摸坐标也使用这个区域。开关保存后重新连接生效。
- 本视频层没有TextureView变换或透明内容，将isOpaque恢复为true，减少透明合成要求。没有修改帧解密、参考帧、队列恢复、解码器选择或基础格式。此调整不等于已经解决实机拖影。
- 将默认返车图标中残留的厂商标志替换为本项目生成的中性汽车PNG，保留SVG源。已在iPhone缓存的旧图标是否刷新需要连接后观察。

## 本地验证与实机边界

修改前，针对实际Activity的API17全屏、视频不透明、避让区域，以及默认均衡Profile的六项断言失败；日志保存在本地构建工作区。修改后检查旧式全屏恢复、API19沉浸与现代分支、共享视频/触摸区域、弹窗保存/取消以及持久偏好。完整检查结果见VALIDATION.md。

这些测试使用RobolectricSDK29的Android对象并选择代码SDK17/19分支，没有运行真实API17窗口管理器、GPU或厂商MediaCodec。解码路径的既有五项回归继续保留。实际清晰度、流畅度、OEM栏隐藏、顶部/底部触摸、音频、重连和30分钟连接均需要用户实车验收。

接口参考：[WindowInsetsControllerCompat](https://developer.android.com/reference/androidx/core/view/WindowInsetsControllerCompat)，[TextureView](https://developer.android.com/reference/android/view/TextureView)。
