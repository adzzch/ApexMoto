# ApexMoto

App Android para motociclistas en Colombia (Valle del Cauca primero): rutas por corredores moteros, comercios aliados, renta de motos, guía de rutas, comunidad de rodadas, S.O.S. con talleres y navegación giro a giro. Todo en un solo viaje, pensado para motos.

> **Estado:** MVP personal en desarrollo (versión `1.1`). No hay usuarios ni comercios reales, ni pagos. Ver la [hoja de ruta](docs/hoja-de-ruta.md).
> **Licencia:** propietaria, todos los derechos reservados. Ver [LICENSE](LICENSE).

## Qué hace

| Para | Funciones |
|---|---|
| **Rider** | Ruta IA y presupuesto, rutas guardadas, comercios en mapa, pedidos y cupones, renta de moto, guía de rutas, rodadas, S.O.S. con taller, guiado giro a giro con voz |
| **Comercio aliado** | Negocio y menú, ubicación en mapa, pedidos, canje de cupones |
| **Taller mecánico** | Recibe y atiende S.O.S. cercanos paso a paso, catálogo de servicios |
| **Dueño de moto** | Flota, solicitudes de renta, ingresos |
| **Guía de rutas** | Salidas, servicio y ganancias (guía en su propia moto, sin pasajeros) |

El contexto de producto, los usuarios y los principios están en [PRODUCT.md](PRODUCT.md).

## Stack

| Capa | Tecnología |
|---|---|
| Lenguaje y UI | Kotlin 2.0.20, Jetpack Compose (Material 3), un solo módulo `:app` |
| Backend | Firebase Authentication (correo, Google, teléfono) y Firestore. No hay servidor propio: las [reglas de Firestore](firestore.rules) son el servidor |
| Mapas y rutas | OpenStreetMap vía osmdroid; rutas y guiado con OpenRouteService (plan gratuito) |
| Compilación | Android Gradle Plugin 8.5.2, Gradle 9.7.1 (wrapper), JDK 21; `minSdk 26`, `targetSdk 34` |
| Compromiso | Solo servicios gratuitos que no pidan tarjeta ni facturación |

## Inicio rápido

Requisitos: Android Studio reciente, JDK 21 (Android Studio lo descarga), un emulador o celular con Google Play Services, y una cuenta de Firebase.

1. **Clona** el repositorio y ábrelo en Android Studio.
2. **Firebase:** crea un proyecto con una app Android de paquete `co.apexmoto.app`, descarga `google-services.json` y colócalo en `app/` (no se versiona).
3. **OpenRouteService:** copia `local.properties.example` a `local.properties`, conserva la línea `sdk.dir` y pon tu `ORS_API_KEY`.
4. **Sincroniza** Gradle y ejecuta la configuración `app`.

Los pasos completos (habilitar métodos de acceso, huellas SHA, publicar reglas, crear administrador, datos demo, pruebas) están en la [guía de desarrollo](docs/desarrollo.md).

## Estructura del repositorio

```
ApexMoto/
├── app/                          Módulo Android
│   └── src/
│       ├── main/java/co/apexmoto/app/   Código (ver docs/arquitectura.md)
│       ├── main/assets/legal/           Textos legales que ve el usuario (única fuente)
│       ├── main/res/                    Recursos, fuentes de marca, tema
│       ├── debug/ y release/            App Check por tipo de compilación
├── docs/                         Documentación (índice en docs/README.md)
│   ├── arquitectura.md · desarrollo.md · seguridad.md · hoja-de-ruta.md
│   └── legal/                    Cumplimiento: normas, datos, Google Play, revisión de abogado
├── firestore-tests/              Pruebas de las reglas de seguridad (emulador)
├── firestore.rules · storage.rules · firebase.json
├── gradle/                       Catálogo de versiones y wrapper
├── .github/                      Plantillas de issues y pull requests
├── PRODUCT.md                    Producto, usuarios, marca y principios
└── LICENSE · THIRD_PARTY_NOTICES.md · SECURITY.md · CONTRIBUTING.md · CHANGELOG.md
```

## Documentación

| Documento | Contenido |
|---|---|
| [docs/README.md](docs/README.md) | Índice completo |
| [docs/desarrollo.md](docs/desarrollo.md) | Configuración, ejecución, pruebas, versiones y publicación |
| [docs/arquitectura.md](docs/arquitectura.md) | Capas, módulos, flujo de datos, mapa del prototipo al código |
| [docs/seguridad.md](docs/seguridad.md) | Modelo de seguridad, reglas por rol, claves, antifraude |
| [docs/legal/](docs/legal/README.md) | Cumplimiento (Ley 1581, Ley 1480…), datos personales, Google Play |
| [docs/hoja-de-ruta.md](docs/hoja-de-ruta.md) | Qué falta antes de abrir al público |
| [CHANGELOG.md](CHANGELOG.md) | Historial de versiones |

## Contribuir y reportar

- Cómo trabajar en el proyecto: [CONTRIBUTING.md](CONTRIBUTING.md) y [código de conducta](CODE_OF_CONDUCT.md).
- Vulnerabilidades: **no abras un issue público**; sigue [SECURITY.md](SECURITY.md).

## Aviso legal

Los documentos de [`docs/legal/`](docs/legal/README.md) son guías internas, **no asesoría jurídica**, y deben revisarse con un abogado antes de publicar la app. Los textos para usuarios tienen marcadores `[COMPLETAR: …]` con los datos del responsable y la compilación de publicación falla hasta completarlos. Componentes de terceros y atribuciones de mapas: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

© 2026 Daniel Alejandro Zamora. Todos los derechos reservados. "ApexMoto" y su identidad visual no están licenciados.
