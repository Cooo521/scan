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
        // 33 にしておくと、アプリが前に出ているときに、ドライブの「スキャン」ボタンを
        // 代わりに押してドライブの画面を開く動きが、Androidに止められにくい
        targetSdk = 33
        versionCode = 2
        versionName = "1.1"
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
