# Registro de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado [SemVer](https://semver.org/lang/es/) (`versionName` en `app/build.gradle.kts`). Las fechas son AAAA-MM-DD.

## [Sin publicar]

### Accesibilidad
- **Pantalla de Accesibilidad** (Mi perfil, Configuración y el botón de accesibilidad al ingresar): tamaño de letra (Normal, Grande, Muy grande), alto contraste "modo sol", botones grandes (56 dp), vibración en giros y alertas, guía por voz y reducir animaciones. Se aplica al instante en toda la app y se guarda en el teléfono. La misma pantalla está en el prototipo HTML.
- **Colores con contraste AA:** naranja `#D94600` → `#C84100` (la letra blanca de los botones pasa de 4,35 a 4,99:1), verde `#177431`, rojo `#BA2C2C`, naranja oscuro `#BA3E0B`; todas las etiquetas de estado pasan 4,5:1. Alto contraste llega a 7:1.
- **Letra:** la app ya no frena la letra grande del celular en 1,15x (llega a 2x, con escala no lineal). Etiquetas de 12 sp como mínimo (antes 9–11).
- **Estados con ícono y palabra** (✓ Listo, ✕ Cancelado, reloj Pendiente), chip elegido con ✓ e interruptor activado con ✓: no se depende del color (daltonismo).
- **Lo que se toca mide 48 dp** (Perfil, Asistencia, SOS, Volver, Enviar, Salir de la llamada, chips, selector y contador). En el guiado y las señales, 56 dp.
- **TalkBack:** títulos marcados, interruptores como una sola fila con "activado/desactivado", chips y opciones con "seleccionado", errores con ícono anunciados, mapas descritos con palabras, barra inferior con el nombre de cada pestaña aunque no se vea.
- **Guiado sin depender de la voz:** indicación en letra grande que TalkBack lee sola, vibración (dos toques en cada giro, uno largo al llegar) y aviso "Voz apagada: te avisamos con texto y vibración".
- **Señales rápidas en la rodada:** Paremos, Gasolina, Me quedé, Peligro y Todo bien. Le llegan a todo el grupo como aviso grande con vibración, sin depender del intercom de voz. Reglas: `signal` (cinco claves) y `signalAt` en `rodadas/{id}/live/{uid}`, con prueba.
- **Prototipo HTML:** misma pantalla de Accesibilidad, letra escalable, íconos ocultos al lector de pantalla, lo que se toca accesible por teclado, foco visible, `lang="es-CO"`, guiado con voz y vibración, señales rápidas; ya no se desplaza la página de afuera en el celular. Detalle en `docs/accesibilidad.md`.

### Cambiado
- **Inicio más simple (antes "Rutas"):** el encabezado ya no dice "ApexMoto": en la pestaña principal de cada rol (rider, comercio, dueño y taller) saluda "¡Hola, Nombre!" con una línea según la hora; en las demás muestra el nombre de la sección. La portada del rider tiene, en orden: dónde estás (con "Cambiar" ciudad o usar la ubicación), "¿A dónde vamos?" con solo dos opciones grandes (Ruta con IA y Explorar) y tres rutas recomendadas que salen cerca. Explorar y Ruta IA se abren aparte con "← Inicio"; Presupuesto pasa a un enlace dentro de Ruta IA. Configuración se abre desde Mi perfil.
- **"¿A dónde vamos?" se escribe:** en Inicio ya no hay dos tarjetas; es un campo donde la persona escribe lo que quiere y la IA responde ahí mismo con la ruta (mismos controles de Ruta IA: solo temas de rutas, tope diario y respaldo local). Explorar queda debajo como opción aparte y abre su vista. La lógica de la IA se movió a `requestAiRoute` y la tarjeta del resultado a `AiRouteCard`, que comparten Inicio y Ruta IA.
- **Ruta IA más rápida y sin congelar la app:** el modelo pasa a `gemini-3.5-flash-lite` (responde en ~2 s; los "flash" grandes estaban saturados y tardaban más de 45 s), con un modelo de respaldo si se satura y 20 s de espera máxima por modelo. La búsqueda de candidatos en el catálogo (miles de lugares) se hace fuera del hilo de la pantalla: con frases largas la app quedaba "no responde". Si la IA no contesta, la ruta de respaldo sale de la ciudad elegida (antes podía proponer el Lago Calima estando en Medellín). Mientras arma la ruta se ve "Armando tu ruta…". El catálogo se empieza a cargar al abrir la app.
- **Espaciado parejo en toda la app:** medidas comunes (`Gap`): 16 dp de margen en la pantalla y dentro de las tarjetas, 12 dp entre bloques y 10 dp dentro de las tarjetas; el encabezado usa el mismo margen, así el saludo, las tarjetas y el botón de perfil quedan alineados. El interlineado ahora es proporcional al tamaño de la letra (antes un texto de 13 sp heredaba 23 sp y los renglones quedaban separados). Los enlaces "Cambiar", "Ver más" y "Ver todas" ya no quedan corridos hacia adentro; los recuadros de cifras en pareja tienen la misma altura; las medallas quedan alineadas. En Comunidad, "Unirme con código" pasa a "Tengo código" (se cortaba).
- **Sin franja "Ambiente de pruebas":** la versión de pruebas se distingue por su nombre ("ApexMoto Pruebas"), su ícono cian y su paquete.
- **Ruta IA más cercana:** palabras como "mirador" o "trucha" ya no se buscan como nombres en todo el país (traían lugares a 300 km); solo los nombres propios ("Cristo Rey", "Salento"). En medio día, candidatos a menos de 80 km y Gemini recibe el límite de distancia.
- **Ciudad compartida:** la ciudad elegida en Inicio, Explorar o Comercios es la misma en las tres.
- **Comercios solo muestra comercios** (comida, café y talleres, más gasolineras) de la ciudad elegida y alrededores (40 km), del más cercano al más lejano, en lista y en el mapa. Se quitaron los corredores (Cali, Lago Calima, La Línea, Las Palmas, Chicamocha) y los sitios turísticos, que siguen en Explorar.
- **Asistencia técnica deja de ser pestaña:** sale como botón naranja en el encabezado solo en ruta (guiado o rodada en curso) o mientras haya una asistencia abierta. El SOS también se ve dentro de Asistencia. Fuera de ruta, ambos se abren desde Mi perfil → "Emergencia y asistencia" (SOS con los contactos de emergencia, y Asistencia técnica para pedirla o calificar la última).
- **Rentar** empieza en la ciudad de la persona; "Cambiar" muestra las ciudades del catálogo y sugiere otras ciudades con motos disponibles.
- **Mi perfil:** una sola tarjeta con el perfil (nombre, @usuario, rol, correo) y, debajo, su trayectoria (rutas y medallas).

