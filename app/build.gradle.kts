import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun configuredValue(propertyName: String, envName: String): String? =
    localProperties.getProperty(propertyName)
        ?.takeIf(String::isNotBlank)
        ?: System.getenv(envName)?.takeIf(String::isNotBlank)

val releaseSigningStoreFile = configuredValue(
    propertyName = "lura.signing.storeFile",
    envName = "LURA_SIGNING_STORE_FILE"
)
val releaseSigningStorePassword = configuredValue(
    propertyName = "lura.signing.storePassword",
    envName = "LURA_SIGNING_STORE_PASSWORD"
)
val releaseSigningKeyAlias = configuredValue(
    propertyName = "lura.signing.keyAlias",
    envName = "LURA_SIGNING_KEY_ALIAS"
)
val releaseSigningKeyPassword = configuredValue(
    propertyName = "lura.signing.keyPassword",
    envName = "LURA_SIGNING_KEY_PASSWORD"
)
val releaseSigningProperties = mapOf(
    "lura.signing.storeFile" to releaseSigningStoreFile,
    "lura.signing.storePassword" to releaseSigningStorePassword,
    "lura.signing.keyAlias" to releaseSigningKeyAlias,
    "lura.signing.keyPassword" to releaseSigningKeyPassword
)
val releaseSigningConfigured = releaseSigningProperties.values.any { it != null }
val missingReleaseSigningProperties = releaseSigningProperties
    .filterValues { it == null }
    .keys

if (releaseSigningConfigured && missingReleaseSigningProperties.isNotEmpty()) {
    throw GradleException(
        "Release signing is partially configured. Missing: " +
            missingReleaseSigningProperties.joinToString()
    )
}

if (releaseSigningConfigured && !rootProject.file(releaseSigningStoreFile!!).isFile) {
    throw GradleException("Release signing keystore does not exist: $releaseSigningStoreFile")
}

android {
    namespace = "com.example.lura"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.lura"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseSigningStoreFile!!)
                storePassword = releaseSigningStorePassword
                keyAlias = releaseSigningKeyAlias
                keyPassword = releaseSigningKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
