plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize") // for @Parcelize
    // 必须引入 Kotlin 2.x 的 compose 插件
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

android {
    namespace = "com.entry.lib.base"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.compose.runtime:runtime:1.7.8")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.google.android.material:material:1.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")

    implementation("androidx.startup:startup-runtime:1.1.1")
    //api("com.tencent:mmkv:2.4.2")
    api("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
}