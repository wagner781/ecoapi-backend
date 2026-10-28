package pe.upeu.ecorecicla.servicio

import org.springframework.stereotype.Service
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import pe.upeu.ecorecicla.config.*
import pe.upeu.ecorecicla.seguridad.*

@Service
class AuthService(private val users: UsuarioRepository,private val passwords: PasswordEncoder,private val tokens: JwtService,private val encoder: JwtEncoder) {
    fun iniciar(body: LoginPeticion): SesionRespuesta {
        val user=users.buscar(body.usuario) ?: throw Fallo(401,"Credenciales incorrectas")
        val coincide=try { passwords.matches(body.password,user.password) } catch (_: IllegalArgumentException) { false }
        if(!user.activo || !coincide) throw Fallo(401,"Credenciales incorrectas")
        val permisos=users.privilegios(user.rol)
        return SesionRespuesta(tokens.emitir(encoder,user.id,user.username,permisos,user.versionSesion),user.username,user.nombre,user.rol,permisos,id=user.id)
    }
    fun actual(id: Long): UsuarioRespuesta {
        val user=users.porId(id)?.takeIf { it.activo } ?: throw Fallo(401,"Sesion invalida")
        return UsuarioRespuesta(user.id,user.username,user.nombre,user.rol,users.privilegios(user.rol),user.activo)
    }
}
