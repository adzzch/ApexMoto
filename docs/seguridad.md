# Seguridad técnica de ApexMoto

## 1. Modelo

La app habla directamente con Firestore; no hay servidor propio. Por eso **las reglas de Firestore son el servidor**: todo lo que la app valida en pantalla se vuelve a validar ahí, porque cualquiera puede hablar con Firestore sin usar la app.

| Capa | Qué hace | Dónde |
|---|---|---|
| Autenticación | Correo y contraseña, Google, celular | Firebase Authentication |
| Autorización y validación | Quién lee y escribe qué, formas, tamaños, estados | `firestore.rules` |
| Antiabuso | Sellos de tiempo por usuario; App Check opcional | `firestore.rules`, `AppCheckInstaller` |
| Cliente | Consultas acotadas, textos recortados, lotes atómicos | `data/Repo.kt` |

## 2. Reglas de Firestore por rol

| Colección | Leer | Escribir |
|---|---|---|
| `users` | Solo el propio perfil | Crear con aceptaciones; editar nombre; el rol no cambia (solo una cuenta del rol de guía, retirado, pasa a rider); borrar el propio |
| `legal_acceptances` | Las propias | Solo crear, a nombre propio, con hora del servidor. Nunca modificar ni borrar |
| `privacy_requests` | Las propias | Crear; solo un administrador responde; nadie borra |
| `providers` | El titular y el administrador | El titular, si es aliado |
| `venues` | Usuarios con sesión | Comercio o taller dueño (sin tocar su calificación); visitas de a una |
| `bikes` | Usuarios con sesión | Dueño; quien renta solo aparta o libera sus propias fechas junto con su solicitud |
| `drivers` | Nadie | Nadie. Eran los perfiles del rol de guía, retirado el 2026-11-15 |
| `orders` | Rider y comercio del pedido | Rider crea y cancela mientras esté "enviado"; comercio avanza paso a paso |
| `requests` | Las dos partes | Solo de renta. Rider crea y cancela antes de empezar; el dueño avanza paso a paso |
| `coupons` | Rider y comercio | Rider crea; comercio canjea una vez |
| `sos` | El rider; talleres mientras busca taller; luego solo el taller asignado | Rider crea y cancela; taller acepta a su nombre y avanza; coordenadas en cero al terminar |
| `chats` | Los dos participantes | Solo las dos partes reales del pedido, la solicitud o el S.O.S. |
| `rodadas` | Usuarios con sesión | Creador; los demás solo se unen o salen a sí mismos, sin pasar los cupos |
| `routes` | El dueño | El dueño |
| `profiles` | Usuarios con sesión | El rider dueño; el @usuario se reserva en `usernames` en el mismo lote |
| `usernames` | Usuarios con sesión, de a uno | Solo existe mientras el perfil de su dueño lo use; nadie toma uno ocupado |
| `friend_requests` | Quien la envía y quien la recibe | Con los datos reales de los dos perfiles, sin duplicar ni invertir, sin bloqueos y con sello cada 10 s |
| `friendships` | Los dos amigos | La crea quien recibió la solicitud, borrándola en el mismo lote; la borra cualquiera de los dos |
| `blocks` | Quien bloquea | Quien bloquea |
| `status` | Solo los amigos, de a uno | El dueño, con la hora del servidor y como máximo un latido cada 30 s |
| `rate_limits` | El dueño | Solo sellos con la hora del servidor y respetando el tiempo mínimo |
| `meta`, datos demo | Usuarios con sesión | Solo administrador |
| `admins` | El propio | Nadie desde la app (se crea en la consola) |
| Cualquier otra | Nadie | Nadie |

### Huecos que tenían las reglas anteriores

| Antes | Riesgo | Ahora |
|---|---|---|
| Cualquier usuario leía todos los perfiles | Fuga de correos y celulares | Cada quien solo el suyo |
| Cualquier usuario leía y escribía cualquier chat | Fuga de conversaciones, suplantación | Solo las dos partes |
| Cualquier usuario leía todos los S.O.S. con coordenadas y podía modificarlos | Ubicación de personas varadas expuesta | Acceso por rol y estado; coordenadas borradas al terminar |
| Cualquier usuario cambiaba su propio rol a cualquiera | Escalada de privilegios | El rol no cambia (única excepción: guía retirado → rider) |
| Cualquier usuario modificaba las fechas reservadas de cualquier moto | Bloquear motos ajenas | Solo con una solicitud propia en el mismo lote |
| Cualquier usuario modificaba calificación y reseñas de cualquier guía | Fraude de reputación | Los perfiles de guía ya no existen. Las reseñas reales (fases 3 y 5 de la hoja de ruta) deben tener la misma garantía: una vez, con el servicio terminado |
| Pedidos y solicitudes con el dueño que dijera el cliente | Desviar pedidos | Se comprueba contra el comercio o la moto |
| Estados cambiables libremente | Saltarse pasos, marcar pagado o finalizado | Transiciones validadas |
| `meta` y datos sin dueño editables por cualquiera | Borrar o falsear datos | Solo administrador |
| Sin límites de tamaño ni de frecuencia | Inundar la base, agotar la cuota gratuita | Tamaños máximos y sellos de tiempo |

## 3. Pruebas de las reglas

`firestore-tests/rules.test.js`: 50 pruebas contra el emulador. Cada una afirma una garantía concreta, tanto lo que debe funcionar como lo que debe rechazarse.

```bash
cd firestore-tests
npm install          # una vez
npm test             # necesita Java 21 o superior en el PATH
```

Necesitas JDK 21 o superior; el daemon de Gradle del proyecto también usa JDK 21 (ver [desarrollo.md](desarrollo.md)).

