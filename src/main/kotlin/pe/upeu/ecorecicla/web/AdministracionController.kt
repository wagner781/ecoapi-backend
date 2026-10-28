package pe.upeu.ecorecicla.web

import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import pe.upeu.ecorecicla.servicio.AdministracionService
import pe.upeu.ecorecicla.web.dto.*
import java.net.URI

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('PUNTO_GESTIONAR')")
class AdministracionController(private val service: AdministracionService) {
    @GetMapping("/usuarios") fun usuarios(@RequestParam(defaultValue="0") pagina: Int,@RequestParam(defaultValue="20") tamanio: Int)=service.usuarios(pagina,tamanio)
    @PostMapping("/usuarios") fun crearUsuario(@Valid @RequestBody b: UsuarioGestionPeticion): ResponseEntity<pe.upeu.ecorecicla.seguridad.UsuarioRespuesta> {
        val u=service.guardarUsuario(null,b); return ResponseEntity.created(URI.create("/api/v1/usuarios/${u.id}")).body(u)
    }
    @PutMapping("/usuarios/{id}") fun editarUsuario(@PathVariable id: Long,@Valid @RequestBody b: UsuarioGestionPeticion)=service.guardarUsuario(id,b)
    @DeleteMapping("/usuarios/{id}") fun desactivar(@PathVariable id: Long): ResponseEntity<Void> { service.desactivarUsuario(id); return ResponseEntity.noContent().build() }
    @GetMapping("/roles") fun roles(@RequestParam(defaultValue="0") pagina: Int,@RequestParam(defaultValue="20") tamanio: Int)=service.roles(pagina,tamanio)
    @GetMapping("/privilegios") fun privilegios()=service.privilegios()
    @PostMapping("/roles") fun crearRol(@Valid @RequestBody b: RolGestionPeticion): ResponseEntity<RolRespuesta> { val r=service.guardarRol(null,b); return ResponseEntity.created(URI.create("/api/v1/roles/${r.nombre}")).body(r) }
    @PutMapping("/roles/{nombre}") fun editarRol(@PathVariable nombre: String,@Valid @RequestBody b: RolGestionPeticion)=service.guardarRol(nombre,b)
    @DeleteMapping("/roles/{nombre}") fun eliminarRol(@PathVariable nombre: String): ResponseEntity<Void> { service.eliminarRol(nombre); return ResponseEntity.noContent().build() }
}
