package com.cx330.toolbox.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigSnapshotTest {

    private fun snapshot(
        strictMode: Boolean = false,
        strictPackages: Set<String> = emptySet(),
        loosePackages: Set<String> = emptySet(),
    ) = ConfigSnapshot(
        enabled = true,
        dryRun = true,
        excludedPackages = emptySet(),
        disabledPackages = emptySet(),
        maxLaunchAgeMs = 15_000,
        strictMode = strictMode,
        strictPackages = strictPackages,
        loosePackages = loosePackages,
    )

    @Test
    fun `follows global strict mode when no per app override`() {
        assertTrue(snapshot(strictMode = true).isStrictFor("com.example.a"))
        assertFalse(snapshot(strictMode = false).isStrictFor("com.example.a"))
    }

    @Test
    fun `per app strict overrides global loose`() {
        val config = snapshot(strictMode = false, strictPackages = setOf("com.example.a"))
        assertTrue(config.isStrictFor("com.example.a"))
        assertFalse(config.isStrictFor("com.example.b"))
    }

    @Test
    fun `per app loose overrides global strict`() {
        val config = snapshot(strictMode = true, loosePackages = setOf("com.example.b"))
        assertFalse(config.isStrictFor("com.example.b"))
        assertTrue(config.isStrictFor("com.example.a"))
    }

    @Test
    fun `strict and loose sets cannot both apply to same package simultaneously`() {
        // 写入侧保证互斥；读取侧仍以 strict 优先，避免脏数据误放开兜底。
        val config = snapshot(
            strictMode = false,
            strictPackages = setOf("com.example.a"),
            loosePackages = setOf("com.example.a"),
        )
        assertTrue(config.isStrictFor("com.example.a"))
    }
}
