# Accesibilidad

Qué hace ApexMoto para que la usen personas con discapacidad, por qué y cómo probarlo. Vale para la app Android y para el prototipo HTML de la presentación (`tools/presentacion/prototipo.html`, publicado en https://apexmotoxz.web.app/demo): los dos tienen la misma pantalla de Accesibilidad.

## Enfoque

- **Principal: discapacidad visual (baja visión y daltonismo).** Para manejar moto hay que ver, así que el rider no es una persona ciega, pero sí puede ser daltónico (1 de cada 12 hombres) o necesitar letra grande. Los otros roles (comercio, dueño de moto, taller) sí pueden tener baja visión o usar lector de pantalla. La Ley 1680 de 2013 trata justamente del acceso de las personas ciegas y con baja visión a la tecnología.
- **Complemento: discapacidad auditiva.** La app dependía de la voz en tres puntos (guiado, intercom de la rodada y SOS). Ahora cada aviso llega también por pantalla y vibración.
- **Útil para todos:** el rider mira el celular en el manubrio, al sol, con guantes, casco y viento. Lo que ayuda a la baja visión y a la sordera le sirve a cualquiera en esas condiciones.

Referencia de nivel: WCAG 2.2 AA. La Resolución MinTIC 1519 de 2020 lo exige a entidades públicas; ApexMoto no lo es, así que se usa como referencia, no como obligación. Marco general: Ley 1618 de 2013.

## Pantalla de Accesibilidad

App: Mi perfil → **Accesibilidad**, Configuración, o el botón de accesibilidad en la pantalla de ingreso (antes de tener cuenta). Código: `ui/Accessibility.kt`; estado en `ui/theme/A11y.kt` (se guarda en el teléfono, SharedPreferences `apex`, claves `a11y_*`). Todo se aplica al instante en toda la app.

| Ajuste | Qué hace | Para quién |
|---|---|---|
| Tamaño de letra: Normal / Grande / Muy grande | Multiplica la letra del celular por 1 / 1,3 / 1,6, con tope de 2x. Escala no lineal (como Android 14): el texto casi se duplica, los títulos crecen poco | Baja visión |
| Alto contraste (modo sol) | Colores hasta 7:1, fondo blanco, tarjetas y campos con borde, texto en peso medio | Baja visión, sol directo |
| Botones grandes | Lo que se toca pasa de 48 a 56 dp; en el guiado y las señales, de 56 a 64 | Guantes, motricidad fina |
| Vibrar en giros y alertas | Dos toques antes de cada giro, uno largo al llegar, tres con las señales de la rodada | Personas sordas, casco |
| Guía por voz | La misma opción de Configuración. Si se apaga, el guiado avisa con texto y vibración | Quien no oye bien |
| Reducir animaciones | Quita movimientos decorativos (se suma a "Quitar animaciones" del celular) | Mareo, distracción |

Además, siempre (sin ajuste): estados con ícono y palabra, botones con nombre para TalkBack, títulos marcados, mapas descritos con palabras.

## Lo que cambió, con el criterio WCAG

| Problema (medido antes) | Corrección | Criterio |
|---|---|---|
| Letra blanca sobre el naranja `#D94600`: 4,35:1 (botón principal, chip elegido) | Naranja `#C84100`: 4,99:1; 4,54 sobre el fondo gris | 1.4.3 Contraste |
| Verde `#1B8A3A` con blanco 4,43:1; etiquetas verdes 3,5:1 y rojas 3,7:1 | Verde `#177431` (5,87), rojo `#BA2C2C` (6,03), naranja oscuro `#BA3E0B` (4,54 sobre su fondo suave). Todas las etiquetas ≥ 4,5 | 1.4.3 |
| Con daltonismo, verde/rojo y naranja/ámbar se confunden (simulación Machado 2009: ΔE 11 y 3,8) | Estados con ícono y palabra: ✓ Listo, ✕ Cancelado, reloj Pendiente; chip elegido con ✓; interruptor activado con ✓ | 1.4.1 Uso del color |
| La app frenaba la letra del celular en 1,15x | Hasta 2x, más el ajuste propio de la app | 1.4.4 Cambio de tamaño |
| Etiquetas de 9 a 11 sp | 12 sp como mínimo | Legibilidad |
| Botones de 36–44 dp (Perfil, Asistencia, SOS, Volver, Enviar, Salir de la llamada, chips de 40) | 48 dp (56 con Botones grandes) | 2.5.8 y guía de Android |
| Interruptores, chips y opciones sin estado para TalkBack | Interruptor = una sola fila "activado/desactivado"; chips y opciones dicen "seleccionado" | 4.1.2 Nombre, función, valor |
| Títulos sin marcar | `heading()` en títulos de sección, encabezado y pasos del ingreso | 1.3.1 y 2.4.6 |
| Errores de formulario solo en rojo | Ícono + texto que dice cómo corregir, anunciado por TalkBack | 3.3.1 Identificación de errores |
| Mapas sin descripción | El mapa dice qué muestra ("Mapa con 3 puntos: …. Con la ruta dibujada.") | 1.1.1 Contenido no textual |
| Guiado solo con voz | Texto grande (24 sp) que TalkBack lee solo + vibración en cada giro; aviso "Voz apagada" | 1.3.3 y multimodalidad |
| Intercom de la rodada solo de voz | **Señales rápidas** (Paremos, Gasolina, Me quedé, Peligro, Todo bien): aviso grande + vibración a todo el grupo | Discapacidad auditiva |

Paleta completa (normal y alto contraste) en `ui/theme/Theme.kt`, con el contraste de cada color en comentarios. En el prototipo, las mismas variables CSS (`:root` y `.phone.hc`).

### Señales rápidas (rodada)

Van en el documento de la posición en vivo (`rodadas/{id}/live/{uid}`, campos `signal` y `signalAt`), así que solo se mandan compartiendo la ubicación. Las reglas aceptan solo las cinco claves (`stop`, `fuel`, `lost`, `danger`, `ok`) y nunca una hora del futuro; prueba en `firestore-tests/rules.test.js`. `Repo.shareLive` usa `merge` para no borrar la señal al actualizar la posición. Quien la recibe ve el aviso 60 s (o hasta "Entendido"), con vibración de tres toques; una emergencia nueva de un compañero también vibra.

## Prototipo HTML

Mismas opciones, mismos textos. Además, por ser una página web:
- Letra con `calc(px * var(--fs))` (títulos con `--fsh`, que crece menos); íconos de Material Symbols con `aria-hidden` (si no, el lector diría "location_on").
- Lo que se toca y era un `<div>` ahora tiene `role="button"`, `tabindex="0"` y responde a Enter/Espacio; foco visible; la hoja inferior toma el foco y deja inerte lo de atrás.
- `lang="es-CO"`, títulos con `role="heading"`, estados con `aria-pressed`/`aria-checked`, `prefers-reduced-motion`.
- Guiado con "Siguiente indicación" (voz con `speechSynthesis` y vibración con `navigator.vibrate`; el iPhone no deja vibrar desde el navegador).
- Ajustes guardados en `localStorage` del navegador.

## Cómo probarlo

**App**
1. Mi perfil → Accesibilidad: cambia cada opción y mira la vista previa y el resto de la app.
2. Letra del sistema: Ajustes → Pantalla → Tamaño de fuente al máximo (o `adb shell settings put system font_scale 2.0`; volver con `1.0`).
3. TalkBack: Ajustes → Accesibilidad → TalkBack. Recorre Inicio, Mi perfil y Accesibilidad: cada botón dice su nombre, los interruptores dicen "activado/desactivado" y los títulos se pueden saltar.
4. Daltonismo: Opciones de desarrollador → Simular espacio de color → Deuteranomalía. Los estados se distinguen por el ícono.
5. Señales: dos teléfonos en una rodada en curso con la ubicación compartida; uno toca "Paremos" y al otro le sale el aviso y vibra.
6. Accessibility Scanner (app de Google) sobre las pantallas principales.

**Prototipo**
- WAVE: https://wave.webaim.org con la dirección https://apexmotoxz.web.app/demo.
- Lighthouse: Chrome → DevTools → Lighthouse → Accesibilidad, sobre la misma dirección.

## Pendiente

- Probar con personas reales: un amigo motero daltónico y alguien con baja visión (validación de las personas hipotéticas).
- Pasar la app completa con TalkBack y Accessibility Scanner (aquí se revisaron las pantallas principales).
- Lugar del SOS para personas sordas: hoy el principal es una llamada al 123 (ya hay mensaje con ubicación a los contactos).
