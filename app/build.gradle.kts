import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// La clave de OpenRouteService (rutas y guiado) se lee de local.properties (no se sube al repositorio):
//   ORS_API_KEY=tu_clave
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val orsKey: String = localProps.getProperty("ORS_API_KEY", "")

android {
    namespace = "co.apexmoto.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.apexmoto.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        buildConfigField("String", "ORS_API_KEY", "\"$orsKey\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    // El APK sale con nombre y versión: apexmotov1.1.apk (y apexmotov1.1-release.apk en release).
    applicationVariants.all {
        val variant = this
        outputs.all {
            val suffix = if (variant.buildType.name == "debug") "" else "-${variant.buildType.name}"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = "apexmotov${variant.versionName}$suffix.apk"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.osmdroid)
    implementation(libs.play.location)
    implementation(libs.okhttp)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)
}
