import assert from 'node:assert/strict';
import { randomUUID, createHmac } from 'node:crypto';
import { writeFileSync } from 'node:fs';

const base = process.env.API_URL ?? 'http://localhost:8080/api/v1';
const resultados = [];
const exportadas = [];
const nombresToken = new Map();
let creacionExportada=false;
async function peticion(method, path, token, body, esperado = 200) {
  const response = await fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  const data = response.status === 204 ? null : await response.json();
  assert.equal(response.status, esperado, `${method} ${path}: ${JSON.stringify(data)}`);
  resultados.push(`${method} ${path}: ${response.status}`);
  const cabeceras=[{key:'Content-Type',value:'application/json'}];
  if(token) cabeceras.push({key:'Authorization',value:nombresToken.has(token)?`Bearer {{${nombresToken.get(token)}}}`:'Bearer token-invalido'});
  const cuerpoExportado=body && path==='/pesajes'?{...body,claveCliente:esperado===201?'{{clavePesaje}}':'{{$guid}}'}:body;
  const raw=cuerpoExportado===undefined?undefined:JSON.stringify(cuerpoExportado,null,2).replace(/"rutaId":\s*\d+/g,'"rutaId": {{rutaId}}').replace(/"puntoId":\s*\d+/g,'"puntoId": {{puntoId}}');
  const rutaExportada=path.replace(/^\/pesajes\/\d+$/,'/pesajes/{{pesajeId}}').replace(/^\/rutas\/\d+\/cierre$/,'/rutas/{{rutaId}}/cierre');
  const request={method,header:cabeceras,url:'{{baseUrl}}'+rutaExportada,...(raw===undefined?{}:{body:{mode:'raw',raw}})};
  const eventos=[{listen:'test',script:{type:'text/javascript',exec:[`pm.test('HTTP ${esperado}', () => pm.response.to.have.status(${esperado}));`]}}];
  if(path==='/auth/login' && esperado===200) eventos[0].script.exec.push(`pm.collectionVariables.set('token_${body.usuario}', pm.response.json().token);`);
  if(path==='/rutas/dia' && esperado===200) eventos[0].script.exec.push("pm.collectionVariables.set('rutaId', pm.response.json().id); pm.collectionVariables.set('puntoId', pm.response.json().puntos[0].id);");
  if(path==='/pesajes' && esperado===201) {
    eventos[0].script.exec.push("pm.collectionVariables.set('pesajeId', pm.response.json().id);");
    if(!creacionExportada) eventos.unshift({listen:'prerequest',script:{type:'text/javascript',exec:["pm.collectionVariables.set('clavePesaje', pm.variables.replaceIn('{{$guid}}'));"]}});
    creacionExportada=true;
  }
  exportadas.push({name:`${response.status} ${method} ${path}`,request,event:eventos,response:[{name:'Respuesta real observada',originalRequest:request,code:response.status,body:JSON.stringify(path==='/auth/login' && data?.token?{...data,token:'[oculto]'}:data,null,2)}]});
  return data;
}
const login = async usuario => {
  const token=(await peticion('POST','/auth/login',null,{usuario,password:'Eco2026*'})).token;
  nombresToken.set(token,'token_'+usuario); return token;
};
await peticion('GET', '/me', null, undefined, 401);
await peticion('POST', '/auth/login', null, { usuario: 'operario1', password: 'incorrecta' }, 401);
const operario = await login('operario1'), supervisor = await login('supervisor1'), admin = await login('admin');
const auditor = await login('auditor1');
const perfilAuditor=await peticion('GET','/me',auditor);
assert.deepEqual([...perfilAuditor.privilegios].sort(),['PESAJE_LEER','PUNTO_LEER']);
await peticion('GET','/puntos',auditor);
await peticion('GET','/puntos/1/pesajes',auditor);
await peticion('POST','/rutas/1/cierre',auditor,undefined,403);
await peticion('DELETE','/pesajes/1',auditor,{motivo:'Prueba auditor'},403);
await peticion('GET','/usuarios',auditor,undefined,403);
await peticion('GET', '/me', operario + 'alterado', undefined, 401);
const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
const claimsActuales = JSON.parse(Buffer.from(operario.split('.')[1], 'base64url').toString());
const payload = Buffer.from(JSON.stringify({ ...claimsActuales, iat: Math.floor(Date.now() / 1000) - 7200, exp: Math.floor(Date.now() / 1000) - 3600 })).toString('base64url');
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
await peticion('POST', '/pesajes', auditor, body, 403);
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
await peticion('GET', '/ruta-inexistente', operario, undefined, 404);
await peticion('GET', '/usuarios', admin);
await peticion('GET', '/roles', admin);
await peticion('GET', '/privilegios', admin);
await peticion('GET', '/usuarios', supervisor, undefined, 403);
await peticion('GET', '/rutas', supervisor);
writeFileSync(new URL('../api/EcoAPI.postman_collection.json',import.meta.url),JSON.stringify({
  info:{name:'EcoAPI - exportacion de peticiones ejecutadas',description:'Exportada por el cliente Node.js tras verificar las respuestas reales. Los tokens se obtienen con login; no se guardan credenciales de sesion.',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
  variable:[{key:'baseUrl',value:base},{key:'rutaId',value:'1'},{key:'puntoId',value:'1'},{key:'pesajeId',value:''},{key:'clavePesaje',value:''},...['operario1','supervisor1','admin','auditor1'].map(u=>({key:'token_'+u,value:''}))],item:exportadas
},null,2)+'\n');
console.log(resultados.join('\n'));
console.log(`OK: ${resultados.length} peticiones verificadas contra ${base}. No se imprimen tokens.`);
