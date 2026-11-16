"""
Arma la demo y la presentación de ApexMoto para Firebase Hosting.

Fuentes (se editan aquí):  tools/presentacion/prototipo.html y comparacion.html
Resultado (se publica):    hosting/public/demo/        → https://apexmotoxz.web.app/demo  (prototipo; se instala como app)
                           hosting/public/presentacion/ → https://apexmotoxz.web.app/presentacion (comparación con el QR)
                           hosting/public/assets/fonts/ (letras locales: la política de seguridad del Hosting bloquea Google Fonts)

Uso:  python tools/presentacion/build.py
Luego: firebase deploy --only hosting --project apexmotoxz
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'tools', 'presentacion')
PUB = os.path.join(ROOT, 'hosting', 'public')
FONTS = os.path.join(PUB, 'assets', 'fonts')
VERSION = 'v1'   # súbela cuando cambien las páginas: así los celulares descartan la copia guardada sin internet

FONTS_CSS = """/* Letras de ApexMoto servidas desde el mismo sitio (Chivo, Space Grotesk y JetBrains Mono: SIL OFL; Material Symbols: Apache 2.0). */
@font-face { font-family: Chivo; font-weight: 700; font-display: swap; src: url(/assets/fonts/chivo_bold.ttf) format("truetype"); }
@font-face { font-family: Chivo; font-weight: 800; font-display: swap; src: url(/assets/fonts/chivo_extrabold.ttf) format("truetype"); }
@font-face { font-family: Chivo; font-weight: 900; font-display: swap; src: url(/assets/fonts/chivo_black.ttf) format("truetype"); }
@font-face { font-family: "Space Grotesk"; font-weight: 400; font-display: swap; src: url(/assets/fonts/space_grotesk_regular.ttf) format("truetype"); }
@font-face { font-family: "Space Grotesk"; font-weight: 500; font-display: swap; src: url(/assets/fonts/space_grotesk_medium.ttf) format("truetype"); }
@font-face { font-family: "Space Grotesk"; font-weight: 600 700; font-display: swap; src: url(/assets/fonts/space_grotesk_bold.ttf) format("truetype"); }
@font-face { font-family: "JetBrains Mono"; font-weight: 500; font-display: swap; src: url(/assets/fonts/jetbrains_mono_medium.ttf) format("truetype"); }
@font-face { font-family: "JetBrains Mono"; font-weight: 600; font-display: swap; src: url(/assets/fonts/jetbrains_mono_semibold.ttf) format("truetype"); }
@font-face { font-family: "JetBrains Mono"; font-weight: 700; font-display: swap; src: url(/assets/fonts/jetbrains_mono_bold.ttf) format("truetype"); }
@font-face { font-family: "JetBrains Mono"; font-weight: 800; font-display: swap; src: url(/assets/fonts/jetbrains_mono_extrabold.ttf) format("truetype"); }
@font-face { font-family: "Material Symbols Rounded"; font-style: normal; font-weight: 500; font-display: block; src: url(/assets/fonts/material-symbols-rounded.woff2) format("woff2"); }
"""

FONT_FILES = sorted(f for f in os.listdir(FONTS) if f.endswith(('.ttf', '.woff2')))


def local_fonts(html: str) -> str:
    """Quita los enlaces a Google Fonts y pone la hoja de letras local."""
    html = re.sub(r'<link rel="preconnect" href="https://fonts\.googleapis\.com">\n?', '', html)
    html = re.sub(r'<link href="https://fonts\.googleapis\.com/css2\?family=Material[^"]*" rel="stylesheet">\n?', '', html)
    html, n = re.subn(r'<link href="https://fonts\.googleapis\.com/css2\?[^"]*" rel="stylesheet">',
                      '<link rel="stylesheet" href="/assets/fonts/fonts.css">', html)
    assert n == 1, 'no encontré el enlace de Google Fonts'
    return html


def head_add(html: str, extra: str) -> str:
    """Agrega etiquetas dentro de <head>, justo después del viewport."""
    m = re.search(r'<meta name=viewport[^>]*>', html)
    assert m, 'no encontré el viewport'
    return html[:m.end()] + extra + html[m.end():]


def icon(size: int, path: str, maskable: bool = False):
    """Ícono naranja de ApexMoto con la moto blanca (glifo "two_wheeler" de Material Symbols)."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if maskable:
        d.rectangle([0, 0, size, size], fill='#D94600')
    else:
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill='#D94600')
    glyph = ''  # two_wheeler
    try:
        f = ImageFont.truetype(os.path.join(FONTS, 'material-symbols-rounded.woff2'), int(size * (0.5 if maskable else 0.62)))
        box = d.textbbox((0, 0), glyph, font=f)
        w, h = box[2] - box[0], box[3] - box[1]
        d.text(((size - w) / 2 - box[0], (size - h) / 2 - box[1]), glyph, font=f, fill='white')
    except OSError:
        f = ImageFont.truetype(os.path.join(FONTS, 'chivo_black.ttf'), int(size * 0.5))
        box = d.textbbox((0, 0), 'A', font=f)
        d.text(((size - (box[2] - box[0])) / 2 - box[0], (size - (box[3] - box[1])) / 2 - box[1]), 'A', font=f, fill='white')
    img.save(path)


