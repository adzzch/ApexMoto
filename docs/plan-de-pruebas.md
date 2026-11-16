# Plan de pruebas por rol

Sirve para la **prueba cerrada con personas reales** en el ambiente productivo: la app "ApexMoto" (variante `prod`), que arranca vacía. El objetivo es comprobar que cada rol funciona de punta a punta y anotar todo lo que falle o confunda.

Cada paso dice quién lo hace y qué debe pasar. Marca la casilla si salió bien. Si algo falla, anótalo en el [registro de hallazgos](#registro-de-hallazgos) con una captura de pantalla.

## Antes de empezar

- **App:** el APK `apexmotov1.1.apk` de la variante `prodDebug` (cómo generarlo: [desarrollo.md](desarrollo.md) §3.2). En el celular hay que permitir la instalación de apps de origen desconocido.
- **Reglas:** `firestore.rules` publicadas en el proyecto productivo.
- **Cuentas:** mínimo cinco, una por rol y dos riders: **Rider 1**, **Rider 2**, **Comercio**, **Taller** y **Dueño de moto**. Una persona puede manejar dos cuentas en dos celulares, o en el mismo celular cerrando sesión entre una y otra.
- **Datos:** cada quien usa sus propios datos. No se registra a otras personas ni se usan negocios reales sin su permiso.
- **Ubicación:** los recorridos de S.O.S. y de guiado necesitan GPS y datos móviles; mejor hacerlos al aire libre.

## Quién participa en cada recorrido

| Recorrido | Rider | Comercio | Taller | Dueño |
|---|:-:|:-:|:-:|:-:|
| R1. Registro e ingreso | ✓ | ✓ | ✓ | ✓ |
| R2. El aliado prepara su negocio | | ✓ | ✓ | |
| R3. Pedido | ✓ | ✓ | | |
| R4. Cupón | ✓ | ✓ | | |
| R5. El dueño publica su moto | ✓ | | | ✓ |
| R6. Renta | ✓ | | | ✓ |
| R7. Cancelación y retracto | ✓ | | | ✓ |
| R8. S.O.S. | ✓ | ✓ | ✓ | ✓ |
| R9. Rodada | ✓ (los dos) | | | |
| R10. Rutas y guiado | ✓ | | | |
| R11. Legal y privacidad | ✓ | ✓ | ✓ | ✓ |
| R13. Amigos | ✓ (los dos) | | | |
| R14. Mapa del grupo | ✓ (los dos) | | | |
| R15. Pedido en ruta y reseñas | ✓ | ✓ | | |
| R12. Eliminar la cuenta | ✓ | | | |

## Recorridos

### R1. Registro e ingreso

- [ ] **Rider 1:** Crear cuenta con correo y una contraseña segura. Las casillas de términos y de datos personales empiezan sin marcar, y sin las dos no deja crear la cuenta. → Entra a "Espacio Rider".
- [ ] **Comercio:** "¿Tienes un negocio, taller o motos? Únete como aliado" → Comercio aliado → nombre del negocio y zona. → Entra a "Espacio Negocio".
- [ ] **Taller:** igual, eligiendo Taller mecánico. → Entra a "Espacio Taller".
- [ ] **Dueño:** igual, eligiendo Dueño de moto. Aparece una casilla más, la de condiciones para dueños. → Entra a "Espacio Dueño".
- [ ] **Rider 2:** entrar con Google (o con número de celular y el código por SMS). → Antes de entrar pide el nombre y las casillas.
- [ ] **Cualquiera:** cerrar sesión y volver a entrar. → Entra directo, sin pedir otra vez las casillas.
- [ ] **Cualquiera:** "Olvidé mi contraseña". → Llega un correo para cambiarla.

### R2. El aliado prepara su negocio

- [ ] **Comercio:** Negocio → nombre, descripción y promoción; fijar la ubicación tocando el mapa o con "Usar mi ubicación actual"; "Guardar cambios". → Los riders lo ven en Comercios y en el mapa.
- [ ] **Comercio:** Menú → agregar dos o tres productos con precio en pesos. → Aparecen en el menú que ve el rider, con formato `$42.000`.
- [ ] **Taller:** Mi taller → ubicación, tipos de emergencia que atiende y radio. Dejar activado "Recibiendo S.O.S.". → Los riders lo ven en Comercios → Gasolina & Talleres.
- [ ] **Taller:** Servicios → revisar el catálogo y cambiar un precio. → Se guarda.

### R3. Pedido

- [ ] **Rider 1:** Comercios → el negocio del Comercio → "Ver menú y pedir" → agregar productos → escoger la hora en "¿Para qué hora lo quieres listo?" → "Enviar pedido". → El pedido queda "Enviado" y el comercio lo ve con "Listo a las …".
- [ ] **Comercio:** Pedidos → "Aceptar y preparar" → "Marcar listo" → "Entregado". → El rider ve cada cambio: Preparando, ¡Listo!, Entregado.
- [ ] **Los dos:** mientras se prepara, abrir el chat del pedido y escribirse. → Cada uno ve los mensajes del otro.
- [ ] **Rider 1:** hacer otro pedido y cancelarlo mientras sigue "Enviado". → Queda "Cancelado". Un pedido ya aceptado no se puede cancelar desde la app.
- [ ] **Comercio:** rechazar un tercer pedido. → El rider lo ve "Rechazado".

### R4. Cupón

- [ ] **Rider 1:** en Comercios, tocar el botón de cupón del negocio. → Aparece un código `APX-XXXX`.
- [ ] **Comercio:** Canjes → escribir el código → "Canjear". → "Cupón canjeado para" y el nombre del rider.
- [ ] **Comercio:** canjear el mismo código otra vez. → Dice que ya fue canjeado.

### R5. El dueño publica su moto

- [ ] **Dueño:** Mi flota → Agregar → modelo, cilindraje, precio por día, ciudad, tipo, fechas de SOAT y revisión técnico-mecánica, años mínimos manejando, depósito y tope por daños → Guardar. Sin las dos fechas no deja guardar. → La moto queda "Disponible" y muestra su acuerdo de daños.
- [ ] **Rider 1:** Rentar. → La moto aparece con su precio por día, "Nueva" (sin reseñas) y la licencia que pide.

### R6. Renta

- [ ] **Rider 1:** Perfil → "Completar mi perfil de conductor" → categoría, vencimiento de la licencia, año desde el que maneja, cilindraje máximo y (opcional) contacto de emergencia → Guardar.
- [ ] **Rider 1:** Rentar → elegir fechas → la moto. → En Requisitos ve si cumple (categoría, vigencia hasta el último día, años mínimos). Marcar la declaración de licencia, aceptar el acuerdo de daños → extras → "Solicitar". → En Perfil → Mis rentas queda "Pendiente". Aunque la moto sea de reserva inmediata, la primera vez sale "Solicitar": la reserva inmediata es para quien ya terminó una renta.
- [ ] **Dueño:** Solicitudes → ve el perfil de conductor del rider, el acuerdo aceptado y "Todavía sin calificaciones de dueños" → Aceptar. → El rider la ve "Aceptada" y esas fechas dejan de estar disponibles para otros.
- [ ] **Los dos:** chat de la renta. → Funciona en los dos sentidos.
- [ ] **Dueño (con el rider al lado):** "Llenar acta de entrega" → sin marcar que vio la licencia física no deja enviar; marcarla, kilometraje, gasolina, estado y accesorios → Enviar. → Queda "Esperando que … confirme el acta"; el contacto de emergencia se ve en la solicitud.
- [ ] **Rider 1:** Perfil → Mis rentas → "Revisar y confirmar" → "Confirmo el acta". → La renta pasa a "En ruta".
- [ ] **Dueño:** "Llenar acta de devolución" → un daño con un valor mayor al tope. → No deja enviar. Con un valor menor → Enviar.
- [ ] **Rider 1:** confirmar el acta de devolución. → La renta queda "Finalizada" con los daños anotados, y aparece en Ingresos del dueño. El contacto de emergencia ya no se ve.
- [ ] **Rider 1:** "Calificar la moto". **Dueño:** "Calificar a …". → La reseña de la moto se ve en su detalle con el promedio. La del rider la ven el dueño (en la próxima solicitud de ese rider) y el propio rider (Perfil → Perfil de conductor), pero no otro rider.
- [ ] **Rider 1:** pedir otra vez una moto de reserva inmediata. → Ahora sale "Reservar" y queda "Aceptada" sin esperar al dueño.
- [ ] **Rider 2:** pedir la misma moto en otras fechas. **Dueño:** Rechazar. → El rider la ve "Rechazada".
- [ ] **Rider 2:** abrir una moto que pide más años manejando de los que declaró, o una de más de 125 c. c. con licencia A1. → Requisitos en rojo y no deja pedirla.

### R7. Cancelación y retracto

- [ ] **Rider 1:** solicitar una renta y cancelarla el mismo día. → La app explica que es un retracto (dentro de los 5 días hábiles) y lo registra. El dueño ve que el rider ejerció su derecho de retracto.
- [ ] **Rider 1:** una renta que ya está "En ruta" no muestra el botón de cancelar.
- [ ] **Dueño:** en una renta "Aceptada", "Cancelar la entrega" → "No presentó la licencia…". → El rider la ve rechazada con ese motivo y las fechas quedan libres.

### R8. S.O.S.

- [ ] **Rider 1:** Taller → "Pedir taller móvil" → tipo de emergencia → ubicación (la primera vez aparece el aviso de ubicación; también se puede escribir dónde está) → "Enviar S.O.S.". → Queda "Buscando taller".
- [ ] **Taller:** ve el S.O.S. disponible si está dentro de su radio y atiende ese tipo → "Aceptar · llego en … min". → El rider ve "Taller asignado".
- [ ] **Taller:** "Voy en camino" (la primera vez pide el aviso y el permiso de ubicación). → Queda activo "Mostrarle al rider dónde voy".
- [ ] **Rider 1:** en su S.O.S. ve el mapa con el taller acercándose, la distancia y "llega en unos … min".
- [ ] **Taller:** "Llegué, empezar atención" → "Marcar resuelto". → El rider ve cada paso y al final "Resuelto"; el taller sale del mapa al llegar.
- [ ] **Rider 1:** "Calificar al taller" → estrellas y comentario. → La reseña aparece en el taller (Comercios → Gasolina & Talleres) y en su resumen.
- [ ] **Los dos:** "Chat con el taller". → Funciona en los dos sentidos.
- [ ] **Rider 2:** enviar un S.O.S. y cancelarlo antes de que lo atiendan. → Queda "Cancelado".
- [ ] **Rider 2:** enviar dos S.O.S. seguidos. → El segundo no sale hasta que pasa un minuto.
- [ ] **Comercio:** no ve ningún S.O.S.
- [ ] **Rider 1 (con una renta "En ruta"):** al pedir el S.O.S. ve que el dueño también lo verá. **Dueño:** en Solicitudes aparece el aviso rojo con el tipo, el lugar y el estado del S.O.S.

### R9. Rodada

- [ ] **Rider 1:** Comunidad → Rodadas → "Crear rodada": nombre, cuándo, número de motos, punto de encuentro, estilo, "Pública" con aprobación y, si tiene una ruta guardada, la ruta. → Se abre el detalle, con Rider 1 como líder.
- [ ] **Rider 2:** en la lista, "Pedir unirme". → Queda "Solicitud enviada".
- [ ] **Rider 1:** en el detalle, "Quieren unirse" → "Aprobar". → Rider 2 aparece en Miembros.
- [ ] **Rider 1:** crear otra rodada "Solo amigos" e invitar a Rider 2 (deben ser amigos, ver R13). → Rider 2 ve "Te invitaron" y entra con "Unirme".
- [ ] **Una cuenta que no sea amiga de Rider 1:** no ve la rodada "Solo amigos".
- [ ] **Rider 2:** "Salir de la rodada". → Ya no aparece en Miembros.

### R10. Rutas y guiado

- [ ] **Rider 1:** Rutas → escribir lo que quiere (por ejemplo "asado barato cerca a Calima") → generar la ruta. → Muestra paradas y presupuesto en pesos.
- [ ] **Rider 1:** "Guardar ruta". → Aparece en Mis rutas guardadas, y se puede borrar.
- [ ] **Rider 1:** "Iniciar guiado" al aire libre. → Pide la ubicación con su aviso, lee las instrucciones en voz alta y recalcula si se desvía.

### R11. Legal y privacidad

- [ ] **Todos:** Perfil → Legal y privacidad → abrir cada documento. → Muestran los datos del responsable; en ningún texto aparece `[COMPLETAR`.
- [ ] **Todos:** "Ver y descargar mis datos" → "Compartir o guardar". → Sale un archivo con los datos de la cuenta.
- [ ] **Uno:** "Enviar solicitud" (por ejemplo, una consulta). → Queda con número de radicado y fecha límite de respuesta.
- [ ] **Todos:** Registro de aceptaciones. → Están términos y privacidad, y ubicación si la aceptó.
- [ ] **Dueño con una cuenta de antes de la fase 5:** al abrir la app pide aceptar de nuevo las Condiciones para dueños 1.1.0 (licencia física, actas y acuerdo de daños). → Después de aceptarlas entra normal y en el registro aparece la versión 1.1.0.
- [ ] **Rider que rentó:** "Ver y descargar mis datos". → Incluye su perfil de conductor y las actas de sus rentas.
- [ ] **Aliados:** completar "Datos del oferente". → Se guardan y nadie más los ve.
- [ ] **Uno:** cambiar su nombre. → Los demás lo ven con el nombre nuevo en pedidos y chats nuevos.

### R13. Amigos

- [ ] **Rider 1:** Comunidad → Amigos → crear el perfil: @usuario, nombre, moto y color. → Aparece su tarjeta con el avatar.
- [ ] **Rider 2:** lo mismo. Probar primero el @usuario de Rider 1. → Avisa que ya lo tiene otra persona.
- [ ] **Rider 1:** buscar el @usuario exacto de Rider 2 → "Agregar". → Queda "Solicitud enviada".
- [ ] **Rider 2:** Solicitudes recibidas → "Aceptar". → Los dos se ven en Amigos.
- [ ] **Rider 2** con la app abierta. → **Rider 1** lo ve "En línea" (puede tardar unos minutos).
- [ ] **Rider 2:** Perfil → apagar "Mostrar mi estado a mis amigos". → Rider 1 deja de ver su estado.
- [ ] **Rider 1:** botón de compartir. → Abre el menú del celular con el texto y su @usuario.
- [ ] **Rider 1:** en el amigo, ⋮ → Bloquear. → Dejan de ser amigos y Rider 2 ya no puede enviarle solicitud. Luego "Desbloquear".
- [ ] **Comercio:** no tiene Comunidad ni Amigos.

### R14. Mapa del grupo (al aire libre y con datos)

- [ ] **Rider 1 (creador):** en el detalle de la rodada, ⋮ de Rider 2 → "Hacer líder". → Rider 2 aparece con la bandera de líder.
- [ ] **Rider 1:** "Iniciar rodada". → Queda "En curso".
- [ ] **Los dos:** activar "Compartir mi ubicación con el grupo" (la primera vez pide el aviso y el permiso). → En el mapa se ven los dos círculos de colores, y en Miembros dice "En el mapa · hace … s · a … km".
- [ ] **Rider 2:** abrir un S.O.S. (R8) sin salir de la rodada. → En el mapa de Rider 1 su círculo se pone rojo y aparece el aviso de auxilio.
- [ ] **Rider 2:** salir de ApexMoto con el botón de inicio. → Al rato deja de actualizarse ("hace … min").
- [ ] **Un amigo de Rider 1 que no está en la rodada:** en Amigos ve "En rodada: …", pero no ve el mapa.
- [ ] **Rider 1:** "Terminar rodada". → El mapa desaparece para todos y la rodada queda "Terminada".

### R15. Pedido desde el guiado y reseñas

- [ ] **Rider 1:** Comercios → el negocio del Comercio → "Guiar en la app". → En el panel de abajo aparece la parada con km, minutos, "llegas a eso de las …" y el botón "Pedir".
- [ ] **Rider 1:** "Pedir". → El menú propone "Cuando llegue" con la hora de llegada y no deja escoger una hora anterior.
- [ ] **Comercio:** Pedidos. → Los pedidos en curso salen ordenados por la hora a la que los quieren listos. Llevar el pedido hasta "Entregado".
- [ ] **Rider 1:** Perfil → Mis pedidos → "Calificar" → estrellas y comentario. → En el negocio se ven la reseña y el promedio, y "Calificar" desaparece.
- [ ] **Rider 2:** abrir el negocio. → Ve la reseña de Rider 1 con su nombre. Un negocio sin reseñas dice "Nuevo".

### R16. Explorar (Rutas → Explorar)

1. Sin permiso de ubicación: debe recomendar para Cali. Toca **Cambiar**, busca otro municipio (por ejemplo "Salento") y revisa que cambien las rodadas y los lugares.
2. Con permiso: **Usar mi ubicación** debe decir la ciudad o el pueblo donde estás.
3. Filtra por distancia y por tipo (miradores, cascadas…). Busca "termales" y "Boyacá": deben salir lugares de todo el país.
4. Abre un lugar y una rodada famosa: **Guiarme hasta allá**, **Guiar la ruta** y **Crear una rodada con esta ruta**.

### R17. Rodada nueva con código y medallas

1. Crea una rodada: elige la fecha en el calendario, el punto de encuentro escribiendo (ej. "D1", "Terpel", "Éxito") y el destino con una parada y un restaurante. El botón **Listo** debe verse sin bajar.
2. Revisa el mapa de la rodada: punto de encuentro, paradas y, ya iniciada, los miembros.
3. Hazla **Privada**, comparte el código y que otro rider entre con **Comunidad → Tengo código**. Un tercero sin código no debe verla.
4. Menú **⋮**: sobre ti (ver perfil, salir), sobre otro como creador (líder, cierra el grupo, expulsar, añadir amigo) y como miembro normal (ver perfil, añadir amigo).
5. Completa una ruta de más de 2 km con el guiado: en **Mi perfil → Trayectoria** debe sumar una ruta. Repetirla antes de 20 minutos no suma.

### R18. Ruta IA con Gemini

1. Con AI Logic activo: escribe "quiero ir a Cristo Rey y comer algo típico" y genera. Las paradas deben ser lugares reales cercanos y explicar por qué.
2. Sin internet: debe avisar que la IA no está disponible y armar la ruta igual.

### R12. Eliminar la cuenta (al final)

- [ ] **Rider 2:** Legal y privacidad → "Eliminar mi cuenta". → Si el ingreso no es reciente, pide cerrar sesión y volver a entrar; luego elimina la cuenta y vuelve a la pantalla de ingreso.

## Qué observar además

- Si algo no se entiende sin que nadie lo explique.
- Cómo se comporta con señal débil o sin datos.
- Si se puede usar con una mano y con guantes, y si se lee al sol.
- Mensajes de error raros o pantallas que se quedan cargando.

## Registro de hallazgos

| # | Fecha | Recorrido y paso | Rol | Qué pasó | Qué esperabas | Gravedad (alta, media o baja) | Captura |
|---|---|---|---|---|---|---|---|
| 1 | | | | | | | |

Cuando una fase de la [hoja de ruta](hoja-de-ruta.md) agregue funciones (amigos, mapa del grupo, pedidos con hora…), se agregan aquí sus recorridos.
