plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.cooo521.kantanscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.cooo521.kantanscan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    // Googleのドキュメントスキャナー（撮影・切り抜き・汚れ消しの画面はGoogle Play開発者サービスが用意する）
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
}
