package pe.upeu.ecorecicla.repositorio

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import pe.upeu.ecorecicla.dominio.*
import pe.upeu.ecorecicla.web.dto.*
import java.sql.ResultSet
import java.time.LocalDate

@Repository
class EcoRepository(val db: JdbcTemplate) {
    fun insertar(sql: String, vararg valores: Any?): Long {
        val keys=org.springframework.jdbc.support.GeneratedKeyHolder()
        db.update({ connection -> connection.prepareStatement(sql,arrayOf("id")).apply { valores.forEachIndexed { i,v -> setObject(i+1,v) } } },keys)
        return keys.key!!.toLong()
    }
    private fun punto(r: ResultSet) = Punto(r.getLong("id"),r.getString("razon_social"),r.getString("direccion"),r.getString("zona"),r.getString("material_habitual"))
    private fun pesaje(r: ResultSet) = Pesaje(r.getLong("id"),r.getLong("ruta_id"),r.getLong("punto_id"),r.getString("fecha_hora"),r.getString("material"),r.getDouble("peso_bruto"),r.getDouble("tara"),r.getDouble("peso_neto"),r.getString("username"),r.getLong("operario_id"),r.getString("observacion"),r.getBoolean("anulado"),r.getString("motivo_anulacion"),r.getBigDecimal("humedad_estimada")?.toDouble())
    private val selectPesaje = "SELECT p.*,u.username FROM pesaje p JOIN usuario u ON u.id=p.operario_id"
    fun ruta(id: Long, lock: Boolean = false): Ruta? = db.query("SELECT * FROM ruta WHERE id=?" + if(lock) " FOR UPDATE" else "", {r,_ -> Ruta(r.getLong("id"),r.getDate("fecha").toLocalDate(),r.getLong("usuario_id"),r.getBoolean("cerrada"))},id).firstOrNull()
    fun delDia(user: Long, fecha: LocalDate): Ruta? = db.queryForList("SELECT id FROM ruta WHERE usuario_id=? AND fecha=? ORDER BY id",Long::class.java,user,fecha).firstOrNull()?.let { ruta(it)?.copy(puntos=puntosRuta(it)) }
    fun rutas(fecha: LocalDate,user: Long?,pagina: Int,tamanio: Int): Pair<List<RutaResumen>,Long> {
        val where=" WHERE r.fecha=?"+if(user==null) "" else " AND r.usuario_id=?"
        val params=mutableListOf<Any>(fecha).apply { if(user!=null) add(user) }.toTypedArray()
        val total=db.queryForObject("SELECT COUNT(*) FROM ruta r$where",Long::class.java,*params)!!
        val contenido=db.query("SELECT r.*,u.username FROM ruta r JOIN usuario u ON u.id=r.usuario_id$where ORDER BY r.id LIMIT ? OFFSET ?",{r,_ -> RutaResumen(r.getLong("id"),r.getDate("fecha").toLocalDate(),r.getLong("usuario_id"),r.getString("username"),r.getBoolean("cerrada"))},*params,tamanio,pagina.toLong()*tamanio)
        return contenido to total
    }
    fun puntosRuta(ruta: Long): List<Punto> = db.query("""SELECT p.*,rp.secuencia,rp.motivo_omision,
        EXISTS(SELECT 1 FROM pesaje x WHERE x.ruta_id=rp.ruta_id AND x.punto_id=p.id AND NOT x.anulado) AS atendido
        FROM punto p JOIN ruta_punto rp ON rp.punto_id=p.id WHERE rp.ruta_id=? ORDER BY rp.secuencia""",{r,_ -> punto(r).copy(secuencia=r.getInt("secuencia"),atendido=r.getBoolean("atendido"),motivoOmision=r.getString("motivo_omision"))},ruta)
    fun punto(id: Long): Punto? = db.query("SELECT * FROM punto WHERE id=?",{r,_ -> punto(r)},id).firstOrNull()
    fun puntos(zona: String?, pagina: Int, tamanio: Int): Pair<List<Punto>,Long> {
        val where=if(zona==null) "" else " WHERE zona=?"
        val params=if(zona==null) emptyArray<Any>() else arrayOf<Any>(zona)
        val total=db.queryForObject("SELECT COUNT(*) FROM punto$where",Long::class.java,*params)!!
        return db.query("SELECT * FROM punto$where ORDER BY id LIMIT ? OFFSET ?",{r,_ -> punto(r)},*params,tamanio,pagina.toLong()*tamanio) to total
    }
    fun pesaje(id: Long): Pesaje? = db.query("$selectPesaje WHERE p.id=?",{r,_ -> pesaje(r)},id).firstOrNull()
    fun porClave(user: Long, clave: String): Pesaje? = db.query("$selectPesaje WHERE p.operario_id=? AND p.clave_cliente=?",{r,_ -> pesaje(r)},user,clave).firstOrNull()
    fun pesajes(punto: Long, pagina: Int, tamanio: Int): Pair<List<Pesaje>,Long> = db.query("$selectPesaje WHERE p.punto_id=? ORDER BY p.fecha_hora DESC,p.id DESC LIMIT ? OFFSET ?",{r,_ -> pesaje(r)},punto,tamanio,pagina.toLong()*tamanio) to db.queryForObject("SELECT COUNT(*) FROM pesaje WHERE punto_id=?",Long::class.java,punto)!!
    fun crear(body: PesajePeticion, user: Long, fecha: String, neto: Double): Pesaje {
        val id=insertar("""INSERT INTO pesaje(ruta_id,punto_id,fecha_hora,material,peso_bruto,tara,peso_neto,operario_id,observacion,clave_cliente,humedad_estimada)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)""",body.rutaId,body.puntoId,fecha,body.material,body.pesoBruto,body.tara,neto,user,body.observacion,body.claveCliente,body.humedadEstimada)
        return pesaje(id)!!
    }
}
