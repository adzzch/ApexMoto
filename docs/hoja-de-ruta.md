# Hoja de ruta

Estado al 2026-11-15. Es una lista de trabajo, no un compromiso de fechas. Las tareas legales y de seguridad detalladas están en [legal/revision-abogado.md](legal/revision-abogado.md) y [seguridad.md](seguridad.md).

## Antes de probar con usuarios reales

- [ ] Probar en dispositivo: registro con casillas (correo, Google, teléfono), reaceptación con una cuenta antigua, aviso de ubicación, renta con declaración de licencia, cancelación y retracto, S.O.S., chat, Legal y privacidad, eliminar cuenta.
- [ ] Publicar `firestore.rules` y crear `admins/{uid}`.
- [ ] Definir al responsable y completar los `[COMPLETAR: …]` de `app/src/main/assets/legal/`.
- [ ] Decidir qué hacer con los planes de "protección" de la renta (`core/Constants.kt`): no son un seguro.
- [ ] Revisión por un abogado de los textos y de `docs/legal/revision-abogado.md`.
- [ ] Publicar la política de privacidad y la página de eliminación de cuenta en una URL pública.

## Antes de abrir al público

- [ ] Verificación de talleres antes de que vean S.O.S.
- [ ] Reportar y bloquear usuarios; verificación de correo para aliados.
- [ ] Borrado por vencimiento de plazos de conservación.
- [ ] App Check activado y exigido; alertas de consumo en Firebase.
- [ ] Firma de la app, R8 y ofuscación; subir `targetSdk`.
- [ ] Pruebas unitarias y de interfaz de la app (hoy solo existen las de reglas).

## Producto pendiente

- Pagos reales (cambia términos, reembolsos y formulario de Google Play; exige backend para validar totales).
- Notificaciones push (FCM).
- Fotos de comercios y motos, reseñas, lista de pilotos de Comunidad.
- Coordenadas reales de corredores y comercios; perfil `driving-motorcycle` de OpenRouteService.

## Calidad de interfaz

- Modo oscuro (hoy solo tema claro).
- Accesibilidad: áreas táctiles de 48 dp, `contentDescription`.
- Mover textos fijos a `strings.xml`.
