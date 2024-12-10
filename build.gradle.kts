import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"

    `maven-publish`
    `java-library`
}

group = "eu.cafestube"
version = "1.2.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.minestom:minestom-snapshots:d760a60a5c")
    implementation("com.github.luben:zstd-jni:1.5.2-3")

    api(project(":Common"))
}

val compileKotlin: KotlinCompile by tasks

compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf("-Xinline-classes")
    jvmTarget = "21"
}

kotlin {
    jvmToolchain(21)
}


java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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