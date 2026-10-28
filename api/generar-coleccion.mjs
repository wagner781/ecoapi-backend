import { writeFileSync } from 'node:fs';
const items = [
  ['Login operario', 'POST', '/auth/login', {usuario:'operario1',password:'Eco2026*'}, false],
  ['Login supervisor', 'POST', '/auth/login', {usuario:'supervisor1',password:'Eco2026*'}, false],
  ['401 sin token', 'GET', '/me', undefined, false],
  ['Perfil', 'GET', '/me'], ['Ruta del día', 'GET', '/rutas/dia'],
  ['Puntos paginados', 'GET', '/puntos?pagina=0&tamanio=20&zona=Norte'],
  ['Pesajes', 'GET', '/puntos/{{puntoId}}/pesajes'],
  ['Registrar', 'POST', '/pesajes', {rutaId:1,puntoId:1,material:'Papel',pesoBruto:20,tara:2,claveCliente:'{{$guid}}'}],
  ['400 validación', 'POST', '/pesajes', {rutaId:1,puntoId:1,material:'Papel',pesoBruto:2,tara:3,claveCliente:'{{$guid}}'}],
  ['Corregir', 'PUT', '/pesajes/{{pesajeId}}', {rutaId:1,puntoId:1,material:'Papel',pesoBruto:25,tara:2,claveCliente:'edicion'}],
  ['403 anular como operario', 'DELETE', '/pesajes/{{pesajeId}}', {motivo:'Duplicado'}],
  ['Cerrar ruta como supervisor', 'POST', '/rutas/{{rutaId}}/cierre'],
  ['Omitir punto', 'PUT', '/rutas/{{rutaId}}/puntos/{{puntoId}}/omision', {motivo:'Empresa cerrada'}]
];
const collection = {
  info:{name:'EcoAPI - contrato y errores',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
  variable:[{key:'baseUrl',value:'http://localhost:8080/api/v1'},{key:'token',value:''},{key:'rutaId',value:'1'},{key:'puntoId',value:'1'},{key:'pesajeId',value:'1'}],
  item:items.map(([name,method,path,body,auth=true]) => ({name,request:{method,header:[{key:'Content-Type',value:'application/json'},...(auth?[{key:'Authorization',value:'Bearer {{token}}'}]:[])],url:'{{baseUrl}}'+path,...(body?{body:{mode:'raw',raw:JSON.stringify(body,null,2)}}:{})},...(path==='/auth/login'?{event:[{listen:'test',script:{type:'text/javascript',exec:["pm.test('Login correcto', () => pm.response.to.have.status(200));","if (pm.response.code === 200) pm.collectionVariables.set('token', pm.response.json().token);"]}}]}:{})}))
};
writeFileSync(new URL('./EcoAPI.postman_collection.json',import.meta.url),JSON.stringify(collection,null,2)+'\n');
