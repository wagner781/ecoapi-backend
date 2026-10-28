# Guía para explicar el servicio

## Recorrido de un registro

`EcoController.crear` recibe y valida el DTO. Spring Security valida primero la firma y vigencia del Bearer JWT y `@PreAuthorize` exige `PESAJE_CREAR`. `EcoService.crear` abre una transacción, bloquea la ruta con `FOR UPDATE`, verifica asignación y punto planificado, y usa `ReglasPesaje.calcularNeto` para validar y restar con BigDecimal. `EcoRepository.crear` solo persiste el neto recibido del servicio mediante parámetros JDBC. PostgreSQL asigna el ID positivo; se devuelve 201 con Location.

El bloqueo de ruta también se toma al cerrar, corregir y anular: evita que una petición cierre mientras otra registra. No basta un botón oculto: prueba `DELETE /pesajes/{id}` con token de operario y verás 403.

## Seguridad

`Semillas` cifra `Eco2026*` con BCrypt; nunca se compara una contraseña en claro contra la columna. `JwtService` firma HS256 con secreto configurable, emisor `ecoapi`, `sub` ID de usuario, username, privilegios, emisión y expiración de una hora. El recurso usa NimbusJwtDecoder y el filtro Bearer oficial de Spring Security; no se escribe un filtro criptográfico casero. Token ausente, modificado o vencido: 401. JWT válido sin privilegio: 403.

`Rol` declara la matriz en configuración; se copia a `rol_privilegio`. Los controladores no comparan nombres de rol. La capacidad de editar cualquier pesaje corresponde al privilegio `PESAJE_ANULAR` de la matriz del supervisor. El servicio usa esa capacidad para distinguir RN-02. Si se requiere separar ambas capacidades en el futuro, se agrega un privilegio específico de alcance global.

No hay renovación. El JWT incluye una versión de sesión que se contrasta con la cuenta activa en la base: cambiar contraseña, estado, rol o privilegios invalida los tokens anteriores. La respuesta de login incluye expiresAt, idéntico al claim exp. Consultar perfil no prolonga la sesión. En producción se exige HTTPS y un JWT_SECRET privado de al menos 32 bytes aleatorios. La clave de ejemplo y las credenciales semilla sirven para desarrollo.

## Reglas de negocio

RN-01 devuelve 409 antes de escribir sobre ruta cerrada. RN-02 usa propietario y fecha del registro en America/Lima para operarios; 403 si falla. RN-03 rechaza neto no positivo con `validationErrors.tara`; el DTO no admite neto y Jackson rechaza campos desconocidos. RN-04 cuenta puntos sin pesaje activo ni omisión y devuelve la cantidad faltante con 409. La anulación es lógica, conserva peso, autor y motivo y deja de contar como atención.

## Offline y reintentos

El servidor recibe `claveCliente`, estable por operación de creación. La combinación `(operario_id, clave_cliente)` es única. Si el POST se guardó y la respuesta se perdió, el reintento devuelve el registro original. El cliente sustituye ID negativo por positivo en transacción local. Un reintento de la misma clave no representa una operación nueva, incluso si posteriormente se cerró la ruta.

## Pruebas y límites

Gradle ejecuta pruebas integradas Spring/MockMvc con H2 en modo PostgreSQL y rollback por caso. No sustituyen verificar el dialecto real: `npm run test:api` hace peticiones HTTP al servidor conectado a PostgreSQL y comprueba login, token alterado/vencido, 400, 401, 403, 404, 409, CRUD e idempotencia. Los tests usan datos de prueba; la app no contiene listas incrustadas.

La Parte I incorpora administración de usuarios, roles, padrón y asignaciones. Todo depende de privilegios, no del nombre del rol. Desactivar una cuenta conserva su historial; eliminar puntos/roles con referencias devuelve 409. La matriz de privilegios inicial es la del PDF. El segundo operario permite demostrar una corrección ajena con el mismo rol.

No se inventan capturas, fechas de sesiones ni conflictos. El historial comenzó el día real de creación; no acredita sesiones anteriores de la unidad. Las etiquetas representan entregas técnicas, no una evaluación realizada por el docente. Las solicitudes SC se documentan en sus propias ramas cuando se implementan, distinguiendo trabajo solicitado por el usuario de una sesión de examen.
## SC-B: auditor

`Rol.AUDITOR` configura exactamente PUNTO_LEER y PESAJE_LEER. La semilla `auditor1 / Eco2026*` se crea al iniciar; no tiene ruta asignada ni privilegios de escritura. Controladores y filtro siguen autorizando por privilegios, sin comparar nombres de rol. AuditorTest verifica lectura y rechazo de once escrituras, ademas de conservar la sesion. El cliente HTTP real exporta tambien peticiones del auditor (34 peticiones verificadas).
