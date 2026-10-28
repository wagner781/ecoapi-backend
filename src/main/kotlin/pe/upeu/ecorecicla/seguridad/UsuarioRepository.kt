package pe.upeu.ecorecicla.seguridad

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class UsuarioRepository(private val db: JdbcTemplate) {
    fun buscar(username: String): Usuario? = db.query("SELECT * FROM usuario WHERE username=?", { r, _ -> Usuario(r.getLong("id"), r.getString("username"), r.getString("password"), r.getString("nombre"), r.getString("rol")) }, username).firstOrNull()
    fun privilegios(rol: String): Set<String> = db.queryForList("SELECT privilegio FROM rol_privilegio WHERE rol=?", String::class.java, rol).toSet()
}
