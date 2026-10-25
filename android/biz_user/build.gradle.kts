plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // Compose编译器版本由根工程与Kotlin统一，避免多模块重复加载。
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.entry.biz.user"
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

    // 确保开启了 Compose
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("com.google.android.material:material:1.10.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")

    // Jetpack Compose 依赖库 (使用 BOM 统一版本)
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Android Studio预览渲染工具；沿用Compose BOM版本，仅用于Debug。
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Lifecycle 与 ViewModel 针对 Compose 的扩展（支持 collectAsState 和 viewmodel()）
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    // Jetpack Compose 导航组件
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.activity:activity-compose:1.8.2")

    api(project(":lib_storage"))
    api(project(":lib_net"))
    api(project(":lib_base"))
}
