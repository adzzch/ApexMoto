// Pruebas de firestore.rules. Se ejecutan con `npm test` (levanta el emulador de Firestore; necesita Java 21+).
// Cada prueba demuestra una garantía concreta: qué puede y qué no puede hacer cada rol.
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, addDoc, collection, query, where, writeBatch,
  serverTimestamp, Timestamp, arrayUnion, arrayRemove, increment,
} from 'firebase/firestore';

let env;
// G es una cuenta creada con el rol de guía, que se retiró el 2026-11-15: sirve para probar que solo puede pasar a rider.
const R = 'rider1', X = 'rider2', S = 'shop1', O = 'owner1', G = 'guide1', T = 'taller1', T2 = 'taller2', A = 'admin1';
const HASH = 'a'.repeat(64);
const db = (uid) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore();
const past = (secs) => Timestamp.fromMillis(Date.now() - secs * 1000);

const acceptance = (uid, docId, extra = {}) => ({
  userId: uid, docId, version: '1.0.0', docHash: HASH, acceptedAt: serverTimestamp(), method: 'casilla_registro',
  platform: 'android', appVersion: '1.1 (2)', os: 'Android 14 (API 34)', deviceId: 'fid-123', locale: 'es-CO', role: 'm', ...extra,
});
const accId = (uid, docId) => `${uid}_${docId}_1.0.0`;
const user = (role, legal) => ({ name: 'Persona', email: 'p@x.co', role, legal });
const BASE = { terminos: '1.0.0', privacidad: '1.0.0' };

/** Crea el perfil y sus aceptaciones en un solo lote, igual que la app. */
function register(uid, role, docs = ['terminos', 'privacidad']) {
  const d = db(uid); const b = writeBatch(d);
  b.set(doc(d, 'users', uid), user(role, Object.fromEntries(docs.map((k) => [k, '1.0.0']))));
  docs.forEach((k) => b.set(doc(d, 'legal_acceptances', accId(uid, k)), acceptance(uid, k)));
  return b.commit();
}

const venue = (ownerId, extra = {}) => ({ ownerId, name: 'Parrilla', desc: '', promo: '', menu: [], types: ['food'], lat: 3.45, lng: -76.53, rating: 5.0, reviews: 0, visits: 0, ...extra });
const bike = (ownerId, extra = {}) => ({ ownerId, name: 'XRE 300', cc: 300, pricePerDay: 160000, city: 'Cali', specs: [], rating: 5.0, trips: 0, instant: false, delivery: false, available: true, booked: [], soatUntil: '2099-01-01', rtmUntil: '2099-01-01', lastReq: '', ...extra });
const sos = (fromId, extra = {}) => ({ fromId, fromName: 'Rider', kind: 'flat', kindName: 'Pinchazo', locationText: 'Km 12 vía al mar', lat: 3.5, lng: -76.6, note: '', price: 35000, status: 'new', providerId: '', providerName: '', eta: 0, createdAt: 1, acceptedAt: 0, doneAt: 0, ...extra });
const order = (fromId, ownerId, extra = {}) => ({ venueId: 'v1', venueName: 'Parrilla', ownerId, fromId, fromName: 'Rider', items: [{ name: 'Churrasco', qty: 1, price: 42000 }], total: 42000, eta: '30 min', note: '', status: 'new', createdAt: 1, readyAt: 0, ...extra });
const rent = (fromId, extra = {}) => ({ type: 'rent', fromId, fromName: 'Rider', toId: O, itemId: 'b1', itemName: 'XRE 300', days: 2, amount: 320000, total: 320000, dateFrom: '2030-01-10', dateTo: '2030-01-11', plan: '', extras: [], delivery: false, status: 'p', rated: 0, createdAt: 1, licenseDeclared: true, licenseCat: 'A2', cancelKind: '', cancelledAt: 0,
  // Fase 5: copia del perfil de conductor y acuerdo de daños de la moto.
  licenseUntil: '2099-01-01', riderSince: 2015, riderMaxCc: 650, emergencyName: '', emergencyPhone: '', deposit: 0, damageCap: 0, damageAccepted: true, prevReq: '', ownerCancel: '', ...extra });
const riderProfile = (extra = {}) => ({ licenseCat: 'A2', licenseUntil: '2099-01-01', since: 2015, maxCc: 650, emergencyName: '', emergencyPhone: '', updatedAt: 1, ...extra });

/** Escribe la acción y su sello de límite de uso en un lote, igual que la app. */
function stamped(uid, kind, fill) {
  const d = db(uid); const b = writeBatch(d);
  fill(b, d);
  b.set(doc(d, 'rate_limits', uid), { [kind]: serverTimestamp() }, { merge: true });
  return b.commit();
}

before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-apexmoto', firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 } });
});
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    const full = { ...BASE };
    await setDoc(doc(d, 'users', R), user('m', full));
    await setDoc(doc(d, 'users', X), user('m', full));
    await setDoc(doc(d, 'users', S), user('c', full));
    await setDoc(doc(d, 'users', O), user('o', { ...full, 'declaracion-propietario': '1.0.0' }));
    await setDoc(doc(d, 'users', G), user('d', { ...full, 'declaracion-guia': '1.0.0' }));
    await setDoc(doc(d, 'users', T), user('t', full));
    await setDoc(doc(d, 'users', T2), user('t', full));
    await setDoc(doc(d, 'users', A), user('m', full));
    await setDoc(doc(d, 'admins', A), { since: 1 });
    for (const u of [R, X, S, O, G, T, T2, A]) for (const k of ['terminos', 'privacidad']) await setDoc(doc(d, 'legal_acceptances', accId(u, k)), { ...acceptance(u, k), acceptedAt: past(100) });
    await setDoc(doc(d, 'legal_acceptances', accId(O, 'declaracion-propietario')), { ...acceptance(O, 'declaracion-propietario'), acceptedAt: past(100) });
    await setDoc(doc(d, 'legal_acceptances', accId(G, 'declaracion-guia')), { ...acceptance(G, 'declaracion-guia'), acceptedAt: past(100) });
    await setDoc(doc(d, 'venues', 'v1'), venue(S));
    await setDoc(doc(d, 'bikes', 'b1'), bike(O));
    await setDoc(doc(d, 'bikes', 'b2'), bike(O, { instant: true }));
    await setDoc(doc(d, 'rider_profiles', R), riderProfile());
    await setDoc(doc(d, 'rider_profiles', X), riderProfile());
    await setDoc(doc(d, 'drivers', G), { ownerId: G, name: 'Guía', rating: 5.0, reviews: 0 });   // perfil viejo que quedó en la base
  });
});

