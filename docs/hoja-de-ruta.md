# Hoja de ruta

Estado al 2026-11-15. Es una lista de trabajo, no un compromiso de fechas. Las tareas legales y de seguridad detalladas están en [legal/revision-abogado.md](legal/revision-abogado.md) y [seguridad.md](seguridad.md).

## Decisiones de producto (2026-11-15)

- **Sin pasajeros.** ApexMoto no lleva pasajeros de ningún tipo: ni parrillero entre amigos, ni conductores tipo DiDi o Yango. Cobrar por llevar a alguien en una moto particular es transporte informal (Ley 769 de 2002, art. 131, infracción D.12).
- **Sin guías pagados.** El rol "Guía de rutas" se retiró. En una rodada, quien la crea asigna al **líder**, que va adelante con una bandera: es un participante más, no cobra y no presta un servicio.
- **Dos ambientes.** *Pruebas* para desarrollo y presentaciones (con datos demo) y *Productivo* para la app real, que arranca vacía. Ver [desarrollo.md](desarrollo.md).
- **Ubicación en vivo** solo entre los miembros de una rodada activa, como mucho cada 12 segundos y solo con la app abierta, guardada en **Firestore** (decisión del 2026-11-15: sus reglas pueden comprobar miembros y amigos; Realtime Database no). Alcanza para la prueba cerrada: una rodada de 5 personas durante 3 horas gasta cerca de un tercio de las lecturas gratis del día. Si el uso crece, pasarla a Realtime Database.
- **Avatares sin fotos:** cada usuario escoge la silueta de su moto y un color. No hay almacenamiento de imágenes (Firebase Storage exige tarjeta).
- **Reseñas:** las de ejemplo solo existen en el ambiente de pruebas. En productivo solo hay reseñas reales, de quien usó el servicio.

## Fases

En **todas** las fases se incluyen las reglas de Firestore con sus pruebas, la actualización de la política y de los avisos cuando haya datos nuevos, los datos demo para presentar y los casos nuevos del [plan de pruebas](plan-de-pruebas.md).

### Fase 0 · Dos ambientes y app lista para probar con amigos (en curso)

- [x] Variantes `pruebas` y `prod`, cada una con su proyecto de Firebase. Datos demo solo en pruebas, con franja y botón para borrarlos.
- [x] Rol "Guía de rutas" retirado: registro, pantallas, reglas, pruebas y textos legales. Las cuentas que lo tenían pasan solas a rider.
- [x] Datos del responsable en los textos legales desde `legal.properties`, sin guardarlos en el repositorio.
- [x] [Plan de pruebas](plan-de-pruebas.md) por rol.
- [x] Proyecto de pruebas (ApexMoto-dev): app `co.apexmoto.dev` con sus huellas, app vieja quitada, `admins/{uid}` creado.
- [x] Su `google-services.json` en `app/src/pruebas/`.
- [x] Proyecto productivo (ApexMoto-prd) creado, con la app `co.apexmoto.prd` y sus huellas.
- [x] En el productivo: métodos de acceso, Firestore, reglas y `google-services.json` en `app/src/prod/`.
- [x] `firestore.rules` publicadas en los dos proyectos.
- [x] Llenar `legal.properties` con los datos del responsable.
- [x] Generar `prodDebug` (`app/build/outputs/apk/prod/debug/apexmotov1.1.apk`), probada en emulador.
- [ ] Entregarla a los amigos y correr el [plan de pruebas](plan-de-pruebas.md).
- [ ] Recomendado antes de la prueba cerrada: que un abogado revise los textos ([legal/revision-abogado.md](legal/revision-abogado.md)). Es obligatorio antes de abrir al público.

### Fase 1 · Perfil y amigos, estilo Steam (hecha)

