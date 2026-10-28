# EcoAPI

Servicio Kotlin/JVM con Spring Boot 3.5.7 y Java 21. PostgreSQL es la base relacional del servidor.

Compilar: `./gradlew build` (Windows: `./gradlew.bat build`).

El desarrollo comienza en esta sesión; no se acredita trabajo de fechas anteriores.

## Ejecutar

Con Docker Desktop activo: `docker compose up --build -d`. Levanta PostgreSQL 17 (puerto 5433) y API (8080), con volumen persistente. Para detener: `docker compose down` (conserva los datos).

Alternativa de desarrollo: `docker compose up -d db` y `./gradlew bootRun`. En Windows usa `gradlew.bat`. Variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `PORT`; las claves predeterminadas son exclusivamente de demostración. No publiques un `.env` ni uses estas claves en producción.

Usuarios semilla: `operario1`, `supervisor1`, `admin`, `operario2` y `auditor1`; contraseña `Eco2026*`. Los tres primeros son los del anexo. El segundo operario permite demostrar RN-02 con otro usuario del mismo rol. Al iniciar se crean 12 puntos en tres zonas, ruta abierta del día con seis puntos, ruta anterior cerrada y al menos 15 pesajes (uno de otro operario y uno anulado). Hay una ruta inicial del supervisor. En una base existente se agrega la semilla ajena faltante sin modificar la autoría de registros anteriores. Las fechas proceden del reloj real en America/Lima y no se reescriben rutas existentes.

## Contrato `/api/v1`

| Verbo y ruta | Privilegio | Éxito |
|---|---|---|
| POST `/auth/login` | Público | 200 sesión |
| GET `/me` | Autenticado | 200 perfil |
| GET `/rutas/dia?fecha=YYYY-MM-DD` | PUNTO_LEER | 200 ruta asignada y puntos ordenados |
| GET `/rutas?fecha=YYYY-MM-DD&pagina=0&tamanio=20` | PUNTO_LEER | 200 página de rutas; operario solo propias |
| GET `/rutas/{id}` | PUNTO_LEER | 200; propias, o consulta global con RUTA_CERRAR/PUNTO_GESTIONAR |
| GET `/puntos?zona=Norte&pagina=0&tamanio=20` | PUNTO_LEER | 200 página |
| GET `/puntos/{id}/pesajes?pagina=0&tamanio=20` | PESAJE_LEER | 200 página, reciente primero |
| POST `/pesajes` | PESAJE_CREAR | 201 pesaje + Location |
| PUT `/pesajes/{id}` | PESAJE_EDITAR | 200 pesaje corregido |
| DELETE `/pesajes/{id}` | PESAJE_ANULAR | 204, anulación lógica |
| PUT `/rutas/{id}/puntos/{puntoId}/omision` | PESAJE_CREAR | 204 |
| POST `/rutas/{id}/cierre` | RUTA_CERRAR | 200 ruta cerrada |
| POST `/puntos` | PUNTO_GESTIONAR | 201 punto |
| PUT `/puntos/{id}` | PUNTO_GESTIONAR | 200 punto actualizado |
| DELETE `/puntos/{id}` | PUNTO_GESTIONAR | 204; 409 si conserva rutas/historial |
| POST `/rutas` | PUNTO_GESTIONAR | 201 ruta asignada |
| PUT `/rutas/{id}` | PUNTO_GESTIONAR | 200; solo planificación abierta sin registros |
| GET/POST `/usuarios` | PUNTO_GESTIONAR | 200 página / 201 cuenta |
| PUT/DELETE `/usuarios/{id}` | PUNTO_GESTIONAR | 200 modificación / 204 desactivación lógica |
| GET/POST `/roles` | PUNTO_GESTIONAR | 200 página / 201 rol |
| PUT/DELETE `/roles/{nombre}` | PUNTO_GESTIONAR | 200 modificación / 204 si no hay usuarios asignados |
| GET `/privilegios` | PUNTO_GESTIONAR | 200 página de privilegios configurados |