// ───────── Aceptación legal trazable ─────────
test('registro: no se puede crear el perfil sin los registros de aceptación', async () => {
  await assertFails(setDoc(doc(db('n1'), 'users', 'n1'), user('m', BASE)));
});
test('registro: perfil + aceptaciones en un lote funciona', async () => {
  await assertSucceeds(register('n1', 'm'));
});
test('registro: aceptar solo los términos (sin autorizar datos) no alcanza', async () => {
  await assertFails(register('n1', 'm', ['terminos']));
});
test('registro: el dueño necesita además su declaración; el rol de guía ya no existe', async () => {
  await assertFails(register('n1', 'o'));
  await assertSucceeds(register('n2', 'o', ['terminos', 'privacidad', 'declaracion-propietario']));
  await assertFails(register('n3', 'd', ['terminos', 'privacidad', 'declaracion-guia']));
});
test('aceptación: la fecha debe ser la hora del servidor, no la del teléfono', async () => {
  const d = db('n1');
  await assertFails(setDoc(doc(d, 'legal_acceptances', accId('n1', 'terminos')), acceptance('n1', 'terminos', { acceptedAt: past(86400 * 365) })));
  await assertSucceeds(setDoc(doc(d, 'legal_acceptances', accId('n1', 'terminos')), acceptance('n1', 'terminos')));
});
test('aceptación: no se puede modificar ni borrar', async () => {
  const ref = doc(db(R), 'legal_acceptances', accId(R, 'terminos'));
  await assertFails(updateDoc(ref, { version: '0.9.0' }));
  await assertFails(setDoc(ref, acceptance(R, 'terminos')));
  await assertFails(deleteDoc(ref));
});
test('aceptación: no se puede registrar a nombre de otra persona ni leer las ajenas', async () => {
  await assertFails(setDoc(doc(db(X), 'legal_acceptances', accId(R, 'ubicacion')), acceptance(R, 'ubicacion')));
  await assertFails(getDoc(doc(db(X), 'legal_acceptances', accId(R, 'terminos'))));
  await assertSucceeds(getDocs(query(collection(db(R), 'legal_acceptances'), where('userId', '==', R))));
  await assertFails(getDocs(collection(db(R), 'legal_acceptances')));
});
test('aceptación: versión inexistente, id que no corresponde o hash inválido se rechazan', async () => {
  const d = db('n1');
  await assertFails(setDoc(doc(d, 'legal_acceptances', 'n1_terminos_9.9.9'), acceptance('n1', 'terminos', { version: '9.9.9' })));
  await assertFails(setDoc(doc(d, 'legal_acceptances', 'cualquiera'), acceptance('n1', 'terminos')));
  await assertFails(setDoc(doc(d, 'legal_acceptances', accId('n1', 'terminos')), acceptance('n1', 'terminos', { docHash: 'xyz' })));
});
test('ubicación: la autorización queda en el perfil solo si existe su registro', async () => {
  const d = db(R);
  await assertFails(updateDoc(doc(d, 'users', R), { legal: { ...BASE, ubicacion: '1.0.0' } }));
  const b = writeBatch(d);
  b.update(doc(d, 'users', R), { legal: { ...BASE, ubicacion: '1.0.0' } });
  b.set(doc(d, 'legal_acceptances', accId(R, 'ubicacion')), acceptance(R, 'ubicacion', { method: 'boton_aviso_ubicacion' }));
  await assertSucceeds(b.commit());
});
test('perfil: una aceptación no se puede retirar', async () => {
  await assertFails(updateDoc(doc(db(R), 'users', R), { legal: { terminos: '1.0.0' } }));
});

// ───────── Roles y privacidad de usuarios ─────────
test('rol: un rider no puede volverse comercio, taller, dueño ni guía por su cuenta', async () => {
  for (const r of ['c', 't', 'o', 'd']) await assertFails(updateDoc(doc(db(R), 'users', R), { role: r }));
  // Ni siquiera con la declaración del guía registrada en el mismo lote.
  const d = db(R); const b = writeBatch(d);
  b.update(doc(d, 'users', R), { role: 'd', legal: { ...BASE, 'declaracion-guia': '1.0.0' } });
  b.set(doc(d, 'legal_acceptances', accId(R, 'declaracion-guia')), acceptance(R, 'declaracion-guia'));
  await assertFails(b.commit());
});
test('rol: una cuenta del rol de guía (retirado) solo puede pasar a rider, y no volver', async () => {
  await assertFails(updateDoc(doc(db(G), 'users', G), { role: 'c' }));
  await assertSucceeds(updateDoc(doc(db(G), 'users', G), { role: 'm' }));
  await assertFails(updateDoc(doc(db(G), 'users', G), { role: 'd' }));
});
test('usuarios: nadie lee el perfil (correo o celular) de otra persona ni lista usuarios', async () => {
  await assertFails(getDoc(doc(db(X), 'users', R)));
  await assertFails(getDocs(collection(db(R), 'users')));
  await assertSucceeds(getDoc(doc(db(R), 'users', R)));
});
test('usuarios: no se puede cambiar el correo ni agregar campos al perfil', async () => {
  await assertFails(updateDoc(doc(db(R), 'users', R), { email: 'otro@x.co' }));
  await assertFails(updateDoc(doc(db(R), 'users', R), { admin: true }));
  await assertSucceeds(updateDoc(doc(db(R), 'users', R), { name: 'Nuevo nombre' }));
});
test('sin sesión no se lee nada', async () => {
  await assertFails(getDocs(collection(db(null), 'venues')));
  await assertFails(getDoc(doc(db(null), 'users', R)));
});
test('supresión: cada persona puede borrar su propio perfil y no el de otra', async () => {
  await assertFails(deleteDoc(doc(db(X), 'users', R)));
  await assertSucceeds(deleteDoc(doc(db(R), 'users', R)));
});
test('administración: nadie se nombra administrador desde la app', async () => {
  await assertFails(setDoc(doc(db(R), 'admins', R), { since: 1 }));
});

// ───────── Derechos de los titulares ─────────
const preq = (uid, extra = {}) => ({ userId: uid, type: 'consulta', detail: 'Quiero saber qué datos tienen de mí.', contact: 'p@x.co', status: 'recibida', createdAt: serverTimestamp(), response: '', respondedAt: null, ...extra });
test('solicitudes: el titular radica y consulta; no puede alterarla ni borrarla; otro no la lee', async () => {
  const ref = doc(db(R), 'privacy_requests', 'pr1');
  await assertSucceeds(setDoc(ref, preq(R)));
  await assertSucceeds(getDoc(ref));
  await assertFails(updateDoc(ref, { status: 'respondida' }));
  await assertFails(deleteDoc(ref));
  await assertFails(getDoc(doc(db(X), 'privacy_requests', 'pr1')));
});
test('solicitudes: no se puede antedatar ni radicar como ya respondida', async () => {
  await assertFails(setDoc(doc(db(R), 'privacy_requests', 'a'), preq(R, { createdAt: past(86400 * 30) })));
  await assertFails(setDoc(doc(db(R), 'privacy_requests', 'b'), preq(R, { status: 'respondida' })));
  await assertFails(setDoc(doc(db(R), 'privacy_requests', 'c'), preq(X)));
});
test('solicitudes: el administrador responde, sin tocar lo que escribió el titular', async () => {
  await setDoc(doc(db(R), 'privacy_requests', 'pr1'), preq(R));
  await assertSucceeds(updateDoc(doc(db(A), 'privacy_requests', 'pr1'), { status: 'respondida', response: 'Adjuntamos tus datos.', respondedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db(A), 'privacy_requests', 'pr1'), { detail: 'cambiado' }));
});
const prov = { legalName: 'Parrilla SAS', idType: 'NIT', idNumber: '900123456-1', address: 'Cra 1 # 2-3, Cali', phone: '3001234567', updatedAt: 1 };
test('oferente: lo registra el aliado; es privado; un rider no lo crea', async () => {
  await assertSucceeds(setDoc(doc(db(S), 'providers', S), prov));
  await assertFails(getDoc(doc(db(R), 'providers', S)));
  await assertSucceeds(getDoc(doc(db(A), 'providers', S)));
  await assertFails(setDoc(doc(db(R), 'providers', R), prov));
  await assertFails(setDoc(doc(db(S), 'providers', S), { ...prov, cedulaFoto: 'x' }));
});

