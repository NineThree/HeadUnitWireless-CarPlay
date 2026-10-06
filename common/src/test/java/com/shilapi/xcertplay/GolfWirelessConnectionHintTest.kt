package com.shilapi.xcertplay

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN")
class GolfWirelessConnectionHintTest {
    @Test fun networkDiscoveryFailureShowsTheCarNetworkProblemAndReportAction() {
        val activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val method = CarPlayHostActivity::class.java.getDeclaredMethod("friendlyStage", String::class.java)
            .apply { isAccessible = true }
        for (detail in listOf("bind failed: EADDRINUSE", "SocketException")) {
            val hint = method.invoke(activity, "失败：Bonjour/mDNS UDP5353启动失败：$detail") as String
            assertTrue(hint, hint.contains("网络") && hint.contains("故障报告"))
        }
    }
}
