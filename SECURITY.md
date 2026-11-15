# Política de seguridad

## Reportar una vulnerabilidad

**No abras un issue público** para temas de seguridad.

1. Usa el reporte privado de GitHub: pestaña **Security → Report a vulnerability** de <https://github.com/adzzch/ApexMoto/security/advisories/new>.
2. Si no está disponible, contacta al titular por el perfil <https://github.com/adzzch> pidiendo un canal privado, sin incluir los detalles técnicos en el primer mensaje.

Incluye: qué encontraste, cómo reproducirlo, el impacto y la versión (`versionName` en `app/build.gradle.kts`). No accedas a datos de otras personas ni degrades el servicio para demostrarlo.

Tiempos de referencia (proyecto personal, sin garantía): acuse en 7 días, evaluación en 30. Se agradece la divulgación coordinada: no publiques detalles hasta que haya corrección.

## Alcance

| En alcance | Fuera de alcance |
|---|---|
| Reglas de Firestore y `storage.rules` | Servicios de terceros (Firebase, OpenRouteService, OpenStreetMap): repórtalos a sus dueños |
| Código de la app en `app/` | Ingeniería social, ataques físicos, denegación de servicio |
| Fuga de datos personales o de ubicación | Hallazgos que requieran un dispositivo con root sin otra falla |
| Escalada de privilegios entre roles | Claves que ya se sabe que son extraíbles del APK (ver abajo) |

## Datos que se manejan

Cuenta, nombre, correo o celular, rol, ubicación (solo con aviso aceptado) y registros de aceptación legal. No se recogen documentos, biometría ni datos de pago. Detalle: [docs/legal/operacion-datos.md](docs/legal/operacion-datos.md).

## Limitaciones conocidas

Están documentadas en [docs/seguridad.md](docs/seguridad.md), entre ellas: la clave de OpenRouteService dentro del APK es extraíble, y no hay servidor que valide totales de pedidos. No son vulnerabilidades nuevas; sí interesan mitigaciones.

## Secretos

Nunca subas `google-services.json`, `local.properties`, claves de firma ni tokens. Si crees que se filtró uno, rótalo de inmediato y avisa por el canal privado.