// ───────── S.O.S. y ubicación ─────────
test('S.O.S.: exige el sello de límite de uso y no deja enviar otro antes de 60 s', async () => {
  await assertFails(setDoc(doc(db(R), 'sos', 's0'), sos(R)));
  await assertSucceeds(stamped(R, 'sos', (b, d) => b.set(doc(d, 'sos', 's1'), sos(R))));
  await assertFails(stamped(R, 'sos', (b, d) => b.set(doc(d, 'sos', 's2'), sos(R))));
});
test('S.O.S.: pasado el tiempo mínimo se puede enviar otro', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'rate_limits', R), { sos: past(120) }));
  await assertSucceeds(stamped(R, 'sos', (b, d) => b.set(doc(d, 'sos', 's1'), sos(R))));
});
test('límite de uso: el sello no se puede falsificar con una hora vieja', async () => {
  await assertFails(setDoc(doc(db(R), 'rate_limits', R), { sos: past(3600) }));
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'rate_limits', R), { sos: past(5) }));
  await assertFails(updateDoc(doc(db(R), 'rate_limits', R), { sos: past(3600) }));
  await assertFails(deleteDoc(doc(db(R), 'rate_limits', R)));
});
test('S.O.S.: la ubicación solo la ven el rider, los talleres mientras se busca taller y luego el taller asignado', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'sos', 'new1'), sos(R));
    await setDoc(doc(c.firestore(), 'sos', 'taken'), sos(R, { status: 'go', providerId: T, providerName: 'Taller' }));
  });
  await assertSucceeds(getDocs(query(collection(db(R), 'sos'), where('fromId', '==', R))));
  await assertFails(getDoc(doc(db(X), 'sos', 'new1')));                                   // otro rider
  await assertFails(getDoc(doc(db(S), 'sos', 'new1')));                                   // un comercio
  await assertFails(getDocs(collection(db(T), 'sos')));                                   // ni un taller lista todo
  await assertSucceeds(getDocs(query(collection(db(T), 'sos'), where('status', '==', 'new'))));
  await assertFails(getDocs(query(collection(db(X), 'sos'), where('status', '==', 'new'))));
  await assertSucceeds(getDoc(doc(db(T), 'sos', 'taken')));
  await assertFails(getDoc(doc(db(T2), 'sos', 'taken')));                                 // otro taller ya no la ve
});
test('S.O.S.: solo un taller acepta, a su propio nombre; el rider no se lo asigna', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'sos', 's1'), sos(R));
    await setDoc(doc(c.firestore(), 'venues', 'tv'), venue(T, { types: ['moto'] }));
    await setDoc(doc(c.firestore(), 'venues', 'tv2'), venue(T2, { types: ['moto'] }));
  });
  const accept = (who, as, vid = 'tv') => updateDoc(doc(db(who), 'sos', 's1'), { status: 'ok', providerId: as, providerName: 'Taller', eta: 30, acceptedAt: 2, providerVenueId: vid });
  await assertFails(accept(X, X));
  await assertFails(accept(R, R));
  await assertFails(accept(T, T2));
  await assertFails(accept(T, T, 'tv2'));                                                 // con el taller de otro
  await assertSucceeds(accept(T, T));
  await assertFails(accept(T2, T2, 'tv2'));                                                      // ya está tomado
});
test('S.O.S.: al terminar o cancelar, las coordenadas deben quedar borradas', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'sos', 'site'), sos(R, { status: 'site', providerId: T }));
    await setDoc(doc(c.firestore(), 'sos', 'mine'), sos(R));
  });
  await assertFails(updateDoc(doc(db(T), 'sos', 'site'), { status: 'fin', doneAt: 3 }));
  await assertSucceeds(updateDoc(doc(db(T), 'sos', 'site'), { status: 'fin', doneAt: 3, lat: 0, lng: 0 }));
  await assertFails(updateDoc(doc(db(R), 'sos', 'mine'), { status: 'no' }));
  await assertSucceeds(updateDoc(doc(db(R), 'sos', 'mine'), { status: 'no', lat: 0, lng: 0, doneAt: 3 }));
});
test('S.O.S.: el taller no se salta pasos ni cambia el precio', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'sos', 's1'), sos(R, { status: 'ok', providerId: T })));
  await assertFails(updateDoc(doc(db(T), 'sos', 's1'), { status: 'fin', doneAt: 3, lat: 0, lng: 0 }));
  await assertFails(updateDoc(doc(db(T), 'sos', 's1'), { status: 'go', price: 999999 }));
  await assertSucceeds(updateDoc(doc(db(T), 'sos', 's1'), { status: 'go' }));
});

// ───────── Pedidos y cupones ─────────
test('pedidos: van al dueño real del comercio y exigen sello', async () => {
  await assertFails(stamped(R, 'order', (b, d) => b.set(doc(d, 'orders', 'o1'), order(R, X))));   // dueño falso
  await assertFails(setDoc(doc(db(R), 'orders', 'o2'), order(R, S)));                              // sin sello
  await assertFails(stamped(R, 'order', (b, d) => b.set(doc(d, 'orders', 'o3'), order(X, S))));   // a nombre de otro
  await assertSucceeds(stamped(R, 'order', (b, d) => b.set(doc(d, 'orders', 'o4'), order(R, S))));
});
test('pedidos: solo los ven el rider y el comercio', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'orders', 'o1'), order(R, S)));
  await assertSucceeds(getDoc(doc(db(R), 'orders', 'o1')));
  await assertSucceeds(getDoc(doc(db(S), 'orders', 'o1')));
  await assertFails(getDoc(doc(db(X), 'orders', 'o1')));
});
test('pedidos: el comercio avanza paso a paso; el rider solo cancela mientras está "enviado"', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'orders', 'o1'), order(R, S));
    await setDoc(doc(c.firestore(), 'orders', 'o2'), order(R, S));
    await setDoc(doc(c.firestore(), 'orders', 'o3'), order(R, S, { status: 'prep' }));
  });
  await assertFails(updateDoc(doc(db(S), 'orders', 'o1'), { status: 'done' }));
  await assertFails(updateDoc(doc(db(S), 'orders', 'o1'), { status: 'prep', total: 1 }));
  await assertFails(updateDoc(doc(db(R), 'orders', 'o1'), { status: 'done' }));
  await assertSucceeds(updateDoc(doc(db(S), 'orders', 'o1'), { status: 'prep' }));
  await assertSucceeds(updateDoc(doc(db(R), 'orders', 'o2'), { status: 'can' }));
  await assertFails(updateDoc(doc(db(R), 'orders', 'o3'), { status: 'can' }));
});
test('cupones: los canjea una sola vez el comercio dueño', async () => {
  const c = { code: 'APX-AB12', venueId: 'v1', ownerId: S, userId: R, userName: 'Rider', used: false, createdAt: 1, usedAt: 0 };
  await assertFails(setDoc(doc(db(R), 'coupons', 'c0'), { ...c, used: true }));
  await assertSucceeds(setDoc(doc(db(R), 'coupons', 'c1'), c));
  await assertFails(updateDoc(doc(db(R), 'coupons', 'c1'), { used: true, usedAt: 2 }));
  await assertSucceeds(updateDoc(doc(db(S), 'coupons', 'c1'), { used: true, usedAt: 2 }));
  await assertFails(updateDoc(doc(db(S), 'coupons', 'c1'), { used: true, usedAt: 3 }));
});

