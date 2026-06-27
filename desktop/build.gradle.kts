import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
}

group = "info.socrtwo.quillbox"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // Jakarta (Angus) Mail — IMAP/POP3/SMTP. Runs on the desktop JVM as-is.
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

compose.desktop {
    application {
        mainClass = "info.socrtwo.quillbox.desktop.MainKt"
        nativeDistributions {
            // .dmg builds on macOS, .msi on Windows, .deb on Linux.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Quillbox"
            packageVersion = "1.0.0"
            description = "Quillbox desktop email client"
        }
    }
}