- @usuario y avatar de moto (silueta y color).
- Agregar amigos por @usuario o con un enlace que se comparte por WhatsApp. Aceptar, rechazar, eliminar y bloquear.
- Estado visible solo para los amigos: "en línea" o "En rodada: Rumbo al Calima".
- De un perfil, los demás solo ven nombre, @usuario y avatar.

### Fase 2 · Rodadas con líder y mapa del grupo (hecha)

- La rodada se arma sobre una ruta con paradas, con fecha y punto de encuentro. "Cupos" pasa a llamarse "motos".
- Invitación rápida desde la lista de amigos. La rodada puede ser abierta o con aprobación, y pública o solo para amigos.
- El creador es el líder y puede pasarle la bandera a otro miembro. Opcional: marcar a quien cierra el grupo.
- Mapa en vivo con la moto de cada miembro, solo mientras la rodada está activa. Un S.O.S. de cualquiera se ve en rojo para todos.
- Aviso de ubicación 1.1: con quién se comparte, cada cuánto y cuándo se borra.

### Fase 3 · Pedidos en ruta con hora de entrega

- Al navegar, cada restaurante de la ruta muestra cuántos km y minutos faltan.
- Al pedir se escoge "listo a las…", nunca antes de la hora estimada de llegada. El comercio ve los pedidos ordenados por hora.
- Al recibir el pedido el rider califica al comercio: primeras reseñas reales.
- No depende de las fases 1 y 2.

### Fase 4 · Taller ↔ rider

- En un S.O.S., el rider ve al taller acercándose en el mapa, con tiempo estimado y chat del caso. Al terminar, lo califica.
- La ubicación del taller solo la ve ese rider, y solo mientras va en camino.
- Verificación de talleres antes de que vean S.O.S. (también está en "Antes de abrir al público").

### Fase 5 · Renta segura

- Perfil de conductor: categoría y vencimiento de la licencia, años manejando, cilindraje más alto que ha manejado, contacto de emergencia.
- Requisitos del dueño por moto (experiencia, edad, depósito, historial) y aprobación con historial. Reserva instantánea solo para quien ya tenga historial.
- En la entrega: licencia física (consulta opcional en RUNT y SIMIT), prueba de manejo corta y acta de entrega confirmada por los dos e inmutable. Al devolver: acta de devolución y calificación mutua.
- Un S.O.S. durante la renta también le llega al dueño.
- Acuerdo de daños (tope y depósito) en lugar de los planes de "protección" de `core/Constants.kt`, que no son un seguro.
- Si se van a rentar motos de verdad en la prueba cerrada, esta fase se adelanta.

### Fase 6 · Más adelante

- Riders visibles en el mapa: cada uno decide, invisible por defecto.

## Antes de abrir al público

- [ ] Revisión por un abogado de los textos y de [legal/revision-abogado.md](legal/revision-abogado.md).
- [ ] Publicar la política de privacidad y la página de eliminación de cuenta en una URL pública.
- [ ] Verificación de talleres antes de que vean S.O.S.
- [ ] Reportar y bloquear usuarios; verificación de correo para aliados.
- [ ] Borrado por vencimiento de plazos de conservación.
- [ ] App Check activado y exigido; alertas de consumo en Firebase.
- [ ] Firma de la app, R8 y ofuscación; subir `targetSdk`.
- [ ] Pruebas unitarias y de interfaz de la app (hoy solo existen las de reglas).

## Producto pendiente

- Pagos reales (cambia términos, reembolsos y formulario de Google Play; exige backend para validar totales).
- Notificaciones push (enviarlas exige un servidor, que no cabe en el plan gratuito; mientras tanto, lo nuevo se ve al abrir la app).
- Fotos de comercios y motos; lista de pilotos de Comunidad.
- Coordenadas reales de corredores y comercios; perfil `driving-motorcycle` de OpenRouteService.

## Calidad de interfaz

- Modo oscuro (hoy solo tema claro).
- Accesibilidad: áreas táctiles de 48 dp, `contentDescription`.
- Mover textos fijos a `strings.xml`.
