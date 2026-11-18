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
    // Miuix 0.9.5-beta01（3177a684）起把自身编译目标提到 SDK 37.2，
    // 下游必须同档编译，否则 AGP 在配置期直接拒绝（"requires compile against 37.2 or later"）。
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
        versionCode = 9
        versionName = "1.7"
    }

    buildTypes {
        // `DEBUG_UI` 是**字面量**布尔（不是 AGP 默认那个 `Boolean.parseBoolean("...")`），
        // 所以 Kotlin 会把它当编译期常量内联，R8 也就能把 `if (BuildConfig.DEBUG_UI)` 的分支
        // 连同里面的界面与文案整段删掉。release 包的验收方式是
        // "dex 里搜不到「开始按钮配色对比」"（`docs/os4-round10-audit.md` §8.3）。
        debug {
            buildConfigField("boolean", "DEBUG_UI", "true")
        }
        release {
            buildConfigField("boolean", "DEBUG_UI", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