// ───────── Renta y reservas ─────────
const newReq = (uid, id, data) => stamped(uid, 'request', (b, d) => b.set(doc(d, 'requests', id), data));
test('renta: exige declaración de licencia, SOAT y revisión vigentes, y el dueño real', async () => {
  await assertFails(newReq(R, 'q1', rent(R, { licenseDeclared: false })));
  await assertFails(newReq(R, 'q2', rent(R, { toId: X })));
  await assertFails(newReq(R, 'q3', rent(R, { status: 'ok' })));                                  // la moto b1 no es de reserva inmediata
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), 'bikes', 'b1'), { soatUntil: '2020-01-01' }));
  await assertFails(newReq(R, 'q4', rent(R)));
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), 'bikes', 'b1'), { soatUntil: '2099-01-01' }));
  await assertSucceeds(newReq(R, 'q5', rent(R)));
});
test('reserva inmediata: solo con historial; la solicitud y las fechas apartadas van en un lote; nadie más toca las fechas', async () => {
  await assertFails(updateDoc(doc(db(R), 'bikes', 'b2'), { booked: arrayUnion('2030-01-01|2030-12-31') }));
  await assertFails(updateDoc(doc(db(R), 'bikes', 'b2'), { pricePerDay: 1 }));
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'requests', 'q0'), rent(R, { status: 'fin' }));        // una renta suya ya terminada
    await setDoc(doc(c.firestore(), 'requests', 'x0'), rent(X, { status: 'fin' }));        // una de otra persona
  });
  const book = (id, extra) => stamped(R, 'request', (b, d) => {
    b.set(doc(d, 'requests', id), rent(R, { itemId: 'b2', status: 'ok', ...extra }));
    b.update(doc(d, 'bikes', 'b2'), { booked: arrayUnion('2030-01-10|2030-01-11'), lastReq: id });
  });
  await assertFails(book('n1', {}));                                                             // sin historial
  await assertFails(book('n2', { prevReq: 'x0' }));                                              // el historial de otro
  await assertSucceeds(book('q1', { prevReq: 'q0' }));
  // Con una solicitud vieja no se puede apartar otra vez ni otras fechas.
  await assertFails(updateDoc(doc(db(R), 'bikes', 'b2'), { booked: arrayUnion('2031-01-01|2031-01-02'), lastReq: 'q1' }));
});
test('retracto o cancelación: el rider cancela antes de empezar y se liberan sus fechas', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'requests', 'q1'), rent(R, { itemId: 'b2', status: 'ok' }));
    await updateDoc(doc(c.firestore(), 'bikes', 'b2'), { booked: ['2030-01-10|2030-01-11', '2030-02-01|2030-02-02'] });
    await setDoc(doc(c.firestore(), 'requests', 'q2'), rent(R, { status: 'ride' }));
  });
  const d = db(R); const b = writeBatch(d);
  b.update(doc(d, 'requests', 'q1'), { status: 'can', cancelKind: 'retracto', cancelledAt: 5 });
  b.update(doc(d, 'bikes', 'b2'), { booked: arrayRemove('2030-01-10|2030-01-11'), lastReq: 'q1' });
  await assertSucceeds(b.commit());
  await assertFails(updateDoc(doc(d, 'requests', 'q2'), { status: 'can', cancelKind: 'retracto', cancelledAt: 5 }));   // ya empezó
  await assertFails(updateDoc(doc(db(X), 'bikes', 'b2'), { booked: arrayRemove('2030-02-01|2030-02-02'), lastReq: 'q1' }));
});
test('solicitudes: el aliado avanza paso a paso; sin acta de entrega no empieza; un tercero no las ve', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'requests', 'q1'), rent(R)));
  await assertFails(getDoc(doc(db(X), 'requests', 'q1')));
  await assertFails(updateDoc(doc(db(O), 'requests', 'q1'), { status: 'fin' }));
  await assertFails(updateDoc(doc(db(O), 'requests', 'q1'), { status: 'ok', total: 1 }));
  await assertFails(updateDoc(doc(db(R), 'requests', 'q1'), { status: 'ok' }));
  await assertSucceeds(updateDoc(doc(db(O), 'requests', 'q1'), { status: 'ok' }));
  await assertFails(updateDoc(doc(db(O), 'requests', 'q1'), { status: 'ride' }));               // falta el acta de entrega
});
test('guía (rol retirado): sin perfiles, sin solicitudes, sin calificaciones y sin datos de oferente', async () => {
  await assertFails(getDoc(doc(db(R), 'drivers', G)));                                         // el perfil viejo ya no se lee
  await assertFails(updateDoc(doc(db(G), 'drivers', G), { name: 'Otro' }));
  await assertFails(setDoc(doc(db(R), 'drivers', R), { ownerId: R, name: 'Nuevo guía' }));
  await assertFails(newReq(R, 'g1', rent(R, { type: 'drv', toId: G, itemId: G })));              // solo hay solicitudes de renta
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'requests', 'fin'), rent(R, { status: 'fin' })));
  await assertFails(updateDoc(doc(db(R), 'requests', 'fin'), { rated: 5 }));                    // nadie se califica desde la solicitud
  await assertFails(setDoc(doc(db(G), 'providers', G), prov));
});
test('motos: solo un dueño publica; sin SOAT y revisión no queda disponible', async () => {
  await assertFails(setDoc(doc(db(R), 'bikes', 'n1'), bike(R)));
  await assertFails(setDoc(doc(db(O), 'bikes', 'n2'), bike(O, { soatUntil: '', rtmUntil: '' })));
  await assertSucceeds(setDoc(doc(db(O), 'bikes', 'n3'), bike(O, { soatUntil: '', rtmUntil: '', available: false })));
  await assertSucceeds(setDoc(doc(db(O), 'bikes', 'n4'), bike(O)));
  await assertFails(updateDoc(doc(db(O), 'bikes', 'n4'), { rating: 1.0 }));
});

// ───────── Chat ─────────
test('chat: solo las dos partes del servicio escriben y leen', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'requests', 'q1'), rent(R)));
  const msg = (from, members) => ({ thread: 'req_q1', fromId: from, fromName: 'Alguien', text: 'Hola', createdAt: 1, members });
  await assertSucceeds(setDoc(doc(db(R), 'chats', 'm1'), msg(R, [O, R].sort())));
  await assertSucceeds(setDoc(doc(db(O), 'chats', 'm2'), msg(O, [O, R].sort())));
  await assertFails(setDoc(doc(db(X), 'chats', 'm3'), msg(X, [R, X].sort())));                  // un extraño se cuela en el hilo
  await assertFails(setDoc(doc(db(R), 'chats', 'm4'), msg(O, [O, R].sort())));                  // suplantar al otro
  await assertSucceeds(getDocs(query(collection(db(O), 'chats'), where('thread', '==', 'req_q1'), where('members', 'array-contains', O))));
  await assertFails(getDocs(query(collection(db(X), 'chats'), where('thread', '==', 'req_q1'))));
  await assertFails(getDoc(doc(db(X), 'chats', 'm1')));
});

// ───────── Comercios, rodadas, rutas y datos demo ─────────
test('comercios: el dueño edita, pero no su calificación; las visitas suben de a una', async () => {
  await assertSucceeds(updateDoc(doc(db(S), 'venues', 'v1'), { promo: '10% con cupón' }));
  await assertFails(updateDoc(doc(db(S), 'venues', 'v1'), { rating: 5.0, reviews: 500 }));
  await assertFails(updateDoc(doc(db(X), 'venues', 'v1'), { name: 'Hackeado' }));
  await assertSucceeds(updateDoc(doc(db(R), 'venues', 'v1'), { visits: increment(1) }));
  await assertFails(updateDoc(doc(db(R), 'venues', 'v1'), { visits: increment(50) }));
  await assertFails(setDoc(doc(db(R), 'venues', 'mio'), venue(R)));                             // un rider no publica negocios
});
test('datos demo y metadatos: solo un administrador', async () => {
  await assertFails(setDoc(doc(db(R), 'venues', 'demo_v1'), venue('')));
  await assertFails(setDoc(doc(db(R), 'meta', 'seed'), { at: 1 }));
  await assertSucceeds(setDoc(doc(db(A), 'venues', 'demo_v1'), venue('')));
  await assertSucceeds(setDoc(doc(db(A), 'meta', 'seed'), { at: 1 }));
});
// ───────── Rodadas y mapa del grupo (fase 2) ─────────
const rodada = (extra = {}) => ({ creatorId: O, name: 'Calima', whenLabel: 'Sábado 7:00', captain: '@o', rating: 5.0, meet: 'Peaje', style: 'Turismo',
  seats: 3, joined: [O], visibility: 'public', approval: false, pending: [], invited: [], lastInvited: '', leaderId: O, sweeperId: '',
  status: 'planned', startedAt: null, endedAt: null, routeName: '', stops: [], ...extra });
const seedRodada = (id, extra) => env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'rodadas', id), rodada(extra)));
const live = (extra = {}) => ({ lat: 3.45, lng: -76.53, heading: 90, name: 'Rider', moto: 'naked', color: 'orange', sos: false, updatedAt: serverTimestamp(), ...extra });

