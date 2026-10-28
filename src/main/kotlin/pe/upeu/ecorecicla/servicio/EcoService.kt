package pe.upeu.ecorecicla.servicio

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.config.Fallo
import pe.upeu.ecorecicla.dominio.*
import pe.upeu.ecorecicla.repositorio.EcoRepository
import pe.upeu.ecorecicla.web.dto.*
import java.time.*

@Service
class EcoService(private val repo: EcoRepository) {
    private val zona=ZoneId.of("America/Lima")
    fun hoy(): LocalDate = LocalDate.now(zona)
    private fun ruta(id: Long): Ruta = repo.ruta(id,true) ?: throw Fallo(404,"Ruta inexistente")
    // PESAJE_ANULAR distingue a quien puede corregir cualquier pesaje sin comparar roles.
    private fun acceso(r: Ruta, user: Long, supervisor: Boolean) { if(r.usuarioId!=user && !supervisor) throw Fallo(403,"Ruta asignada a otro usuario") }
    private fun abierta(r: Ruta) { if(r.cerrada) throw Fallo(409,"La ruta ya esta cerrada") }
    private fun validar(b: PesajePeticion) {
        if(!b.pesoBruto.isFinite() || !b.tara.isFinite() || b.pesoBruto<=b.tara || b.tara<0)
            throw Fallo(400,"Datos invalidos",mapOf("tara" to "La tara debe ser no negativa y menor que el peso bruto"))
    }
    @Transactional
    fun crear(b: PesajePeticion, user: Long, supervisor: Boolean): Pesaje {
        val r=ruta(b.rutaId); acceso(r,user,supervisor)
        // La clave estable hace seguro reintentar si el servidor guardo pero se perdio la respuesta.
        repo.porClave(user,b.claveCliente)?.let { return it }
        abierta(r); validar(b)
        if(repo.puntosRuta(r.id).none { it.id==b.puntoId }) throw Fallo(404,"Punto fuera de la ruta")
        return repo.crear(b,user,OffsetDateTime.now(zona).toString())
    }
    @Transactional
    fun corregir(id: Long, b: PesajePeticion, user: Long, supervisor: Boolean): Pesaje {
        val p=repo.pesaje(id) ?: throw Fallo(404,"Pesaje inexistente")
        val r=ruta(p.rutaId); abierta(r)
        if(!supervisor && (p.operarioId!=user || OffsetDateTime.parse(p.fechaHora).atZoneSameInstant(zona).toLocalDate()!=hoy())) throw Fallo(403,"Solo puede corregir sus pesajes del mismo dia")
        if(p.anulado) throw Fallo(409,"El pesaje esta anulado")
        if(b.rutaId!=p.rutaId || b.puntoId!=p.puntoId) throw Fallo(400,"No se puede mover un pesaje de ruta o punto")
        validar(b)
        repo.db.update("UPDATE pesaje SET material=?,peso_bruto=?,tara=?,peso_neto=?,observacion=? WHERE id=?",b.material,b.pesoBruto,b.tara,b.pesoBruto-b.tara,b.observacion,id)
        return repo.pesaje(id)!!
    }
    @Transactional
    fun anular(id: Long, motivo: String) {
        val p=repo.pesaje(id) ?: throw Fallo(404,"Pesaje inexistente")
        abierta(ruta(p.rutaId))
        repo.db.update("UPDATE pesaje SET anulado=TRUE,motivo_anulacion=? WHERE id=?",motivo,id)
    }
    @Transactional
    fun omitir(id: Long, punto: Long, motivo: String, user: Long, supervisor: Boolean) {
        val r=ruta(id); acceso(r,user,supervisor); abierta(r)
        if(repo.db.update("UPDATE ruta_punto SET motivo_omision=? WHERE ruta_id=? AND punto_id=?",motivo,id,punto)==0) throw Fallo(404,"Punto fuera de la ruta")
    }
    @Transactional
    fun cerrar(id: Long): Ruta {
        val r=ruta(id); abierta(r)
        val faltan=repo.puntosRuta(id).count { !it.atendido && it.motivoOmision.isNullOrBlank() }
        if(faltan>0) throw Fallo(409,"Faltan $faltan puntos sin pesaje ni motivo de omision")
        repo.db.update("UPDATE ruta SET cerrada=TRUE WHERE id=?",id)
        return r.copy(cerrada=true,puntos=repo.puntosRuta(id))
    }
    @Transactional
    fun crearPunto(b: PuntoPeticion): Punto {
        val id=repo.insertar("INSERT INTO punto(razon_social,direccion,zona,material_habitual) VALUES (?,?,?,?)",b.razonSocial,b.direccion,b.zona,b.materialHabitual)
        return repo.punto(id)!!
    }
    @Transactional
    fun asignar(b: RutaPeticion): Ruta {
        if(b.puntoIds.distinct().size!=b.puntoIds.size) throw Fallo(400,"No se permiten puntos repetidos")
        if(repo.db.queryForObject("SELECT COUNT(*) FROM usuario WHERE id=?",Long::class.java,b.usuarioId)==0L) throw Fallo(404,"Usuario inexistente")
        b.puntoIds.forEach { if(repo.punto(it)==null) throw Fallo(404,"Punto inexistente") }
        val id=repo.insertar("INSERT INTO ruta(fecha,usuario_id,cerrada) VALUES (?,?,FALSE)",b.fecha,b.usuarioId)
        b.puntoIds.forEachIndexed { i,p -> repo.db.update("INSERT INTO ruta_punto(ruta_id,punto_id,secuencia) VALUES (?,?,?)",id,p,i+1) }
        return repo.ruta(id)!!.copy(puntos=repo.puntosRuta(id))
    }
}
