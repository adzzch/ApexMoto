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
const rent = (fromId, extra = {}) => ({ type: 'rent', fromId, fromName: 'Rider', toId: O, itemId: 'b1', itemName: 'XRE 300', days: 2, amount: 320000, total: 320000, dateFrom: '2030-01-10', dateTo: '2030-01-11', plan: 'Básica', extras: [], delivery: false, status: 'p', rated: 0, createdAt: 1, licenseDeclared: true, licenseCat: 'A2', cancelKind: '', cancelledAt: 0, ...extra });

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
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'sos', 's1'), sos(R)));
  const accept = (who, as) => updateDoc(doc(db(who), 'sos', 's1'), { status: 'ok', providerId: as, providerName: 'Taller', eta: 30, acceptedAt: 2 });
  await assertFails(accept(X, X));
  await assertFails(accept(R, R));
  await assertFails(accept(T, T2));
  await assertSucceeds(accept(T, T));
  await assertFails(accept(T2, T2));                                                      // ya está tomado
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
test('reserva inmediata: la solicitud y las fechas apartadas van en un lote; nadie más toca las fechas', async () => {
  await assertFails(updateDoc(doc(db(R), 'bikes', 'b2'), { booked: arrayUnion('2030-01-01|2030-12-31') }));
  await assertFails(updateDoc(doc(db(R), 'bikes', 'b2'), { pricePerDay: 1 }));
  const r = rent(R, { itemId: 'b2', status: 'ok' });
  await assertSucceeds(stamped(R, 'request', (b, d) => {
    b.set(doc(d, 'requests', 'q1'), r);
    b.update(doc(d, 'bikes', 'b2'), { booked: arrayUnion('2030-01-10|2030-01-11'), lastReq: 'q1' });
  }));
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
test('solicitudes: el aliado avanza paso a paso; un tercero no las ve', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'requests', 'q1'), rent(R)));
  await assertFails(getDoc(doc(db(X), 'requests', 'q1')));
  await assertFails(updateDoc(doc(db(O), 'requests', 'q1'), { status: 'fin' }));
  await assertFails(updateDoc(doc(db(O), 'requests', 'q1'), { status: 'ok', total: 1 }));
  await assertFails(updateDoc(doc(db(R), 'requests', 'q1'), { status: 'ok' }));
  await assertSucceeds(updateDoc(doc(db(O), 'requests', 'q1'), { status: 'ok' }));
  await assertSucceeds(updateDoc(doc(db(O), 'requests', 'q1'), { status: 'ride' }));
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
