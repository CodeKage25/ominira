plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun setting(name: String, fallback: String): String =
    (project.findProperty(name) as String?)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

fun literal(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val useFakeApi = setting("ominira.useFakeApi", "true").toBoolean()
// No default: the live URL stays out of git (see README).
val gatewayUrl = setting("ominira.gatewayUrl", "")
val gatewayToken = setting("ominira.gatewayToken", "demo-customer")
val mockAdminToken = setting("ominira.mockAdminToken", "")
val cloudVoice = setting("ominira.cloudVoice", "true").toBoolean()

android {
    namespace = "app.ominira"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.ominira"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0-zecathon"

        buildConfigField("Boolean", "USE_FAKE_API", useFakeApi.toString())
        buildConfigField("String", "GATEWAY_URL", literal(gatewayUrl))
        buildConfigField("String", "GATEWAY_TOKEN", literal(gatewayToken))
        buildConfigField("String", "MOCK_ADMIN_TOKEN", literal(mockAdminToken))
        buildConfigField("Boolean", "CLOUD_VOICE", cloudVoice.toString())

        // Cleartext is debug only, for a laptop Gateway on the same Wi-Fi.
        manifestPlaceholders["usesCleartext"] = "false"
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartext"] = "true"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.face)
    implementation(libs.mlkit.text)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
