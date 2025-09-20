import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    application
}

group = "com.baronofai"
version = "1.0-SNAPSHOT"
val logbackVersion = "1.5.18"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/koog/maven")
    maven("https://packages.jetbrains.team/maven/p/koog/maven")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")


    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Koog dependencies (align with samples)
    val koogVersion = "0.4.1"
    implementation("ai.koog:agents-core:$koogVersion")
    implementation("ai.koog:agents-tools:$koogVersion")
    implementation("ai.koog:koog-agents:$koogVersion")
    //implementation("ai.koog:providers-openai:$koogVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

kotlin {
    // Use the locally installed JDK 24 to compile and run
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDirs("WADTool/src", "KoogDsl/src")
        resources.srcDirs("WADTool/data")
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "wadtool.MainKt"
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

