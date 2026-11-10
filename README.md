# ApexMoto (Android)

Migración del prototipo `apexmoto.html` a una app nativa: **Kotlin + Jetpack Compose + Firebase (Auth y Firestore) + OpenStreetMap + OpenRouteService** (sin tarjeta ni facturación de Google Cloud).

## 1. Configuración (una sola vez)

### Firebase
1. Crea un proyecto en https://console.firebase.google.com y agrega una app Android con el paquete **`co.apexmoto.app`**.
2. Descarga `google-services.json` y colócalo en `app/google-services.json`. Sin este archivo el proyecto no compila.
3. En **Authentication → Método de acceso** activa **Correo/contraseña**, **Google** (elige un correo de asistencia) y **Teléfono**. Luego crea la base **Firestore**.
4. Pega el contenido de `firestore.rules` en Firestore → Reglas y publícalo.
5. Para cargar los datos demo necesitas ser administrador: en Firestore crea a mano el documento `admins/{tu uid}` (el uid está en Authentication → Usuarios). Las reglas no dejan crearlo desde la app.

### Inicio de sesión con Google y teléfono
- Agrega el **SHA-1** y el **SHA-256** de tu keystore de depuración en Firebase: Configuración del proyecto → Tus apps → Agregar huella digital. Se obtienen con `signingReport` (panel Gradle: `app → Tasks → android → signingReport`).
- **Después de activar Google y agregar las huellas, descarga de nuevo `google-services.json`** y reemplaza el de `app/`. Trae el ID de cliente web que la app necesita.
- Para probar el SMS sin gastar mensajes reales, en Authentication → Método de acceso → Teléfono agrega **números de prueba** con su código fijo. El envío de SMS reales puede tener cupos o costo según tu plan de Firebase; revísalo en la consola.
- El emulador debe tener **Google Play** y una cuenta de Google agregada para que aparezca el selector de cuentas.
- En el primer ingreso con Google o teléfono, la app muestra una pantalla para elegir el rol (Rider, Comercio, Taller, Dueño de moto o Guía de rutas) y aceptar los documentos legales.

### Mapas y rutas (gratis, sin tarjeta)
- Los **mapas** vienen de OpenStreetMap (librería osmdroid): no requieren clave.
- Las **rutas y la guía giro a giro** usan OpenRouteService:
  1. Regístrate en https://openrouteservice.org (solo correo) → Dashboard → **Request a token** (tipo *Free*).
  2. Agrega la clave a `local.properties` (ver `local.properties.example`), conservando la línea `sdk.dir`:
     ```
     ORS_API_KEY=tu_clave
     ```
  3. Android Studio → **Sync Now** y vuelve a ejecutar. La pantalla **Configuración** de la app indica si la clave quedó cargada.
- El plan gratuito tiene un límite diario de peticiones de rutas; si se agota, la app muestra un aviso.

### Abrir y ejecutar
Abre la carpeta en Android Studio (versión reciente), sincroniza Gradle y ejecuta en un celular o emulador con Google Play Services. En **Perfil → Cargar datos demo** (solo en compilaciones de desarrollo y siendo administrador) se cargan los comercios, motos, guías y rodadas del prototipo.

## 2. De prototipo a módulos

