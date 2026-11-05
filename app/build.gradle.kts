import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 发布签名：keystore.properties 存在且有效时使用正式密钥；否则退回 debug 签名
// （仅用于本地验证 release 构建，不能作为最终发布包）。
// 兼容 Windows PowerShell 写出的 UTF-8 BOM，以及 storeFile 的相对路径写法差异。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        val bytes = keystorePropertiesFile.readBytes()
        val hasBom = bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        val text = if (hasBom) {
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        } else {
            String(bytes, Charsets.UTF_8)
        }
        load(text.reader())
    }
}
val releaseKeystoreFile: File? = keystoreProperties.getProperty("storeFile")?.let { raw ->
    listOf(
        rootProject.file(raw),
        rootProject.file(raw.removePrefix("../")),
        rootProject.file("keystore/cx330.jks"),
    ).firstOrNull { it.exists() }
}
val hasReleaseKeystore = releaseKeystoreFile != null &&
    keystoreProperties.getProperty("storePassword") != null &&
    keystoreProperties.getProperty("keyAlias") != null &&
    keystoreProperties.getProperty("keyPassword") != null

// 允许「本地验证用」debug 签名的 release 构建，必须显式传 -PallowDebugSigning=true，
// 避免误把 debug 签名包装成正式包发出去。
val allowDebugSigning = providers.gradleProperty("allowDebugSigning").orNull.toBoolean()

android {
    namespace = "com.cx330.toolbox"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cx330.toolbox"
        minSdk = 31
        targetSdk = 36
        versionCode = 8
        versionName = "1.2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = requireNotNull(releaseKeystoreFile)
                storePassword = requireNotNull(keystoreProperties.getProperty("storePassword"))
                keyAlias = requireNotNull(keystoreProperties.getProperty("keyAlias"))
                keyPassword = requireNotNull(keystoreProperties.getProperty("keyPassword"))
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // 没有正式 keystore 时仅用于本地验证 release 构建，不可直接发行。
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// release 构建安全门：没有正式 keystore 时，除非显式允许，否则直接失败，
// 防止把 debug 签名的包当成正式包发出去。
// 放在配置阶段（而不是任务 doFirst 闭包）执行，避免配置缓存序列化 Gradle 脚本对象。
val requestedReleaseBuild = gradle.startParameter.taskNames.any { it.endsWith("Release") }
if (requestedReleaseBuild && !hasReleaseKeystore && !allowDebugSigning) {
    error(
        "release 构建需要有效的 keystore.properties；" +
            "若仅本地验证构建，请显式加 -PallowDebugSigning=true（产物不可对外分发）。",
    )
}