test('rodadas: se crean con el creador como único miembro y líder', async () => {
  await assertSucceeds(setDoc(doc(db(R), 'rodadas', 'n1'), rodada({ creatorId: R, joined: [R], leaderId: R })));
  await assertFails(setDoc(doc(db(R), 'rodadas', 'n2'), rodada({ creatorId: R, joined: [R, X], leaderId: R })));
  await assertFails(setDoc(doc(db(R), 'rodadas', 'n3'), rodada({ creatorId: R, joined: [R], leaderId: R, status: 'active' })));
  await assertFails(setDoc(doc(db(R), 'rodadas', 'n4'), rodada({ creatorId: X, joined: [X], leaderId: X })));
});
test('rodadas: cada quien solo se une o sale a sí mismo, sin pasar el máximo de motos', async () => {
  await seedRodada('r1', { seats: 2 });
  await assertFails(updateDoc(doc(db(R), 'rodadas', 'r1'), { joined: arrayUnion(X) }));
  await assertFails(updateDoc(doc(db(R), 'rodadas', 'r1'), { joined: [R] }));                   // sacar al creador
  await assertSucceeds(updateDoc(doc(db(R), 'rodadas', 'r1'), { joined: arrayUnion(R) }));
  await assertFails(updateDoc(doc(db(X), 'rodadas', 'r1'), { joined: arrayUnion(X) }));         // ya está completa
  await assertSucceeds(updateDoc(doc(db(R), 'rodadas', 'r1'), { joined: arrayRemove(R) }));
});
test('rodadas con aprobación: se pide, el creador aprueba o rechaza; nadie entra sin pedir', async () => {
  await seedRodada('r2', { approval: true, seats: 5 });
  await assertFails(updateDoc(doc(db(R), 'rodadas', 'r2'), { joined: arrayUnion(R) }));
  await assertSucceeds(updateDoc(doc(db(R), 'rodadas', 'r2'), { pending: arrayUnion(R) }));
  await assertSucceeds(updateDoc(doc(db(O), 'rodadas', 'r2'), { pending: arrayRemove(R), joined: arrayUnion(R) }));
  await assertSucceeds(updateDoc(doc(db(X), 'rodadas', 'r2'), { pending: arrayUnion(X) }));
  await assertSucceeds(updateDoc(doc(db(O), 'rodadas', 'r2'), { pending: arrayRemove(X) }));
  await assertFails(updateDoc(doc(db(O), 'rodadas', 'r2'), { joined: arrayUnion(S) }));        // S nunca pidió unirse
});
test('rodadas de amigos: solo se invita a amigos del creador y nadie más la ve', async () => {
  const fid = [O, X].sort().join('_');
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'friendships', fid), { members: [O, X].sort(), since: 1 }));
  await seedRodada('r3', { visibility: 'friends', approval: true });
  await assertFails(getDoc(doc(db(X), 'rodadas', 'r3')));
  await assertFails(updateDoc(doc(db(O), 'rodadas', 'r3'), { invited: arrayUnion(S), lastInvited: S }));   // no es su amigo
  await assertSucceeds(updateDoc(doc(db(O), 'rodadas', 'r3'), { invited: arrayUnion(X), lastInvited: X }));
  await assertSucceeds(getDoc(doc(db(X), 'rodadas', 'r3')));
  await assertFails(getDoc(doc(db(S), 'rodadas', 'r3')));
  await assertSucceeds(updateDoc(doc(db(X), 'rodadas', 'r3'), { joined: arrayUnion(X) }));       // con invitación entra directo
});
test('rodadas: líder y cierre deben ser miembros; solo el creador inicia, con la hora del servidor', async () => {
  await seedRodada('r4', { joined: [O, R] });
  await assertFails(updateDoc(doc(db(O), 'rodadas', 'r4'), { leaderId: X }));
  await assertSucceeds(updateDoc(doc(db(O), 'rodadas', 'r4'), { leaderId: R, sweeperId: O }));
  await assertFails(updateDoc(doc(db(R), 'rodadas', 'r4'), { status: 'active', startedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db(O), 'rodadas', 'r4'), { status: 'active', startedAt: past(3600) }));
  await assertSucceeds(updateDoc(doc(db(O), 'rodadas', 'r4'), { status: 'active', startedAt: serverTimestamp() }));
});
test('mapa del grupo: solo miembros de una rodada activa, con la hora del servidor y sin inundar', async () => {
  await seedRodada('r5', { joined: [O, R], status: 'active', startedAt: past(60) });
  await seedRodada('r6', { joined: [O, R] });
  await assertSucceeds(setDoc(doc(db(R), 'rodadas/r5/live', R), live()));
  await assertFails(setDoc(doc(db(R), 'rodadas/r5/live', R), live()));                           // menos de 8 s después
  await assertFails(setDoc(doc(db(X), 'rodadas/r5/live', X), live()));                           // no es miembro
  await assertFails(setDoc(doc(db(R), 'rodadas/r6/live', R), live()));                           // la rodada no ha empezado
  await assertSucceeds(getDoc(doc(db(O), 'rodadas/r5/live', R)));
  await assertFails(getDoc(doc(db(S), 'rodadas/r5/live', R)));
  await assertSucceeds(deleteDoc(doc(db(O), 'rodadas/r5/live', R)));                              // el creador limpia al terminar
});
test('señales rápidas de la rodada (accesibilidad): solo las cinco claves y nunca con hora del futuro', async () => {
  await seedRodada('r7', { joined: [O, R, S, X], status: 'active', startedAt: past(60) });
  await assertSucceeds(setDoc(doc(db(R), 'rodadas/r7/live', R), live({ signal: 'stop', signalAt: serverTimestamp() })));
  await assertFails(setDoc(doc(db(O), 'rodadas/r7/live', O), live({ signal: 'gritar', signalAt: serverTimestamp() })));    // clave inventada
  await assertFails(setDoc(doc(db(S), 'rodadas/r7/live', S), live({ signal: 'fuel', signalAt: Timestamp.fromMillis(Date.now() + 3600e3) })));  // del futuro
  await assertFails(setDoc(doc(db(X), 'rodadas/r7/live', X), live({ signal: 'ok', signalAt: 'ya' })));                  // no es una fecha
  // La señal se conserva al actualizar la posición (merge) y la lee el resto del grupo.
  await assertSucceeds(setDoc(doc(db(O), 'rodadas/r7/live', O), live({ signal: 'danger', signalAt: past(30) })));
  await assertSucceeds(getDoc(doc(db(S), 'rodadas/r7/live', R)));
});
test('rodadas: código de invitación (enlace) para unirse, también a las privadas', async () => {
  // Se crea la rodada privada con su código en un lote, como la app.
  const d = db(O); const b = writeBatch(d);
  b.set(doc(d, 'rodadas', 'rc1'), rodada({ visibility: 'friends', approval: true, inviteCode: 'ABCD1234', startsAt: 1900000000000, meetLat: 3.4, meetLng: -76.5 }));
  b.set(doc(d, 'rodada_codes', 'ABCD1234'), { rodadaId: 'rc1', creatorId: O, name: 'Calima', whenLabel: 'sáb 22 nov', meet: 'Peaje' });
  await assertSucceeds(b.commit());
  // Nadie más crea un código para una rodada ajena.
  await assertFails(setDoc(doc(db(R), 'rodada_codes', 'ZZZZ9999'), { rodadaId: 'rc1', creatorId: R, name: 'x', whenLabel: '', meet: '' }));
  // Con el código se ve la invitación, pero no la rodada privada.
  await assertSucceeds(getDoc(doc(db(R), 'rodada_codes', 'ABCD1234')));
  await assertFails(getDoc(doc(db(R), 'rodadas', 'rc1')));
  // Sin la prueba del código no entra; con ella sí (en el mismo lote).
  await assertFails(updateDoc(doc(db(R), 'rodadas', 'rc1'), { joined: arrayUnion(R) }));
  const r = db(R); const j = writeBatch(r);
  j.set(doc(r, 'link_joins', 'rc1_' + R), { uid: R, rodadaId: 'rc1', code: 'ABCD1234' });
  j.update(doc(r, 'rodadas', 'rc1'), { joined: arrayUnion(R) });
  await assertSucceeds(j.commit());
  await assertSucceeds(getDoc(doc(db(R), 'rodadas', 'rc1')));
  // Un código inventado no sirve como prueba.
  await assertFails(setDoc(doc(db(X), 'link_joins', 'rc1_' + X), { uid: X, rodadaId: 'rc1', code: 'NOEXISTE' }));
});
test('rider_stats: públicas, y las rutas suben de a una con la hora del servidor y sin inundar', async () => {
  const st = (extra = {}) => ({ since: 1750000000000, routes: 0, lastAt: serverTimestamp(), ...extra });
  await assertFails(setDoc(doc(db(R), 'rider_stats', X), st()));                                 // el de otro
  await assertFails(setDoc(doc(db(R), 'rider_stats', R), st({ routes: 30 })));                    // no arranca con 30
  await assertSucceeds(setDoc(doc(db(R), 'rider_stats', R), st()));
  await assertSucceeds(getDoc(doc(db(X), 'rider_stats', R)));
  await assertFails(updateDoc(doc(db(R), 'rider_stats', R), { routes: 1, lastAt: serverTimestamp() }));   // menos de 20 min
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), 'rider_stats', R), { lastAt: past(3600) }));
  await assertFails(updateDoc(doc(db(R), 'rider_stats', R), { routes: 5, lastAt: serverTimestamp() }));   // salto de 5
  await assertSucceeds(updateDoc(doc(db(R), 'rider_stats', R), { routes: 1, lastAt: serverTimestamp() }));
});
test('rutas guardadas: son privadas', async () => {
  const route = { userId: R, name: 'Calima · 2 paradas', corridor: 'calima', km: 90, mins: 120, stops: [], createdAt: 1 };
  await assertSucceeds(setDoc(doc(db(R), 'routes', 'rt1'), route));
  await assertFails(getDoc(doc(db(X), 'routes', 'rt1')));
  await assertFails(setDoc(doc(db(X), 'routes', 'rt2'), route));
});
test('cualquier colección no declarada está cerrada', async () => {
  await assertFails(setDoc(doc(db(R), 'otra', 'x'), { a: 1 }));
  await assertFails(getDoc(doc(db(A), 'otra', 'x')));
});

