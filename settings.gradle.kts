pluginManagement {
    repositories {
        // 国内网络环境：阿里云镜像优先，官方源兜底（google() 直连超时会拖垮整个构建）。
        // 例外：GitHub runner 访问阿里云会间歇性返回 502，而 Gradle 在插件解析遇 5xx
        // 时直接终止、不再回退后续仓库（见 DEC-032），故 CI 上跳过镜像只用官方源。
        if (System.getenv("CI") != "true") {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 同 pluginManagement：CI 上避开对 runner 返回 502 的阿里云镜像。
        if (System.getenv("CI") != "true") {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "qk-health-v2"
include(":domain", ":application", ":infrastructure", ":app")
