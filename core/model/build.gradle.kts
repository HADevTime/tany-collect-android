import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: API contract models (docs/API_CONTRACT_V1.md in tany-backend).
// No Android dependency ⇒ decoding tests run on the JVM in seconds.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
