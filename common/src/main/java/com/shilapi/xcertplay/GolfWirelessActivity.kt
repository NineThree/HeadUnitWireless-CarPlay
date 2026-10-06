package com.shilapi.xcertplay

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.network.CarHotspotStatus
import java.net.NetworkInterface
import java.util.Collections
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Small manual-hotspot entry point with owner-selected startup and voice options. */
class GolfWirelessActivity : Activity() {
    private lateinit var ssid: EditText
    private lateinit var password: EditText
    private lateinit var phone: TextView
    private lateinit var displaySummary: TextView
    private lateinit var featureSummary: TextView
    private lateinit var status: TextView
    private lateinit var connectButton: Button
    private var setupError: String? = null
    private var connecting = false
    private var keyTestDialog: AlertDialog? = null
    private var keyTestToken: Any? = null
    private val exportWorker = Executors.newSingleThreadExecutor()
    private val startupHandler = Handler(Looper.getMainLooper())
    private val autoGate = GolfAutoConnectionGate()
    private val autoTick = Runnable { checkAutomaticConnection() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        GolfWirelessProfile.apply(this)
        setupError = runCatching { DiPlayBootstrap.ensure(this) }.exceptionOrNull()?.let {
            "本地连接组件初始化失败（${it.javaClass.simpleName}），请导出故障报告。"
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
        }
        val title = TextView(this).apply { text = HeadUnitEdition.title(this@GolfWirelessActivity); textSize = 24f }
        column.addView(title)
        column.addView(TextView(this).apply {
            text = "先开启车机热点，再把 iPhone 与车机蓝牙配对。\n填写车机热点名称和密码；iPhone 保持 Wi-Fi、蓝牙开启。"
            textSize = 17f
            setPadding(0, dp(12), 0, dp(8))
        })
        ssid = EditText(this).apply {
            hint = "车机热点名称"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(AirPlayPersistence.loadManualHotspotSsid(this@GolfWirelessActivity))
        }
        password = EditText(this).apply {
            hint = "车机热点密码（8–63 字符）"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(AirPlayPersistence.loadManualHotspotPassphrase(this@GolfWirelessActivity))
        }
        column.addView(ssid)
        column.addView(password)
        phone = TextView(this).apply { textSize = 18f; setPadding(0, dp(10), 0, dp(4)) }
        column.addView(phone)
        row(column, "车机热点设置" to { openHotspotSettings() }, "蓝牙配对设置" to {
            openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        })
        row(column, "选择已配对的 iPhone" to { choosePhone() }, "保存热点" to {
            if (saveHotspot()) setStatus("已保存。确认车机热点开启后，点击连接。")
        })
        displaySummary = TextView(this).apply { textSize = 17f; setPadding(0, dp(8), 0, 0) }
        column.addView(displaySummary)
        row(column, "画质与显示" to { chooseDisplay() })
        featureSummary = TextView(this).apply { textSize = 16f; setPadding(0, dp(8), 0, 0) }
        column.addView(featureSummary)
        row(column, "启动与语音" to { chooseFeatures() })
        row(column, "实体按键检测" to { chooseHardwareKeys() }, "方向盘按键学习" to { chooseKeyLearning() })
        val actions = LinearLayout(this)
        connectButton = button("开始无线连接") {
            cancelAutomaticConnection()
            CarHotspotStatus.resetRecoveryBudget(ssid.text.toString())
            connect()
        }
        actions.addView(connectButton, weighted())
        actions.addView(button("断开连接") {
            cancelAutomaticConnection()
            connecting = false
            connectButton.isEnabled = true
            CarPlayBackgroundSession.stop { runOnUiThread { setStatus("连接已停止。") } }
            setStatus("正在停止连接……")
        }, weighted())
        column.addView(actions)
        row(column, "返回投屏画面" to {
            if (CarPlayBackgroundSession.hasSession()) openProjection()
            else setStatus("尚未连接，请先点击“开始无线连接”。")
        }, "导出故障报告" to { exportReport() })
        row(column, "重置 CarPlay 配对" to { resetPairings() })
        status = TextView(this).apply { textSize = 17f; setPadding(0, dp(12), 0, 0) }
        column.addView(status)
        column.addView(TextView(this).apply {
            text = "投屏、触摸、基础音频；可在“启动与语音”开启实验麦克风。\n连接后按车机返回键可回到这里；首次连接请允许 iPhone 上的 CarPlay 提示。"
            textSize = 15f
            setPadding(0, dp(12), 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(column) })
        updatePhone()
        updateDisplaySummary()
        updateFeatureSummary()
        setStatus(setupError ?: "等待连接。实际无线连接和画面效果需在本车机验证。")
    }

    override fun onResume() {
        super.onResume()
        if (::phone.isInitialized) updatePhone()
        if (::displaySummary.isInitialized) updateDisplaySummary()
        if (::featureSummary.isInitialized) updateFeatureSummary()
        scheduleAutomaticConnection()
    }

    override fun onPause() {
        closeHardwareKeys()
        startupHandler.removeCallbacks(autoTick)
        super.onPause()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun weighted() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; minHeight = dp(54); setOnClickListener { action() }
    }
    private fun row(parent: LinearLayout, vararg actions: Pair<String, () -> Unit>) {
        parent.addView(LinearLayout(this).apply {
            actions.forEach { (label, action) -> addView(button(label, action), weighted()) }
        })
    }
    private fun setStatus(message: String) { status.text = message }
    private fun updatePhone() {
        phone.text = if (DiPlayPreferences.phoneAddress(this) == null) "尚未选择 iPhone"
        else "已选择：${DiPlayPreferences.phoneName(this)}"
    }

    private fun updateDisplaySummary() {
        displaySummary.text = "画质：${GolfDisplaySettings.label(this, GolfDisplaySettings.quality(this))} · 30帧" +
            if (GolfDisplaySettings.avoidTopBar(this)) " · 顶部避让已开启" else ""
    }

    private fun chooseDisplay() {
        val choices = GolfDisplaySettings.Quality.values()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val qualityGroup = RadioGroup(this)
        choices.forEachIndexed { index, quality ->
            qualityGroup.addView(RadioButton(this).apply {
                id = index + 1; text = GolfDisplaySettings.label(this@GolfWirelessActivity, quality); textSize = 18f; minHeight = dp(48)
            })
        }
        qualityGroup.check(choices.indexOf(GolfDisplaySettings.quality(this)) + 1)
        content.addView(qualityGroup)
        val avoidTop = CheckBox(this).apply {
            text = "避让顶部系统按钮栏"; textSize = 18f; minHeight = dp(48)
            isChecked = GolfDisplaySettings.avoidTopBar(this@GolfWirelessActivity)
        }
        content.addView(avoidTop)
        content.addView(TextView(this).apply {
            text = "清晰档跟随实际可用屏幕；上述尺寸为估计，连接后的实际尺寸可在报告查看。全部保持30帧。\n均衡使用80%尺寸，流畅使用60%尺寸；清晰档如卡顿加重可切回。\n如果顶部按钮遮住画面，开启避让会在顶部留出空白。\n保存后请断开，再重新连接。"
            textSize = 16f; setPadding(0, dp(8), 0, 0)
        })
        AlertDialog.Builder(this).setTitle("画质与显示").setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("保存") { _, _ ->
                val quality = choices[(qualityGroup.checkedRadioButtonId - 1).coerceIn(choices.indices)]
                GolfDisplaySettings.save(this, quality, avoidTop.isChecked)
                updateDisplaySummary()
                setStatus("已保存。请断开连接，再点击“开始无线连接”使显示设置生效。")
            }.setNegativeButton("取消", null).show()
    }

    private fun chooseHardwareKeys() {
        closeHardwareKeys()
        cancelAutomaticConnection()
        val result = TextView(this).apply {
            textSize = 17f; setPadding(dp(20), dp(16), dp(20), dp(16))
            text = "请按方向盘上一首、下一首、电话键，观察下方是否收到信号。\n此页只检测，不控制音乐或电话。\n投屏里电话键短按接听，长按约1秒挂断；功能需实车确认。\n\n等待按键……"
        }
        val dialog = AlertDialog.Builder(this).setTitle("实体按键检测").setView(result)
            .setPositiveButton("关闭", null).create()
        val token = CarPlayMediaKeys.beginKeyTest(this) { event ->
            result.text = "已收到按键：${event.keyCode}，扫描码：${event.scanCode}\n请继续测试其他键，然后关闭并导出故障报告。\n\n此页只检测，不控制音乐或电话。"
        }
        keyTestToken = token
        keyTestDialog = dialog
        dialog.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            CarPlayMediaKeys.dispatch(event, "key-test")
        }
        dialog.setOnDismissListener {
            CarPlayMediaKeys.endKeyTest(token)
            if (keyTestToken === token) keyTestToken = null
            if (keyTestDialog === dialog) keyTestDialog = null
            scheduleAutomaticConnection()
        }
        dialog.show()
    }

