# Arquitectura

Un solo módulo Android (`:app`), paquete `co.apexmoto.app`, sin servidor propio. La app habla directo con Firebase; **las reglas de Firestore son la capa de servidor** ([seguridad.md](seguridad.md)).

## 1. Vista general

```
┌───────────────────────── Teléfono (app/) ─────────────────────────┐
│  ui/ (Compose)  ──►  AppViewModel  ──►  data/Repo  ──►  Firestore │
│       │                    │                 │         (reglas)   │
│       │                    │                 └──►  Firebase Auth  │
│       ▼                    ▼                                      │
│  maps/ (osmdroid,     legal/ (textos,                             │
│  OpenRouteService,    aceptaciones,                               │
│  ubicación, voz)      plazos)                                     │
└───────────────────────────────────────────────────────────────────┘
        │ HTTPS                                    │ HTTPS
        ▼                                          ▼
 OpenStreetMap / OpenRouteService            Firebase (plan Spark)
```

Decisiones de diseño vigentes:

| Decisión | Motivo |
|---|---|
| Inyección manual (`AppContainer` en `ApexApp.kt`), sin Hilt | Mantener el proyecto simple en la etapa MVP |
| Un único `AppViewModel` | El estado de sesión y rol es compartido por casi todas las pantallas |
| Todo el acceso a datos pasa por `data/Repo.kt` | Un solo lugar donde se arman consultas acotadas y lotes atómicos |
| Servicios solo gratuitos y sin tarjeta | Compromiso de producto: sin Cloud Functions, Storage, Messaging ni Analytics |
| Modelos con valores por defecto y `var` | Requisito de Firestore para leer y escribir objetos |

## 2. Estructura del código

Ruta base: `app/src/main/java/co/apexmoto/app/`

| Paquete / archivo | Responsabilidad |
|---|---|
| `ApexApp.kt`, `MainActivity.kt` | Aplicación, contenedor de dependencias (`AppContainer`), preferencias, punto de entrada |
| `auth/SignIn.kt` | Google Sign-In (Credential Manager) y verificación por SMS |
| `data/Models.kt` | Modelos: `AppUser`, `Venue`, `Bike`, `Driver`, `Rodada`, `ServiceRequest`, `Order`, `Coupon`, `Sos`, `ChatMessage`, `SavedRoute`, y roles (`Role`) |
| `data/Repo.kt` | Acceso a Firestore y Auth: perfiles, comercios, motos, pedidos, S.O.S., chat, aceptaciones, datos demo |
| `legal/Legal.kt` | Catálogo versionado de documentos (`Legal.DOCS`), registro de aceptaciones, solicitudes de privacidad, `ConsentRecorder` |
| `maps/Maps.kt` | `RoutesClient` (OpenRouteService) y `LocationHelper` |
| `maps/OsmMap.kt` | Mapa OpenStreetMap integrado en Compose, marcadores |
| `maps/RoutePlanner.kt` | Ruta IA: arma paradas y presupuesto a partir de filtros del rider |
| `core/Constants.kt` | Corredores, planes de protección, extras, tipos de S.O.S. (`C`) |
| `core/BusinessDays.kt` | Días hábiles colombianos (festivos) para plazos legales y retracto |
| `core/Util.kt` | Utilidades (formato de COP, etc.) |
| `ui/Root.kt` | Navegación, barra por rol (`tabsFor`), pantalla de ingreso y elección de rol |
| `ui/Rider*.kt` | Rutas, comercios y mapa, renta y comunidad, taller S.O.S. del rider |
| `ui/Commerce.kt`, `ui/Taller.kt`, `ui/Owner.kt` | Espacios de comercio, taller mecánico, dueño de moto y guía |
| `ui/NavigationScreen.kt` | Guiado giro a giro: ubicación, voz es-CO, recálculo a más de 150 m |
| `ui/Legal.kt` | Casillas de aceptación, reaceptación, aviso de ubicación, derechos del titular, eliminar cuenta |
| `ui/Common.kt`, `ui/Misc.kt` | Componentes compartidos; chat, perfil y configuración |
| `ui/theme/Theme.kt` | Paleta y tipografías de marca (las pantallas no definen colores propios) |

