import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0"

    `maven-publish`
    `java-library`
}

group = "eu.cafestube"
version = "1.5.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.minestom:minestom:2025.12.19-1.21.10")
    implementation("com.github.luben:zstd-jni:1.5.2-3")
    compileOnly("it.unimi.dsi:fastutil:8.5.18")

    api(project(":Common"))
}

kotlin {
    jvmToolchain(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
        }
        repositories {
            maven {
                name = "cafestubeRepository"
                credentials(PasswordCredentials::class)
                url = uri("https://repo.cafestube.net/repository/maven-public-snapshots/")
            }
        }
    }
}