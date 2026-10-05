import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.compose.screenshot")
}

val releaseSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("signing.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { input -> load(input) }
    }
}

val releaseSigningValue: (String, String) -> String? = { propertyName, environmentName ->
    releaseSigningProperties
        .getProperty(propertyName)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable(environmentName).orNull?.trim()?.takeIf { it.isNotEmpty() }
}

val releaseStoreFile = releaseSigningValue("storeFile", "HAPPYTALKY_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("storePassword", "HAPPYTALKY_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "HAPPYTALKY_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "HAPPYTALKY_RELEASE_KEY_PASSWORD")
val releaseSigningReady =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

android {
    namespace = "com.xldev.happytalky.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xldev.happytalky"
        minSdk = 30
        targetSdk = 36
        versionCode = 9
        versionName = "0.4.3"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildFeatures {
        compose = true
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(releaseSigningReady) {
            "Release signing is not configured. Copy signing.properties.example to signing.properties and fill it, or set the HAPPYTALKY_RELEASE_* environment variables."
        }
        check(rootProject.file(releaseStoreFile!!).isFile) {
            "Release signing keystore not found: $releaseStoreFile"
        }
    }
}


dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")

    implementation(project(":core"))
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.wear.compose:compose-material3:1.7.0")
    implementation("androidx.wear:wear-input:1.2.0")

    debugImplementation("org.jsoup:jsoup:1.23.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")

    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha16")
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
}
