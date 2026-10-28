package pe.upeu.ecorecicla.dominio

import java.time.LocalDate

data class Punto(val id: Long, val razonSocial: String, val direccion: String, val zona: String, val materialHabitual: String, val secuencia: Int = 0, val atendido: Boolean = false, val motivoOmision: String? = null)
data class Ruta(val id: Long, val fecha: LocalDate, val usuarioId: Long, val cerrada: Boolean, val puntos: List<Punto> = emptyList())
data class RutaResumen(val id: Long,val fecha: LocalDate,val usuarioId: Long,val usuario: String,val cerrada: Boolean)
data class Pesaje(val id: Long, val rutaId: Long, val puntoId: Long, val fechaHora: String, val material: String, val pesoBruto: Double, val tara: Double, val pesoNeto: Double, val operario: String, val operarioId: Long, val observacion: String?, val anulado: Boolean, val motivoAnulacion: String?,val humedadEstimada: Double?=null)
