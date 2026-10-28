import assert from 'node:assert/strict';
import { randomUUID, createHmac } from 'node:crypto';

const base = process.env.API_URL ?? 'http://localhost:8080/api/v1';
const resultados = [];
async function peticion(method, path, token, body, esperado = 200) {
  const response = await fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  const data = response.status === 204 ? null : await response.json();
  assert.equal(response.status, esperado, `${method} ${path}: ${JSON.stringify(data)}`);
  resultados.push(`${method} ${path}: ${response.status}`);
  return data;
}
const login = async usuario => (await peticion('POST', '/auth/login', null, { usuario, password: 'Eco2026*' })).token;
await peticion('GET', '/me', null, undefined, 401);
await peticion('POST', '/auth/login', null, { usuario: 'operario1', password: 'incorrecta' }, 401);
const operario = await login('operario1'), supervisor = await login('supervisor1'), admin = await login('admin');
await peticion('GET', '/me', operario + 'alterado', undefined, 401);
const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
const payload = Buffer.from(JSON.stringify({ iss: 'ecoapi', sub: '1', exp: Math.floor(Date.now() / 1000) - 3600 })).toString('base64url');
const firma = createHmac('sha256', process.env.JWT_SECRET ?? 'solo-desarrollo-cambiar-esta-clave-de-32-bytes-minimo').update(`${header}.${payload}`).digest('base64url');
await peticion('GET', '/me', `${header}.${payload}.${firma}`, undefined, 401);
const ruta = await peticion('GET', '/rutas/dia', operario);
assert.equal(ruta.cerrada, false);
const pagina = await peticion('GET', '/puntos?zona=Norte&pagina=0&tamanio=2', operario);
assert.equal(pagina.contenido.length, 2); assert.equal(pagina.totalElementos, 4);
await peticion('POST', `/rutas/${ruta.id}/cierre`, operario, undefined, 403);
await peticion('POST', `/rutas/${ruta.id}/cierre`, supervisor, undefined, 409);
const body = { rutaId: ruta.id, puntoId: ruta.puntos[0].id, material: 'Papel', pesoBruto: 20, tara: 2, observacion: 'Prueba HTTP real', claveCliente: randomUUID() };
await peticion('POST', '/pesajes', admin, body, 403);
const invalido = await peticion('POST', '/pesajes', operario, { ...body, tara: 21 }, 400);
assert.ok(invalido.validationErrors.tara);
const creado = await peticion('POST', '/pesajes', operario, body, 201);
assert.equal(creado.pesoNeto, 18);
const repetido = await peticion('POST', '/pesajes', operario, body, 201);
assert.equal(repetido.id, creado.id);
const editado = await peticion('PUT', `/pesajes/${creado.id}`, operario, { ...body, pesoBruto: 24 });
assert.equal(editado.pesoNeto, 22);
await peticion('DELETE', `/pesajes/${creado.id}`, operario, { motivo: 'Prueba' }, 403);
await peticion('DELETE', `/pesajes/${creado.id}`, supervisor, { motivo: 'Finaliza prueba HTTP' }, 204);
const listado = await peticion('GET', `/puntos/${body.puntoId}/pesajes?tamanio=100`, operario);
assert.equal(listado.contenido.find(p => p.id === creado.id).anulado, true);
await peticion('GET', '/puntos/9999999/pesajes', operario, undefined, 404);
console.log(resultados.join('\n'));
console.log(`OK: ${resultados.length} peticiones verificadas contra ${base}. No se imprimen tokens.`);
