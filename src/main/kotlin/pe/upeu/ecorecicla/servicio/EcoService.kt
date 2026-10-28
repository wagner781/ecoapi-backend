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
    @Transactional(readOnly=true)
    fun consultarRuta(id: Long,user: Long,global: Boolean): Ruta {
        val r=repo.ruta(id) ?: throw Fallo(404,"Ruta inexistente")
        acceso(r,user,global)
        return r.copy(puntos=repo.puntosRuta(id))
    }
    private fun ruta(id: Long): Ruta = repo.ruta(id,true) ?: throw Fallo(404,"Ruta inexistente")
    // PESAJE_ANULAR distingue a quien puede corregir cualquier pesaje sin comparar roles.
    private fun acceso(r: Ruta, user: Long, supervisor: Boolean) { if(r.usuarioId!=user && !supervisor) throw Fallo(403,"Ruta asignada a otro usuario") }
    private fun abierta(r: Ruta) { if(r.cerrada) throw Fallo(409,"La ruta ya esta cerrada") }
    @Transactional
    fun crear(b: PesajePeticion, user: Long, supervisor: Boolean): Pesaje {
        val r=ruta(b.rutaId); acceso(r,user,supervisor)
        // La clave estable hace seguro reintentar si el servidor guardo pero se perdio la respuesta.
        repo.porClave(user,b.claveCliente)?.let { return it }
        abierta(r)
        val neto=ReglasPesaje.calcularNeto(b.pesoBruto,b.tara)
        if(repo.puntosRuta(r.id).none { it.id==b.puntoId }) throw Fallo(404,"Punto fuera de la ruta")
        return repo.crear(b,user,OffsetDateTime.now(zona).toString(),neto)
    }
    @Transactional
    fun corregir(id: Long, b: PesajePeticion, user: Long, supervisor: Boolean): Pesaje {
        val p=repo.pesaje(id) ?: throw Fallo(404,"Pesaje inexistente")
        val r=ruta(p.rutaId); abierta(r)
        if(!supervisor && (p.operarioId!=user || OffsetDateTime.parse(p.fechaHora).atZoneSameInstant(zona).toLocalDate()!=hoy())) throw Fallo(403,"Solo puede corregir sus pesajes del mismo dia")
        if(p.anulado) throw Fallo(409,"El pesaje esta anulado")
        if(b.rutaId!=p.rutaId || b.puntoId!=p.puntoId) throw Fallo(400,"No se puede mover un pesaje de ruta o punto")
        val neto=ReglasPesaje.calcularNeto(b.pesoBruto,b.tara)
        repo.db.update("UPDATE pesaje SET material=?,peso_bruto=?,tara=?,peso_neto=?,observacion=? WHERE id=?",b.material,b.pesoBruto,b.tara,neto,b.observacion,id)
        return repo.pesaje(id)!!
    }
    @Transactional
    fun anular(id: Long, motivo: String) {
        val p=repo.pesaje(id) ?: throw Fallo(404,"Pesaje inexistente")
        ruta(p.rutaId) // Serializa la anulacion con el cierre; RN-01 solo limita crear/corregir.
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
    fun editarPunto(id: Long,b: PuntoPeticion): Punto {
        if(repo.db.update("UPDATE punto SET razon_social=?,direccion=?,zona=?,material_habitual=? WHERE id=?",b.razonSocial,b.direccion,b.zona,b.materialHabitual,id)==0) throw Fallo(404,"Punto inexistente")
        return repo.punto(id)!!
    }
    @Transactional
    fun eliminarPunto(id: Long) {
        if(repo.punto(id)==null) throw Fallo(404,"Punto inexistente")
        if(repo.db.queryForObject("SELECT COUNT(*) FROM ruta_punto WHERE punto_id=?",Long::class.java,id)!!>0) throw Fallo(409,"El punto tiene rutas asignadas; se conserva su historial")
        repo.db.update("DELETE FROM punto WHERE id=?",id)
    }
    private fun validarAsignacion(b: RutaPeticion,id: Long?=null) {
        if(b.puntoIds.isEmpty()) throw Fallo(400,"Datos invalidos",mapOf("puntoIds" to "Seleccione al menos un punto"))
        if(b.puntoIds.distinct().size!=b.puntoIds.size) throw Fallo(400,"Datos invalidos",mapOf("puntoIds" to "No se permiten puntos repetidos"))
        val usuarios=repo.db.queryForList("SELECT id FROM usuario WHERE id=? FOR UPDATE",Long::class.java,b.usuarioId)
        if(usuarios.isEmpty()) throw Fallo(404,"Usuario inexistente")
        val permitido=repo.db.queryForObject("SELECT COUNT(*) FROM usuario u JOIN rol_privilegio rp ON rp.rol=u.rol WHERE u.id=? AND u.activo AND rp.privilegio='PESAJE_CREAR'",Long::class.java,b.usuarioId)!!>0
        if(!permitido) throw Fallo(400,"Datos invalidos",mapOf("usuarioId" to "Debe ser un usuario activo con PESAJE_CREAR"))
        if(repo.delDia(b.usuarioId,b.fecha)?.let { it.id!=id }==true) throw Fallo(409,"El usuario ya tiene una ruta para esa fecha")
        b.puntoIds.forEach { if(repo.punto(it)==null) throw Fallo(404,"Punto inexistente") }
    }
    @Transactional
    fun asignar(b: RutaPeticion): Ruta {
        validarAsignacion(b)
        val id=repo.insertar("INSERT INTO ruta(fecha,usuario_id,cerrada) VALUES (?,?,FALSE)",b.fecha,b.usuarioId)
        b.puntoIds.forEachIndexed { i,p -> repo.db.update("INSERT INTO ruta_punto(ruta_id,punto_id,secuencia) VALUES (?,?,?)",id,p,i+1) }
        return repo.ruta(id)!!.copy(puntos=repo.puntosRuta(id))
    }
    @Transactional
    fun editarAsignacion(id: Long,b: RutaPeticion): Ruta {
        val r=ruta(id); abierta(r); validarAsignacion(b,id)
        val pesajes=repo.db.queryForObject("SELECT COUNT(*) FROM pesaje WHERE ruta_id=?",Long::class.java,id)!!
        val omisiones=repo.puntosRuta(id).any { !it.motivoOmision.isNullOrBlank() }
        if(pesajes>0 || omisiones) throw Fallo(409,"No se modifica la planificacion de una ruta con pesajes u omisiones")
        repo.db.update("UPDATE ruta SET usuario_id=?,fecha=? WHERE id=?",b.usuarioId,b.fecha,id)
        repo.db.update("DELETE FROM ruta_punto WHERE ruta_id=?",id)
        b.puntoIds.forEachIndexed { i,p -> repo.db.update("INSERT INTO ruta_punto(ruta_id,punto_id,secuencia) VALUES (?,?,?)",id,p,i+1) }
        return repo.ruta(id)!!.copy(puntos=repo.puntosRuta(id))
    }
}
