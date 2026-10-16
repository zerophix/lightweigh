// :infrastructure — Android 库模块：端口的实现层（Room/BLE/云同步）。
// 依赖方向：infrastructure → application + domain（实现它们的端口），绝不反向。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xcwk.scalehub.infrastructure"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// MigrationTestHelper 从 androidTest assets 读 schema 快照（G3 真实迁移测试）
android {
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

// Room schema 快照导出（入库，作为 MigrationTest 的依据）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":domain"))
    implementation(project(":application"))

    // AppDatabase 属于本模块公开 API，其超类型 RoomDatabase 必须对消费方可见
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
