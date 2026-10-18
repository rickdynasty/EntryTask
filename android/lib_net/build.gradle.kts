plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.entry.lib.net"
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
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("com.google.android.material:material:1.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")

    implementation(project(":lib_base"))
    api("com.squareup.okhttp3:okhttp:4.12.0")
    // 可选：日志拦截器
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Retrofit 核心库
    api("com.squareup.retrofit2:retrofit:2.9.0")
    // Gson 转换器支持（解决 converter 报错的关键）
    api("com.squareup.retrofit2:converter-gson:2.9.0")
    // Gson 序列化库
    api("com.google.code.gson:gson:2.10.1")


    implementation(project(":lib_storage"))
    implementation(project(":lib_base"))
}