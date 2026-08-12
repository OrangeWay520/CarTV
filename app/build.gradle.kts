import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        versionCode = 1
        versionName = "1.0.0"
        // 问题反馈用的 GitHub Token（仅创建 Issue 权限），未配置时提交会提示失败
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
        // 问题反馈用 WxPusher 微信推送配置，未配置时提交会提示失败
        buildConfigField("String", "WXPUSHER_APPTOKEN", "\"$wxpusherAppToken\"")
        buildConfigField("String", "WXPUSHER_UID", "\"$wxpusherUid\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}