package pe.upeu.ecorecicla.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.LocalDateTime

data class ErrorRespuesta(val timestamp: String = LocalDateTime.now().toString(), val status: Int, val error: String, val message: String, val path: String, val validationErrors: Map<String, String> = emptyMap())
class Fallo(val status: Int, override val message: String, val campos: Map<String, String> = emptyMap()) : RuntimeException(message)

@RestControllerAdvice
class ManejadorDeErrores {
    @ExceptionHandler(Exception::class)
    fun manejar(e: Exception, request: HttpServletRequest): ResponseEntity<ErrorRespuesta> {
        val (status, message, campos) = when (e) {
            is Fallo -> Triple(e.status, e.message, e.campos)
            is AccessDeniedException -> Triple(403, "No posee el privilegio requerido", emptyMap())
            is MethodArgumentNotValidException -> Triple(400, "Datos invalidos", e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalido") })
            is HttpMessageNotReadableException, is MethodArgumentTypeMismatchException -> Triple(400, "Formato de peticion invalido", emptyMap())
            else -> Triple(500, "Error interno del servicio", emptyMap())
        }
        return ResponseEntity.status(status).body(ErrorRespuesta(status = status, error = HttpStatus.valueOf(status).reasonPhrase, message = message, path = request.requestURI, validationErrors = campos))
    }
}
