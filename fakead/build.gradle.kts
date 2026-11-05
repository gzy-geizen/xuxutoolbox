// 测试桩模块：一个假的「开屏广告」页面，用于验证无障碍判定流水线。
// 它不参与正式发布，只是开发/回归用的可控对手。
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.cx330.fakead"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cx330.fakead"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
