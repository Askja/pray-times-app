import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "ru.admiral.praytimes"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.admiral.praytimes"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"

        buildConfigField("String", "YANDEX_GEOCODER_API_KEY", quotedProperty("YANDEX_GEOCODER_API_KEY"))
        buildConfigField("String", "TWOGIS_API_KEY", quotedProperty("TWOGIS_API_KEY"))
        buildConfigField("String", "OPENCAGE_API_KEY", quotedProperty("OPENCAGE_API_KEY"))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    lint {
        // AGP 9.2 официально поддерживает максимум API 36.1; lint уже видит API 37 и шумит.
        disable += setOf("GradleDependency", "ObsoleteSdkInt", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.21")
}

fun quotedProperty(name: String): String {
    val value = providers.gradleProperty(name).orNull.orEmpty()
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
