import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    application
}

group = "me.cormand"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        setUrl("https://m2.dv8tion.net/releases")
    }
}

dependencies {
    implementation("dev.kord:kord-core:0.8.0-M7")
    implementation("io.github.cdimascio:dotenv-kotlin:6.2.2")
    implementation("com.sedmelluq:lavaplayer:1.3.77")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}