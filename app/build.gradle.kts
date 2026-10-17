// :app — 组合根 + feature 层（UI 四件套，DEC-021）+ trigger 预留落点（BLE 回调/后台任务）。
// 也是架构守护测试的宿主：ArchUnit 从这里能看到全部模块。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xcwk.scalehub"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.xcwk.scalehub"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time 属 API 26；minSdk 24 由此脱糖补到低版本
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))

    implementation(libs.core.ktx)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.zxing.embedded)   // 扫码绑定体脂秤（移植补齐）
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // Material 图标全集（Material Symbols 的 Compose 侧等价物，见 QkIcons）
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.archunit)
    testImplementation(libs.coroutines.test)
}