### Agregado
- **Intercom de voz en las rodadas (Agora):** en la pantalla de la rodada, "Entrar a la llamada" abre una llamada de voz con los miembros, como un intercomunicador. Dos modos: micrófono abierto (con silenciar) o pulsar para hablar. Muestra quién está conectado y quién habla, reduce el ruido del viento y del motor, suena por el intercom Bluetooth del casco o audífonos y sigue con la pantalla apagada (notificación fija con "Salir"). Mientras está abierta, una franja bajo el encabezado permite hablar o salir desde cualquier pantalla. Requiere `AGORA_APP_ID` en `local.properties`.
- **SOS de emergencia de verdad:** botón rojo "SOS" en el encabezado mientras estás en ruta (guiado o rodada en curso); también desde Asistencia. Abre una hoja con "Llamar al 123" y las líneas de Policía (123), Ambulancia (123), Accidente en carretera (#767), Bomberos (119) y Cruz Roja (132); al tocar una se abre el teléfono con el número ya marcado (la persona solo toca Llamar). Contactos de emergencia (hasta 3, de la agenda o escritos) guardados solo en el teléfono, con Llamar y Mensaje con la ubicación; ofrece el contacto del perfil de conductor. "Dónde estás": pueblo más cercano y coordenadas para dictar, y compartir la ubicación. En una rodada con mapa del grupo, "Avisar a mi rodada" pone el punto en rojo con una alerta de emergencia.
- **Gasolineras en Comercios:** filtro nuevo con las 30 estaciones más cercanas a ti (o al corredor elegido), en lista y en el mapa, con guía en la app o Google Maps. Salen del catálogo que trae la app (OpenStreetMap), sin internet. "Gasolina & Talleres" pasa a llamarse "Talleres".
- **Pedidos más claros:** el rider ve en cada comercio si puede pedir por la app o por qué no (sin menú, o aún no está en ApexMoto); el comercio ve en Resumen qué le falta para recibir pedidos (menú, ubicación, abierto) con botón a cada pestaña.
- **Catálogo con los 33 departamentos:** 4.132 poblaciones, 4.487 lugares y 6.927 puntos de encuentro (3.802 gasolineras, 1.998 supermercados de cadena, 929 centros comerciales, 198 peajes). Nuevo `tools/places/fetch_pbf.py` lee el extracto de Geofabrik cuando Overpass no responde.

- **Enlace clicable para invitar a rodadas:** `https://<proyecto>.web.app/r/<CÓDIGO>` abre la invitación en la app con un toque (App Links + `apexmoto://r/<CÓDIGO>`), ya buscada; si la app no está, una página en Firebase Hosting (`hosting/public/`) muestra el código y cómo unirse. Publicación: `docs/desarrollo.md` §2.5.
- **"Falta: …" en el resto de la app:** menú y negocio del comercio, canje de cupones, moto del dueño, perfil de conductor, actas, solicitud de renta (bajo el total), pedido, S.O.S., servicios y S.O.S. del taller, aceptación de documentos, solicitudes de privacidad y datos del oferente; "Completa" en rodadas llenas.
- **Rodadas mejoradas:** fecha y hora reales con calendario (ya no "Sábado 7:00"); punto de encuentro elegido de una lista que se filtra al escribir (estaciones de servicio, supermercados de cadena, centros comerciales, peajes y lugares conocidos, con su barrio), sin texto libre; destino y paradas al crearla, que todos ven en el mapa junto con el punto de encuentro y los miembros; "Llevarme al punto de encuentro". **Pública o Privada:** la pública aparece en Comunidad y cualquiera se une; la privada solo con código o invitación de amigos. **Invitar con código** (8 caracteres, se comparte por WhatsApp) y **Unirme con código** en Comunidad, también para rodadas privadas (reglas: `rodada_codes`, `link_joins`).
- **Menú de cada miembro (⋮):** tú mismo: ver tu perfil, editar avatar y @usuario, compartir o dejar de compartir la ubicación y salir; otro rider: ver perfil y añadir amigo; quien creó la rodada además asigna líder (guía), quién cierra el grupo y expulsa.
- **Perfil de rider público** (`rider/{uid}`) y **trayectoria con medallas:** tiempo en ApexMoto y rutas completadas con el guiado (de 2 km o más), con medallas a las 5 (Principiante), 10 (Explorador), 20 (Rodador), 35 (Trotamundos) y 50 rutas (Rutero experimentado). Reglas: `rider_stats`, solo sube de a una y como mucho cada 20 minutos.
- **Ruta IA con Gemini** (Firebase AI Logic, sin clave en la app): Gemini escoge solo entre lugares reales del catálogo y comercios aliados cercanos, y explica la ruta con consejos. Si no está disponible, se usa el planificador local.
- **Selector de paradas** (Ruta IA → "Yo elijo destino" y crear rodada): al elegir un destino se oculta la lista; quedan "Agregar parada" y, aparte, "Agregar restaurante" (aliados); el botón "Listo" queda fijo abajo.
- **Explorar (Rutas → Explorar):** rodadas famosas de Colombia (con nivel, tipo de vía, km, consejos y botones para guiar la ruta o crear una rodada con ella) y lugares para visitar cerca de donde estás: miradores, pueblos, cascadas, naturaleza, termales, playas, cultura y más, con filtros por distancia y tipo y búsqueda en todo el país. La ciudad se calcula en el teléfono con la ubicación (si ya diste permiso) o la eliges a mano; la ubicación no sale del teléfono. El catálogo viene dentro de la app (`assets/places/colombia.json`): una lista curada de ApexMoto más los lugares de OpenStreetMap (© colaboradores de OpenStreetMap, ODbL), generado con `tools/places/`.
- **Renta segura** (fase 5): perfil de conductor privado (categoría y vencimiento de la licencia, año desde el que maneja, cilindraje máximo y contacto de emergencia opcional), que se copia en la solicitud y solo ve el dueño de esa moto. Cada moto tiene requisitos (años mínimos manejando) y un acuerdo de daños (depósito y tope) que el rider acepta al pedirla. La reserva inmediata queda para riders con una renta terminada. En la entrega, el dueño revisa la licencia física, puede hacer una prueba de manejo y llena el acta de entrega; la renta empieza cuando el rider la confirma y termina con el acta de devolución confirmada (con los daños, sin pasar del tope). El dueño puede cancelar en la entrega diciendo el motivo. Calificación mutua: el rider califica la moto y el dueño al rider (esa solo la ven los dueños y ese rider). Un S.O.S. con una moto rentada también lo ve su dueño. El contacto de emergencia se borra de la solicitud al terminar, rechazar o cancelar.
- **Taller ↔ rider** (fase 4): el taller acepta con su propio taller y, al ir en camino, le muestra al rider dónde va (solo a ese rider, con la app abierta). El rider lo ve acercarse en un mapa con la distancia y una estimación de llegada; la ubicación del taller se borra al llegar, terminar o liberar. Después del S.O.S., el rider califica al taller.
- **Pedidos en ruta con hora y reseñas** (fase 3): durante el guiado se ven la distancia, el tiempo y la hora de llegada a cada parada, con un botón para pedir; el menú propone la hora de llegada y no deja pedir para antes. El comercio ordena los pedidos por esa hora. Reseñas reales (1 a 5 estrellas y comentario) de quien recibió el pedido, una vez, con el promedio validado por las reglas; "Nuevo" para comercios sin reseñas y reseñas de ejemplo en el ambiente de pruebas.
- **Rodadas con líder y mapa del grupo** (fase 2): rodadas públicas o solo de amigos, con aprobación, invitación rápida a amigos, ruta guardada con sus paradas, líder (bandera) y quien cierra el grupo, y un mapa en vivo con la moto de cada miembro, que se pone en rojo si alguien abre un S.O.S. La ubicación es opcional, solo con la app abierta y se borra al terminar. Aviso de ubicación 1.1.0.
- **Amigos** (fase 1): perfil público con @usuario único y avatar de moto (silueta y color, sin fotos), búsqueda por @usuario exacto, solicitudes, bloqueos y estado "en línea" o "en rodada", visible solo para los amigos y que se puede apagar. Textos legales 1.1.0 (términos y privacidad).
- Dos ambientes (variantes `pruebas` y `prod`), cada uno con su proyecto de Firebase y su paquete (`co.apexmoto.dev` y `co.apexmoto.prd`); se instalan a la vez. La versión de pruebas se llama "ApexMoto Pruebas", tiene ícono cian y una franja "Ambiente de pruebas".
- Botón **Borrar datos demo** (sin volver a cargarlos). Los datos demo y el diagnóstico de la clave de rutas solo existen en la versión de pruebas.
- Los datos del responsable se completan en los textos legales al compilar, desde `legal.properties` (no versionado; plantilla `legal.properties.example`).
- `docs/plan-de-pruebas.md`: recorridos para probar todos los roles con personas reales.
- Documentación del repositorio: README, guía de desarrollo, arquitectura, hoja de ruta, licencia propietaria, avisos de terceros, política de seguridad, guía de contribución, código de conducta y plantillas de GitHub.
- `gradle/gradle-daemon-jvm.properties`: el daemon de Gradle usa JDK 21 (JetBrains).

### Corregido
- `build_places.py`: el filtro de supermercados de cadena tenía un carácter invisible y no dejaba pasar ninguno.

### Cambiado
- **El S.O.S. de talleres ahora es "Asistencia técnica":** la pestaña del rider se llama Asistencia (naranja, ya no roja) y la del taller Asistencias; los textos de pinchazos, batería, gasolina, remolque y fallas dicen "asistencia". En el mapa de la rodada, el punto rojo ya no sale por una asistencia sino por una emergencia avisada desde el SOS.
- Búsquedas y cálculos de distancia de Explorar, puntos de encuentro, paradas y ciudades en segundo plano (con espera mientras se escribe), para que la app no se trabe con miles de lugares.
- Firebase BoM 33.4.0 → 34.0.0 (necesario para `firebase-ai`). Textos 1.1.0 de privacidad, ubicación y términos: Explorar, Ruta IA con Gemini, trayectoria y descargo de rutas sugeridas (aún no se habían repartido).
- **Formularios de acceso más tranquilos:** al crear cuenta hay un solo campo de contraseña (con el ojo para revisarla) y debajo, en texto pequeño, solo lo que le falta; cada requisito se oculta al cumplirse. Se quitaron la caja con píldoras, los mensajes de ánimo y "Confirmar contraseña". Los errores salen debajo de cada campo al salir de él (correo sin @ o sin .com, faltan números del celular), y sobre el botón aparece "Falta: …" cuando todavía no se puede continuar. Nombres con mayúscula inicial y sin autocorrección en correos y contraseñas. `Field` acepta `error`, `cap` y `onFocus`; nuevo `Missing` en `Common.kt`.
- Los textos legales pasan de `app/src/main/assets/legal/` a `app/src/main/legal/`. Se quitó del texto todo lo del guía, y los términos prohíben cobrar por participar en una rodada o por guiarla.
- La tarea `checkLegalPlaceholders` se reemplaza por `fill<Variante>LegalTexts`.
- `google-services.json` ya no va en `app/`, sino en `app/src/<ambiente>/`.
- Gradle 9.7.1 con resolver de toolchains Foojay (`settings.gradle.kts`).
- `docs/seguridad.md` actualizado a los requisitos de Java vigentes.
- Textos legales de la renta segura: Términos y Privacidad 1.1.0, Cancelaciones y reembolsos 1.1.0 (depósito, actas, cancelación en la entrega) y Condiciones para dueños 1.1.0 (licencia física, actas, acuerdo de daños, datos del rider). Los dueños aceptan de nuevo sus condiciones.

### Quitado
- Planes de "protección" de la renta (Básica, Estándar, Premium): nadie los respaldaba y podían confundirse con un seguro. Los reemplaza el acuerdo de daños de cada moto.
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
