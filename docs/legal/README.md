# Cumplimiento legal de ApexMoto

Guía interna. No es asesoría jurídica: los textos y criterios deben pasar por un abogado antes de publicar (ver [revision-abogado.md](revision-abogado.md)).

| Documento | Para qué |
|---|---|
| Este archivo | Mapa de normas, registro de aceptación y cómo versionar los documentos |
| [operacion-datos.md](operacion-datos.md) | Inventario de datos por rol, plazos de conservación, procedimiento para atender a los titulares, RNBD |
| [google-play.md](google-play.md) | Formulario de seguridad de los datos, permisos, clasificación de contenido |
| [revision-abogado.md](revision-abogado.md) | Lo que debe revisar un abogado, riesgos del rol guía y de la renta, licencias de terceros |
| [../seguridad.md](../seguridad.md) | Reglas de Firestore y Storage, pruebas, claves, antifraude |

Los textos que ve el usuario están en `app/src/main/assets/legal/`. Son la única fuente: la app los muestra y para publicarlos en la web se copian tal cual.

## 1. Supuestos

Estos puntos no estaban claros en el proyecto y se asumieron. Si alguno no es cierto, hay que ajustar los documentos.

1. **El responsable aún no está definido.** Los documentos tienen marcadores `[COMPLETAR: …]` para razón social o nombre, NIT o cédula, dirección, correo y teléfono. La compilación de publicación falla mientras existan.
2. **La app no procesa pagos.** Todo se paga directamente al aliado. Si se agregan pagos, cambian los términos, la política de reembolsos (reversión del pago, art. 51 Ley 1480) y el formulario de Google Play.
3. **Solo planes gratuitos de Firebase.** Por eso no hay Cloud Functions, Storage, Messaging, Analytics ni Crashlytics, aunque el encargo los mencionaba. Las consecuencias están en el punto 4.
4. **No se recogen documentos ni biometría.** La app no pide foto de cédula, selfi, antecedentes ni foto de la licencia. Se usan declaraciones.
5. **El "conductor" es un guía que va en su propia moto.** No lleva pasajeros. Si el negocio quiere otra cosa, ver los riesgos en [revision-abogado.md](revision-abogado.md).

## 2. Mapa de normas

| Tema | Norma | Cómo se cumple en la app |
|---|---|---|
| Autorización previa, expresa e informada | Ley 1581 de 2012, arts. 3, 9 y 12; Decreto 1377 de 2013, arts. 5 a 7 | Casilla propia para la Política de Privacidad, sin marcar, con enlace al texto; el perfil no se crea sin ella |
| Prueba de la autorización | Decreto 1377 de 2013, art. 8; Ley 1581, art. 17 lit. b | Colección `legal_acceptances`, inmutable, con hora del servidor |
| Política de tratamiento | Decreto 1377 de 2013, art. 13 | `privacidad.md` |
| Datos sensibles y de menores | Ley 1581, arts. 5 a 7 | No se recogen; solo mayores de 18 |
| Derechos y procedimientos | Ley 1581, arts. 8, 14, 15 y 16 | Perfil → Legal y privacidad; plazos de 10 y 15 días hábiles calculados con festivos |
| Transmisión internacional | Ley 1581, art. 26; Decreto 1377, arts. 24 y 25 | Declarada en la política y autorizada con la casilla |
| Registro de bases de datos | Decreto 090 de 2018 | Ver [operacion-datos.md](operacion-datos.md) |
| Responsabilidad demostrada | Decreto 1377 de 2013, arts. 26 y 27 | Esta carpeta, las reglas y sus pruebas |
| Información al consumidor y comercio electrónico | Ley 1480 de 2011, arts. 23, 24 y 50 | Resumen y total antes de confirmar; identidad del responsable en los términos |
| Portal de contacto | Ley 1480 de 2011, art. 53 | Colección `providers` con la identificación de cada oferente |
| Retracto | Ley 1480 de 2011, art. 47 | Botón "Cancelar" en rentas y salidas; se registra como retracto si aplica |
| Reversión del pago | Ley 1480 de 2011, art. 51; Decreto 587 de 2016 | No aplica todavía (sin pagos); previsto en `reembolsos.md` |
| Publicidad engañosa | Ley 1480 de 2011, arts. 29 y 30 | Los planes de "protección" se aclaran como "no son un seguro"; datos demo fuera de producción |
| Validez del consentimiento electrónico | Ley 527 de 1999, arts. 5, 10 y 14 | Aceptación por mensaje de datos, con registro |
| Licencia, SOAT y revisión técnico-mecánica | Ley 769 de 2002, arts. 17 a 19, 42 y 50 a 52; Resolución 1500 de 2005 | Declaraciones del rider, del dueño y del guía; fechas de SOAT y revisión obligatorias para publicar una moto |
| Transporte público | Ley 105 de 1993; Ley 336 de 1996, arts. 5, 9 y 16 | La app no ofrece transporte; prohibido en términos y en la declaración del guía |
| Ubicación y permisos | Política de datos de usuario de Google Play | Aviso previo al permiso; solo primer plano |

