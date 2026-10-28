package pe.upeu.ecorecicla

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.seguridad.UsuarioRepository
import pe.upeu.ecorecicla.repositorio.EcoRepository
import pe.upeu.ecorecicla.servicio.EcoService

@SpringBootTest @AutoConfigureMockMvc @Transactional
class RutasGestionTest(@Autowired val mvc: MockMvc,@Autowired val json: ObjectMapper,@Autowired val users: UsuarioRepository,@Autowired val repo: EcoRepository,@Autowired val service: EcoService) {
    private fun token(user: String): String {
        val r=mvc.post("/api/v1/auth/login") { contentType=MediaType.APPLICATION_JSON; content="""{"usuario":"$user","password":"Eco2026*"}""" }.andExpect { status { isOk() } }.andReturn()
        return json.readTree(r.response.contentAsString)["token"].asText()
    }
    @Test fun `supervisor y gestor seleccionan rutas mientras operario solo ve las propias`() {
        val r=repo.delDia(users.buscar("operario1")!!.id,service.hoy())!!
        for(user in listOf("supervisor1","admin")) {
            val t=token(user)
            mvc.get("/api/v1/rutas/${r.id}") { header("Authorization","Bearer $t") }.andExpect { status { isOk() }; jsonPath("$.puntos.length()") { value(6) } }
            mvc.get("/api/v1/rutas?pagina=0&tamanio=1") { header("Authorization","Bearer $t") }.andExpect { status { isOk() }; jsonPath("$.totalElementos") { value(2) }; jsonPath("$.ultima") { value(false) } }
        }
        mvc.get("/api/v1/rutas/${r.id}") { header("Authorization","Bearer ${token("operario2")}") }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/rutas") { header("Authorization","Bearer ${token("operario1")}") }.andExpect { status { isOk() }; jsonPath("$.totalElementos") { value(1) } }
    }
    @Test fun `crear y editar asignacion conserva secuencia y rechaza duplicado por dia`() {
        val t=token("admin"); val id=users.buscar("operario2")!!.id
        fun body(puntos: List<Long>)=json.writeValueAsString(mapOf("usuarioId" to id,"fecha" to service.hoy().toString(),"puntoIds" to puntos))
        val respuesta=mvc.post("/api/v1/rutas") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=body(listOf(6,5,4)) }.andExpect { status { isCreated() }; jsonPath("$.puntos[0].id") { value(6) }; jsonPath("$.puntos[0].secuencia") { value(1) } }.andReturn()
        val rutaId=json.readTree(respuesta.response.contentAsString)["id"].asLong()
        mvc.post("/api/v1/rutas") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=body(listOf(6,5,4)) }.andExpect { status { isConflict() } }
        mvc.put("/api/v1/rutas/$rutaId") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=body(listOf(8,7)) }.andExpect { status { isOk() }; jsonPath("$.puntos[0].id") { value(8) } }
        mvc.get("/api/v1/rutas/dia") { header("Authorization","Bearer ${token("operario2")}") }.andExpect { status { isOk() }; jsonPath("$.id") { value(rutaId) } }
    }
    @Test fun `planificacion con pesajes no se reemplaza`() {
        val r=repo.delDia(users.buscar("operario1")!!.id,service.hoy())!!
        mvc.put("/api/v1/rutas/${r.id}") { header("Authorization","Bearer ${token("admin")}"); contentType=MediaType.APPLICATION_JSON; content=json.writeValueAsString(mapOf("usuarioId" to r.usuarioId,"fecha" to r.fecha.toString(),"puntoIds" to listOf(7,8))) }.andExpect { status { isConflict() } }
    }
    @Test fun `padron se puede mantener sin borrar puntos con historial`() {
        val t=token("admin")
        val b="""{"razonSocial":"Nuevo punto","direccion":"Av. Prueba 10","zona":"Centro","materialHabitual":"Papel"}"""
        val result=mvc.post("/api/v1/puntos") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=b }.andExpect { status { isCreated() } }.andReturn()
        val id=json.readTree(result.response.contentAsString)["id"].asLong()
        mvc.put("/api/v1/puntos/$id") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=b.replace("Nuevo punto","Punto corregido") }.andExpect { status { isOk() }; jsonPath("$.razonSocial") { value("Punto corregido") } }
        mvc.delete("/api/v1/puntos/$id") { header("Authorization","Bearer $t") }.andExpect { status { isNoContent() } }
        mvc.delete("/api/v1/puntos/1") { header("Authorization","Bearer $t") }.andExpect { status { isConflict() } }
    }
    @Test fun `no se asignan rutas de pesaje a administrador sin PESAJE_CREAR`() {
        mvc.post("/api/v1/rutas") { header("Authorization","Bearer ${token("admin")}"); contentType=MediaType.APPLICATION_JSON; content=json.writeValueAsString(mapOf("usuarioId" to users.buscar("admin")!!.id,"fecha" to service.hoy().toString(),"puntoIds" to listOf(1))) }.andExpect { status { isBadRequest() }; jsonPath("$.validationErrors.usuarioId") { isNotEmpty() } }
    }
}
