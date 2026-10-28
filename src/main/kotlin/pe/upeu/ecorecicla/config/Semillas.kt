package pe.upeu.ecorecicla.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.seguridad.Rol

@Component
class Semillas(private val db: JdbcTemplate, private val passwords: PasswordEncoder) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        for (rol in Rol.entries) for (p in rol.privilegios) {
            if (db.queryForObject("SELECT COUNT(*) FROM rol_privilegio WHERE rol=? AND privilegio=?", Long::class.java, rol.name,p.name)==0L)
                db.update("INSERT INTO rol_privilegio VALUES (?,?)",rol.name,p.name)
        }
        listOf("operario1" to Rol.OPERARIO, "supervisor1" to Rol.SUPERVISOR, "admin" to Rol.ADMINISTRADOR).forEach { (user, rol) ->
            if (db.queryForObject("SELECT COUNT(*) FROM usuario WHERE username=?", Long::class.java,user)==0L)
                db.update("INSERT INTO usuario(username,password,nombre,rol) VALUES (?,?,?,?)",user,passwords.encode("Eco2026*"),user,rol.name)
        }
    }
}