// ───────── Perfil público y amigos (fase 1) ─────────
const prof = (username, extra = {}) => ({ username, name: 'Rider', moto: 'naked', color: 'orange', showStatus: true, ...extra });
const UN = { [R]: 'rider_uno', [X]: 'rider_dos' };
async function seedProfiles() {
  await env.withSecurityRulesDisabled(async (c) => {
    const d = c.firestore();
    for (const u of [R, X]) { await setDoc(doc(d, 'profiles', u), prof(UN[u])); await setDoc(doc(d, 'usernames', UN[u]), { uid: u }); }
  });
}
const freq = (from, to, extra = {}) => ({ from, to, fromName: 'Rider', fromUsername: UN[from], fromMoto: 'naked', fromColor: 'orange', toName: 'Rider', toUsername: UN[to], createdAt: 1, ...extra });
const saveProfile = (uid, username, extra = {}, dropOld = null) => { const d = db(uid); const b = writeBatch(d);
  b.set(doc(d, 'profiles', uid), prof(username, extra)); b.set(doc(d, 'usernames', username), { uid });
  if (dropOld) b.delete(doc(d, 'usernames', dropOld)); return b.commit(); };

test('perfil: el @usuario se reserva junto con el perfil y nadie toma uno ocupado', async () => {
  await seedProfiles();
  await register('n1', 'm');
  await assertFails(setDoc(doc(db('n1'), 'profiles', 'n1'), prof('nuevo_rider')));                 // sin reservar el @usuario
  await assertFails(saveProfile('n1', 'Mayusculas'));                                               // formato inválido
  await assertFails(saveProfile('n1', 'rider_uno'));                                                // ya es de otra persona
  await assertFails(saveProfile(S, 'mi_negocio'));                                                  // un comercio no tiene perfil de rider
  await assertSucceeds(saveProfile('n1', 'nuevo_rider'));
});
test('perfil: cambiar de @usuario libera el anterior; el perfil lo lee cualquiera con sesión', async () => {
  await seedProfiles();
  await assertFails(saveProfile(R, 'rider_nuevo'));                                                 // sin liberar rider_uno
  await assertSucceeds(saveProfile(R, 'rider_nuevo', {}, 'rider_uno'));
  await assertSucceeds(getDoc(doc(db(X), 'profiles', R)));
  await assertFails(getDoc(doc(db(null), 'profiles', R)));
  await assertFails(updateDoc(doc(db(X), 'profiles', R), { name: 'Hackeado' }));
});
test('amistad: solicitud con los datos reales, sin duplicar ni invertir, y con sello', async () => {
  await seedProfiles();
  await assertFails(setDoc(doc(db(R), 'friend_requests', `${R}_${X}`), freq(R, X)));                // sin sello
  await assertFails(stamped(R, 'friend', (b, d) => b.set(doc(d, 'friend_requests', `${R}_${X}`), freq(R, X, { fromUsername: 'rider_dos' }))));
  await assertFails(stamped(R, 'friend', (b, d) => b.set(doc(d, 'friend_requests', `${R}_${R}`), freq(R, R))));
  await assertSucceeds(stamped(R, 'friend', (b, d) => b.set(doc(d, 'friend_requests', `${R}_${X}`), freq(R, X))));
  await assertFails(stamped(X, 'friend', (b, d) => b.set(doc(d, 'friend_requests', `${X}_${R}`), freq(X, R))));   // ya hay una al revés
  await assertFails(getDoc(doc(db(S), 'friend_requests', `${R}_${X}`)));
  await assertSucceeds(getDoc(doc(db(X), 'friend_requests', `${R}_${X}`)));
});
test('amistad: solo quien recibió la solicitud la acepta, y la solicitud se consume', async () => {
  await seedProfiles();
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'friend_requests', `${R}_${X}`), freq(R, X)));
  const pair = [R, X].sort(); const fid = pair.join('_');
  const accept = (who, consume) => { const d = db(who); const b = writeBatch(d);
    b.set(doc(d, 'friendships', fid), { members: pair, since: 1 }); if (consume) b.delete(doc(d, 'friend_requests', `${R}_${X}`)); return b.commit(); };
  await assertFails(accept(R, true));                                                              // quien la envió no se acepta solo
  await assertFails(accept(X, false));                                                             // hay que borrar la solicitud
  await assertSucceeds(accept(X, true));
  await assertSucceeds(getDoc(doc(db(R), 'friendships', fid)));
  await assertFails(getDoc(doc(db(S), 'friendships', fid)));
  await assertSucceeds(deleteDoc(doc(db(R), 'friendships', fid)));                                 // cualquiera de los dos termina la amistad
});
test('amistad: con un bloqueo no hay solicitudes, y el bloqueo es privado', async () => {
  await seedProfiles();
  await assertSucceeds(setDoc(doc(db(R), 'blocks', `${R}_${X}`), { blocker: R, blocked: X, blockedName: 'Rider', blockedUsername: 'rider_dos', createdAt: 1 }));
  await assertFails(stamped(X, 'friend', (b, d) => b.set(doc(d, 'friend_requests', `${X}_${R}`), freq(X, R))));
  await assertFails(getDoc(doc(db(X), 'blocks', `${R}_${X}`)));
  await assertFails(setDoc(doc(db(X), 'blocks', `${R}_${S}`), { blocker: R, blocked: S, blockedName: '', blockedUsername: '', createdAt: 1 }));
});
test('estado: solo los amigos lo ven, y se escribe con la hora del servidor', async () => {
  await seedProfiles();
  const fid = [R, X].sort().join('_');
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'friendships', fid), { members: [R, X].sort(), since: 1 }));
  await assertFails(setDoc(doc(db(R), 'status', R), { state: 'online', rodada: '', updatedAt: past(1) }));
  await assertSucceeds(setDoc(doc(db(R), 'status', R), { state: 'online', rodada: '', updatedAt: serverTimestamp() }));
  await assertSucceeds(getDoc(doc(db(X), 'status', R)));
  await assertFails(getDoc(doc(db(S), 'status', R)));
  await assertFails(setDoc(doc(db(X), 'status', R), { state: 'online', rodada: '', updatedAt: serverTimestamp() }));
});

