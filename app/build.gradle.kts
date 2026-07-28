plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.devtools.ksp)
}

android {
    namespace = "com.lianyu.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lianyu.ai.zzz"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "9.9.9"

        

        // Force multi-DEX output
        multiDexEnabled = true


        

        buildConfigField("String", "HARDENING_LEVEL", "\"OPEN_SOURCE\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    ndkVersion = "30.0.14904198"

    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = System.getenv("LIANYU_STORE_PASSWORD") ?: project.findProperty("LIANYU_STORE_PASSWORD") as String? ?: "debug_password_placeholder"
            keyAlias = System.getenv("LIANYU_KEY_ALIAS") ?: project.findProperty("LIANYU_KEY_ALIAS") as String? ?: "your_alias"
            keyPassword = System.getenv("LIANYU_KEY_PASSWORD") ?: project.findProperty("LIANYU_KEY_PASSWORD") as String? ?: "debug_password_placeholder"
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "app_name", "LianYu")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    // jniLibs are picked up automatically from src/main/jniLibs/
}



// ── 剥离 sherpa-onnx aar 内置的 libonnxruntime.so ──
// 原因：core:network / feature:chat 里的 sherpa-onnx aar 自带一份 libonnxruntime.so，
// 跟 feature:memory 直接依赖的官方 onnxruntime-android 里的 libonnxruntime.so 路径完全相同
// （lib/<abi>/libonnxruntime.so），打包时 mergeDebugNativeLibs 会因为两个不同文件同名而报错。
// sherpa 的 Java 层不暴露 onnxruntime API，它的 libonnxruntime.so 只给它自己的
// libsherpa-onnx-jni.so 内部调用；剔除后 APK 里只保留官方 onnxruntime-android 那一份，
// sherpa 运行时会动态链接到这唯一一份（ONNX Runtime C API 向后兼容，可跨版本调用）。
val sherpaOnnxRawAar = file("../core/network/libs/sherpa-onnx-1.13.3.aar")

val extractSherpaOnnxAar = tasks.register<Copy>("extractSherpaOnnxAar") {
    from(zipTree(sherpaOnnxRawAar))
    into(layout.buildDirectory.dir("sherpaAarExtract"))
}

val stripSherpaOnnxRuntime = tasks.register("stripSherpaOnnxRuntime") {
    dependsOn(extractSherpaOnnxAar)
    doLast {
        val dir = layout.buildDirectory.dir("sherpaAarExtract").get().asFile
        listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").forEach { abi ->
            val so = file("$dir/jni/$abi/libonnxruntime.so")
            if (so.exists()) {
                so.delete()
                println("Stripped libonnxruntime.so from sherpa-onnx aar ($abi)")
            }
        }
    }
}

val repackSherpaOnnxAar = tasks.register<Zip>("repackSherpaOnnxAar") {
    dependsOn(stripSherpaOnnxRuntime)
    from(layout.buildDirectory.dir("sherpaAarExtract"))
    archiveFileName.set("sherpa-onnx-1.13.3-stripped.aar")
    destinationDirectory.set(layout.buildDirectory.dir("sherpaAarOutput"))
}




dependencies {
    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":core:ui-common"))

    // Feature modules
    implementation(project(":feature:companion"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:groupchat"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:notification"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:localmodel"))
    
    implementation(project(":feature:backup"))
    

    // sherpa-onnx: 离线流式语音识别/合成，运行时由 app 模块提供
    // 注意：不直接引用原始 aar，而是引用下面 repackSherpaOnnxAar 任务生成的"剥离版"aar
    // （剔除了内置的 libonnxruntime.so，避免与 feature:memory 的官方 onnxruntime-android 冲突）
    implementation(files(repackSherpaOnnxAar.flatMap { it.archiveFile }))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.animation.core)
    implementation(libs.androidx.animation.graphics)
    implementation(libs.lottie.compose)
    implementation(libs.androidx.app.update.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.tracing)



    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

