package pe.upeu.ecorecicla.web.dto

import jakarta.validation.constraints.*

data class Pagina<T>(val contenido: List<T>, val pagina: Int, val tamanio: Int, val totalElementos: Long, val totalPaginas: Int, val ultima: Boolean)
data class PesajePeticion(val rutaId: Long, val puntoId: Long,
    @field:NotBlank @field:Size(max=80) val material: String,
    @field:Positive val pesoBruto: Double, @field:PositiveOrZero val tara: Double,
    @field:Size(max=500) val observacion: String? = null,
    @field:NotBlank @field:Size(max=150) val claveCliente: String)
data class MotivoPeticion(@field:NotBlank @field:Size(max=300) val motivo: String)
data class PuntoPeticion(@field:NotBlank val razonSocial: String, @field:NotBlank val direccion: String, @field:NotBlank val zona: String, @field:NotBlank val materialHabitual: String)
data class RutaPeticion(val usuarioId: Long, val fecha: java.time.LocalDate, @field:Size(min=1) val puntoIds: List<Long>)
