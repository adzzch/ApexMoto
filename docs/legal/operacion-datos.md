# Datos personales: inventario, conservación y atención a titulares

## 1. Inventario y minimización

Revisión de cada formulario de la app. "Se queda" significa que el dato es necesario para la finalidad indicada.

### Registro (todos los roles)

| Campo | Decisión | Justificación |
|---|---|---|
| Nombre | Se queda | Lo ve la otra parte del servicio. Puede ser un apodo; máximo 60 caracteres |
| Correo o celular | Se queda | Es el medio de ingreso. No se pide el otro |
| Contraseña | Se queda | La guarda Firebase Authentication, no la app |
| Rol | Se queda | Define qué pantallas y permisos tiene |
| Nombre del negocio y zona (comercio, taller) | Se queda | Se publica para los riders |
| Fecha de nacimiento, documento, dirección, género | No se piden | No son necesarios. La mayoría de edad se declara en la casilla de términos |

### Por rol

| Rol | Dato | Decisión | Justificación |
|---|---|---|---|
| Rider | Declaración de licencia y categoría | Agregado | Requisito para conducir (Ley 769). Se guarda un sí y la categoría, sin foto ni número |
| Rider | Ubicación en el S.O.S. | Se queda, opcional | Para que el taller llegue. Se borra al terminar |
| Rider | Nota del pedido y del S.O.S. | Se queda, opcional | Texto libre con límite de tamaño |
| Rider | Perfil público: @usuario, nombre visible, avatar (moto y color) | Agregado, opcional | Para usar Amigos. Sin fotos. Lo ve cualquier usuario con sesión que busque el @usuario exacto |
| Rider | Solicitudes, amistades y bloqueos | Agregado | Función de Amigos. Se borran al eliminar la cuenta |
| Rider | Estado (en línea o en rodada) y hora de la última actividad | Agregado, se puede apagar | Solo lo ven los amigos; se reemplaza en cada latido |
| Dueño | Modelo, cilindraje, ciudad, precio | Se queda | Oferta |
| Dueño | Vencimiento de SOAT y de revisión | Agregado | Sin ellos la moto no puede circular; el rider debe saberlo |
| Dueño | Placa, tarjeta de propiedad | No se piden | La colección de motos es visible para todos los usuarios; se verifican al entregar |
| Guía | Todo el perfil (moto, zona, tarifa, presentación, servicios) | Eliminado con el rol (2026-11-15) | ApexMoto no ofrece guías ni transporte de pasajeros (ver revision-abogado.md, A.2) |
| Aliados | Nombre o razón social, documento, dirección, teléfono | Agregado, privado | Obligación del art. 53 de la Ley 1480. Solo lo ven el titular y el administrador |
| Comercio, taller | Ubicación del local | Se queda | Dato del negocio, no de la persona |

### Datos sensibles y biométricos

La app no los recoge. Si más adelante se necesita verificar identidad (foto de documento, selfi, antecedentes):

- Requiere autorización específica: el titular debe saber que es un dato sensible y que no está obligado a darlo (Ley 1581, art. 6; Decreto 1377, art. 6).
- Requiere Firebase Storage, que exige el plan Blaze. Hoy `storage.rules` lo niega todo.
- Diseño de acceso previsto: ruta `kyc/{uid}/{archivo}`, escritura solo del titular (imagen, máximo 5 MB), lectura solo de un verificador con permiso especial, nunca de otros usuarios. Borrado a los 30 días de aprobada o rechazada la verificación; se conserva solo el resultado.
- Los antecedentes judiciales son especialmente delicados. Consultar con el abogado antes de pedirlos.

## 2. Plazos de conservación

| Dato | Plazo | Cómo se cumple hoy |
|---|---|---|
| Perfil | Mientras exista la cuenta | Se borra al eliminar la cuenta (automático) |
| Coordenadas del S.O.S. | Hasta que termina o se cancela | Automático; las reglas lo exigen |
| Rutas guardadas, negocio, motos, identificación del oferente | Hasta eliminar la cuenta | Automático |
| Pedidos, rentas, S.O.S. | 5 años | **Manual** |
| Chats | 1 año | **Manual** |
| Aceptaciones y solicitudes de privacidad | 10 años tras el cierre | **Manual** |

