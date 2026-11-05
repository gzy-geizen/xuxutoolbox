package com.cx330.toolbox.core.rule

/**
 * 内置公钥（Base64 SPKI，RSA 2048）。
 *
 * 对应私钥只在开发者本机 `keys/rule-signing.pkcs8`（已加入 .gitignore，不进版本库）。
 * 用 `tools/RuleSigner.java` 或 `scripts/sign-rules.ps1` 给规则 JSON 签名。
 */
object BuiltinKeys {
    const val KEY_ID = "builtin-1"

    const val PUBLIC_KEY_B64: String =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwndXhPuAV45FTe9tgWgOlmL1b6kTZ0VJ/lKDpF6K60BsNzOSXQg5RL7Gd5DlOfrX/wuRYtntI5JkW1GsazOGI5uRGTkz8jusZNC8Ktt/6RK6OdXVS1nSbK6Q1WyH34NfY8zQJCf6UhzSEiUkkvCroPVf3+13Er7gTh17iQcGph6Pw501IPQjtsIS9zj0MWk/XSNHLQiWG25oQMp/ILlV/sSRUfHmM0UbXPDQKH92kc4jIdiLLwQApPvU3a47kT0bPGDyNbys4JqMHCfKnDCrSb8jteoRG3LGgsL8RySKpW8F/e8Wu408ZQQXcBJvO65rRt0EiaZAmIqIVBj4UKhIuQIDAQAB"
}
