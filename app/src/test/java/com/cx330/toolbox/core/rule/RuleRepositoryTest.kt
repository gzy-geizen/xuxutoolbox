package com.cx330.toolbox.core.rule

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleRepositoryTest {

    private val json = Json { prettyPrint = false }

    private fun payloadFor(ruleId: String): String =
        """{"version":2,"rules":[{"id":"$ruleId","priority":50,"match":{"any":[{"text":"测试跳过"}]}}]}"""

    private fun bundleFor(ruleId: String, pair: KeyPair): String {
        val payload = payloadFor(ruleId)
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(pair.private)
        signature.update(payload.toByteArray())
        return json.encodeToString(
            SignedRuleBundle(
                payload = Base64.getEncoder().encodeToString(payload.toByteArray()),
                signature = Base64.getEncoder().encodeToString(signature.sign()),
                keyId = "test-key",
            ),
        )
    }

    private fun signedBundle(ruleId: String, pair: KeyPair): String = bundleFor(ruleId, pair)

    private fun pair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    private fun publicKey(pair: KeyPair): String =
        Base64.getEncoder().encodeToString(pair.public.encoded)

    @Test
    fun `签名导入规则会与内置规则合并`() {
        val pair = pair()
        val loaded = RuleRepository.load(
            assetContents = listOf(readRuleAsset("builtin.json")),
            importedRaw = signedBundle("user.signed", pair),
            publicKeyBase64 = publicKey(pair),
        )
        assertTrue(loaded.importedSigned)
        assertTrue(loaded.errors.isEmpty())
        assertTrue(loaded.ruleSet.rules.any { it.id == "user.signed" })
        assertTrue(loaded.ruleSet.rules.any { it.id == "generic.splash.skip" })
    }

    @Test
    fun `未签名导入会被忽略并记录错误`() {
        val pair = pair()
        val loaded = RuleRepository.load(
            assetContents = listOf(readRuleAsset("builtin.json")),
            importedRaw = """{"version":2,"rules":[{"id":"plain.rule","match":{"any":[{"text":"跳过"}]}}]}""",
            publicKeyBase64 = publicKey(pair),
        )
        assertFalse(loaded.importedSigned)
        assertTrue(loaded.errors.any { it.contains("签名校验") })
        assertFalse(loaded.ruleSet.rules.any { it.id == "plain.rule" })
    }

    @Test
    fun `篡改后的签名导入会被忽略`() {
        val pair = pair()
        val payload = payloadFor("user.signed")
        val raw = bundleFor("user.signed", pair)
        val payloadB64 = Base64.getEncoder().encodeToString(payload.toByteArray())
        val tamperedPayload = payload.replace("user.signed", "user.evil")
        val tamperedB64 = Base64.getEncoder().encodeToString(tamperedPayload.toByteArray())
        val tampered = raw.replace(payloadB64, tamperedB64)
        val loaded = RuleRepository.load(
            assetContents = listOf(readRuleAsset("builtin.json")),
            importedRaw = tampered,
            publicKeyBase64 = publicKey(pair),
        )
        assertFalse(loaded.importedSigned)
        assertTrue(loaded.errors.any { it.contains("签名校验") })
        assertFalse(loaded.ruleSet.rules.any { it.id == "user.evil" })
    }

    @Test
    fun `没有导入文件时只用内置规则`() {
        val loaded = RuleRepository.load(
            assetContents = listOf(readRuleAsset("builtin.json")),
            importedRaw = null,
        )
        assertFalse(loaded.importedSigned)
        assertEquals(10, loaded.ruleSet.rules.size)
    }
}
