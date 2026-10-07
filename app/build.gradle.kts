import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Fixed, reviewed API origins. PROD can never point to STAGING: checked here at configuration time,
// at runtime (ApiEndpoint) and by EnvironmentConfigTest for every variant.
val productionBaseUrl = "https://tany.ma"
val stagingBaseUrl = "https://staging.tany.ma"
val devBaseUrl = (findProperty("tany.devApiBaseUrl") as String?) ?: "http://10.0.2.2:3000"
check(!devBaseUrl.contains("tany.ma")) { "tany.devApiBaseUrl must not target a TANY hosted environment" }

/**
 * Firebase (FCM push) WITHOUT the google-services plugin and without committing any Firebase file: when
 * `app/src/<buildType>/google-services.json` exists (git-ignored, one Firebase project PER environment), the values
 * FirebaseInitProvider reads are generated as resources — exactly what the plugin would generate. The file MUST
 * declare this build's applicationId (a staging build can never carry the PROD Firebase app, nor the reverse).
 * No file ⇒ no Firebase, no push token: the app and the in-app notification centre work unchanged (CI builds).
 */
@Suppress("UNCHECKED_CAST")
fun firebaseResValues(buildType: String, applicationId: String): Map<String, String> {
    val json = file("src/$buildType/google-services.json")
    if (!json.exists()) return emptyMap()
    val root = groovy.json.JsonSlurper().parse(json) as Map<String, Any?>
    val project = root["project_info"] as Map<String, Any?>
    val client = (root["client"] as List<Map<String, Any?>>).firstOrNull { entry ->
        val info = entry["client_info"] as Map<String, Any?>
        (info["android_client_info"] as Map<String, Any?>)["package_name"] == applicationId
    } ?: error("src/$buildType/google-services.json has no Android app '$applicationId': wrong Firebase project / environment")
    val appId = (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String
    val apiKey = ((client["api_key"] as List<Map<String, Any?>>).first())["current_key"] as String
    return buildMap {
        put("google_app_id", appId)
        put("google_api_key", apiKey)
        put("gcm_defaultSenderId", project["project_number"].toString())
        put("project_id", project["project_id"].toString())
        (project["storage_bucket"] as String?)?.let { put("google_storage_bucket", it) }
    }
}


android {
    namespace = "ma.tany.collect"
    compileSdk = 36

    defaultConfig {
        applicationId = "ma.tany.collect"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "TANY_ENVIRONMENT", "\"DEV\"")
            buildConfigField("String", "TANY_API_BASE_URL", "\"$devBaseUrl\"")
            resValue("string", "app_name", "Collect dev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "TANY_ENVIRONMENT", "\"PROD\"")
            buildConfigField("String", "TANY_API_BASE_URL", "\"$productionBaseUrl\"")
            resValue("string", "app_name", "TANY Collect")
        }
        // Declared after `release` so initWith copies its final configuration (R8, shrinking).
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "TANY_ENVIRONMENT", "\"STAGING\"")
            buildConfigField("String", "TANY_API_BASE_URL", "\"$stagingBaseUrl\"")
            resValue("string", "app_name", "Collect β")
            // Installable internal builds; release signing is configured outside the repo (never committed).
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // Internal tools (design system showcase) exist in debug + staging only — never in the PROD artifact.
    sourceSets {
        getByName("debug").java.srcDir("src/internal/kotlin")
        getByName("staging").java.srcDir("src/internal/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Per VARIANT (never inherited through initWith: staging must not pick up the release Firebase app).
val firebaseApplicationIds = mapOf("debug" to "ma.tany.collect.dev", "staging" to "ma.tany.collect.staging", "release" to "ma.tany.collect")
androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: return@onVariants
        val applicationId = firebaseApplicationIds[buildType] ?: return@onVariants
        firebaseResValues(buildType, applicationId).forEach { (key, value) ->
            variant.resValues.put(variant.makeResValueKey("string", key), com.android.build.api.variant.ResValue(value, null))
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.network.okhttp)
    // FCM push (token + foreground messages). Firebase config is per environment and never committed (see above).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    // QR / label scanning: CameraX preview + ML Kit barcode (bundled model — works offline, no Play Services download).
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    // Compose UI tests (Robolectric) live in src/testDebug: the test activity manifest is debug-only.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
