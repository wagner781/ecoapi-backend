package pe.upeu.ecorecicla.seguridad

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class UsuarioRepository(private val db: JdbcTemplate) {
    private val mapper=org.springframework.jdbc.core.RowMapper { r: java.sql.ResultSet, _: Int -> Usuario(r.getLong("id"),r.getString("username"),r.getString("password"),r.getString("nombre"),r.getString("rol"),r.getBoolean("activo"),r.getLong("version_sesion")) }
    fun buscar(username: String): Usuario? = db.query("SELECT * FROM usuario WHERE username=?",mapper,username).firstOrNull()
    fun porId(id: Long): Usuario? = db.query("SELECT * FROM usuario WHERE id=?",mapper,id).firstOrNull()
    fun listar(pagina: Int,tamanio: Int)=db.query("SELECT * FROM usuario ORDER BY id LIMIT ? OFFSET ?",mapper,tamanio,pagina.toLong()*tamanio)
    fun privilegios(rol: String): Set<String> = db.queryForList("SELECT privilegio FROM rol_privilegio WHERE rol=?", String::class.java, rol).toSet()
}
