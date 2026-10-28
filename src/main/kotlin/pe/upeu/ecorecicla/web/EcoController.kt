package pe.upeu.ecorecicla.web

import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import pe.upeu.ecorecicla.config.Fallo
import pe.upeu.ecorecicla.repositorio.EcoRepository
import pe.upeu.ecorecicla.servicio.EcoService
import pe.upeu.ecorecicla.web.dto.*
import java.net.URI
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1")
class EcoController(private val repo: EcoRepository, private val service: EcoService) {
    private fun supervisor(jwt: Jwt) = jwt.getClaimAsStringList("privilegios").contains("PESAJE_ANULAR")
    private fun <T> pagina(p: Int,t: Int, consulta: () -> Pair<List<T>,Long>): Pagina<T> {
        if(p<0 || t !in 1..100) throw Fallo(400,"Paginacion invalida",mapOf("pagina" to "Pagina >= 0 y tamanio entre 1 y 100"))
        val (list,total)=consulta(); val pages=((total+t-1)/t).toInt()
        return Pagina(list,p,t,total,pages,p>=pages-1)
    }
    @GetMapping("/rutas/dia") @PreAuthorize("hasAuthority('PUNTO_LEER')")
    fun dia(@AuthenticationPrincipal jwt: Jwt, @RequestParam(required=false) fecha: LocalDate?) = repo.delDia(jwt.subject.toLong(),fecha ?: service.hoy()) ?: throw Fallo(404,"No hay ruta asignada para esta fecha")
    @GetMapping("/puntos") @PreAuthorize("hasAuthority('PUNTO_LEER')")
    fun puntos(@RequestParam(required=false) zona: String?, @RequestParam(defaultValue="0") pagina: Int,@RequestParam(defaultValue="20") tamanio: Int) = pagina(pagina,tamanio) { repo.puntos(zona,pagina,tamanio) }
    @GetMapping("/puntos/{id}/pesajes") @PreAuthorize("hasAuthority('PESAJE_LEER')")
    fun pesajes(@PathVariable id: Long,@RequestParam(defaultValue="0") pagina: Int,@RequestParam(defaultValue="20") tamanio: Int): Pagina<pe.upeu.ecorecicla.dominio.Pesaje> {
        if(repo.punto(id)==null) throw Fallo(404,"Punto inexistente")
        return pagina(pagina,tamanio) { repo.pesajes(id,pagina,tamanio) }
    }
    @PostMapping("/pesajes") @PreAuthorize("hasAuthority('PESAJE_CREAR')")
    fun crear(@AuthenticationPrincipal jwt: Jwt,@Valid @RequestBody b: PesajePeticion): ResponseEntity<pe.upeu.ecorecicla.dominio.Pesaje> {
        val p=service.crear(b,jwt.subject.toLong(),supervisor(jwt)); return ResponseEntity.created(URI.create("/api/v1/pesajes/${p.id}")).body(p)
    }
    @PutMapping("/pesajes/{id}") @PreAuthorize("hasAuthority('PESAJE_EDITAR')")
    fun corregir(@AuthenticationPrincipal jwt: Jwt,@PathVariable id: Long,@Valid @RequestBody b: PesajePeticion) = service.corregir(id,b,jwt.subject.toLong(),supervisor(jwt))
    @DeleteMapping("/pesajes/{id}") @PreAuthorize("hasAuthority('PESAJE_ANULAR')")
    fun anular(@PathVariable id: Long,@Valid @RequestBody b: MotivoPeticion): ResponseEntity<Void> { service.anular(id,b.motivo); return ResponseEntity.noContent().build() }
    @PutMapping("/rutas/{id}/puntos/{puntoId}/omision") @PreAuthorize("hasAuthority('PESAJE_CREAR')")
    fun omitir(@AuthenticationPrincipal jwt: Jwt,@PathVariable id: Long,@PathVariable puntoId: Long,@Valid @RequestBody b: MotivoPeticion): ResponseEntity<Void> { service.omitir(id,puntoId,b.motivo,jwt.subject.toLong(),supervisor(jwt)); return ResponseEntity.noContent().build() }
    @PostMapping("/rutas/{id}/cierre") @PreAuthorize("hasAuthority('RUTA_CERRAR')")
    fun cerrar(@PathVariable id: Long) = service.cerrar(id)
    @PostMapping("/puntos") @PreAuthorize("hasAuthority('PUNTO_GESTIONAR')")
    fun punto(@Valid @RequestBody b: PuntoPeticion): ResponseEntity<pe.upeu.ecorecicla.dominio.Punto> { val p=service.crearPunto(b); return ResponseEntity.created(URI.create("/api/v1/puntos/${p.id}")).body(p) }
    @PostMapping("/rutas") @PreAuthorize("hasAuthority('PUNTO_GESTIONAR')")
    fun asignar(@Valid @RequestBody b: RutaPeticion): ResponseEntity<pe.upeu.ecorecicla.dominio.Ruta> { val r=service.asignar(b); return ResponseEntity.created(URI.create("/api/v1/rutas/${r.id}")).body(r) }
}
