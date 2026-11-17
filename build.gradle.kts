import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

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

/**
 * 发布签名：读仓库根目录的 `keystore.properties`（**不入库**，`.gitignore` 已排除）。
 *
 * 文件不存在、或里面指的 .jks 不存在时**回退 debug 签名** —— 保证任何人 clone 下来都能直接
 * `assembleRelease` 装机试用，不会因为缺密钥编译失败。回退时会打一条 warning，别忽略它：
 * 用 debug 签名的包不能对外分发（同一套 debug key 任何人都能生成，谁都能造一个同包名的包）。
 *
 * 模板见 `keystore.properties.example`。
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.isFile) FileInputStream(keystorePropsFile).use { load(it) }
}
val releaseStoreFile = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it) }
val hasReleaseKey = releaseStoreFile != null && releaseStoreFile.isFile

android {
    namespace = "com.ayang.giftoolkit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ayang.giftoolkit"
        // 33：miuix-blur 的下限，也是 OS4 玻璃（RuntimeShader）能正常工作且不降级的起点
        minSdk = 33
        targetSdk = 36
        versionCode = 7
        versionName = "1.5"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // minSdk 33：v1（JAR 签名）没有意义，只留 v2 + v3。
                // 实测 AGP 在 minSdk ≥ 33 时会把 v2 当作冗余省掉，产物只有 v3 —— 这对 13+ 的
                // 设备是完整的；留 v2 这一行是为了以后 minSdk 下调时不用再改。
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "release 没有配置发布签名（缺 keystore.properties 或其中的 .jks），" +
                        "本次回退 debug 签名 —— 这样的包只能自己装机，不能对外分发。",
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
