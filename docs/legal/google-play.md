# Publicación en Google Play

Respuestas para la consola de Play según lo que la app hace **hoy**. Si se agregan pagos, analítica, notificaciones o subida de archivos, hay que actualizarlas antes de publicar esa versión.

## 1. Requisitos previos

| Requisito | Estado |
|---|---|
| Política de privacidad en una URL pública | **Pendiente.** Publicar el texto completo de `privacidad.md`, el que deja la compilación de prod en `app/build/generated/assets/fillProdReleaseLegalTexts/legal/` con los datos del responsable ya puestos (por ejemplo en Firebase Hosting o GitHub Pages, ambos gratuitos) y poner la URL en la ficha y en el formulario |
| Eliminación de cuenta dentro de la app | Hecho: Perfil → Legal y privacidad → Eliminar mi cuenta |
| URL para pedir la eliminación sin abrir la app | **Pendiente.** Una página pública que explique cómo pedirla por correo y qué se borra y qué se conserva. Play la exige |
| Nivel de API de destino | **Pendiente.** El proyecto apunta a API 34. Play exige un nivel reciente para apps nuevas: revisar el requisito vigente y subir `targetSdk` (implica actualizar AGP y probar) |
| Firma y ofuscación de la versión de publicación | **Pendiente.** No hay configuración de firma ni R8 activado |
| Marcadores `[COMPLETAR: …]` | La compilación de publicación falla hasta completarlos |

## 2. Formulario "Seguridad de los datos"

**Preguntas generales**

- ¿La app recopila o comparte datos de usuario? **Sí.**
- ¿Los datos se cifran en tránsito? **Sí** (HTTPS/TLS con Firebase, OpenStreetMap y OpenRouteService).
- ¿Los usuarios pueden pedir que se eliminen sus datos? **Sí** (en la app y por la URL de eliminación).

**Tipos de datos**

"Compartido" en Play significa transferido a un tercero que no actúa como proveedor de servicios tuyo. Firebase actúa como proveedor, así que no cuenta como compartir.

| Tipo de dato | ¿Se recopila? | ¿Se comparte? | Opcional | Finalidad |
|---|---|---|---|---|
| Nombre | Sí | No | No | Funcionalidad de la app, administración de la cuenta |
| Correo electrónico | Sí (si ingresa con correo o Google) | No | No | Administración de la cuenta |
| Número de teléfono | Sí (si ingresa con celular; aliados) | No | Según método | Administración de la cuenta, cumplimiento legal |
| Dirección | Sí (solo aliados) | No | No para aliados | Cumplimiento legal (identificación del oferente) |
| ID de usuario | Sí | No | No | Funcionalidad, seguridad |
| Otra información personal (número de documento de aliados) | Sí (solo aliados) | No | No para aliados | Cumplimiento legal |
| Otra información personal (perfil de conductor: categoría y vencimiento de la licencia, año desde el que maneja; contacto de emergencia con nombre y celular) | Sí (solo riders que rentan) | No | Sí | Funcionalidad de la app (renta) |
| Ubicación precisa | Sí | **Sí**: los puntos de la ruta se envían a OpenRouteService; el punto del S.O.S. lo ven talleres (y el dueño, si es una moto rentada) | Sí | Funcionalidad de la app |
| Ubicación aproximada | Sí | Igual que la precisa | Sí | Funcionalidad de la app |
| Mensajes dentro de la app (chat) | Sí | No | Sí | Funcionalidad de la app |
| Historial de compras (pedidos, rentas, salidas) | Sí | No | No | Funcionalidad de la app |
| Otro contenido generado por el usuario (negocio, menú, moto, rodadas) | Sí | No | Sí | Funcionalidad de la app |
| IDs de dispositivo u otros (identificador de instalación de Firebase) | Sí | No | No | Seguridad, cumplimiento legal |
| Información financiera, salud, fotos, audio, contactos, calendario, archivos, actividad en otras apps, diagnósticos | **No** | No | | |

Ningún dato se usa para publicidad ni para analítica.

**Puntos a confirmar:** si enviar las coordenadas a OpenRouteService cuenta como "compartir" o como "proveedor de servicios" depende de sus términos. Se marcó como compartido por prudencia. Los servidores de mapas reciben la IP y la zona consultada, no una identidad.

## 3. Permisos

| Permiso | Uso | Declaración |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Conexión | No requieren declaración |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Guiado giro a giro, punto del S.O.S., mapa de comercios, ubicación del negocio | Solo en primer plano. No se pide ubicación en segundo plano, así que no hay formulario especial de permisos |

Lo que ya cumple la app:

- El permiso se pide **en contexto**: al tocar "Usar mi ubicación" o al abrir el guiado, nunca al iniciar la app.
- Antes del cuadro de Android aparece un **aviso propio** que explica para qué se usa, con "Aceptar y continuar" y "Ahora no". La aceptación queda registrada.
- Si la persona dice que no, la app sigue funcionando: la ruta se calcula desde el centro de Cali y el S.O.S. se envía con la descripción escrita.

La app no usa cámara, micrófono, contactos, SMS, almacenamiento ni notificaciones.

## 4. Clasificación de contenido (cuestionario IARC)

| Pregunta | Respuesta |
|---|---|
| Categoría | Utilidad / mapas y navegación (no es un juego) |
| Violencia, sexo, lenguaje soez, drogas, apuestas | No |
| ¿Los usuarios pueden interactuar o intercambiar contenido? | **Sí** (chat, rodadas, perfiles de negocio) |
| ¿Comparte la ubicación del usuario con otros usuarios? | **Sí** (el punto del S.O.S. con los talleres) |
| ¿Permite compras de bienes digitales? | No |
| ¿Contenido generado por usuarios sin moderación? | Sí. Play exige un mecanismo para reportar y bloquear: **pendiente** (hoy solo existe "Enviar una solicitud → Queja") |

## 5. Otras declaraciones de la consola

| Sección | Respuesta |
|---|---|
| Público objetivo | Mayores de 18 años |
| Anuncios | No contiene |
| Acceso a la app | Requiere cuenta: entregar a Google un usuario de prueba de cada rol |
| Apps de noticias, salud, finanzas, gobierno | No |
| ID de publicidad | No se usa. Declarar que no, y verificar que el manifiesto final no incluya `com.google.android.gms.permission.AD_ID` |

## 6. Ficha de la tienda

- No usar nombres ni logos de restaurantes, marcas de motos o sitios reales como si fueran aliados. Los datos demo incluyen restaurantes reales de Cali con menús inventados: **no deben aparecer en capturas ni en producción**.
- No prometer "seguro", "cobertura" ni "transporte".
- No mostrar pagos, reseñas ni aliados que aún no existen.
