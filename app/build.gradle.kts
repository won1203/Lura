import java.util.Properties
import java.net.URI
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

fun normalizeBaseUrl(baseUrl: String): String =
    if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

fun configuredValues(propertyName: String, envName: String): List<String> =
    configuredValue(propertyName, envName)
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        .orEmpty()

fun distinctNormalizedBaseUrls(values: List<String>): List<String> =
    values.map(::normalizeBaseUrl).distinct()

val debugApiBaseUrl = normalizeBaseUrl(
    configuredValue("lura.debugApiBaseUrl", "LURA_DEBUG_API_BASE_URL")
        ?: configuredValue("lura.apiBaseUrl", "LURA_API_BASE_URL")
        ?: "http://10.0.2.2:8080/"
)

val debugApiBaseUrls = distinctNormalizedBaseUrls(
    configuredValues("lura.debugApiBaseUrls", "LURA_DEBUG_API_BASE_URLS") +
        debugApiBaseUrl +
        listOf(
            "http://10.0.2.2:8080/",
            "http://127.0.0.1:8080/"
        )
)

val releaseApiBaseUrl = configuredValue(
    propertyName = "lura.releaseApiBaseUrl",
    envName = "LURA_RELEASE_API_BASE_URL"
)?.let(::normalizeBaseUrl)

fun validateReleaseApiBaseUrl(baseUrl: String) {
    val uri = runCatching { URI(baseUrl) }.getOrElse {
        throw GradleException("Release API base URL is invalid: $baseUrl")
    }
    val host = uri.host?.lowercase()
        ?: throw GradleException("Release API base URL must include a host: $baseUrl")

    if (!uri.scheme.equals("https", ignoreCase = true)) {
        throw GradleException("Release API base URL must use HTTPS: $baseUrl")
    }

    val isLocalHost = host == "localhost" ||
        host.endsWith(".localhost") ||
        host.endsWith(".local")
    val isIpv4Address = host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
    val isPrivateIpv4Address = when {
        host.startsWith("10.") -> true
        host.startsWith("192.168.") -> true
        host.startsWith("127.") -> true
        host.startsWith("169.254.") -> true
        host.startsWith("172.") -> {
            val secondOctet = host.split(".").getOrNull(1)?.toIntOrNull()
            secondOctet in 16..31
        }
        else -> false
    }
    val isLocalIpv6Address = host == "::1" ||
        host.startsWith("fc") ||
        host.startsWith("fd") ||
        host.startsWith("fe80:")

    if (isLocalHost || isPrivateIpv4Address || isLocalIpv6Address) {
        throw GradleException(
            "Release API base URL must be a public backend domain, not a local address: $baseUrl"
        )
    }
    if (isIpv4Address) {
        throw GradleException(
            "Release API base URL must use a public HTTPS domain so the endpoint can move without rebuilding: $baseUrl"
        )
    }
}

releaseApiBaseUrl?.let(::validateReleaseApiBaseUrl)

val releaseBuildRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.lowercase().let { normalizedTaskName ->
        normalizedTaskName.contains("release") ||
            normalizedTaskName.endsWith("assemble") ||
            normalizedTaskName.endsWith("bundle") ||
            normalizedTaskName.endsWith("build")
    }
}

if (releaseBuildRequested && releaseApiBaseUrl == null) {
    throw GradleException(
        "Release builds require lura.releaseApiBaseUrl in local.properties " +
            "or LURA_RELEASE_API_BASE_URL in the environment."
    )
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun buildConfigStringArray(values: List<String>): String =
    values.joinToString(
        prefix = "new String[] {",
        postfix = "}",
        transform = ::buildConfigString
    )

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

    buildTypes {
        debug {
            buildConfigField("String", "LURA_API_BASE_URL", buildConfigString(debugApiBaseUrl))
            buildConfigField("String[]", "LURA_API_BASE_URLS", buildConfigStringArray(debugApiBaseUrls))
        }

        release {
            buildConfigField(
                "String",
                "LURA_API_BASE_URL",
                buildConfigString(releaseApiBaseUrl ?: "https://invalid.lura.local/")
            )
            buildConfigField(
                "String[]",
                "LURA_API_BASE_URLS",
                buildConfigStringArray(listOf(releaseApiBaseUrl ?: "https://invalid.lura.local/"))
            )
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
        buildConfig = true
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
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
