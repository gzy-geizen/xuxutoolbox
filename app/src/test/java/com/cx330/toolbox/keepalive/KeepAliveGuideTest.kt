package com.cx330.toolbox.keepalive

import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAliveGuideTest {

    @Test
    fun `小米设备包含省电策略与自启动步骤`() {
        val steps = KeepAliveGuide.stepsFor("Xiaomi")
        assertTrue(steps.any { it.title.contains("省电策略") })
        assertTrue(steps.any { it.title.contains("自启动") })
        assertTrue(steps.any { it.target == DeepLinkTarget.ACCESSIBILITY })
    }

    @Test
    fun `红米设备同样走小米分支`() {
        val steps = KeepAliveGuide.stepsFor("Redmi")
        assertTrue(steps.any { it.title.contains("省电策略") })
    }

    @Test
    fun `OPPO 设备包含自启动步骤`() {
        val steps = KeepAliveGuide.stepsFor("OPPO")
        assertTrue(steps.any { it.title.contains("自启动") })
        assertTrue(steps.any { it.target == DeepLinkTarget.BATTERY_OPTIMIZATION })
    }

    @Test
    fun `vivo 设备包含后台耗电步骤`() {
        val steps = KeepAliveGuide.stepsFor("vivo")
        assertTrue(steps.any { it.title.contains("后台") })
    }

    @Test
    fun `未知厂商给出通用步骤`() {
        val steps = KeepAliveGuide.stepsFor("UnknownBrand")
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.any { it.target == DeepLinkTarget.BATTERY_OPTIMIZATION })
        assertTrue(steps.any { it.target == DeepLinkTarget.ACCESSIBILITY })
    }

    @Test
    fun `每一步都必须有标题与说明`() {
        val manufacturers = listOf("Xiaomi", "OPPO", "vivo", "HUAWEI", "Google")
        manufacturers.forEach { vendor ->
            KeepAliveGuide.stepsFor(vendor).forEach { step ->
                assertTrue(step.title.isNotBlank())
                assertTrue(step.detail.isNotBlank())
            }
        }
    }
}
