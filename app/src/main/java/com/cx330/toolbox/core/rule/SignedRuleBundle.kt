package com.cx330.toolbox.core.rule

import kotlinx.serialization.Serializable

/**
 * 签名规则包格式（S12）。
 *
 * payload 是原始规则 JSON 的 Base64；签名算法为 SHA256withRSA。
 * App 内置公钥，只接受验签通过的规则包，防止规则来源被篡改。
 */
@Serializable
data class SignedRuleBundle(
    val payload: String,
    val signature: String,
    val keyId: String = "builtin-1",
    val algorithm: String = "SHA256withRSA",
)
