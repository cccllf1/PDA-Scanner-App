plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.yourcompany.pdawmsapp"
    compileSdk = 28  // 降低到适合Android 6.0的版本

    defaultConfig {
        applicationId = "com.yourcompany.pdawmsapp"
        minSdk = 23
        targetSdk = 23  // 专门针对Android 6.0
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // 降低版本以兼容Android 6.0
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.core:core-ktx:1.6.0")
    
    // 网络请求库 - 降低版本
    implementation("com.squareup.okhttp3:okhttp:3.14.9")  // 最后支持Android 6.0的版本
    implementation("com.squareup.retrofit2:retrofit:2.6.4")
    implementation("com.squareup.retrofit2:converter-gson:2.6.4")
    
    // WebSocket支持 - 使用兼容版本
    implementation("org.java-websocket:Java-WebSocket:1.3.9")
    
    // JSON解析
    implementation("com.google.code.gson:gson:2.8.6")
    
    // UI组件 - 降低版本
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("com.google.android.material:material:1.4.0")
    
    // 协程支持 - 兼容版本
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}