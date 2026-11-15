# Registro de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado [SemVer](https://semver.org/lang/es/) (`versionName` en `app/build.gradle.kts`). Las fechas son AAAA-MM-DD.

## [Sin publicar]

### Agregado
- Dos ambientes (variantes `pruebas` y `prod`), cada uno con su proyecto de Firebase y su paquete (`co.apexmoto.dev` y `co.apexmoto.prd`); se instalan a la vez. La versión de pruebas se llama "ApexMoto Pruebas", tiene ícono cian y una franja "Ambiente de pruebas".
- Botón **Borrar datos demo** (sin volver a cargarlos). Los datos demo y el diagnóstico de la clave de rutas solo existen en la versión de pruebas.
- Los datos del responsable se completan en los textos legales al compilar, desde `legal.properties` (no versionado; plantilla `legal.properties.example`).
- `docs/plan-de-pruebas.md`: recorridos para probar todos los roles con personas reales.
- Documentación del repositorio: README, guía de desarrollo, arquitectura, hoja de ruta, licencia propietaria, avisos de terceros, política de seguridad, guía de contribución, código de conducta y plantillas de GitHub.
- `gradle/gradle-daemon-jvm.properties`: el daemon de Gradle usa JDK 21 (JetBrains).

### Cambiado
- Los textos legales pasan de `app/src/main/assets/legal/` a `app/src/main/legal/`. Se quitó del texto todo lo del guía, y los términos prohíben cobrar por participar en una rodada o por guiarla.
- La tarea `checkLegalPlaceholders` se reemplaza por `fill<Variante>LegalTexts`.
- `google-services.json` ya no va en `app/`, sino en `app/src/<ambiente>/`.
- Gradle 9.7.1 con resolver de toolchains Foojay (`settings.gradle.kts`).
- `docs/seguridad.md` actualizado a los requisitos de Java vigentes.

### Quitado
- Rol **Guía de rutas**: registro, pantallas, perfiles (`drivers`), solicitudes de guía, calificación de guías y su declaración legal. ApexMoto no ofrece guías pagados ni transporte de pasajeros. Las cuentas que tenían el rol pasan solas a rider; las reglas solo permiten ese cambio (44 pruebas).

## [1.1] — 2026-11

### Agregado
- Marco legal y de privacidad: casillas separadas de términos y datos personales, registro inmutable de aceptaciones, reaceptación por versión, aviso de ubicación, retracto, derechos del titular y eliminación de cuenta; documentos en `docs/legal/`.
- Reglas de Firestore de producción con mínimo privilegio por rol y 45 pruebas en el emulador; `storage.rules` que niega todo.
- App Check (apagado por defecto), límites de frecuencia por usuario.
- Rol **Taller mecánico** con pantalla de atenciones S.O.S. y catálogo de servicios.
- Mapas de comercios, guiado giro a giro con voz y recálculo de ruta; ruta IA con presupuesto.
- Identidad de marca: paleta, fuentes (Chivo, Space Grotesk, JetBrains Mono) e ícono.

### Cambiado
- Inicio de sesión: correo y contraseña; teléfono para cuentas sin correo; creación de cuenta o Google.
- Todos los precios en pesos colombianos (COP).
- "Conductor guía" pasa a **Guía de rutas** (no ofrece transporte de pasajeros).
- Los comercios aliados ya no atienden S.O.S.; lo hacen los talleres.

## [1.0] — 2026-11-04

### Agregado
- Migración del prototipo `apexmoto.html` a Android: Kotlin, Jetpack Compose, Firebase Auth y Firestore, OpenStreetMap y OpenRouteService.

[Sin publicar]: https://github.com/adzzch/ApexMoto/compare/master...HEAD
