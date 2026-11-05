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

class RuleSignatureVerifierTest {

    private val json = Json { prettyPrint = false }
    private val payload = """{"version":2,"rules":[{"id":"user.test","match":{"any":[{"text":"跳过"}]}}]}"""

    private fun keyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    private fun bundle(pair: KeyPair, payloadText: String = payload, algorithm: String = "SHA256withRSA"): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(pair.private)
        signature.update(payloadText.toByteArray())
        val signed = signature.sign()
        return json.encodeToString(
            SignedRuleBundle(
                payload = Base64.getEncoder().encodeToString(payloadText.toByteArray()),
                signature = Base64.getEncoder().encodeToString(signed),
                keyId = "test-key",
                algorithm = algorithm,
            ),
        )
    }

    private fun publicKeyBase64(pair: KeyPair): String =
        Base64.getEncoder().encodeToString(pair.public.encoded)

    @Test
    fun `合法签名可以通过校验并返回原文`() {
        val pair = keyPair()
        val result = RuleSignatureVerifier.verify(bundle(pair), publicKeyBase64(pair))
        assertTrue(result.isSuccess)
        assertEquals(payload, result.getOrThrow().payloadJson)
        assertEquals("test-key", result.getOrThrow().keyId)
    }

    @Test
    fun `篡改 payload 后签名失败`() {
        val pair = keyPair()
        val raw = bundle(pair)
        val tampered = raw.replace(
            Base64.getEncoder().encodeToString(payload.toByteArray()),
            Base64.getEncoder().encodeToString(payload.replace("user.test", "evil.test").toByteArray()),
        )
        val result = RuleSignatureVerifier.verify(tampered, publicKeyBase64(pair))
        assertTrue(result.isFailure)
    }

    @Test
    fun `用错误公钥验证失败`() {
        val pair = keyPair()
        val other = keyPair()
        val result = RuleSignatureVerifier.verify(bundle(pair), publicKeyBase64(other))
        assertTrue(result.isFailure)
    }

    @Test
    fun `不支持的算法被拒绝`() {
        val pair = keyPair()
        val result = RuleSignatureVerifier.verify(bundle(pair, algorithm = "MD5withRSA"), publicKeyBase64(pair))
        assertTrue(result.isFailure)
    }

    @Test
    fun `非签名包会被识别出来`() {
        assertFalse(RuleSignatureVerifier.looksSigned(payload))
        val pair = keyPair()
        assertTrue(RuleSignatureVerifier.looksSigned(bundle(pair)))
    }

    @Test
    fun `内置公钥可以正常解析`() {
        val bytes = Base64.getDecoder().decode(BuiltinKeys.PUBLIC_KEY_B64)
        val key = java.security.KeyFactory.getInstance("RSA")
            .generatePublic(java.security.spec.X509EncodedKeySpec(bytes))
        assertEquals("RSA", key.algorithm)
    }
}