**Pendiente:** el borrado por vencimiento de plazo no está automatizado, porque requiere un proceso de servidor. Hasta que exista, el administrador debe hacerlo a mano una vez al año desde la consola de Firebase o con un script y una cuenta de servicio. Sin esto, los plazos de la política no se cumplen.

Los plazos de 5 y 10 años son una propuesta (prescripción de acciones de consumo y conservación de documentos del comerciante, Ley 962 de 2005, art. 28). Debe confirmarlos el abogado.

## 3. Procedimiento interno para atender a los titulares

### Canales

- **En la app:** Perfil → Legal y privacidad → Enviar una solicitud. Crea un documento en `privacy_requests` con hora del servidor y número de radicado (los primeros 8 caracteres del ID).
- **Correo:** el que se defina en la política. Cada correo recibido se debe radicar a mano en `privacy_requests`.

### Lo que el usuario resuelve solo

| Derecho | Dónde |
|---|---|
| Conocer y acceder | "Ver y descargar mis datos": genera un archivo con todo lo suyo |
| Rectificar | Cambia su nombre ahí mismo; los datos del negocio, en su pantalla |
| Prueba de la autorización | "Lo que has aceptado": documento, versión y fecha |
| Suprimir | "Eliminar mi cuenta" |
| Retirar el permiso de ubicación | Ajustes de Android |

### Pasos del responsable

1. **Revisar a diario** las solicitudes nuevas: consola de Firebase → Firestore → `privacy_requests`, estado `recibida`.
2. **Verificar identidad.** Si llegó por la app, la persona ya está autenticada. Si llegó por correo, confirmar que el correo coincide con el de la cuenta.
3. **Clasificar y contar el plazo** desde `createdAt`:
   - Consulta (`consulta`, `prueba`): 10 días hábiles, prorrogables 5 más avisando el motivo (art. 14).
   - Reclamo (`rectificacion`, `supresion`, `revocatoria`, `queja`): 15 días hábiles desde el día siguiente, prorrogables 8 más (art. 15). Si está incompleto, pedir que lo complete dentro de los 5 días siguientes. Mientras se tramita, marcar `status: en_tramite` dentro de los 2 días hábiles siguientes.
   - `oferente`: verificar que quien pide tuvo un pedido, renta o salida con ese aliado, y entregarle nombre, documento, dirección y teléfono del registro `providers`.
4. **Resolver.**
   - Supresión o revocatoria: procede salvo deber legal o contractual de conservar (Decreto 1377, art. 9). Borrar lo que se pueda y explicar qué se conserva y por qué.
   - Si se niega, motivar la respuesta.
5. **Responder** escribiendo en el documento `response`, `respondedAt` y `status: respondida`. El usuario lo ve en la app. Las reglas solo le permiten esto a un administrador.
6. **Conservar** la solicitud y la respuesta. No se pueden borrar desde la app.

Para ser administrador: crear a mano el documento `admins/{uid}` en la consola de Firebase.

### Incidentes de seguridad

Si hay acceso no autorizado, pérdida o filtración: contenerlo, documentarlo, avisar a los afectados y reportarlo a la SIC (Ley 1581, art. 17 lit. n). La SIC pide el reporte dentro de los 15 días hábiles siguientes a detectarlo; confirmar el plazo y el canal vigentes con el abogado.

## 4. Registro Nacional de Bases de Datos (RNBD)

- **Quién debe registrar** (Decreto 090 de 2018): sociedades y entidades sin ánimo de lucro con **activos totales superiores a 100.000 UVT**, y personas jurídicas de naturaleza pública.
- **Una persona natural o una empresa pequeña no está obligada a registrar**, pero sí debe cumplir todo lo demás de la Ley 1581: autorización, política, canales, seguridad y atención de reclamos. La SIC puede sancionar sin importar el tamaño.
- **Si se supera el umbral:** registrar cada base de datos en rnbd.sic.gov.co dentro de los 2 meses siguientes a su creación, actualizar entre el 2 de enero y el 31 de marzo de cada año, y reportar los cambios sustanciales dentro de los 10 primeros días hábiles del mes siguiente.
- Bases que tendría ApexMoto: usuarios, aliados (oferentes), y servicios (pedidos, rentas, S.O.S., chats).

El valor de la UVT cambia cada año. Confirmar con un contador si se supera el umbral y con el abogado los plazos vigentes.
