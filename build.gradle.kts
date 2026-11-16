import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application") version "9.5.1"
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
            implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
            implementation("org.jetbrains.compose.ui:ui:1.12.2")
            implementation("androidx.navigationevent:navigationevent-compose:1.2.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.13.0")

            // Miuix：由 ../miuix 这个 composite build 提供（group: top.yukonga.miuix.kmp）
            implementation("top.yukonga.miuix.kmp:miuix-ui")
            implementation("top.yukonga.miuix.kmp:miuix-preference")
            implementation("top.yukonga.miuix.kmp:miuix-icons")
            // miuix-material：GlassDefaults / GlassStyle / isRenderEffectSupported 在这里，
            // glass 模块不会把它们透出来，OS4 化必须显式依赖。
            implementation("top.yukonga.miuix.kmp:miuix-material")
            implementation("top.yukonga.miuix.kmp:miuix-glass")
            // 反射 ApplicationInfo.setEnableOnBackInvokedCallback 需要解隐藏 API 豁免
            implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
            implementation("top.yukonga.miuix.kmp:miuix-glass-icons")
            implementation("top.yukonga.miuix.kmp:miuix-nav")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.13.0")
            implementation("androidx.core:core-ktx:1.17.0")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        }
    }
}

android {
    namespace = "com.ayang.giftoolkit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ayang.giftoolkit"
        // 33：miuix-blur 的下限，也是 OS4 玻璃（RuntimeShader）能正常工作且不降级的起点
        minSdk = 33
        targetSdk = 36
        versionCode = 6
        versionName = "1.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
