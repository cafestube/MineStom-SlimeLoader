plugins {
    kotlin("jvm")
    `maven-publish`

}

group = "eu.cafestube"
version = "1.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")

    implementation("com.github.luben:zstd-jni:1.5.2-3")
    implementation("net.kyori:adventure-nbt:4.17.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "${project.name}-SlimeLoader"
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