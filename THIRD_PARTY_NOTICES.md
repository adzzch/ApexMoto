# Avisos de terceros

ApexMoto incluye o usa los componentes siguientes. Cada uno conserva su licencia; la licencia propietaria del repositorio ([LICENSE](LICENSE)) no los cubre.

> Las versiones salen de [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Si cambias una dependencia, actualiza esta tabla **y** el texto que ve el usuario en [`app/src/main/legal/licencias.md`](app/src/main/legal/licencias.md) (cambiar ese texto exige subir su versión; ver [`docs/legal/README.md`](docs/legal/README.md), sección 4).

## Librerías (se distribuyen dentro del APK)

| Componente | Versión | Licencia |
|---|---|---|
| AndroidX core-ktx | 1.13.1 | Apache 2.0 |
| AndroidX Lifecycle (runtime, viewmodel-compose) | 2.8.6 | Apache 2.0 |
| AndroidX Activity Compose | 1.9.2 | Apache 2.0 |
| AndroidX Navigation Compose | 2.8.2 | Apache 2.0 |
| AndroidX Credentials y Google ID | 1.3.0 / 1.1.1 | Apache 2.0 |
| Jetpack Compose (BOM, UI, Material 3, íconos extendidos) | 2024.09.03 | Apache 2.0 |
| Kotlin coroutines (android, play-services) | 1.8.1 | Apache 2.0 |
| OkHttp | 4.12.0 | Apache 2.0 |
| osmdroid | 6.1.20 | Apache 2.0 |
| Firebase Android SDK (BoM; Auth, Firestore, Installations, App Check) | 33.4.0 | Términos de Firebase / Google APIs |
| Google Play services Location | 21.3.0 | Términos de Google Play services |

## Herramientas de compilación (no se distribuyen)

| Componente | Versión | Licencia |
|---|---|---|
| Android Gradle Plugin | 8.5.2 | Apache 2.0 |
| Kotlin y plugin de Compose | 2.0.20 | Apache 2.0 |
| Plugin google-services | 4.4.2 | Apache 2.0 |
| Gradle | 9.7.1 | Apache 2.0 |
| Foojay toolchains resolver | 1.0.0 | Apache 2.0 |
| Firebase CLI y rules-unit-testing (solo `firestore-tests/`) | 13.x / 4.x | MIT / Apache 2.0 |

## Fuentes tipográficas (`app/src/main/res/font/`)

| Fuente | Titular | Licencia |
|---|---|---|
| Chivo | The Chivo Project Authors | SIL Open Font License 1.1 |
| Space Grotesk | The Space Grotesk Project Authors | SIL Open Font License 1.1 |
| JetBrains Mono | The JetBrains Mono Project Authors | SIL Open Font License 1.1 |

La OFL 1.1 permite incluirlas en una aplicación; no pueden venderse por separado. Texto: <https://openfontlicense.org>.

## Datos y servicios externos

| Servicio | Atribución obligatoria | Condiciones |
|---|---|---|
| OpenStreetMap (teselas y datos) | © colaboradores de OpenStreetMap | ODbL; <https://www.openstreetmap.org/copyright> y política de uso de teselas |
| OpenTopoMap | © OpenTopoMap (CC-BY-SA), datos OSM y SRTM | <https://opentopomap.org> |
| OpenRouteService | © openrouteservice.org by HeiGIT, datos OSM | Plan gratuito; límites diarios y términos de uso |
| Firebase (Authentication, Firestore) | — | Plan Spark; términos de Google |

## Marcas de terceros

El logotipo "G" de Google y el ícono de Google Maps son marcas de Google LLC y se usan solo en los botones "Continuar con Google" y "Abrir en Google Maps", conforme a sus pautas de marca. "Android" y "Google Play" son marcas de Google LLC. Ningún tercero respalda a ApexMoto.

## Texto de las licencias

- Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>
- SIL Open Font License 1.1: <https://openfontlicense.org/open-font-license-official-text/>
- Open Database License: <https://opendatacommons.org/licenses/odbl/1-0/>
