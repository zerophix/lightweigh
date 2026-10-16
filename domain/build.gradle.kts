// :domain — 纯 JVM 模块（不是 Android 模块！）
// 这是本脚手架相对 Java 版更硬的一条纪律：
// 没有 AGP 就没有 android.jar，领域层依赖 Android SDK 会直接编译失败，
// Java 版靠 ArchUnit 守的规则，这里由编译器强制。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<JavaCompile> { options.release.set(17) }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Flow 作为出站端口(ScaleGateway)的返回类型；coroutines-core 是纯 JVM 库
    api(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
