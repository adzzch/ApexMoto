// Guarda la demo en el celular para que abra sin internet (por si falla el wifi del salón).
const CACHE = 'apexmoto-demo-v2';
const FILES = ["/demo/", "/demo/manifest.webmanifest", "/demo/icon-192.png", "/demo/icon-512.png", "/assets/fonts/fonts.css", "/assets/fonts/chivo_black.ttf", "/assets/fonts/chivo_bold.ttf", "/assets/fonts/chivo_extrabold.ttf", "/assets/fonts/jetbrains_mono_bold.ttf", "/assets/fonts/jetbrains_mono_extrabold.ttf", "/assets/fonts/jetbrains_mono_medium.ttf", "/assets/fonts/jetbrains_mono_semibold.ttf", "/assets/fonts/material-symbols-rounded.woff2", "/assets/fonts/space_grotesk_bold.ttf", "/assets/fonts/space_grotesk_medium.ttf", "/assets/fonts/space_grotesk_regular.ttf"];
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