Publicar las reglas: son las mismas para los dos ambientes y se publican en los dos proyectos. Copiar `firestore.rules` en la consola de Firebase → Firestore → Reglas, o `npx firebase deploy --only firestore:rules --project <id>` desde `firestore-tests` (pide iniciar sesión; el proyecto de pruebas es `apexmotoxz`).

**Antes de publicarlas** hay que crear `admins/{tu uid}` en el proyecto de pruebas si quieres seguir cargando datos demo. Las cuentas que tenían el rol de guía pasan solas a rider la próxima vez que abran la app. Las cuentas que ya existían verán la pantalla de aceptación al abrir la app. Los chats antiguos dejan de verse (no tenían participantes) y las motos publicadas sin fechas de SOAT y revisión no se pueden rentar hasta que el dueño las complete.

## 4. Validación de datos

| Dato | En la app | En las reglas |
|---|---|---|
| Nombre | Máximo 60, no vacío | Igual |
| Textos libres (notas, mensajes, descripciones) | Recortados | Tamaño máximo por campo |
| Precios y totales | Solo dígitos | Entero, mayor o igual a cero, con tope |
| Fechas | Selector de fecha | Formato `AAAA-MM-DD`, desde ≤ hasta |
| Coordenadas | GPS o toque en el mapa | Rango válido |
| Campos permitidos | Modelos de datos | Lista cerrada en perfil, aceptaciones, solicitudes y oferente |
| Dueño del destino | Tomado del comercio o la moto | Comprobado contra el documento real |
| Estados | Botones por estado | Transiciones permitidas |

**Lo que las reglas no pueden validar** sin un servidor: que el total de un pedido sea la suma de sus productos a los precios del menú, y que dos reservas con fechas distintas no se crucen. Hoy no hay dinero dentro de la app, así que el efecto es limitado; antes de procesar pagos esto debe moverse a Cloud Functions.

## 5. Datos sensibles y cifrado

- La app no recoge documentos, selfis, biometría, antecedentes ni datos de pago.
- Todo viaja por TLS. Firestore cifra en reposo. Android no permite tráfico sin cifrar con este nivel de API.
- Las contraseñas las maneja Firebase Authentication.
- Copias de seguridad de Android desactivadas (`allowBackup="false"` y `data_extraction_rules.xml`): la sesión y la caché no salen del teléfono.
- La identificación del oferente es privada (titular y administrador).
- `storage.rules` niega todo. El diseño para una futura verificación con documentos está en `docs/legal/operacion-datos.md`.

## 6. Claves y secretos

| Secreto | Dónde está | Estado |
|---|---|---|
| Clave de OpenRouteService | `local.properties` → `BuildConfig.ORS_API_KEY` | Fuera del repositorio. Se revisó el historial de git: nunca se subió |
| `google-services.json` | `app/src/pruebas/` y `app/src/prod/`, ignorados por git | Fuera del repositorio. No es un secreto en sentido estricto: la seguridad la dan las reglas y App Check |
| Claves de firma | No existen todavía | Guardarlas fuera del repositorio |
| Datos del responsable (`legal.properties`) | Raíz del proyecto, ignorado por git | No son secretos (salen en los textos legales de la app), pero son datos personales: no se suben al repositorio |

**Limitación:** una clave dentro de `BuildConfig` se puede extraer del APK. La de OpenRouteService no se puede restringir por aplicación. Mitigaciones: usar una clave exclusiva para la app, vigilar el consumo en su panel y rotarla si se abusa. La solución completa es un intermediario propio (servidor), que hoy no cabe en el plan gratuito.

La pantalla de Configuración muestra el estado y el final de la clave solo en la versión de pruebas.

Recomendación: restringir la clave de API de Android de Firebase en Google Cloud Console (por nombre de paquete y huella SHA-1 de firma).

## 7. Limitación de abuso y antifraude

**Ya activo**

- Sellos de tiempo: un S.O.S. cada 60 s, un pedido cada 15 s y una solicitud cada 15 s por usuario. El sello va en el mismo lote que la acción y debe ser la hora del servidor.
- Un cupón se canjea una vez; un S.O.S. lo toma un solo taller.
- Sin autoasignación: el rider no acepta su propio S.O.S.
- Firebase Authentication limita por su cuenta los intentos de ingreso y los SMS.
- Contraseña con mínimo 8 caracteres, mayúscula, número y símbolo. Conviene exigirlo también en la consola (Authentication → Configuración → Política de contraseñas).

**App Check (listo en el código, apagado)**

Comprueba que cada petición viene de la app original, instalada desde Google Play, en un dispositivo íntegro. Es la defensa más efectiva contra scripts y apps modificadas, y es gratuito.

1. Consola de Firebase → App Check → registrar la app Android con Play Integrity (necesita la huella SHA-256 de firma).
2. En `local.properties`: `APP_CHECK_ENABLED=true`.
3. En desarrollo, copiar el token que aparece en Logcat y agregarlo en App Check → Administrar tokens de depuración.
4. Vigilar las métricas unos días y luego activar "Exigir" para Firestore y Authentication.

Está apagado por defecto porque sin el registro en la consola solo genera errores en el registro del sistema, y no se pudo probar en un dispositivo.

**Pendiente**

- Verificar a los talleres antes de que puedan ver S.O.S. (ver `docs/legal/revision-abogado.md`, A.4).
- Reportar y bloquear usuarios.
- Verificación de correo antes de operar como aliado.
- Firma, R8 y ofuscación para la versión de publicación.
- Alertas de consumo en la consola de Firebase.