    private fun closeHardwareKeys() {
        keyTestToken?.let { CarPlayMediaKeys.endKeyTest(it) }
        keyTestToken = null
        val dialog = keyTestDialog
        keyTestDialog = null
        dialog?.dismiss()
    }

    private fun updateFeatureSummary() {
        val options = GolfFeatureSettings.load(this)
        featureSummary.text = "开机打开：${if (options.bootLaunch) "开" else "关"} · 自动连接：${if (options.autoConnect) "开" else "关"}" +
            " · 麦克风：${if (options.microphone) "实验开启" else "关"}"
    }

    private fun chooseKeyLearning() {
        closeHardwareKeys()
        cancelAutomaticConnection()
        val actions = GolfKeyBindings.Action.values()
        val learned = GolfKeyBindings.load(this).associateBy { it.action }
        AlertDialog.Builder(this).setTitle("方向盘按键学习")
            .setItems(actions.map { action ->
                learned[action]?.let { "${action.label}（已学${it.keyCode}／${it.scanCode}）" }
                    ?: "学习${action.label}"
            }.toTypedArray()) { _, index -> learnKey(actions[index]) }
            .setNeutralButton("清除学习记录") { _, _ ->
                GolfKeyBindings.clear(this)
                val token = CarPlayMediaKeys.beginKeyTest(this) {}
                CarPlayMediaKeys.endKeyTest(token)
                setStatus("已清除按键学习；标准按键转发保留。")
            }.setNegativeButton("关闭", null).show()
    }