// ───────── Pedidos con hora y reseñas (fase 3) ─────────
const review = (uid, serviceId, extra = {}) => ({ kind: 'order', serviceId, targetType: 'venue', targetId: 'v1', authorId: uid, authorName: 'Rider', stars: 4, comment: 'Muy rico', createdAt: 1, ...extra });
const rateOrder = (uid, serviceId, rating, reviews, extra = {}) => { const d = db(uid); const b = writeBatch(d);
  b.set(doc(d, 'reviews', `order_${serviceId}`), review(uid, serviceId, extra));
  b.update(doc(d, 'venues', 'v1'), { rating, reviews, lastReview: `order_${serviceId}` }); return b.commit(); };

test('pedidos: la hora a la que se quiere listo debe estar entre ahora y 12 horas', async () => {
  await assertFails(stamped(R, 'order', (b, d) => b.set(doc(d, 'orders', 'h1'), order(R, S, { readyAt: Date.now() + 2 * 86400000 }))));
  await assertSucceeds(stamped(R, 'order', (b, d) => b.set(doc(d, 'orders', 'h2'), order(R, S, { readyAt: Date.now() + 45 * 60000 }))));
});
test('reseñas: solo quien recibió el pedido, una vez, y el promedio se calcula bien', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'orders', 'od'), order(R, S, { status: 'done' }));
    await setDoc(doc(c.firestore(), 'orders', 'op'), order(R, S, { status: 'prep' }));
  });
  await assertFails(rateOrder(R, 'op', 4, 1));                                                    // aún no se entrega
  await assertFails(rateOrder(X, 'od', 4, 1, { authorId: X }));                                    // no es su pedido
  await assertFails(rateOrder(R, 'od', 5, 1));                                                    // promedio mal calculado
  await assertFails(updateDoc(doc(db(R), 'venues', 'v1'), { rating: 1.0, reviews: 1, lastReview: 'nada' }));
  await assertSucceeds(rateOrder(R, 'od', 4, 1));
  await assertFails(setDoc(doc(db(R), 'reviews', 'order_od'), review(R, 'od')));                 // una sola vez
  await assertSucceeds(getDoc(doc(db(X), 'reviews', 'order_od')));                                // son públicas
  await assertFails(deleteDoc(doc(db(X), 'reviews', 'order_od')));
  await assertSucceeds(deleteDoc(doc(db(R), 'reviews', 'order_od')));                             // su autor la borra
});

// ───────── Taller ↔ rider (fase 4) ─────────
test('S.O.S.: el taller muestra dónde va solo mientras va en camino, y la borra al llegar', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'venues', 'tv'), venue(T, { types: ['moto'] }));
    await setDoc(doc(c.firestore(), 'sos', 's1'), sos(R, { status: 'ok', providerId: T, providerName: 'Taller', providerVenueId: 'tv' }));
  });
  const loc = () => ({ providerLat: 3.46, providerLng: -76.52, providerAt: serverTimestamp() });
  await assertFails(updateDoc(doc(db(R), 'sos', 's1'), loc()));                                  // el rider no la escribe
  await assertFails(updateDoc(doc(db(T2), 'sos', 's1'), loc()));                                 // ni otro taller
  await assertSucceeds(updateDoc(doc(db(T), 'sos', 's1'), loc()));
  await assertFails(updateDoc(doc(db(T), 'sos', 's1'), loc()));                                  // menos de 8 s después
  await assertSucceeds(updateDoc(doc(db(T), 'sos', 's1'), { status: 'go' }));
  await assertFails(updateDoc(doc(db(T), 'sos', 's1'), { status: 'site' }));                     // al llegar debe borrar su ubicación
  await assertSucceeds(updateDoc(doc(db(T), 'sos', 's1'), { status: 'site', providerLat: 0, providerLng: 0 }));
  await assertFails(updateDoc(doc(db(T), 'sos', 's1'), { providerLat: 3.4, providerLng: -76.5, providerAt: serverTimestamp() }));   // ya está atendiendo
});
test('reseñas: el rider califica al taller que lo atendió, una vez y con el S.O.S. resuelto', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'venues', 'tv'), venue(T, { types: ['moto'] }));
    await setDoc(doc(c.firestore(), 'sos', 'sf'), sos(R, { status: 'fin', providerId: T, providerVenueId: 'tv', lat: 0, lng: 0 }));
    await setDoc(doc(c.firestore(), 'sos', 'sg'), sos(R, { status: 'go', providerId: T, providerVenueId: 'tv' }));
  });
  const rateSos = (uid, sid) => { const d = db(uid); const b = writeBatch(d);
    b.set(doc(d, 'reviews', `sos_${sid}`), { kind: 'sos', serviceId: sid, targetType: 'venue', targetId: 'tv', authorId: uid, authorName: 'Rider', stars: 5, comment: '', createdAt: 1 });
    b.update(doc(d, 'venues', 'tv'), { rating: 5, reviews: 1, lastReview: `sos_${sid}` }); return b.commit(); };
  await assertFails(rateSos(R, 'sg'));                                                           // todavía no se resuelve
  await assertFails(rateSos(X, 'sf'));                                                           // no era su S.O.S.
  await assertSucceeds(rateSos(R, 'sf'));
});

// ───────── Renta segura (fase 5) ─────────
const acta = (kind, extra = {}) => ({ reqId: 'q1', kind, ownerId: O, riderId: R, bikeId: 'b1', bikeName: 'XRE 300', km: 12000, fuel: 4,
  condition: 'Rayón pequeño en el tanque', items: ['Llaves', 'Casco'], licenseChecked: kind === 'entrega', testRide: true, damages: '', damageAmount: 0,
  confirmed: false, createdAt: serverTimestamp(), confirmedAt: null, ...extra });
const confirm = (uid, kind, reqChange) => { const d = db(uid); const b = writeBatch(d);
  b.update(doc(d, 'actas', `q1_${kind}`), { confirmed: true, confirmedAt: serverTimestamp() });
  b.update(doc(d, 'requests', 'q1'), reqChange); return b.commit(); };

