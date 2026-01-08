plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.wanghuixian.a202305100122.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wanghuixian.a202305100122.myapplication"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // 原有基础依赖
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 确保有 AndroidX Core 依赖
    implementation("androidx.core:core:1.12.0")

    // Gson（序列化附件）
    implementation("com.google.code.gson:gson:2.10.1")
    // 权限适配
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // Room数据库依赖（完整启用）
    val roomVersion = "2.6.1" // 变量名规范：无下划线
    implementation("androidx.room:room-runtime:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    // 生命周期协程依赖（必须启用）
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}

// kapt配置块（必须保留）
kapt {
    correctErrorTypes = true
}