    private fun learnKey(action: GolfKeyBindings.Action) {
        closeHardwareKeys()
        cancelAutomaticConnection()
        var candidate: KeyEvent? = null
        val result = TextView(this).apply {
            textSize = 17f; setPadding(dp(20), dp(16), dp(20), dp(16))
            text = "请按${action.label}的实体键。\n此页只学习，不控制音乐或电话。\n等待信号……没有信号时不能学习。"
        }
        val dialog = AlertDialog.Builder(this).setTitle("学习${action.label}").setView(result)
            .setPositiveButton("保存此键", null).setNegativeButton("取消", null).create()
        val token = CarPlayMediaKeys.beginKeyTest(this) { event ->
            if (!GolfKeyBindings.canLearn(event)) {
                candidate = null
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                result.text = "此键保留原用途，不能学习。请按${action.label}键。"
            } else {
                candidate = KeyEvent(event)
                result.text = "已收到：按键${event.keyCode}／扫描码${event.scanCode}\n确认这是${action.label}键后，点击“保存此键”。\n保存后返回投屏验证；收到信号不代表iPhone已执行。"
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
            }
        }
        keyTestToken = token; keyTestDialog = dialog
        dialog.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK) false else CarPlayMediaKeys.dispatch(event, "key-learning")
        }
        dialog.setOnDismissListener {
            CarPlayMediaKeys.endKeyTest(token)
            if (keyTestToken === token) keyTestToken = null
            if (keyTestDialog === dialog) keyTestDialog = null
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val event = candidate ?: return@setOnClickListener
            runCatching { GolfKeyBindings.save(this, action, event) }.onSuccess {
                setStatus("已学习${action.label}。请返回投屏测试。")
                dialog.dismiss()
            }.onFailure { result.text = "该信号已经分配给其他用途，请先清除学习记录再设置。" }
        }
    }

    private fun chooseFeatures() {
        val old = GolfFeatureSettings.load(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        fun check(label: String, enabled: Boolean) = CheckBox(this).apply {
            text = label; textSize = 18f; minHeight = dp(48); isChecked = enabled; content.addView(this)
        }
        val boot = check("开机打开应用", old.bootLaunch)
        val automatic = check("打开应用后自动连接", old.autoConnect)
        val microphone = check("Siri／麦克风（实验）", old.microphone)
        val recovery = check("热点异常自动恢复（旧系统）", old.hotspotRecovery)
        content.addView(TextView(this).apply {
            text = "自动连接需要已保存热点、已选择手机，且车机热点和蓝牙就绪；等待最多90秒。\n热点异常自动恢复：仅Android4.2–5.1，已开启热点缺少网络接口超过5秒时尝试关闭再开启一次，保留系统热点设置；明确关闭的热点不自动打开。固件拒绝时仍需系统设置。\n开机打开受车机固件影响，休眠唤醒不等于重新开机。\n麦克风只在手机请求语音时录音并上传，不保存录音；修改后断开再连。"
            textSize = 16f; setPadding(0, dp(8), 0, 0)
        })
        AlertDialog.Builder(this).setTitle("启动与语音").setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("保存") { _, _ ->
                val options = GolfFeatureSettings.Options(boot.isChecked, automatic.isChecked,
                    microphone.isChecked, recovery.isChecked)
                GolfFeatureSettings.save(this, options)
                GolfWirelessProfile.apply(this)
                updateFeatureSummary()
                if (!options.autoConnect) cancelAutomaticConnection()
                if (!old.autoConnect && options.autoConnect) {
                    autoGate.reset()
                    scheduleAutomaticConnection()
                }
                setStatus("已保存。麦克风设置需断开后重新连接；自启动受车机系统设置影响。")
            }.setNegativeButton("取消", null).show()
    }

    private fun cancelAutomaticConnection() {
        autoGate.cancel()
        startupHandler.removeCallbacks(autoTick)
    }

    private fun scheduleAutomaticConnection() {
        if (!::status.isInitialized || !GolfFeatureSettings.load(this).autoConnect ||
            setupError != null || connecting || CarPlayBackgroundSession.hasSession()) return
        autoGate.begin(SystemClock.elapsedRealtime())
        startupHandler.removeCallbacks(autoTick)
        startupHandler.postDelayed(autoTick, 1500)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun checkAutomaticConnection() {
        if (!GolfFeatureSettings.load(this).autoConnect || isFinishing || isDestroyed ||
            connecting || CarPlayBackgroundSession.hasSession()) { cancelAutomaticConnection(); return }
        val error = ManualHotspotValidation.error(ssid.text.toString(), password.text.toString())
        val selected = DiPlayPreferences.phoneAddress(this)
        if (ssid.text.toString() != AirPlayPersistence.loadManualHotspotSsid(this) ||
            password.text.toString() != AirPlayPersistence.loadManualHotspotPassphrase(this)) {
            cancelAutomaticConnection()
            setStatus("自动连接已暂停：请先保存修改后的热点信息，再手动开始连接。")
            return
        }
        if (error != null || selected == null) {
            cancelAutomaticConnection()
            setStatus("自动连接需要先保存正确的热点信息，并选择已配对的 iPhone。")
            return
        }
        val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
        val bluetoothReady = runCatching {
            adapter?.isEnabled == true && adapter.bondedDevices.any { it.address.equals(selected, true) }
        }.getOrDefault(false)
        val hotspotReady = when (CarHotspotStatus.state(this)) {
            CarHotspotStatus.State.ENABLED, CarHotspotStatus.State.ENABLING -> true
            CarHotspotStatus.State.FAILED -> GolfFeatureSettings.load(this).hotspotRecovery
            CarHotspotStatus.State.DISABLED, CarHotspotStatus.State.DISABLING -> false
            CarHotspotStatus.State.UNKNOWN -> runCatching {
                Collections.list(NetworkInterface.getNetworkInterfaces()).any {
                    (it.name.startsWith("ap") || it.name.contains("softap", true)) &&
                        it.isUp && !it.isLoopback && Collections.list(it.inetAddresses).any { address -> !address.isLoopbackAddress }
                }
            }.getOrDefault(false)
        }
        when (autoGate.evaluate(SystemClock.elapsedRealtime(), bluetoothReady && hotspotReady)) {
            GolfAutoConnectionGate.Decision.CONNECT -> connect()
            GolfAutoConnectionGate.Decision.WAIT -> {
                setStatus("自动连接：等待车机蓝牙和热点就绪……需要时可手动开始或断开。")
                startupHandler.postDelayed(autoTick, 2000)
            }
            GolfAutoConnectionGate.Decision.TIMEOUT -> setStatus("自动连接等待已结束。请开启车机热点和蓝牙，再手动开始连接。")
            GolfAutoConnectionGate.Decision.STOP -> Unit
        }
    }

    private fun saveHotspot(): Boolean {
        // SSIDs and passphrases are exact values: do not trim spaces entered by the owner.
        val name = ssid.text.toString()
        val passphrase = password.text.toString()
        val error = ManualHotspotValidation.error(name, passphrase)
        if (error != null) {
            setStatus(when (error) {
                ManualHotspotValidation.Error.EMPTY_NAME -> "请填写车机热点名称。"
                ManualHotspotValidation.Error.LONG_NAME -> "热点名称不能超过 32 字节。"
                ManualHotspotValidation.Error.INVALID_CHARACTER -> "热点名称或密码含无效字符。"
                ManualHotspotValidation.Error.PASSWORD_LENGTH -> "热点密码应为 8–63 字符。"
            })
            return false
        }
        AirPlayPersistence.saveManualHotspotSsid(this, name)
        AirPlayPersistence.saveManualHotspotPassphrase(this, passphrase)
        AirPlayPersistence.saveManualHotspotSecurity(this, ManualHotspotValidation.securityFor(passphrase))
        return true
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun choosePhone() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            setStatus("系统没有向应用提供 Android 蓝牙接口。车机原有蓝牙通话功能不一定支持这条无线路线，请导出故障报告。")
            return
        }
        if (!adapter.isEnabled) {
            setStatus("请开启车机蓝牙，并先与 iPhone 配对。")
            openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return
        }
        val devices = runCatching { adapter.bondedDevices.sortedBy { it.name ?: "" } }.getOrElse {
            setStatus("无法读取已配对设备，请导出故障报告。")
            return
        }
        if (devices.isEmpty()) {
            setStatus("应用未找到已配对设备。请在车机和 iPhone 的蓝牙设置里完成配对。")
            return
        }
        AlertDialog.Builder(this).setTitle("选择你的 iPhone")
            .setItems(devices.map { "${it.name ?: "已配对设备"} · ${it.address.takeLast(5)}" }.toTypedArray()) { _, which ->
                val device = devices[which]
                DiPlayPreferences.savePhone(this, device.address, device.name ?: "iPhone")
                updatePhone()
                setStatus("已选择手机。点击“开始无线连接”。")
            }.setNegativeButton("取消", null).show()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun connect() {
        if (connecting) return
        setupError?.let { setStatus(it); return }
        if (!saveHotspot()) return
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) { choosePhone(); return }
        val selected = DiPlayPreferences.phoneAddress(this)
        if (selected == null || runCatching {
            adapter.bondedDevices.none { it.address.equals(selected, ignoreCase = true) }
        }.getOrDefault(true)) { choosePhone(); return }
        GolfWirelessProfile.apply(this)
        connecting = true
        connectButton.isEnabled = false
        setStatus("正在准备无线连接……")
        CarPlayBackgroundSession.stop {
            runOnUiThread {
                if (!isFinishing && !isDestroyed && connecting) {
                    connecting = false
                    connectButton.isEnabled = true
                    openProjection()
                }
            }
        }
    }
    private fun openProjection() {
        startActivity(Intent(this, CarPlayHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
    private fun resetPairings() {
        AlertDialog.Builder(this).setTitle("重置 CarPlay 配对")
            .setMessage("这会断开连接并清除本应用的 CarPlay 配对记录。之后请在 iPhone“设置 → 通用 → CarPlay”中忽略原连接，再重新连接。热点设置和已选择的手机会保留。")
            .setPositiveButton("重置") { _, _ ->
                cancelAutomaticConnection()
                connecting = false
                connectButton.isEnabled = false
                setStatus("正在停止连接并重置……")
                CarPlayBackgroundSession.stop {
                    runOnUiThread {
                        val cleared = runCatching { AirPlayPersistence.clearCarPlayPairings(this) }.isSuccess
                        if (!isFinishing && !isDestroyed) {
                            connectButton.isEnabled = true
                            setStatus(if (cleared) "CarPlay 配对已重置。请在 iPhone 忽略原连接后重新连接。"
                                else "无法清除配对记录，请导出故障报告。")
                        }
                    }
                }
            }.setNegativeButton("取消", null).show()
    }
    private fun openSystem(intent: Intent): Boolean = runCatching { startActivity(intent); true }.getOrElse {
        setStatus("无法打开此设置入口，请在车机系统设置中手动开启。")
        false
    }
    private fun openHotspotSettings() {
        if (!openSystem(Intent("com.android.settings.WIFI_TETHER_SETTINGS"))) {
            openSystem(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    private fun exportReport() {
        setStatus("正在导出……")
        exportWorker.execute {
            val result = runCatching { buildReport() }
            if (result.isFailure) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) setStatus("报告导出失败（${result.exceptionOrNull()?.javaClass?.simpleName}），请重试。")
                }
                return@execute
            }
            val report = result.getOrThrow()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val destination = runCatching {
                @Suppress("DEPRECATION")
                val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), HeadUnitEdition.reportDirectory(this))
                check(root.isDirectory || root.mkdirs())
                File(root, "${HeadUnitEdition.reportPrefix(this)}-$stamp.txt").also { it.writeText(report) }
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (destination != null) setStatus("报告已保存：${destination.absolutePath}\n可用车机文件管理器复制到 U 盘。")
                else {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("车机无线报告", report.take(200_000)))
                    setStatus("无法写入下载目录，报告已复制到剪贴板。")
                }
            }
        }
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun buildReport(): String = buildString {
        append("${HeadUnitEdition.title(this@GolfWirelessActivity)} ${HeadUnitEdition.version(this@GolfWirelessActivity)}\n")
        append("Package=${packageName} Edition=${if (HeadUnitEdition.universal(this@GolfWirelessActivity)) "universal" else "golf"}\n")
        append("Android=${Build.VERSION.RELEASE} API=${Build.VERSION.SDK_INT}\n")
        append("Model=${Build.MODEL} manufacturer=${Build.MANUFACTURER}\n")
        val quality = GolfDisplaySettings.quality(this@GolfWirelessActivity)
        append("Profile=manual hotspot, H.264, ${quality.scaleTenths * 10} percent, 30 fps, playback and touch\n")
        append("DisplayPreference=${quality.id} avoidTopBar=${GolfDisplaySettings.avoidTopBar(this@GolfWirelessActivity)}\n")
        append("DisplayPreferenceApplies=next connection; current session size is in DisplayDiagnosticSnapshot below\n")
        append("HardwareKeys=standard Android media/voice/CALL/ENDCALL; CALL short=hook long800ms=drop; OEM input delivery unverified\n")
        val optional = GolfFeatureSettings.load(this@GolfWirelessActivity)
        append("OptionalFeatures=bootLaunch:${optional.bootLaunch} autoConnect:${optional.autoConnect} microphone:${optional.microphone} hotspotRecovery:${optional.hotspotRecovery}\n")
        append("HotspotState=${CarHotspotStatus.state(this@GolfWirelessActivity)}\n")
        GolfKeyBindings.load(this@GolfWirelessActivity).forEach {
            append("LearnedKey=${it.action.id} code=${it.keyCode} scan=${it.scanCode}\n")
        }
        append(InputDiagnosticSnapshot.report())
        append("FullscreenMethod=${if (Build.VERSION.SDK_INT < 20) "legacy-window-flags" else "window-insets"}; OEM bar visibility requires visual confirmation\n")
        val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
        append("AndroidBluetoothInterface=${adapter != null}\n")
        append("BluetoothEnabled=${runCatching { adapter?.isEnabled }.getOrNull()}\n")
        append("BondedDeviceCount=${runCatching { adapter?.bondedDevices?.size }.getOrNull()}\n")
        append("PhoneSelected=${DiPlayPreferences.phoneAddress(this@GolfWirelessActivity) != null}\n")
        append("AuthenticationReady=${setupError == null}\n")
        append("SessionActive=${CarPlayBackgroundSession.active}\n")
        append(DisplayDiagnosticSnapshot.report(applicationContext)).append('\n')
        for (name in SessionLogFile.REPORT_NAMES + listOf(GolfHardwareKeyLog.PREVIOUS_FILE_NAME, GolfHardwareKeyLog.FILE_NAME)) {
            val file = File(filesDir, "logs/$name")
            if (!file.isFile) continue
            append("\n--- $name ---\n")
            runCatching {
                file.bufferedReader().useLines { lines ->
                    lines.take(5000).forEach { line -> DiagnosticRedactor.redact(line)?.let { append(it).append('\n') } }
                }
            }.onFailure { append("Log unavailable: ${it.javaClass.simpleName}\n") }
        }
    }
    override fun onDestroy() {
        closeHardwareKeys()
        startupHandler.removeCallbacksAndMessages(null)
        exportWorker.shutdown()
        super.onDestroy()
    }
}
