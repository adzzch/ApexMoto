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

### 2.1 Firebase

1. Crea un proyecto en <https://console.firebase.google.com> y agrega una app Android con el paquete **`co.apexmoto.app`**.
2. Descarga `google-services.json` y colócalo en `app/google-services.json`. Está en `.gitignore` y sin él el proyecto no compila.
3. En **Authentication → Método de acceso** activa **Correo/contraseña**, **Google** (elige un correo de asistencia) y **Teléfono**. Crea la base **Firestore**.
4. Publica las reglas: pega `firestore.rules` en Firestore → Reglas, o ejecuta `npx firebase deploy --only firestore:rules --project <tu-proyecto>` desde `firestore-tests/`.
5. Para cargar datos demo debes ser administrador: crea a mano en Firestore el documento `admins/{tu uid}` (el uid está en Authentication → Usuarios). Las reglas no permiten crearlo desde la app.

### 2.2 Google y teléfono

- Agrega el **SHA-1** y el **SHA-256** de tu keystore de depuración: Configuración del proyecto → Tus apps → Agregar huella. Se obtienen con `app → Tasks → android → signingReport`.
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

  Hay una plantilla en `local.properties.example`. `local.properties` **nunca se sube**. La pantalla Configuración de la app indica si la clave quedó cargada (solo en compilaciones de desarrollo).
- El plan gratuito tiene un límite diario de peticiones; si se agota, la app muestra un aviso.

## 3. Ejecutar

1. Sincroniza Gradle (**Sync Now**).
2. Ejecuta la configuración `app` en un emulador o celular.
3. En **Perfil → Cargar datos demo** (solo compilaciones de desarrollo y siendo administrador) se cargan comercios, motos, guías y rodadas del prototipo. **Perfil → Reiniciar datos demo** los recarga.
4. Los datos demo no tienen cuenta propietaria: pedidos y solicitudes hacia ellos quedan en "Enviado/Pendiente". Para probar el flujo completo crea una cuenta Comercio (o Taller) y otra Rider, en dos dispositivos o cerrando sesión entre una y otra.

## 4. Pruebas

Hoy la única suite automatizada es la de las **reglas de Firestore** (45 pruebas contra el emulador):

```bash
cd firestore-tests
npm install     # una vez
npm test        # requiere JDK 21 o superior en el PATH
```

No hay pruebas unitarias ni de interfaz de la app Android (ver la [hoja de ruta](hoja-de-ruta.md)). Mientras tanto, la verificación de la app es manual en emulador o celular.

## 5. Versiones y dependencias

- Las versiones viven en `gradle/libs.versions.toml`. Actualiza **una a la vez** y compila entre cambios.
- Al agregar o cambiar una dependencia: actualiza [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) y, si cambia lo que ve el usuario, `app/src/main/assets/legal/licencias.md` (sube su versión; ver [legal/README.md](legal/README.md) §4).
- Cualquier servicio nuevo debe tener plan gratuito sin tarjeta; si no, no entra ([PRODUCT.md](../PRODUCT.md)).
- El APK se nombra `apexmotov<versionName>[-release].apk`. Sube `versionCode` y `versionName` en `app/build.gradle.kts` en cada entrega y registra el cambio en [CHANGELOG.md](../CHANGELOG.md).

## 6. Documentos legales

Los textos que ve el usuario están en `app/src/main/assets/legal/` y son la única fuente. Cambiarlos tiene un procedimiento obligatorio (archivar la versión, subir `Legal.DOCS`, actualizar `versionOk()` en `firestore.rules`, correr las pruebas): [legal/README.md](legal/README.md) §4.

La tarea `checkLegalPlaceholders` hace fallar la compilación de publicación mientras algún texto conserve `[COMPLETAR: …]`.

## 7. Publicar una versión

1. Completar los datos del responsable en los textos legales y obtener la revisión de un abogado ([legal/revision-abogado.md](legal/revision-abogado.md)).
2. Publicar `firestore.rules` **antes** que la app, y correr `npm test`.
3. Subir versión en `app/build.gradle.kts`, actualizar el changelog y crear una etiqueta `vX.Y`.
4. Firmar la compilación (las claves de firma viven fuera del repositorio), con R8 activado.
5. Completar la ficha y los formularios de la consola: [legal/google-play.md](legal/google-play.md).

## 8. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| No compila: falta `google-services.json` | Descárgalo de Firebase y ponlo en `app/` |
| Gradle falla por la versión de Java | El daemon exige JDK 21; usa el JDK que descarga Android Studio o instala uno |
| Las rutas no se calculan | `ORS_API_KEY` ausente o límite diario agotado |
| Google Sign-In no abre el selector | Faltan huellas SHA o `google-services.json` desactualizado; el emulador no tiene Google Play |
| `npm test` no encuentra Java | Instala JDK 21+ y verifica `java -version` |
| La compilación de publicación falla en `checkLegalPlaceholders` | Quedan `[COMPLETAR: …]` en los textos legales |
