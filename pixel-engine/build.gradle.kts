import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.purride.pixelengine"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildToolsVersion = "36.0.0"
}

/**
 * 强制 main 源集所有公开 API 显式标注 visibility。
 *
 * 已切到 `Strict` 模式：未标 visibility 的公开/protected 成员、未声明
 * 返回类型的 public fun 都会变成编译错误，永久守住公开 API 表达明确性
 * 的红线。如需临时回退到 `Warning` 做大规模迁移，切换枚举即可。
 */
kotlin {
    explicitApi = ExplicitApiMode.Strict
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
