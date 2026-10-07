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

// STAGING signing (beta.tany.ma): a DEDICATED, stable STAGING key — never the PROD key — injected by the `publish-beta`
// CI job from GitHub environment secrets (keystore decoded to a temp file, never committed). Without it (PR CI, local
// builds) staging falls back to the debug key: installable for a quick test, but never published (`publish-beta.mjs`
// refuses any certificate other than TANY_STAGING_CERT_SHA256).
val stagingKeystoreFile = providers.environmentVariable("TANY_STAGING_KEYSTORE_FILE").orNull
check(providers.gradleProperty("tany.staging.requireSigning").orNull != "true" || stagingKeystoreFile != null) {
    "tany.staging.requireSigning=true but TANY_STAGING_KEYSTORE_FILE is not set"
}
// STAGING versionCode: monotonic, set by CI (`git rev-list --count HEAD` on main) so each published build updates the
// installed Collect β. PROD keeps defaultConfig.versionCode (its own strategy, untouched).
val stagingVersionCode = providers.gradleProperty("tany.staging.versionCode").orNull?.toInt()

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

    signingConfigs {
        if (stagingKeystoreFile != null) {
            create("staging") {
                storeFile = file(stagingKeystoreFile)
                storePassword = providers.environmentVariable("TANY_STAGING_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("TANY_STAGING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("TANY_STAGING_KEY_PASSWORD").get()
            }
        }
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
            // Stable STAGING key when CI provides it (see top of file), debug key otherwise. PROD signing is
            // configured outside the repo and is never this key.
            signingConfig = signingConfigs.findByName("staging") ?: signingConfigs.getByName("debug")
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

androidComponents {
    onVariants(selector().withBuildType("staging")) { variant ->
        stagingVersionCode?.let { code -> variant.outputs.forEach { it.versionCode.set(code) } }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
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
