package pe.upeu.ecorecicla.seguridad

data class Usuario(val id: Long, val username: String, val password: String, val nombre: String, val rol: String)
enum class Privilegio { PUNTO_LEER, PESAJE_LEER, PESAJE_CREAR, PESAJE_EDITAR, PESAJE_ANULAR, RUTA_CERRAR, PUNTO_GESTIONAR }
enum class Rol(val privilegios: Set<Privilegio>) {
    OPERARIO(setOf(Privilegio.PUNTO_LEER, Privilegio.PESAJE_LEER, Privilegio.PESAJE_CREAR, Privilegio.PESAJE_EDITAR)),
    SUPERVISOR(OPERARIO.privilegios + setOf(Privilegio.PESAJE_ANULAR, Privilegio.RUTA_CERRAR)),
    ADMINISTRADOR(setOf(Privilegio.PUNTO_LEER, Privilegio.PESAJE_LEER, Privilegio.PUNTO_GESTIONAR))
}
