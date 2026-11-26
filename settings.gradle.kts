pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "GifToolkit"

// Miuix 以 composite build 引入。
//
// 它没有把 glass / glass-icons 两个模块发布到 Maven Central（截至 0.9.4），
// 所以只能从源码构建：本工程要求同级目录下有一份 Miuix 源码。
//
// 一键准备：./scripts/setup-miuix.sh
// 或手动：  git clone https://github.com/compose-miuix-ui/miuix.git ../miuix
// 也可以在环境变量 MIUIX_DIR 里指定其他路径。
val miuixDir = providers.environmentVariable("MIUIX_DIR").orNull
    ?.let { file(it) }
    ?: file("../miuix")

if (!miuixDir.exists()) {
    error(
        """
        |找不到 Miuix 源码目录：${miuixDir.absolutePath}
        |
        |本工程通过 composite build 引用 Miuix 的 main 分支（glass 模块未发布到 Maven Central）。
        |请先在本工程同级目录准备一份源码：
        |
        |    git clone https://github.com/compose-miuix-ui/miuix.git ../miuix
        |
        |或直接运行：./scripts/setup-miuix.sh（会自动克隆并打上必需的上游补丁）
        """.trimMargin(),
    )
}

includeBuild(miuixDir)
