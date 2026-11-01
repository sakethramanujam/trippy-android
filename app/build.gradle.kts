import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "samalabs.trippy"
    compileSdk = 37

    defaultConfig {
        applicationId = "samalabs.trippy"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        // Default server shown on first launch; users change it on the welcome screen.
        buildConfigField("String", "DEFAULT_SERVER", "\"http://10.0.2.2:8000\"")
    }

    // Release signing lives outside the repo. Point at it with -PtrippySigning=<file>
    // or TRIPPY_SIGNING_PROPERTIES; default ~/.android/trippy-release.properties.
    // Without it, assembleRelease still builds (unsigned).
    val signingFile = ((findProperty("trippySigning") as String?)
        ?: System.getenv("TRIPPY_SIGNING_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.android/trippy-release.properties")
        .let(::file)
    val signingProps = Properties().apply {
        if (signingFile.isFile) signingFile.inputStream().use(::load)
    }
    if (signingProps.getProperty("storeFile") != null) {
        signingConfigs {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
