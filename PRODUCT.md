# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

**Principal: el rider (motero).** Planea y hace rodadas por carretera: escoge un corredor, arma la ruta con paradas, calcula presupuesto, renta moto si lo necesita, pide en comercios del camino, navega giro a giro y pide auxilio si algo falla. Su experiencia manda en las decisiones de diseño.

Roles secundarios, que existen para servir al rider:
- **Comercio aliado:** negocio de carretera (comida + parqueo, café/mirador, gasolina/taller). Publica su negocio y menú, fija su ubicación, recibe pedidos, cupones y S.O.S.
- **Dueño de moto:** renta su flota a otros riders: fija requisitos y un acuerdo de daños, revisa el perfil de conductor de quien pide, llena las actas de entrega y devolución y califica a los riders.
- **Taller mecánico:** recibe los S.O.S. de los riders cerca de su taller (según los tipos de emergencia que cubre y su radio), los atiende paso a paso (aceptado → en camino → atendiendo → resuelto), publica su catálogo de servicios con precios y aparece para los riders en "Gasolina & Talleres". Los comercios aliados ya no atienden S.O.S.: son de comida y café.

El rol se elige al registrarse (o en el primer ingreso con Google/teléfono).

**No hay guías ni conductores (decisión del 2026-11-15).** El rol "Guía de rutas" se retiró. En una rodada, quien va adelante es un participante más: lo asigna quien creó la rodada, se distingue con una bandera, no cobra y no presta un servicio. La app no lleva pasajeros de ningún tipo: ni parrillero, ni conductores tipo DiDi o Yango.

## Product Purpose

Reunir en una sola app todo lo que un rider necesita para una rodada, que hoy anda repartido entre Google Maps, WhatsApp y grupos moteros de Facebook: rutas, paradas pensadas para motos, renta, comunidad, navegación y asistencia en carretera. Por ahora el éxito es validar que el ciclo completo del rider funciona de punta a punta.

## Positioning

Lo que ApexMoto puede decir y una app de mapas o un grupo de chat no puede decir con verdad: todo eso junto, para motos.
- **Corredores curados:** rutas moteras concretas (Lago Calima, Alto de La Línea, Las Palmas, Chicamocha) con paradas aliadas pensadas para motos: parqueo, mirador, comida, taller.
- **Todo en un viaje:** planear, presupuestar, rentar moto, pedir en el comercio y navegar sin salir de la app.
- **Respaldo en ruta:** S.O.S. con taller móvil, más la comunidad de rodadas.
- **Ruta IA:** el rider describe lo que quiere ("asado barato cerca a Calima") y la app arma la ruta con presupuesto.

## Operating Context

- Se usa en el teléfono, antes de salir (planear y presupuestar) y en la carretera (guiado, pedidos, S.O.S.). En la carretera el rider puede tener guantes, sol directo, señal débil y la atención puesta en manejar.
- El guiado sigue la ubicación, lee las instrucciones en voz alta y recalcula la ruta si el rider se desvía más de 150 m. También se puede pasar la navegación a Google Maps u otra app de mapas.
- Rituales del motero: las rodadas (salidas en grupo con estilo Turismo, Aventura, Deportivo o Relajado), las paradas de comida y mirador, y la asistencia por pinchazo, batería, gasolina, remolque o falla mecánica.

## Capabilities and Constraints

