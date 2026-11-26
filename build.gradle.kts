import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 必须与 ../miuix 的 gradle/libs.versions.toml 完全同版本：composite build 里
    // 两个独立构建共用一个 AGP，不一致时在依赖解析期报 "Using multiple versions ..."。
    // 0.9.5-rc01（6d784f19）把上游 AGP 从 9.5.2 提到 9.5.3，这里同步。
    id("com.android.application") version "9.5.3"
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.13.0-rc01"
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.13.0-rc01")
            implementation("org.jetbrains.compose.foundation:foundation:1.13.0-rc01")
            implementation("org.jetbrains.compose.ui:ui:1.13.0-rc01")
            implementation("androidx.navigationevent:navigationevent-compose:1.2.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.13.0")

            // Miuix：由 ../miuix 这个 composite build 提供（group: top.yukonga.miuix.kmp）
            implementation("top.yukonga.miuix.kmp:miuix-ui")
            implementation("top.yukonga.miuix.kmp:miuix-preference")
            implementation("top.yukonga.miuix.kmp:miuix-icons")
            // miuix-material：GlassDefaults / GlassStyle / isRenderEffectSupported 在这里，
            // glass 模块不会把它们透出来，OS4 化必须显式依赖。
            implementation("top.yukonga.miuix.kmp:miuix-material")
            // miuix-squircle：OS4 的连续曲率圆角（squircleClip / squircleBackground），
            // 关于页的应用图标用它，而不是自己 clip 一个 RoundedCornerShape。
            // 不支持 RuntimeShader 的设备上它内部会自动退回普通圆角，不需要调用方判断。
            implementation("top.yukonga.miuix.kmp:miuix-squircle")
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
    // Miuix 0.9.5-beta01 起把自身编译目标提到 SDK 37.2，
    // 下游必须同档编译，否则 AGP 在配置期直接拒绝（"requires compile against 37.2 or later"）。
    // 基线提交跟进到 f3829d3d（v0.9.5-beta02 + 3 个 main 提交）后这条要求未变。
    // 这里只影响编译期可用 API，targetSdk 仍是 36，不改运行时行为。
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.ayang.giftoolkit"
        // 33：miuix-blur 的下限，也是 OS4 玻璃（RuntimeShader）能正常工作且不降级的起点
        minSdk = 33
        targetSdk = 36
        versionCode = 19
        versionName = "1.17"
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
