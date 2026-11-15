# Documentación de ApexMoto

| Quiero… | Lee |
|---|---|
| Entender qué es el producto y para quién | [../PRODUCT.md](../PRODUCT.md) |
| Configurar y ejecutar el proyecto, correr pruebas, publicar | [desarrollo.md](desarrollo.md) |
| Entender cómo está organizado el código | [arquitectura.md](arquitectura.md) |
| Revisar seguridad, reglas de Firestore, claves y antifraude | [seguridad.md](seguridad.md) |
| Saber qué normas se cumplen y cómo versionar textos legales | [legal/README.md](legal/README.md) |
| Atender a titulares de datos, plazos y RNBD | [legal/operacion-datos.md](legal/operacion-datos.md) |
| Publicar en Google Play | [legal/google-play.md](legal/google-play.md) |
| Preparar la revisión con un abogado | [legal/revision-abogado.md](legal/revision-abogado.md) |
| Ver qué falta por hacer y en qué fase vamos | [hoja-de-ruta.md](hoja-de-ruta.md) |
| Probar la app con personas reales, rol por rol | [plan-de-pruebas.md](plan-de-pruebas.md) |
| Ver el historial de cambios | [../CHANGELOG.md](../CHANGELOG.md) |
| Contribuir o reportar una vulnerabilidad | [../CONTRIBUTING.md](../CONTRIBUTING.md), [../SECURITY.md](../SECURITY.md) |
| Licencias de terceros | [../THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) |

## Reglas de esta carpeta

- Escribir en español de Colombia; precios en COP con el formato `$280.000`.
- Una fuente de verdad por tema: enlazar en lugar de copiar. Los textos legales que ve el usuario viven **solo** en `app/src/main/legal/` (al compilar se completan con `legal.properties`).
- Si cambias código, reglas o dependencias, actualiza el documento correspondiente en el mismo cambio.
- Nunca escribir secretos (`google-services.json`, `ORS_API_KEY`, claves de firma, tokens).
- Los documentos de `legal/` son guías internas, no asesoría jurídica.
