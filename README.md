# ApexMoto

App Android para motociclistas en Colombia (Valle del Cauca primero): rutas por corredores moteros, comercios aliados, renta de motos, comunidad de rodadas, S.O.S. con talleres y navegación giro a giro. Todo en un solo viaje, pensado para motos.

> **Estado:** MVP personal en desarrollo (versión `1.1`). Sin pagos. Se prepara una prueba cerrada con personas reales en el ambiente productivo. Ver la [hoja de ruta](docs/hoja-de-ruta.md).
> **Licencia:** propietaria, todos los derechos reservados. Ver [LICENSE](LICENSE).

## Qué hace

| Para | Funciones |
|---|---|
| **Rider** | Ruta IA y presupuesto, rutas guardadas, comercios en mapa, pedidos y cupones, renta de moto, rodadas con mapa del grupo, amigos, S.O.S. con taller, guiado giro a giro con voz |
| **Comercio aliado** | Negocio y menú, ubicación en mapa, pedidos, canje de cupones |
| **Taller mecánico** | Recibe y atiende S.O.S. cercanos paso a paso, catálogo de servicios |
| **Dueño de moto** | Flota, solicitudes de renta, ingresos |

ApexMoto no ofrece transporte de pasajeros ni guías pagados. El contexto de producto, los usuarios y los principios están en [PRODUCT.md](PRODUCT.md).

## Dos ambientes

| Versión | Para qué | Firebase |
|---|---|---|
| **ApexMoto Pruebas** (`pruebas`) | Desarrollo y presentaciones. Trae los datos demo y una franja "Ambiente de pruebas" | Proyecto de pruebas, `app/src/pruebas/google-services.json` |
| **ApexMoto** (`prod`) | La app real, que arranca vacía. No compila sin los datos del responsable en los textos legales | Proyecto productivo, `app/src/prod/google-services.json` |

Se instalan las dos a la vez en el mismo teléfono. Detalle en la [guía de desarrollo](docs/desarrollo.md).

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
2. **Firebase:** en el proyecto de pruebas registra la app `co.apexmoto.dev`, descarga su `google-services.json` y colócalo en `app/src/pruebas/` (no se versiona). Para la versión real, lo mismo con `co.apexmoto.prd` en el proyecto productivo y `app/src/prod/`.
3. **OpenRouteService:** copia `local.properties.example` a `local.properties`, conserva la línea `sdk.dir` y pon tu `ORS_API_KEY`.
4. **Solo para la versión real:** copia `legal.properties.example` a `legal.properties` y completa los datos del responsable.
5. **Sincroniza** Gradle, elige la variante en *Build Variants* (`pruebasDebug` por defecto) y ejecuta `app`.

Los pasos completos (habilitar métodos de acceso, huellas SHA, publicar reglas, crear administrador, datos demo, pruebas) están en la [guía de desarrollo](docs/desarrollo.md).

## Estructura del repositorio

```
ApexMoto/
├── app/                          Módulo Android
│   └── src/
│       ├── main/java/co/apexmoto/app/   Código (ver docs/arquitectura.md)
│       ├── main/legal/                  Textos legales que ve el usuario (única fuente; se completan al compilar)
│       ├── main/res/                    Recursos, fuentes de marca, tema
│       ├── pruebas/ y prod/             Ambientes: google-services.json (no versionado) y recursos propios
│       ├── debug/ y release/            App Check por tipo de compilación
├── docs/                         Documentación (índice en docs/README.md)
│   ├── arquitectura.md · desarrollo.md · seguridad.md · hoja-de-ruta.md
│   └── legal/                    Cumplimiento: normas, datos, Google Play, revisión de abogado
├── firestore-tests/              Pruebas de las reglas de seguridad (emulador)
├── firestore.rules · storage.rules · firebase.json
├── local.properties.example · legal.properties.example   Plantillas de configuración local (no se versionan)
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
| [docs/hoja-de-ruta.md](docs/hoja-de-ruta.md) | Fases de trabajo y qué falta antes de abrir al público |
| [docs/plan-de-pruebas.md](docs/plan-de-pruebas.md) | Recorridos para probar todos los roles con personas reales |
| [CHANGELOG.md](CHANGELOG.md) | Historial de versiones |

## Contribuir y reportar

- Cómo trabajar en el proyecto: [CONTRIBUTING.md](CONTRIBUTING.md) y [código de conducta](CODE_OF_CONDUCT.md).
- Vulnerabilidades: **no abras un issue público**; sigue [SECURITY.md](SECURITY.md).

## Aviso legal

Los documentos de [`docs/legal/`](docs/legal/README.md) son guías internas, **no asesoría jurídica**, y deben revisarse con un abogado antes de publicar la app. Los textos para usuarios tienen marcadores `[COMPLETAR: …]` que se llenan al compilar con los datos del responsable de `legal.properties` (fuera del repositorio). La versión real y cualquier compilación de publicación fallan si falta alguno. Componentes de terceros y atribuciones de mapas: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

© 2026 Daniel Alejandro Zamora. Todos los derechos reservados. "ApexMoto" y su identidad visual no están licenciados.
