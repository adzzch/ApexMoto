# Guía de desarrollo

## 1. Requisitos

| Herramienta | Versión | Nota |
|---|---|---|
| Android Studio | Reciente | Incluye el SDK; el proyecto compila con `compileSdk 34` |
| JDK | 21 | El daemon de Gradle lo exige (`gradle/gradle-daemon-jvm.properties`, JetBrains 21); se descarga solo con el resolver de toolchains |
| Gradle | 9.7.1 | Lo descarga el wrapper (`gradle/wrapper/gradle-wrapper.properties`) |
| Node.js | LTS reciente | Solo para las pruebas de reglas de Firestore |
| Emulador o celular | Android 8.0+ (`minSdk 26`) con Google Play | Necesario para Google Sign-In y el selector de cuentas |

El repositorio versiona solo `gradle-wrapper.properties`, sin `gradlew` ni el JAR. Para usar `./gradlew` por consola, genera el wrapper desde Android Studio o con `gradle wrapper`.

## 2. Configuración inicial (una sola vez)

### 2.1 Firebase: un proyecto por ambiente

La app tiene dos ambientes. Cada uno usa su propio proyecto de Firebase en el plan Spark (gratis), así las pruebas y las presentaciones nunca se mezclan con los datos reales.

| Ambiente | Variante | Paquete | Archivo de configuración |
|---|---|---|---|
| **Pruebas**: desarrollo y presentaciones | `pruebas` | `co.apexmoto.dev` | `app/src/pruebas/google-services.json` |
| **Productivo**: la app real, que arranca vacía | `prod` | `co.apexmoto.prd` | `app/src/prod/google-services.json` |

En cada proyecto:

1. Crea el proyecto en <https://console.firebase.google.com>. Google Analytics no hace falta.
2. Agrega una app Android con el paquete del ambiente (tabla de arriba).
3. En **Authentication → Método de acceso** activa **Correo/contraseña**, **Google** (elige un correo de asistencia) y **Teléfono**.
4. Crea la base **Firestore**. La ubicación no se puede cambiar después: usa la misma en los dos proyectos y, si no sabes cuál, `nam5 (Estados Unidos)`, que es la que menciona la Política de Privacidad.
5. Agrega las huellas SHA (punto 2.2) y **después** descarga `google-services.json` en la ruta de la tabla. Está en `.gitignore`; sin él esa variante no compila.
6. Publica las reglas: pega `firestore.rules` en Firestore → Reglas → Publicar, o ejecuta `npx firebase deploy --only firestore:rules --project <id-del-proyecto>` desde `firestore-tests/`.
7. Para usar los datos demo (solo en pruebas) o responder solicitudes de privacidad debes ser administrador: crea a mano en Firestore el documento `admins/{tu uid}` con cualquier campo (por ejemplo `since: 1`). El uid está en Authentication → Usuarios. Las reglas no permiten crearlo desde la app.

> **El proyecto que se usaba antes del 2026-11-15 queda como el de pruebas**, con sus cuentas y datos. En él agrega la app `co.apexmoto.dev`, registra sus huellas y descarga otra vez `google-services.json` en `app/src/pruebas/`. Mientras no lo hagas, la variante `pruebas` falla con *No matching client found for package name 'co.apexmoto.dev'*. Si tiene la colección `drivers` (perfiles del rol de guía, ya retirado), puedes borrarla desde la consola.
>
> **La app vieja `co.apexmoto.app` de ese proyecto ya no se usa: quítala.** Firebase permite restaurar una app quitada durante 30 días. Ojo con una regla de Google: un mismo paquete con la misma huella SHA-1 solo puede estar en un proyecto. Por eso cada ambiente usa un paquete propio (`co.apexmoto.dev` y `co.apexmoto.prd`).

### 2.2 Google y teléfono

- Agrega el **SHA-1** y el **SHA-256** de tu keystore de depuración en la app de **cada** proyecto: Configuración del proyecto → Tus apps → Agregar huella. Se obtienen con `app → Tasks → android → signingReport`, o en Windows con:

  ```bat
  "C:\Program Files\Android\Android Studio\jbr\bin\keytool" -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
  ```

  Si otra persona compila la app en su computador, agrega también sus huellas (su keystore de depuración es otro).
