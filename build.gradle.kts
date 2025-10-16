plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.fighting.study"
version = "1.2.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    // 添加包含 zai-sdk 的 Maven 仓库
    maven("https://oss.sonatype.org/content/repositories/snapshots") // 根据实际仓库地址调整
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        // 调试的idea 版本
        create("IC", "2025.2.2")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
    // 添加 zai-sdk 依赖
    implementation("ai.z.openapi:zai-sdk:0.0.6")

    // 添加 SLF4J 依赖
    implementation("org.slf4j:slf4j-api:2.0.9")
    // 选择一个 SLF4J 实现，例如：
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

intellijPlatform {
    pluginConfiguration {
        // 设置插件的兼容版本,开始版本
        ideaVersion {
            sinceBuild = "242"
        }
        // 设置插件的变更说明
        changeNotes = """
            English:
            <ul>
                <li>Fixed an issue where today's commit record was incomplete</li>
            </ul>
            
            中文:
            <ul>
                <li>修复今日提交记录获取不完整问题</li>
            </ul>
        """.trimIndent()
    }
}

tasks {
    // 设置 JVM 兼容性版本
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
