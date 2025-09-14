plugins {
    kotlin("jvm") version "1.9.24"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.baronofai"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/koog/maven")
    maven("https://packages.jetbrains.team/maven/p/koog/maven")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Koog dependencies (align with samples)
    val koogVersion = "0.4.0"
    implementation("ai.koog:agents-core:$koogVersion")
    implementation("ai.koog:agents-tools:$koogVersion")
    implementation("ai.koog:koog-agents:$koogVersion")
    implementation("ai.koog:providers-openai:$koogVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

kotlin {
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

tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowFatJar") {
    archiveClassifier.set("all")
    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
}
