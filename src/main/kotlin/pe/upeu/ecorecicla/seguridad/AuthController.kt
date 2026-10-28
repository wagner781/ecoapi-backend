package pe.upeu.ecorecicla.seguridad

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.web.bind.annotation.*
import pe.upeu.ecorecicla.config.*

data class LoginPeticion(@field:NotBlank val usuario: String, @field:NotBlank val password: String)
data class UsuarioRespuesta(val id: Long, val usuario: String, val nombre: String, val rol: String, val privilegios: Set<String>,val activo: Boolean=true)
data class SesionRespuesta(val token: String, val usuario: String, val nombre: String, val rol: String, val privilegios: Set<String>, val expiresIn: Long = 3600,val id: Long=0)

@RestController
@RequestMapping("/api/v1")
class AuthController(private val auth: pe.upeu.ecorecicla.servicio.AuthService) {
    @PostMapping("/auth/login")
    fun login(@Valid @RequestBody body: LoginPeticion): SesionRespuesta {
        return auth.iniciar(body)
    }
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): UsuarioRespuesta {
        return auth.actual(jwt.subject.toLong())
    }
}