| Prototipo | Archivo |
|---|---|
| Login: correo, Google y teléfono, y elección de rol | `ui/Root.kt` (AuthScreen, RoleSelectScreen), `auth/SignIn.kt`, `data/Repo.kt` |
| Rutas: Ruta IA y presupuesto | `ui/RiderRoutes.kt`, `maps/RoutePlanner.kt` |
| Comercios, menú, pedidos, cupones | `ui/RiderVenues.kt`, `ui/Commerce.kt` |
| Comunidad (rodadas) | `ui/RiderRent.kt` |
| Rentar moto y conductor guía | `ui/RiderRent.kt`, `ui/Owner.kt` |
| Taller móvil S.O.S. | `ui/RiderWorkshop.kt`, `ui/Commerce.kt` |
| Dueño de moto: flota, solicitudes, ingresos | `ui/Owner.kt` |
| Conductor guía: salidas, servicio, ganancias | `ui/Owner.kt` |
| Chat, perfil, configuración | `ui/Misc.kt` |
| **Legal y privacidad (nuevo):** casillas de aceptación, reaceptación, aviso de ubicación, retracto, derechos del titular, eliminar cuenta | `legal/Legal.kt`, `ui/Legal.kt`, `core/BusinessDays.kt`, `assets/legal/*.md` |
| **Mapas, rutas y guiado (nuevo)** | `maps/Maps.kt`, `ui/NavigationScreen.kt` |

## 3. Mapas y guiado

- **Mapa de comercios:** pestaña Comercios → "Ver mapa". Toca un marcador para ver el detalle.
- **Guiado dentro de la app:** botón **Ir** de un comercio, **Iniciar guiado** de una ruta IA o **Guiar** de una ruta guardada. Calcula la ruta con OpenRouteService (con paradas intermedias), la dibuja, sigue tu ubicación, avanza las instrucciones, las lee en voz alta y recalcula si te sales más de 150 m.
- **Alternativa:** "Abrir en otra app" lanza la navegación de Google Maps (si está instalada) o cualquier app de mapas del teléfono.
- **Ubicación de cada negocio:** el comercio la fija tocando el mapa en Negocio, o con su ubicación actual.

## 4. Cosas que debes saber

- **Este código no se compiló aquí.** Está escrito con cuidado, pero la primera compilación puede mostrar errores de importación o de versiones. Pásamelos y los corrijo.
- **Versiones:** AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09.03. Android Studio puede sugerir actualizarlas; hazlo con calma y una a la vez.
- **Gradle wrapper:** solo incluye `gradle-wrapper.properties` (Android Studio descarga Gradle 8.7 solo). Para usar `./gradlew` por consola, genera el wrapper desde Android Studio.
- **Modo de transporte:** las rutas se calculan como auto (`driving-car`). OpenRouteService también ofrece el perfil `driving-motorcycle` en algunos planes; puedes probarlo cambiando la URL en `maps/Maps.kt`.
- **Coordenadas demo aproximadas:** los centros de cada corredor y los comercios demo están en `core/Constants.kt` y `data/Repo.kt`. Ajústalos a los puntos reales.
- **Datos demo sin dueño:** los comercios, motos y conductores demo no tienen cuenta propietaria, así que pedidos y solicitudes hacia ellos quedan en "Enviado/Pendiente". Para probar el flujo completo crea una cuenta Comercio y una Rider (dos celulares o cierra sesión entre una y otra).
- **Reglas de Firestore:** son de producción, con mínimo privilegio por rol, y tienen pruebas (`cd firestore-tests && npm install && npm test`, necesita Java 21). Detalle en `docs/seguridad.md`.
- **Legal:** los textos que ve el usuario están en `app/src/main/assets/legal/`. Tienen marcadores `[COMPLETAR: …]` con los datos del responsable; la compilación de publicación falla hasta completarlos. Todo debe revisarlo un abogado: empieza por `docs/legal/README.md` y `docs/legal/revision-abogado.md`.
- **Fuentes e ícono:** las fuentes de marca (Chivo, Space Grotesk y JetBrains Mono) ya vienen en `app/src/main/res/font/`. El ícono de la app (moto blanca sobre naranja) está en `res/drawable/ic_launcher_foreground.xml`.
- **Moneda:** todos los precios están en pesos colombianos (COP). Si tenías datos demo cargados de antes (precios en dólares), usa **Perfil → Reiniciar datos demo**.
- **Pendiente respecto al prototipo:** pagos reales, notificaciones push (FCM), fotos de comercios y motos, reseñas y la lista de pilotos de Comunidad.
