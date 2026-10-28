package pe.upeu.ecorecicla.web.dto

import jakarta.validation.constraints.*

data class UsuarioGestionPeticion(
    @field:NotBlank @field:Pattern(regexp="[a-zA-Z0-9._-]{3,80}") val usuario: String,
    @field:NotBlank @field:Size(max=120) val nombre: String,
    @field:NotBlank @field:Size(max=40) val rol: String,
    val password: String?=null,val activo: Boolean=true
)
data class RolGestionPeticion(@field:NotBlank @field:Pattern(regexp="[A-Z][A-Z0-9_]{2,39}") val nombre: String,val privilegios: Set<String>)
data class RolRespuesta(val nombre: String,val privilegios: Set<String>)
