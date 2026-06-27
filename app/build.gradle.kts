import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// Load signing properties from local.properties (never committed) or environment
// variables. Nothing here is hardcoded — if no keystore is configured the release
// signingConfig is simply left unset and assembleRelease will report it.
val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProperties.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

android {
    namespace = "info.socrtwo.quillbox"
    compileSdk = 35

    defaultConfig {
        applicationId = "info.socrtwo.quillbox"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Release signing reads from local.properties or env vars. Keys:
        //   RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD
        // (env equivalents: QUILLBOX_RELEASE_STORE_FILE, _STORE_PASSWORD, _KEY_ALIAS, _KEY_PASSWORD)
        val storeFilePath = signingValue("RELEASE_STORE_FILE", "QUILLBOX_RELEASE_STORE_FILE")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("RELEASE_STORE_PASSWORD", "QUILLBOX_RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("RELEASE_KEY_ALIAS", "QUILLBOX_RELEASE_KEY_ALIAS")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD", "QUILLBOX_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only attach the release signingConfig when a keystore is actually configured.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // Jakarta/Angus Mail ships duplicate metadata across its jars; keep one copy
            // of each so the mail providers still resolve on Android.
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/*.kotlin_module"
            )
            pickFirsts += setOf(
                "META-INF/javamail.default.providers",
                "META-INF/javamail.default.address.map",
                "META-INF/javamail.providers",
                "META-INF/javamail.address.map",
                "META-INF/mailcap",
                "META-INF/mailcap.default",
                "META-INF/mimetypes.default"
            )
        }
    }
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.13.1")

    // Lifecycle / ViewModel / Flows
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Compose (Material 3)
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Hilt (DI)
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room (persistence)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Jakarta Mail (IMAP / POP3 / SMTP) — Eclipse Angus implementation. This transitively
    // brings angus-activation + jakarta.activation-api (DataHandler etc.), so no separate
    // activation dependency is declared (doing so would duplicate those classes).
    implementation("org.eclipse.angus:angus-mail:2.0.3")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