## 3. Registro de aceptación trazable

Colección `legal_acceptances`, un documento por persona, documento y versión. ID: `{uid}_{docId}_{version}`.

| Campo | Contenido |
|---|---|
| `userId` | Identificador de la cuenta |
| `docId`, `version` | Qué documento y qué versión |
| `docHash` | SHA-256 del texto exacto que se mostró |
| `acceptedAt` | Fecha y hora **del servidor** (las reglas rechazan la hora del teléfono) |
| `method` | `casilla_registro`, `casilla_primer_ingreso`, `casilla_reaceptacion`, `casilla_cambio_de_rol` o `boton_aviso_ubicacion` |
| `platform`, `appVersion`, `os`, `locale` | Android, versión de la app, versión del sistema, idioma |
| `deviceId` | Identificador de instalación de Firebase |
| `role` | Rol en ese momento |

Garantías que imponen las reglas (y demuestran las pruebas):

- El perfil de usuario **no se puede crear** si en el mismo lote no quedan las aceptaciones de términos y de privacidad, cada una por separado.
- Un registro **no se puede modificar ni borrar** desde la app, ni crear a nombre de otra persona.
- Solo se aceptan versiones vigentes.
- Si la persona elimina su cuenta, los registros se conservan.

**Limitaciones que debes conocer:**

- **No se guarda la dirección IP.** La IP solo la puede ver un servidor, y la app no tiene uno (Cloud Functions exige plan de pago). En su lugar se guarda el identificador de instalación. Si se pasa al plan Blaze, una función puede añadir la IP y escribir el registro desde el servidor, lo que lo hace más fuerte como prueba.
- **El registro lo escribe el propio teléfono**, aunque validado por el servidor. Un abogado debe decir si eso basta ante la SIC o si se necesita el registro del lado del servidor.
- Con ingreso por Google o celular, la cuenta de acceso existe en Firebase unos segundos antes de la aceptación. El perfil y cualquier otro dato solo se crean después.

## 4. Cómo cambiar un documento legal

1. Copia el texto vigente a `docs/legal/historial/{docId}-{version}.md`. Nunca se borra una versión que alguien aceptó.
2. Edita el texto en `app/src/main/assets/legal/` y cambia la versión y la fecha en su encabezado.
3. Sube la versión en `Legal.DOCS` (`app/src/main/java/co/apexmoto/app/legal/Legal.kt`).
4. Agrega la versión nueva a `versionOk()` en `firestore.rules` y publica las reglas **antes** de publicar la app. Mantén la versión anterior en la lista mientras la app vieja siga en uso.
5. Corre `npm test` en `firestore-tests`.
6. Publica la app. Al abrirla, cada usuario ve la pantalla de reaceptación con las casillas y no puede continuar sin aceptar. Puede cerrar sesión o eliminar su cuenta.

Regla práctica para el número de versión: cambio de fondo (finalidades, responsable, obligaciones) sube el primero o el segundo número y exige reaceptación. Una corrección de redacción también cambia el hash, así que también debe subir la versión.

## 5. Qué existe y qué no

| Mencionado en el encargo | Estado real |
|---|---|
| Hilt, MVVM por módulo | No. Un `AppViewModel` y un contenedor manual. No se cambió |
| Cloud Functions, Messaging, Storage | No están en el proyecto. No se agregaron (plan gratuito) |
| Analytics, Crashlytics | No están. La política lo dice y explica cómo se pediría permiso |
| Foto de documento, selfi, antecedentes | No se recogen |
| Rol Taller mecánico | Existe en la app aunque el encargo no lo nombraba. Quedó cubierto |