def write(path: str, text: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, 'w', encoding='utf8', newline='\n') as fh:
        fh.write(text)


def main():
    write(os.path.join(FONTS, 'fonts.css'), FONTS_CSS)

    # ── Demo (prototipo): se instala como app y funciona sin internet ──
    demo = os.path.join(PUB, 'demo')
    os.makedirs(demo, exist_ok=True)
    icon(192, os.path.join(demo, 'icon-192.png'))
    icon(512, os.path.join(demo, 'icon-512.png'))
    icon(512, os.path.join(demo, 'icon-maskable-512.png'), maskable=True)
    icon(180, os.path.join(demo, 'apple-touch-icon.png'), maskable=True)
    write(os.path.join(demo, 'manifest.webmanifest'), """{
  "name": "ApexMoto · demo",
  "short_name": "ApexMoto",
  "description": "Prototipo navegable de ApexMoto para la presentación del proyecto.",
  "lang": "es-CO",
  "start_url": "/demo/",
  "scope": "/demo/",
  "display": "standalone",
  "orientation": "portrait",
  "background_color": "#F3F4F8",
  "theme_color": "#FFFFFF",
  "icons": [
    { "src": "/demo/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/demo/icon-512.png", "sizes": "512x512", "type": "image/png" },
    { "src": "/demo/icon-maskable-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable" }
  ]
}
""")
    cached = ['/demo/', '/demo/manifest.webmanifest', '/demo/icon-192.png', '/demo/icon-512.png', '/assets/fonts/fonts.css'] + \
             ['/assets/fonts/' + f for f in FONT_FILES]
    write(os.path.join(demo, 'sw.js'), """// Guarda la demo en el celular para que abra sin internet (por si falla el wifi del salón).
const CACHE = 'apexmoto-demo-%s';
const FILES = %s;
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
// Primero la red (para ver cambios), y si no hay señal, la copia guardada.
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET' || new URL(e.request.url).origin !== location.origin) return;
  e.respondWith(fetch(e.request).then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); return r; })
    .catch(() => caches.match(e.request, { ignoreSearch: true }).then(r => r || caches.match('/demo/'))));
});
""" % (VERSION, str(cached).replace("'", '"')))
    html = io.open(os.path.join(SRC, 'prototipo.html'), encoding='utf8').read()
    html = local_fonts(html)
    html = head_add(html, '<meta name="robots" content="noindex"><meta name="theme-color" content="#FFFFFF">'
                          '<link rel="manifest" href="/demo/manifest.webmanifest"><link rel="icon" href="/demo/icon-192.png">'
                          '<link rel="apple-touch-icon" href="/demo/apple-touch-icon.png"><meta name="apple-mobile-web-app-capable" content="yes">'
                          '<meta name="mobile-web-app-capable" content="yes"><meta name="apple-mobile-web-app-title" content="ApexMoto">')
    sw = "<script>if ('serviceWorker' in navigator) navigator.serviceWorker.register('/demo/sw.js', { scope: '/demo/' }).catch(() => {});</script>\n"
    html = html[:html.rfind('</body>')] + sw + html[html.rfind('</body>'):]
    write(os.path.join(demo, 'index.html'), html)

    # ── Presentación (comparación con el QR) ──
    html = io.open(os.path.join(SRC, 'comparacion.html'), encoding='utf8').read()
    html = local_fonts(html)
    html = head_add(html, '<meta name="robots" content="noindex"><link rel="icon" href="/demo/icon-192.png">')
    write(os.path.join(PUB, 'presentacion', 'index.html'), html)
    print('Listo: hosting/public/demo y hosting/public/presentacion')


if __name__ == '__main__':
    main()
