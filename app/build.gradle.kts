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
// App Check (antifraude). Se activa con APP_CHECK_ENABLED=true en local.properties, después de registrar la app
// en la consola de Firebase. Ver docs/seguridad.md.
val appCheck: Boolean = localProps.getProperty("APP_CHECK_ENABLED", "false").toBoolean()

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
        buildConfigField("boolean", "APP_CHECK_ENABLED", "$appCheck")
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
    implementation(libs.firebase.installations)                     // identificador de instalación para el registro de aceptaciones
    releaseImplementation(libs.firebase.appcheck.playintegrity)     // App Check real (Play Integrity)
    debugImplementation(libs.firebase.appcheck.debug)               // App Check con token de depuración
    implementation(libs.osmdroid)
    implementation(libs.play.location)
    implementation(libs.okhttp)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)
}

// Una versión de publicación no puede salir con datos del responsable sin completar en los documentos legales.
val checkLegalPlaceholders by tasks.registering {
    val docs = fileTree("src/main/assets/legal") { include("*.md") }
    inputs.files(docs)
    doLast {
        val pending = docs.files.filter { it.readText().contains("[COMPLETAR") }.map { it.name }.sorted()
        if (pending.isNotEmpty()) throw GradleException(
            "Documentos legales con datos por completar ([COMPLETAR: ...]): ${pending.joinToString()}. Complétalos antes de generar la versión de publicación.")
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkLegalPlaceholders) }
