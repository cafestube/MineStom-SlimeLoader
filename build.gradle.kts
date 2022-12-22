import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.0"

    `maven-publish`
    `java-library`
}

group = "eu.cafestube"
version = "1.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Minestom:Minestom:809d9516b2")
    implementation("com.github.luben:zstd-jni:1.5.2-3")
}

val compileKotlin: KotlinCompile by tasks

compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf("-Xinline-classes")
    jvmTarget = "17"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
                url = uri("https://repo.cafestu.be/repository/maven-public-snapshots/")
            }
        }
    }
}