Login: `{"usuario":"operario1","password":"Eco2026*"}`. Pesaje: `{"rutaId":1,"puntoId":1,"material":"Papel","pesoBruto":20,"tara":2,"observacion":null,"claveCliente":"uuid-estable","humedadEstimada":12.75}`. No se envía `pesoNeto`. Anular y omitir reciben `{"motivo":"Empresa cerrada"}`. Crear punto recibe razón social, dirección, zona y material habitual. Asignar ruta recibe `usuarioId`, fecha ISO y `puntoIds` en orden de visita.

Los listados usan `{contenido,pagina,tamanio,totalElementos,totalPaginas,ultima}`. La ruta del día es un recurso compuesto singular: su lista ordenada de puntos no es un listado paginado. Todos los errores usan `{timestamp,status,error,message,path,validationErrors}`. 400 indica campo inválido; 401 exige login; 403 mantiene la sesión; 404 recurso inexistente; 409 regla incumplida; 500 fallo interno sin exponer detalles. Tamaño permitido 1–100, página desde cero.

## Roles

Operario: PUNTO_LEER, PESAJE_LEER, PESAJE_CREAR y PESAJE_EDITAR (solo propios del día). Supervisor: los anteriores más PESAJE_ANULAR y RUTA_CERRAR. Administrador: PUNTO_LEER, PESAJE_LEER, PUNTO_GESTIONAR; no crea, edita ni anula pesajes y no cierra rutas. La gestión de usuarios y roles descrita en 3.3 se implementa usando PUNTO_GESTIONAR, sin comparar nombres de rol en los controladores ni añadir privilegios de pesajes al administrador.

Usuario de gestión: `{usuario,nombre,rol,password,activo}`; password es obligatorio al crear y opcional al editar, de 8 caracteres mínimo y 72 bytes UTF-8 máximo. Rol: `{nombre,privilegios}` con identificador en mayúsculas y solo privilegios existentes. El último gestor activo no puede eliminar su acceso. Cambios de cuenta o privilegios incrementan `version_sesion` y hacen que sus tokens anteriores reciban 401. El token sigue vigente una hora y no se renueva; `expiresAt` comunica su expiración exacta en segundos Unix.

Las rutas son únicas por usuario/fecha desde el servicio. La asignación requiere un usuario activo con PESAJE_CREAR. Una planificación con pesajes u omisiones se conserva. RN-01 bloquea crear y corregir en rutas cerradas; la anulación autorizada sigue siendo lógica y no reabre la ruta.

## Verificar y defender

`./gradlew build` ejecuta pruebas y genera el JAR. Con la API activa, `npm run test:api` ejecuta 35 peticiones contra PostgreSQL real; añade un pesaje de prueba y luego lo anula. Al terminar exporta las peticiones y respuestas observadas a `api/EcoAPI.postman_collection.json`, sin tokens válidos. Es la exportación del cliente Node.js utilizado para probar, importable en Postman; las variables de login, ID y clave de creación permiten repetir el recorrido. No se presenta como una exportación manual realizada desde Postman.

Paquetes: `config` (JWT, seguridad, errores, semillas), `seguridad` (usuario y matriz), `dominio` (entidades), `repositorio` (JDBC), `servicio` (RN y transacciones), `web/dto` (contrato). Consulta [la guía de defensa](docs/DEFENSA.md).

Git: bootstrap compilable en main; funcionalidades desde develop y PR con merge commit, sin squash. El bootstrap es la única excepción inicial necesaria para crear las ramas. Las cuatro SC se implementan por solicitud del usuario para practicar, sin afirmar asignacion docente ni desarrollo en fechas anteriores. Revisa `git log --graph --oneline --all`.

SC-B agrega `auditor1 / Eco2026*` (AUDITOR), con PUNTO_LEER y PESAJE_LEER exclusivamente. Puede consultar perfil, padron y pesajes; no gestionar ni escribir. Los privilegios se configuran en Rol y se guardan por rol en PostgreSQL. La suite ahora incluye AuditorTest y la coleccion exportada contiene las pruebas reales del auditor.

SC-C agrega `humedadEstimada` nullable en peticion y respuesta, porcentaje 0..100 con hasta dos decimales. No altera pesoNeto; vacio/null significa sin estimar. El esquema se amplia sin borrar datos y las semillas incluyen valores. Pruebas: 35 casos Spring y 35 peticiones HTTP reales exportadas.