Variantes de compilación: `src/debug/` y `src/release/` aportan cada una su `AppCheckInstaller` (token de depuración en debug, Play Integrity en release).

## 3. Roles y navegación

El rol se elige al registrarse (o en el primer ingreso con Google o teléfono) y define la barra inferior (`tabsFor` en `ui/Root.kt`):

| Rol | Código | Pestañas |
|---|---|---|
| Rider | `m` | Rutas, Comercios, Comunidad, Rentar, Taller |
| Guía de rutas | `d` | Las del rider + Salidas |
| Comercio | `c` | Resumen, Pedidos, Menú, Negocio, Canjes |
| Taller mecánico | `t` | Resumen, Atenciones, Servicios, Mi taller, Canjes |
| Dueño de moto | `o` | Mi flota, Solicitudes, Ingresos |

Solo rider ↔ guía se puede cambiar después; los demás roles no son editables por el usuario (lo imponen las reglas).

## 4. Flujo de datos

1. La UI observa estado del `AppViewModel` y le pide acciones.
2. El `AppViewModel` llama a `Repo`, que usa Firestore con consultas acotadas y lotes atómicos (por ejemplo, el perfil nuevo se crea en el mismo lote que las aceptaciones legales).
3. Firestore valida cada escritura con `firestore.rules` (autorización, tamaños, transiciones de estado, sellos de tiempo del servidor).
4. Las rutas se piden a OpenRouteService con `RoutesClient`; la clave se inyecta como `BuildConfig.ORS_API_KEY` desde `local.properties`.

Colecciones de Firestore y sus permisos: [seguridad.md](seguridad.md) §2. Registro de aceptaciones y cómo versionar textos: [legal/README.md](legal/README.md).

## 5. De prototipo a módulos

El prototipo original era `apexmoto.html`. Correspondencia:

| Prototipo | Código |
|---|---|
| Login (correo, Google, teléfono) y elección de rol | `ui/Root.kt` (`AuthScreen`, `RoleSelectScreen`), `auth/SignIn.kt`, `data/Repo.kt` |
| Rutas: Ruta IA y presupuesto | `ui/RiderRoutes.kt`, `maps/RoutePlanner.kt` |
| Comercios, menú, pedidos, cupones | `ui/RiderVenues.kt`, `ui/Commerce.kt` |
| Comunidad (rodadas), renta y guía | `ui/RiderRent.kt`, `ui/Owner.kt` |
| Taller móvil S.O.S. | `ui/RiderWorkshop.kt`, `ui/Taller.kt` |
| Dueño de moto y guía: flota, solicitudes, ganancias | `ui/Owner.kt` |
| Chat, perfil, configuración | `ui/Misc.kt` |
| Legal y privacidad | `legal/Legal.kt`, `ui/Legal.kt`, `core/BusinessDays.kt`, `assets/legal/*.md` |
| Mapas, rutas y guiado | `maps/*.kt`, `ui/NavigationScreen.kt` |

## 6. Limitaciones conocidas

- Sin servidor: no se puede validar que un total sea la suma de sus ítems ni que dos reservas no se crucen. Antes de procesar pagos esto debe pasar a un backend ([seguridad.md](seguridad.md) §4).
- La clave de OpenRouteService va dentro del APK y es extraíble ([seguridad.md](seguridad.md) §6).
- Las rutas se calculan con el perfil `driving-car`; `driving-motorcycle` depende del plan de ORS (`maps/Maps.kt`).
- Sin pruebas automatizadas de la app; solo de las reglas de Firestore.
- Textos de interfaz fijos en código (no en `strings.xml`), sin modo oscuro y accesibilidad pendiente.