- **Stack:** Kotlin + Jetpack Compose (Material 3), Firebase Auth (correo, Google, teléfono) y Firestore, mapas OpenStreetMap (osmdroid), rutas y guiado con OpenRouteService. Hoy las rutas se calculan con el perfil `driving-car`.
- **Módulos:** rutas (Ruta IA, presupuesto, rutas guardadas), comercios (mapa, menú, pedidos, cupones), rodadas (públicas o de amigos, con aprobación, líder y cierre, mapa del grupo en vivo), amigos (perfil público con @usuario y avatar de moto, solicitudes, bloqueos y estado), renta segura (perfil de conductor, requisitos y acuerdo de daños por moto, actas de entrega y devolución, calificación mutua, reserva inmediata solo con historial) y extras, taller móvil S.O.S., chat, perfil y configuración.
- **Etapa:** proyecto personal / MVP. Se prepara una prueba cerrada con amigos que cubre todos los roles. Las reglas de Firestore son de producción y tienen pruebas.
- **Dos ambientes (compromiso):** *Pruebas* (desarrollo y presentaciones; los datos demo solo existen aquí; se reconoce por el nombre "ApexMoto Pruebas" y el ícono cian) y *Productivo* (la app real, vacía al empezar). Cada uno con su proyecto de Firebase. Ver `docs/desarrollo.md`.
- **Legal (compromiso):** casillas separadas para términos y para datos personales, registro inmutable de cada aceptación, no se recogen documentos ni biometría, la ubicación solo se usa con aviso aceptado. En la renta no hay planes de "protección": cada moto tiene un acuerdo de daños (depósito y tope) que no es un seguro, y así se dice. Ver `docs/legal/`.
- **Pendiente (no diseñar como si existiera):** pagos reales, notificaciones push, fotos de comercios y motos y lista de pilotos de Comunidad.
- **Solo planes gratuitos (compromiso):** nada que exija tarjeta o facturación de Google Cloud. Por eso los mapas son OpenStreetMap y las rutas OpenRouteService (plan Free). Cualquier servicio nuevo debe tener plan gratuito sin tarjeta; si no lo tiene, no entra.
- **Mercado: Colombia, empezando por el Valle del Cauca (compromiso):** Cali y sus corredores cercanos primero. Todo en español de Colombia, todos los precios en pesos colombianos (COP, formato `$280.000`, sin decimales; nunca dólares), teléfonos con +57 por defecto y voz del guiado en es-CO.

## Brand Commitments

- **Nombre:** ApexMoto.
- **Paleta (compromiso, del prototipo `apexmoto.html`):** naranja `#D94600` (primario) y `#C2410C`, cian `#0E7490` y `#0E6A82`, verde `#1B8A3A`, rojo `#D13B3B`, tinta `#1C2230`, gris `#566074`, fondos `#F3F4F8` / `#E9EBF2` / `#D5D9E4`. Vive en `ui/theme/Theme.kt`; las pantallas no definen colores propios.
- **Fuentes (compromiso, del prototipo):** Chivo para títulos (los de sección van en mayúsculas), Space Grotesk para el texto, JetBrains Mono para cifras, precios y etiquetas. Van empaquetadas en `app/src/main/res/font/`.

## Evidence on Hand

- Datos demo: comercios, motos y rodadas del prototipo, solo en la versión de pruebas (Perfil → Datos demo). Las coordenadas de corredores y comercios son aproximadas (`app/src/main/java/co/apexmoto/app/core/Constants.kt`, `data/Repo.kt`).
- No hay testimonios, clientes, comercios aliados reales, métricas, fotos ni reseñas. El trabajo futuro no debe inventarlos.
- El ícono es el que viene por defecto. No existe logo.

## Product Principles

1. **El rider primero.** Si un flujo de comercio, dueño o taller choca con la experiencia del rider, gana el rider.
2. **Pensado para la carretera.** Lo que se usa en marcha (guiado, S.O.S., pedidos) debe funcionar con una mirada, a una mano y en malas condiciones.
3. **Un solo viaje, sin saltos.** Del plan a la parada y al regreso sin cambiar de app. Pasar la navegación a otra app es una salida, no el camino principal.
4. **Específico para motos.** Parqueo, miradores, talleres y corredores moteros. Nada de directorios genéricos.
5. **Honesto sobre la etapa.** No mostrar como si existiera lo que todavía no existe (pagos, reseñas, aliados reales).
