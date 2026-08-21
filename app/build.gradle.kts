import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 读取签名配置（keystore.properties 已加入 .gitignore 严禁提交；不存在时回退本地文件）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

// 读取 local.properties（不在版本控制中），用于问题反馈的 GitHub Token
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val githubToken = localProps.getProperty("GITHUB_TOKEN") ?: ""
val wxpusherAppToken = localProps.getProperty("WXPUSHER_APPTOKEN") ?: ""
val wxpusherUid = localProps.getProperty("WXPUSHER_UID") ?: ""

android {
    namespace = "com.orangeway.iptv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.orangeway.iptv"
        minSdk = 23
        targetSdk = 37
        versionCode = 5
        versionName = "1.0.4"
        // 问题反馈用的 GitHub Token（仅创建 Issue 权限），未配置时提交会提示失败
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
        // 问题反馈用 WxPusher 微信推送配置，未配置时提交会提示失败
        buildConfigField("String", "WXPUSHER_APPTOKEN", "\"$wxpusherAppToken\"")
        buildConfigField("String", "WXPUSHER_UID", "\"$wxpusherUid\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile", "release.jks"))
            storePassword = keystoreProps.getProperty("storePassword", "")
            keyAlias = keystoreProps.getProperty("keyAlias", "hereiam")
            keyPassword = keystoreProps.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// AGP 9 内置 Kotlin：使用 kotlin {} 扩展配置编译器选项（替代已移除的 kotlinOptions）
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    // FFmpeg 软解码扩展（media3 1.9.0 对应版本，含 native .so 库）
    // 支持 mp3/aac/vorbis/opus/flac 软解码，解决设备不支持音频编码问题
    implementation(files("libs/lib-decoder-ffmpeg-release.aar"))
    debugImplementation(libs.androidx.ui.tooling)

    // hCaptcha 人机验证（Jetpack Compose 原生 SDK，替代自研验证码）
    // 反馈前端在 SDK 容器内渲染 checkbox，验证通过后携带 token 交由 Worker 服务端校验
    implementation("com.github.hCaptcha.hcaptcha-android-sdk:compose-sdk:5.0.1")
    // hCaptcha SDK 依赖 FragmentActivity，需显式引入 fragment-ktx
    implementation("androidx.fragment:fragment-ktx:1.8.5")
}