plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "info.socrtwo.quillbox"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktor = "3.0.3"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor")
    implementation("io.ktor:ktor-server-cors-jvm:$ktor")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Jakarta (Angus) Mail — the backend speaks IMAP/POP3/SMTP on behalf of the browser.
    implementation("org.eclipse.angus:angus-mail:2.0.3")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("info.socrtwo.quillbox.web.ApplicationKt")
}
