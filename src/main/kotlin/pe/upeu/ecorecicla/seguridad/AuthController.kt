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
data class UsuarioRespuesta(val id: Long, val usuario: String, val nombre: String, val rol: String, val privilegios: Set<String>)
data class SesionRespuesta(val token: String, val usuario: String, val nombre: String, val rol: String, val privilegios: Set<String>, val expiresIn: Long = 3600)

@RestController
@RequestMapping("/api/v1")
class AuthController(private val users: UsuarioRepository, private val passwords: PasswordEncoder, private val tokens: JwtService, private val encoder: JwtEncoder) {
    @PostMapping("/auth/login")
    fun login(@Valid @RequestBody body: LoginPeticion): SesionRespuesta {
        val user = users.buscar(body.usuario) ?: throw Fallo(401, "Credenciales incorrectas")
        if (!passwords.matches(body.password, user.password)) throw Fallo(401, "Credenciales incorrectas")
        val privileges = users.privilegios(user.rol)
        return SesionRespuesta(tokens.emitir(encoder, user.id, user.username, privileges), user.username, user.nombre, user.rol, privileges)
    }
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): UsuarioRespuesta {
        val user = users.buscar(jwt.getClaimAsString("username")) ?: throw Fallo(401,"Usuario inexistente")
        return UsuarioRespuesta(user.id,user.username,user.nombre,user.rol,users.privilegios(user.rol))
    }
}