test('perfil de conductor: privado, solo de riders y con datos válidos', async () => {
  await assertSucceeds(getDoc(doc(db(R), 'rider_profiles', R)));
  await assertFails(getDoc(doc(db(X), 'rider_profiles', R)));                                    // ni otro rider
  await assertFails(getDoc(doc(db(O), 'rider_profiles', R)));                                    // ni un dueño: lo ve en la solicitud
  await assertFails(setDoc(doc(db(O), 'rider_profiles', O), riderProfile()));                    // un dueño no tiene perfil de conductor
  await assertFails(setDoc(doc(db(R), 'rider_profiles', R), riderProfile({ licenseCat: 'B1' })));
  await assertFails(setDoc(doc(db(R), 'rider_profiles', R), riderProfile({ since: 2999 })));
  await assertFails(setDoc(doc(db(R), 'rider_profiles', R), riderProfile({ licenseNumber: '123' })));   // sin número de licencia
  await assertSucceeds(setDoc(doc(db(R), 'rider_profiles', R), riderProfile({ emergencyName: 'Mamá', emergencyPhone: '300 123 4567' })));
});
test('renta: copia el perfil, cumple la experiencia y la categoría, y acepta el acuerdo de daños publicado', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'bikes', 'b3'), bike(O, { minYears: 20, deposit: 500000, damageCap: 2000000 }));
    await setDoc(doc(c.firestore(), 'rider_profiles', X), riderProfile({ licenseCat: 'A1', licenseUntil: '2030-01-10' }));
  });
  const ok = { itemId: 'b3', deposit: 500000, damageCap: 2000000 };
  await assertFails(newReq(R, 'r1', rent(R, ok)));                                                // pide 20 años; maneja desde 2015
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), 'bikes', 'b3'), { minYears: 5 }));
  await assertFails(newReq(R, 'r2', rent(R, { ...ok, deposit: 0 })));                             // no es el depósito publicado
  await assertFails(newReq(R, 'r3', rent(R, { ...ok, damageAccepted: false })));
  await assertFails(newReq(R, 'r4', rent(R, { ...ok, riderSince: 1990 })));                       // no coincide con su perfil
  await assertFails(newReq(R, 'r5', rent(R, { ...ok, ownerCancel: 'licencia' })));
  await assertFails(newReq(X, 'r6', rent(X, { ...ok, licenseCat: 'A1', licenseUntil: '2030-01-10', dateTo: '2030-01-10' })));   // A1 en una moto de 300 c. c.
  await assertSucceeds(newReq(R, 'r7', rent(R, ok)));
});
test('renta: sin perfil de conductor, o con la licencia vencida antes de devolver, no se puede pedir', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await deleteDoc(doc(c.firestore(), 'rider_profiles', R));
    await setDoc(doc(c.firestore(), 'bikes', 'b4'), bike(O, { cc: 110 }));
    await setDoc(doc(c.firestore(), 'rider_profiles', X), riderProfile({ licenseCat: 'A1', licenseUntil: '2030-01-10' }));
  });
  await assertFails(newReq(R, 'r1', rent(R)));
  await assertFails(newReq(X, 'r2', rent(X, { itemId: 'b4', licenseCat: 'A1', licenseUntil: '2030-01-10' })));            // vence antes del 11
  await assertSucceeds(newReq(X, 'r3', rent(X, { itemId: 'b4', licenseCat: 'A1', licenseUntil: '2030-01-10', dateTo: '2030-01-10' })));
});
test('actas: el dueño las llena, el rider las confirma, y sin acta confirmada la renta no empieza ni termina', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'requests', 'q1'),
    rent(R, { status: 'ok', damageCap: 1000000, emergencyName: 'Mamá', emergencyPhone: '3001234567' })));
  await assertFails(setDoc(doc(db(R), 'actas', 'q1_entrega'), acta('entrega', { ownerId: R })));          // el rider no la llena
  await assertFails(setDoc(doc(db(O), 'actas', 'q1_entrega'), acta('entrega', { licenseChecked: false })));   // sin revisar la licencia
  await assertFails(setDoc(doc(db(O), 'actas', 'q1_devolucion'), acta('devolucion')));                   // todavía no la entrega
  await assertSucceeds(setDoc(doc(db(O), 'actas', 'q1_entrega'), acta('entrega')));
  await assertFails(updateDoc(doc(db(O), 'actas', 'q1_entrega'), { km: 1 }));                            // no se edita…
  await assertSucceeds(deleteDoc(doc(db(O), 'actas', 'q1_entrega')));                                    // …se borra y se vuelve a llenar
  await assertSucceeds(setDoc(doc(db(O), 'actas', 'q1_entrega'), acta('entrega')));
  await assertFails(getDoc(doc(db(X), 'actas', 'q1_entrega')));
  await assertFails(confirm(X, 'entrega', { status: 'ride' }));
  await assertSucceeds(confirm(R, 'entrega', { status: 'ride' }));
  await assertFails(deleteDoc(doc(db(O), 'actas', 'q1_entrega')));                                       // confirmada: ya no se borra
  await assertFails(updateDoc(doc(db(R), 'actas', 'q1_entrega'), { confirmed: false }));
  await assertFails(updateDoc(doc(db(O), 'requests', 'q1'), { status: 'fin', emergencyName: '', emergencyPhone: '' }));   // sin acta de devolución
  await assertFails(setDoc(doc(db(O), 'actas', 'q1_devolucion'), acta('devolucion', { damages: 'Espejo roto', damageAmount: 2000000 })));   // pasa el tope
  await assertSucceeds(setDoc(doc(db(O), 'actas', 'q1_devolucion'), acta('devolucion', { damages: 'Espejo roto', damageAmount: 300000 })));
  await assertFails(confirm(R, 'devolucion', { status: 'fin' }));                                         // debe borrar el contacto de emergencia
  await assertSucceeds(confirm(R, 'devolucion', { status: 'fin', emergencyName: '', emergencyPhone: '' }));
});
test('renta: el dueño cancela en la entrega con un motivo, y se borra el contacto de emergencia', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'requests', 'q2'), rent(R, { status: 'ok', emergencyName: 'Mamá', emergencyPhone: '3001234567' })));
  await assertFails(updateDoc(doc(db(O), 'requests', 'q2'), { status: 'no', ownerCancel: 'licencia' }));
  await assertFails(updateDoc(doc(db(O), 'requests', 'q2'), { status: 'no', ownerCancel: 'me caes mal', emergencyName: '', emergencyPhone: '' }));
  await assertFails(updateDoc(doc(db(R), 'requests', 'q2'), { status: 'no', ownerCancel: 'licencia', emergencyName: '', emergencyPhone: '' }));
  await assertSucceeds(updateDoc(doc(db(O), 'requests', 'q2'), { status: 'no', ownerCancel: 'licencia', emergencyName: '', emergencyPhone: '' }));
});
test('reseñas mutuas: el rider califica la moto y el dueño al rider; las de riders solo las ven los dueños y él', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'requests', 'q1'), rent(R, { status: 'fin' })));
  const rateBike = (uid, rating, reviews) => { const d = db(uid); const b = writeBatch(d);
    b.set(doc(d, 'reviews', 'rent_q1'), review(uid, 'q1', { kind: 'rent', targetType: 'bike', targetId: 'b1', stars: 4 }));
    b.update(doc(d, 'bikes', 'b1'), { rating, reviews, lastReview: 'rent_q1' }); return b.commit(); };
  await assertFails(rateBike(X, 4, 1));                                                            // no la rentó
  await assertFails(rateBike(R, 5, 1));                                                            // promedio mal calculado
  await assertSucceeds(rateBike(R, 4, 1));
  await assertFails(updateDoc(doc(db(O), 'bikes', 'b1'), { reviews: 99 }));                         // el dueño no toca su calificación
  const renter = (uid, extra = {}) => review(uid, 'q1', { kind: 'renter', targetType: 'rider', targetId: R, authorName: 'Dueño', stars: 5, comment: 'Cuidó la moto', ...extra });
  await assertFails(setDoc(doc(db(X), 'reviews', 'renter_q1'), renter(X)));
  await assertFails(setDoc(doc(db(O), 'reviews', 'renter_q1'), renter(O, { targetId: X })));
  await assertSucceeds(setDoc(doc(db(O), 'reviews', 'renter_q1'), renter(O)));
  await assertSucceeds(getDoc(doc(db(R), 'reviews', 'renter_q1')));                                 // el rider ve lo que dicen de él
  await assertFails(getDoc(doc(db(X), 'reviews', 'renter_q1')));                                    // otro rider no
});
test('S.O.S. en una moto rentada: solo con una renta en curso, y el dueño de la moto lo ve', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'requests', 'q1'), rent(R, { status: 'ride' }));
    await setDoc(doc(c.firestore(), 'requests', 'q2'), rent(R, { status: 'ok' }));
  });
  const send = (id, extra) => stamped(R, 'sos', (b, d) => b.set(doc(d, 'sos', id), sos(R, extra)));
  await assertFails(send('s1', { rentalReq: 'q2', rentalOwnerId: O }));                           // la renta no ha empezado
  await assertFails(send('s2', { rentalReq: 'q1', rentalOwnerId: X }));                           // ese no es el dueño
  await assertSucceeds(send('s3', { rentalReq: 'q1', rentalOwnerId: O }));
  await assertSucceeds(getDoc(doc(db(O), 'sos', 's3')));
  await assertSucceeds(getDocs(query(collection(db(O), 'sos'), where('rentalOwnerId', '==', O))));
  await assertFails(getDoc(doc(db(X), 'sos', 's3')));
  await assertFails(updateDoc(doc(db(O), 'sos', 's3'), { rentalOwnerId: '' }));                    // el dueño solo lo ve
});