- **Después de activar Google y agregar las huellas, descarga de nuevo `google-services.json`**: trae el ID de cliente web que la app necesita.
- Para probar SMS sin gastar mensajes reales, agrega **números de prueba** con código fijo en Authentication → Método de acceso → Teléfono. El envío real puede tener cupos o costo según tu plan.
- El emulador necesita Google Play y una cuenta de Google agregada.

### 2.3 Mapas y rutas (gratis, sin tarjeta)

- Los mapas de OpenStreetMap (osmdroid) no requieren clave.
- Para rutas y guiado: regístrate en <https://openrouteservice.org> → Dashboard → **Request a token** (tipo *Free*) y agrégala a `local.properties`:

  ```properties
  sdk.dir=...            # lo crea Android Studio; no lo borres
  ORS_API_KEY=tu_clave
  APP_CHECK_ENABLED=false  # opcional; ver docs/seguridad.md
  ```

  Hay una plantilla en `local.properties.example`. `local.properties` **nunca se sube**. En la versión de pruebas, la pantalla Configuración indica si la clave quedó cargada.
- El plan gratuito tiene un límite diario de peticiones; si se agota, la app muestra un aviso.

### 2.4 Datos del responsable (solo para la versión real)

Copia `legal.properties.example` a `legal.properties` en la raíz y llena cada valor (fecha, responsable, documento, dirección, correos y teléfono). Ese archivo **no se sube** al repositorio: los datos personales del responsable solo viven en tu computador y en la app compilada. Sin él la variante `prod` no compila (ver el punto 6).

## 3. Ejecutar

1. Sincroniza Gradle (**Sync Now**).
2. En **Build Variants** elige `pruebasDebug` (la de siempre) o `prodDebug`, y ejecuta `app`. Las dos se pueden tener instaladas a la vez: la de pruebas se llama "ApexMoto Pruebas", tiene el ícono cian y una franja "Ambiente de pruebas · datos de ejemplo" arriba en todas las pantallas.
3. **Datos demo** (solo en pruebas y siendo administrador): Perfil → Datos demo → **Cargar**, **Reiniciar** o **Borrar**. Se cargan comercios, motos y rodadas del prototipo; borrarlos no toca lo que crearon los usuarios.
4. Los datos demo no tienen cuenta propietaria: pedidos y solicitudes hacia ellos quedan en "Enviado/Pendiente". Para probar el flujo completo crea una cuenta Comercio (o Taller) y otra Rider, en dos dispositivos o cerrando sesión entre una y otra.

### 3.1 Para una presentación

1. Abre ApexMoto Pruebas con tu cuenta de administrador y toca **Cargar datos demo** (o **Reiniciar**, si ya estaban).
2. Presenta. La franja deja claro que son datos de ejemplo.
3. Si quieres dejar el ambiente limpio, toca **Borrar datos demo** al terminar.

### 3.2 Entregar la app a otras personas (prueba cerrada)

1. Completa `legal.properties` (punto 2.4).
2. Genera el APK de la variante `prodDebug`: **Build → Build APK(s)** (o *Generate APKs*, según la versión de Android Studio). Sale en `app/build/outputs/apk/prod/debug/apexmotov1.1.apk`.
3. Compártelo por WhatsApp o Drive, o con Firebase App Distribution (gratis). En el celular hay que permitir la instalación de apps de origen desconocido.
4. Google Sign-In funciona porque el APK va firmado con tu keystore de depuración, cuya huella registraste en el proyecto productivo.
5. Sigue el [plan de pruebas](plan-de-pruebas.md).

## 4. Pruebas

Hoy la única suite automatizada es la de las **reglas de Firestore** (66 pruebas contra el emulador):

```bash
cd firestore-tests
npm install     # una vez
npm test        # requiere JDK 21 o superior en el PATH (sirve el de Android Studio, carpeta jbr)
```

