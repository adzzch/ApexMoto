# Guía de contribución

ApexMoto es un proyecto personal con licencia propietaria ([LICENSE](LICENSE)). Se aceptan sugerencias y correcciones; al enviar una contribución aceptas la cesión de licencia del punto 5 de esa licencia. Para temas de seguridad usa [SECURITY.md](SECURITY.md), no un issue. Todo el trabajo sigue el [código de conducta](CODE_OF_CONDUCT.md).

## Antes de empezar

1. Lee [PRODUCT.md](PRODUCT.md): principios y compromisos (solo servicios gratuitos sin tarjeta, español de Colombia, precios en COP, honestidad sobre lo que no existe).
2. Configura el entorno con [docs/desarrollo.md](docs/desarrollo.md) y entiende la estructura en [docs/arquitectura.md](docs/arquitectura.md).
3. Abre un issue antes de un cambio grande para alinear el alcance.

## Flujo de trabajo

- Rama principal: `master`. Trabaja en ramas `tipo/descripcion-corta` (`feat/`, `fix/`, `docs/`, `refactor/`, `chore/`, `legal/`) y abre un pull request.
- Commits en español, en imperativo, con prefijo de [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/): `feat: agrega filtro por corredor`, `fix: corrige recálculo de ruta`, `docs: …`. Un cambio lógico por commit.
- Un PR pequeño y enfocado; completa la plantilla de PR.

## Reglas de código

- Kotlin y Compose siguen el estilo existente (`.editorconfig`). Nada de colores ni fuentes propios en pantallas: usa `ui/theme/Theme.kt`.
- Todo acceso a datos pasa por `data/Repo.kt`. Si una escritura nueva necesita permiso, cambia `firestore.rules` **y** agrega pruebas en `firestore-tests/rules.test.js`; `npm test` debe pasar.
- Textos para el usuario en español de Colombia; precios con `cop()` (`$280.000`).
- Ningún servicio nuevo sin plan gratuito sin tarjeta.
- Dependencia nueva: actualiza [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) y verifica que su licencia sea compatible con una app propietaria (no GPL/AGPL).
- **Textos legales** (`app/src/main/assets/legal/`): siguen el procedimiento de [docs/legal/README.md](docs/legal/README.md) §4. Cambian versión, reglas y reaceptación; no los edites sin ese proceso.
- **Secretos:** nunca subas `google-services.json`, `local.properties`, claves de firma ni tokens.
- No presentes como existente lo que no existe (pagos, reseñas, aliados reales). No inventes testimonios ni métricas.

## Lista antes de enviar el PR

- [ ] Compila en debug (`app`) y lo probé en emulador o celular.
- [ ] `cd firestore-tests && npm test` pasa (si toqué reglas o modelos).
- [ ] Actualicé la documentación afectada y [CHANGELOG.md](CHANGELOG.md) (sección *Sin publicar*).
- [ ] No hay secretos ni archivos locales en el diff.
