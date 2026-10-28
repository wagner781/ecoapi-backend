# EcoAPI

Servicio Kotlin/JVM con Spring Boot 3.5.7 y Java 21. PostgreSQL es la base relacional del servidor.

Compilar: `./gradlew build` (Windows: `./gradlew.bat build`).

El desarrollo comienza en esta sesión; no se acredita trabajo de fechas anteriores.

## Ejecutar

Con Docker Desktop activo: `docker compose up --build -d`. Levanta PostgreSQL 17 (puerto 5433) y API (8080), con volumen persistente. Para detener: `docker compose down` (conserva los datos).

Alternativa de desarrollo: `docker compose up -d db` y `./gradlew bootRun`. En Windows usa `gradlew.bat`. Variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `PORT`; las claves predeterminadas son exclusivamente de demostración. No publiques un `.env` ni uses estas claves en producción.

Usuarios semilla: `operario1`, `supervisor1`, `admin`; contraseña `Eco2026*`. Al iniciar se crean 12 puntos en tres zonas, ruta abierta del día con seis puntos, ruta anterior cerrada y 15 pesajes (uno ajeno y uno anulado). Se agrega una ruta vacía del supervisor para su pantalla. Las fechas proceden del reloj real en America/Lima y no se reescriben rutas existentes. Si reinicias al día siguiente aparecen las nuevas rutas de demostración.

## Contrato `/api/v1`

| Verbo y ruta | Privilegio | Éxito |
|---|---|---|
| POST `/auth/login` | Público | 200 sesión |
| GET `/me` | Autenticado | 200 perfil |
| GET `/rutas/dia?fecha=YYYY-MM-DD` | PUNTO_LEER | 200 ruta asignada y puntos ordenados |
| GET `/puntos?zona=Norte&pagina=0&tamanio=20` | PUNTO_LEER | 200 página |
| GET `/puntos/{id}/pesajes?pagina=0&tamanio=20` | PESAJE_LEER | 200 página, reciente primero |
| POST `/pesajes` | PESAJE_CREAR | 201 pesaje + Location |
| PUT `/pesajes/{id}` | PESAJE_EDITAR | 200 pesaje corregido |
| DELETE `/pesajes/{id}` | PESAJE_ANULAR | 204, anulación lógica |
| PUT `/rutas/{id}/puntos/{puntoId}/omision` | PESAJE_CREAR | 204 |
| POST `/rutas/{id}/cierre` | RUTA_CERRAR | 200 ruta cerrada |
| POST `/puntos` | PUNTO_GESTIONAR | 201 punto |
| POST `/rutas` | PUNTO_GESTIONAR | 201 ruta asignada |

Login: `{"usuario":"operario1","password":"Eco2026*"}`. Pesaje: `{"rutaId":1,"puntoId":1,"material":"Papel","pesoBruto":20,"tara":2,"observacion":null,"claveCliente":"uuid-estable"}`. No se envía `pesoNeto`. Anular y omitir reciben `{"motivo":"Empresa cerrada"}`. Crear punto recibe razón social, dirección, zona y material habitual. Asignar ruta recibe `usuarioId`, fecha ISO y `puntoIds` en orden de visita.

Los listados usan `{contenido,pagina,tamanio,totalElementos,totalPaginas,ultima}`. La ruta del día es un recurso compuesto singular: su lista ordenada de puntos no es un listado paginado. Todos los errores usan `{timestamp,status,error,message,path,validationErrors}`. 400 indica campo inválido; 401 exige login; 403 mantiene la sesión; 404 recurso inexistente; 409 regla incumplida; 500 fallo interno sin exponer detalles. Tamaño permitido 1–100, página desde cero.

## Roles

Operario: PUNTO_LEER, PESAJE_LEER, PESAJE_CREAR y PESAJE_EDITAR (solo propios del día). Supervisor: los anteriores más PESAJE_ANULAR y RUTA_CERRAR. Administrador: PUNTO_LEER, PESAJE_LEER, PUNTO_GESTIONAR; no registra ni edita pesajes. El alcance administrativo obligatorio del contrato es crear puntos y asignar rutas; no se añade una pantalla de administración de usuarios no definida por sus endpoints.

## Verificar y defender

`./gradlew build` ejecuta pruebas y genera el JAR. Con la API activa, `npm run test:api` verifica HTTP y PostgreSQL reales; añade un pesaje de prueba y luego lo anula, conservando la trazabilidad. `api/EcoAPI.postman_collection.json` contiene peticiones para importar en Postman.

Paquetes: `config` (JWT, seguridad, errores, semillas), `seguridad` (usuario y matriz), `dominio` (entidades), `repositorio` (JDBC), `servicio` (RN y transacciones), `web/dto` (contrato). Consulta [la guía de defensa](docs/DEFENSA.md).

Git: bootstrap compilable en main; funcionalidades desde develop y PR con merge commit, sin squash. El bootstrap es la única excepción inicial necesaria para crear las ramas. No se fabrica desarrollo en fechas anteriores ni una rama de solicitud de cambio sin conocer la asignada. Revisa `git log --graph --oneline --all`.
