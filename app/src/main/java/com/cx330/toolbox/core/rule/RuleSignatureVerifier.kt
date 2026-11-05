package com.cx330.toolbox.core.rule

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.json.Json

/**
 * 规则包签名校验（SHA256withRSA）。
 *
 * 规则能驱动点击，等价于「远程控制手机」，所以导入的规则必须验签；
 * 验签失败则整包丢弃，不退回明文规则。
 */
object RuleSignatureVerifier {

    private val json = Json { ignoreUnknownKeys = true }

    data class Verified(val payloadJson: String, val keyId: String)

    fun verify(bundleJson: String, publicKeyBase64: String): Result<Verified> = runCatching {
        val bundle = json.decodeFromString<SignedRuleBundle>(bundleJson)
        require(bundle.algorithm == "SHA256withRSA") {
            "不支持的签名算法: ${bundle.algorithm}"
        }
        val payloadBytes = Base64.getDecoder().decode(bundle.payload)
        val signatureBytes = Base64.getDecoder().decode(bundle.signature)
        val keyBytes = Base64.getDecoder().decode(publicKeyBase64)

        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(keyBytes))
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(publicKey)
        signature.update(payloadBytes)
        require(signature.verify(signatureBytes)) { "签名校验失败" }

        Verified(payloadJson = String(payloadBytes, Charsets.UTF_8), keyId = bundle.keyId)
    }

    /** 判断一段文本是否「看起来像」签名规则包（用于导入页提示）。 */
    fun looksSigned(raw: String): Boolean =
        raw.contains("\"payload\"") && raw.contains("\"signature\"")
}
