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
// Datos del responsable para los textos legales. Se leen de legal.properties, que no se sube al repositorio
// (plantilla: legal.properties.example). Ver "Textos legales" al final de este archivo.
val legalProps = Properties().apply {
    val f = rootProject.file("legal.properties")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}

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
    // Dos ambientes, cada uno con su propio proyecto de Firebase y su google-services.json en app/src/<ambiente>/:
    //  · pruebas: desarrollo y presentaciones. Trae las herramientas de datos demo y una franja que lo identifica.
    //  · prod: la app real, que arranca vacía. No compila si los textos legales no tienen todos los datos del responsable.
    // Como tienen paquetes distintos (co.apexmoto.dev y co.apexmoto.prd), las dos se pueden instalar a la vez.
    // El namespace del código sigue siendo co.apexmoto.app (ahí se generan R y BuildConfig).
    flavorDimensions += "ambiente"
    productFlavors {
        create("pruebas") {
            dimension = "ambiente"
            isDefault = true
            applicationId = "co.apexmoto.dev"   // así quedó registrada en el proyecto de pruebas (ApexMoto-dev)
            versionNameSuffix = "-pruebas"
            buildConfigField("boolean", "PRUEBAS", "true")
        }
        create("prod") {
            dimension = "ambiente"
            applicationId = "co.apexmoto.prd"   // así quedó registrada en el proyecto productivo (ApexMoto-prd)
            buildConfigField("boolean", "PRUEBAS", "false")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    // El APK sale con nombre y versión: apexmotov1.1.apk (prod), apexmotov1.1-pruebas.apk (pruebas) y "-release" en release.
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
    implementation(libs.firebase.ai)                                // Gemini con Firebase AI Logic (Ruta IA), sin clave en la app
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

// ───────── Textos legales ─────────
// Los textos viven en src/main/legal/ con marcadores [COMPLETAR: …]. Al compilar se copian a los assets de cada
// variante con los datos de legal.properties, así el repositorio no guarda datos personales del responsable.
// En prod y en cualquier versión de publicación faltar un dato hace fallar la compilación: nadie debe recibir unos
// términos sin responsable. En pruebas los marcadores que falten se ven tal cual.
val legalMarkers = mapOf(
    "fecha de publicación" to "fecha_publicacion",
    "razón social o nombre completo del responsable" to "responsable",
    "NIT o cédula" to "documento",
    "dirección, Cali, Valle del Cauca, Colombia" to "direccion",
    "correo de atención" to "correo_atencion",
    "correo de privacidad" to "correo_privacidad",
    "teléfono" to "telefono",
)

/** Copia los textos legales a assets/legal/ y reemplaza cada [COMPLETAR: …] por su valor de legal.properties. */
abstract class FillLegalTexts : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val templates: DirectoryProperty
    /** Texto del marcador → clave en legal.properties. */
    @get:Input abstract val markers: MapProperty<String, String>
    @get:Input abstract val values: MapProperty<String, String>
    /** true: falla si queda algún marcador sin valor. */
    @get:Input abstract val strict: Property<Boolean>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fill() {
        val out = outputDir.get().asFile.resolve("legal")
        out.deleteRecursively(); out.mkdirs()
        val known = markers.get(); val given = values.get()
        val missing = sortedSetOf<String>()
        val marker = Regex("""\[COMPLETAR: ([^\]]+)]""")
        templates.get().asFile.listFiles().orEmpty().filter { it.isFile && it.extension == "md" }.forEach { f ->
            val text = marker.replace(f.readText(Charsets.UTF_8)) { m ->
                val key = known[m.groupValues[1]]
                val value = key?.let { given[it] }?.trim().orEmpty()
                if (value.isNotEmpty()) value
                else { missing += key ?: "\"${m.groupValues[1]}\" (marcador sin clave en legalMarkers)"; m.value }
            }
            out.resolve(f.name).writeText(text, Charsets.UTF_8)
        }
        if (strict.get() && missing.isNotEmpty()) throw GradleException(
            "Faltan datos del responsable en los textos legales: ${missing.joinToString()}. " +
                "Complétalos en legal.properties (plantilla: legal.properties.example).")
    }
}

androidComponents {
    onVariants { variant ->
        val fill = tasks.register<FillLegalTexts>("fill${variant.name.replaceFirstChar { it.uppercase() }}LegalTexts") {
            templates.set(layout.projectDirectory.dir("src/main/legal"))
            markers.set(legalMarkers)
            values.set(legalProps.stringPropertyNames().associateWith { legalProps.getProperty(it) })
            strict.set(variant.flavorName == "prod" || variant.buildType == "release")
        }
        variant.sources.assets?.addGeneratedSourceDirectory(fill, FillLegalTexts::outputDir)
    }
}
