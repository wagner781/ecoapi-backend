package pe.upeu.ecorecicla.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.seguridad.Rol

@Component
class Semillas(private val db: JdbcTemplate, private val passwords: PasswordEncoder, private val repo: pe.upeu.ecorecicla.repositorio.EcoRepository) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        for (rol in Rol.entries) {
            if(db.queryForObject("SELECT COUNT(*) FROM rol WHERE nombre=?",Long::class.java,rol.name)==0L) {
                db.update("INSERT INTO rol(nombre) VALUES (?)",rol.name)
                for(p in rol.privilegios) if(db.queryForObject("SELECT COUNT(*) FROM rol_privilegio WHERE rol=? AND privilegio=?",Long::class.java,rol.name,p.name)==0L)
                    db.update("INSERT INTO rol_privilegio VALUES (?,?)",rol.name,p.name)
            }
        }
        listOf("operario1" to Rol.OPERARIO, "supervisor1" to Rol.SUPERVISOR, "admin" to Rol.ADMINISTRADOR,"operario2" to Rol.OPERARIO,"auditor1" to Rol.AUDITOR).forEach { (user, rol) ->
            if (db.queryForObject("SELECT COUNT(*) FROM usuario WHERE username=?", Long::class.java,user)==0L)
                db.update("INSERT INTO usuario(username,password,nombre,rol) VALUES (?,?,?,?)",user,passwords.encode("Eco2026*"),user,rol.name)
        }
        if(db.queryForObject("SELECT COUNT(*) FROM punto",Long::class.java)==0L) {
            (1..12).forEach { i -> repo.insertar("INSERT INTO punto(razon_social,direccion,zona,material_habitual) VALUES (?,?,?,?)","Empresa $i","Av. Reciclaje $i",listOf("Norte","Centro","Sur")[(i-1)%3],listOf("Papel","Plastico","Metal")[(i-1)%3]) }
        }
        // Se crean rutas para la fecha real; nunca se cambia la fecha de registros existentes.
        val today=java.time.LocalDate.now(java.time.ZoneId.of("America/Lima"))
        val operario=db.queryForObject("SELECT id FROM usuario WHERE username='operario1'",Long::class.java)!!
        val supervisor=db.queryForObject("SELECT id FROM usuario WHERE username='supervisor1'",Long::class.java)!!
        val otroOperario=db.queryForObject("SELECT id FROM usuario WHERE username='operario2'",Long::class.java)!!
        val puntos=db.queryForList("SELECT id FROM punto ORDER BY id LIMIT 6",Long::class.java)
        for ((fecha,cerrada) in listOf(today to false,today.minusDays(1) to true)) {
            if(repo.delDia(operario,fecha)==null) {
                val ruta=repo.insertar("INSERT INTO ruta(fecha,usuario_id,cerrada) VALUES (?,?,?)",fecha,operario,cerrada)
                puntos.forEachIndexed { i,p -> db.update("INSERT INTO ruta_punto(ruta_id,punto_id,secuencia) VALUES (?,?,?)",ruta,p,i+1) }
                val cantidad=if(cerrada) 12 else 3
                repeat(cantidad) { i ->
                    val punto=if(cerrada) puntos[i%6] else puntos[i]
                    val user=if(i==1) otroOperario else operario
                    val b=pe.upeu.ecorecicla.web.dto.PesajePeticion(ruta,punto,"Papel",25.0+i,2.0,"Semilla de demostracion","semilla-$ruta-$i")
                    val p=repo.crear(b,user,fecha.atTime(8,i).atZone(java.time.ZoneId.of("America/Lima")).toOffsetDateTime().toString(),pe.upeu.ecorecicla.servicio.ReglasPesaje.calcularNeto(b.pesoBruto,b.tara))
                    if(cerrada && i==11) db.update("UPDATE pesaje SET anulado=TRUE,motivo_anulacion='Registro duplicado de demostracion' WHERE id=?",p.id)
                }
            }
        }
        val rutaActual=repo.delDia(operario,today)!!
        val clave="semilla-ajeno-${rutaActual.id}"
        if(db.queryForObject("SELECT COUNT(*) FROM pesaje WHERE ruta_id=? AND operario_id=?",Long::class.java,rutaActual.id,otroOperario)==0L) {
            val b=pe.upeu.ecorecicla.web.dto.PesajePeticion(rutaActual.id,puntos[1],"Papel",26.0,2.0,"Semilla: otro operario",clave)
            repo.crear(b,otroOperario,java.time.OffsetDateTime.now(java.time.ZoneId.of("America/Lima")).toString(),pe.upeu.ecorecicla.servicio.ReglasPesaje.calcularNeto(b.pesoBruto,b.tara))
        }
        if(repo.delDia(supervisor,today)==null) {
            val ruta=repo.insertar("INSERT INTO ruta(fecha,usuario_id,cerrada) VALUES (?,?,FALSE)",today,supervisor)
            puntos.forEachIndexed { i,p -> db.update("INSERT INTO ruta_punto(ruta_id,punto_id,secuencia) VALUES (?,?,?)",ruta,p,i+1) }
        }
    }
}