No hay pruebas unitarias ni de interfaz de la app Android (ver la [hoja de ruta](hoja-de-ruta.md)). Mientras tanto, la verificación de la app es manual, con el [plan de pruebas](plan-de-pruebas.md).

## 5. Versiones y dependencias

- Las versiones viven en `gradle/libs.versions.toml`. Actualiza **una a la vez** y compila entre cambios.
- Al agregar o cambiar una dependencia: actualiza [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) y, si cambia lo que ve el usuario, `app/src/main/legal/licencias.md` (ver [legal/README.md](legal/README.md) §4).
- Cualquier servicio nuevo debe tener plan gratuito sin tarjeta; si no, no entra ([PRODUCT.md](../PRODUCT.md)).
- El APK se nombra `apexmotov<versionName>.apk` (prod) o `apexmotov<versionName>-pruebas.apk` (pruebas), con `-release` en las de publicación. Sube `versionCode` y `versionName` en `app/build.gradle.kts` en cada entrega y registra el cambio en [CHANGELOG.md](../CHANGELOG.md).

## 6. Documentos legales

Los textos que ve el usuario están en `app/src/main/legal/` y son la única fuente. Al compilar, la tarea `fill<Variante>LegalTexts` los copia a los assets de la app y reemplaza cada `[COMPLETAR: …]` por su valor de `legal.properties`:

- En `prod` y en cualquier compilación de publicación, si falta un valor la compilación falla y dice cuál.
- En `pruebasDebug` los valores que falten se ven como `[COMPLETAR: …]`.
- Si agregas un marcador nuevo, agrégalo también a `legalMarkers` en `app/build.gradle.kts` y a `legal.properties.example`.

Cambiar un texto tiene un procedimiento (archivar la versión, subir `Legal.DOCS`, actualizar `versionOk()` en `firestore.rules`, correr las pruebas): [legal/README.md](legal/README.md) §4.

## 7. Publicar una versión

1. Completar `legal.properties` y obtener la revisión de un abogado ([legal/revision-abogado.md](legal/revision-abogado.md)).
2. Publicar `firestore.rules` **antes** que la app, en los dos proyectos, y correr `npm test`.
3. Subir versión en `app/build.gradle.kts`, actualizar el changelog y crear una etiqueta `vX.Y`.
4. Firmar la compilación `prodRelease` (las claves de firma viven fuera del repositorio), con R8 activado.
5. Completar la ficha y los formularios de la consola: [legal/google-play.md](legal/google-play.md).

## 8. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| No compila: falta `google-services.json` | Cada variante necesita el suyo: `app/src/pruebas/` o `app/src/prod/` (punto 2.1) |
| *No matching client found for package name 'co.apexmoto.dev'* | El `google-services.json` de pruebas no incluye la app de pruebas: regístrala en el proyecto y descárgalo de nuevo |
| Al agregar una huella, error de que ya existe un cliente OAuth con ese paquete y esa SHA-1 en otro proyecto | Ese paquete ya está registrado con esa huella en otro proyecto: quita allí la app (o su huella). Si el error sigue, borra el cliente "Android client for <paquete>" en Google Cloud Console → APIs y servicios → Credenciales de ese otro proyecto |
| `prodDebug` falla con "Faltan datos del responsable" | Falta `legal.properties` o algún valor (plantilla: `legal.properties.example`) |
| Gradle falla por la versión de Java | El daemon exige JDK 21; usa el JDK que descarga Android Studio o instala uno |
| Las rutas no se calculan | `ORS_API_KEY` ausente o límite diario agotado |
| Google Sign-In no abre el selector | Faltan huellas SHA en ese proyecto o `google-services.json` desactualizado; el emulador no tiene Google Play |
| `npm test` no encuentra Java | Pon un JDK 21+ en el PATH (por ejemplo, la carpeta `jbr` de Android Studio) y verifica `java -version` |
| No aparecen las herramientas de datos demo | Solo existen en la variante `pruebas`, y hay que ser administrador para usarlas |
