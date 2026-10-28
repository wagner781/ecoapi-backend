package pe.upeu.ecorecicla

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.delete
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.repositorio.EcoRepository
import pe.upeu.ecorecicla.servicio.EcoService
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import java.time.Instant

@SpringBootTest @AutoConfigureMockMvc @Transactional
class OperacionesTest(@Autowired val mvc: MockMvc,@Autowired val json: ObjectMapper,@Autowired val repo: EcoRepository,@Autowired val service: EcoService,@Autowired val encoder: JwtEncoder) {
    private fun token(user: String): String {
        val result=mvc.post("/api/v1/auth/login") { contentType=MediaType.APPLICATION_JSON; content="""{"usuario":"$user","password":"Eco2026*"}""" }.andReturn()
        return json.readTree(result.response.contentAsString)["token"].asText()
    }
    private fun ruta()=repo.delDia(repo.db.queryForObject("SELECT id FROM usuario WHERE username='operario1'",Long::class.java)!!,service.hoy())!!
    private fun body(r: Long,p: Long,clave: String="prueba",bruto: Double=20.0,tara: Double=2.0)=json.writeValueAsString(mapOf("rutaId" to r,"puntoId" to p,"material" to "Papel","pesoBruto" to bruto,"tara" to tara,"claveCliente" to clave))
    @Test fun `admin no registra aunque tenga token valido`() {
        mvc.post("/api/v1/pesajes") { header("Authorization","Bearer ${token("admin")}"); contentType=MediaType.APPLICATION_JSON; content=body(ruta().id,ruta().puntos.first().id) }.andExpect { status { isForbidden() } }
    }
    @Test fun `operario no anula ni cierra`() {
        val t=token("operario1")
        mvc.delete("/api/v1/pesajes/1") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content="""{"motivo":"Duplicado"}""" }.andExpect { status { isForbidden() } }
        mvc.post("/api/v1/rutas/${ruta().id}/cierre") { header("Authorization","Bearer $t") }.andExpect { status { isForbidden() } }
    }
    @Test fun `calcula neto y reintento no duplica`() {
        val r=ruta(); val t=token("operario1"); val b=body(r.id,r.puntos.last().id)
        val ids=(1..2).map {
            val response=mvc.post("/api/v1/pesajes") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=b }.andExpect { status { isCreated() }; jsonPath("$.pesoNeto") { value(18.0) } }.andReturn()
            json.readTree(response.response.contentAsString)["id"].asLong()
        }
        assertEquals(ids[0],ids[1])
    }
    @Test fun `RN03 devuelve error de campo`() {
        val r=ruta()
        mvc.post("/api/v1/pesajes") { header("Authorization","Bearer ${token("operario1")}"); contentType=MediaType.APPLICATION_JSON; content=body(r.id,r.puntos.first().id,bruto=2.0,tara=3.0) }.andExpect { status { isBadRequest() }; jsonPath("$.validationErrors.tara") { isNotEmpty() } }
    }
    @Test fun `RN01 impide crear en ruta cerrada`() {
        val r=repo.delDia(ruta().usuarioId,service.hoy().minusDays(1))!!
        mvc.post("/api/v1/pesajes") { header("Authorization","Bearer ${token("operario1")}"); contentType=MediaType.APPLICATION_JSON; content=body(r.id,r.puntos.first().id) }.andExpect { status { isConflict() } }
    }
    @Test fun `RN02 rechaza pesaje ajeno`() {
        val r=ruta(); val p=repo.pesajes(r.puntos[1].id,0,100).first.first { it.rutaId==r.id }
        mvc.put("/api/v1/pesajes/${p.id}") { header("Authorization","Bearer ${token("operario1")}"); contentType=MediaType.APPLICATION_JSON; content=body(r.id,p.puntoId) }.andExpect { status { isForbidden() } }
    }
    @Test fun `RN04 informa puntos sin atender y permite omisiones`() {
        val r=ruta(); val t=token("supervisor1")
        mvc.post("/api/v1/rutas/${r.id}/cierre") { header("Authorization","Bearer $t") }.andExpect { status { isConflict() }; jsonPath("$.message") { value("Faltan 3 puntos sin pesaje ni motivo de omision") } }
        r.puntos.filter { !it.atendido }.forEach { p -> mvc.put("/api/v1/rutas/${r.id}/puntos/${p.id}/omision") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content="""{"motivo":"Empresa cerrada"}""" }.andExpect { status { isNoContent() } } }
        mvc.post("/api/v1/rutas/${r.id}/cierre") { header("Authorization","Bearer $t") }.andExpect { status { isOk() }; jsonPath("$.cerrada") { value(true) } }
    }
    @Test fun `anula sin borrar y pagina con filtro`() {
        val r=ruta(); val p=repo.pesajes(r.puntos.first().id,0,100).first.first { it.rutaId==r.id }
        mvc.delete("/api/v1/pesajes/${p.id}") { header("Authorization","Bearer ${token("supervisor1")}"); contentType=MediaType.APPLICATION_JSON; content="""{"motivo":"Error de registro"}""" }.andExpect { status { isNoContent() } }
        assertTrue(repo.pesaje(p.id)!!.anulado)
        mvc.get("/api/v1/puntos?zona=Norte&pagina=0&tamanio=2") { header("Authorization","Bearer ${token("admin")}") }.andExpect { status { isOk() }; jsonPath("$.contenido.length()") { value(2) }; jsonPath("$.totalElementos") { value(4) }; jsonPath("$.ultima") { value(false) } }
    }
    @Test fun `token firmado vencido no permite acceso`() {
        val claims=JwtClaimsSet.builder().issuer("ecoapi").subject("1").issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)).build()
        val expired=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).tokenValue
        mvc.get("/api/v1/me") { header("Authorization","Bearer $expired") }.andExpect { status { isUnauthorized() } }
    }
    @Test fun `RN02 rechaza correccion propia fuera del mismo dia`() {
        val r=ruta(); val p=repo.pesajes(r.puntos.first().id,0,100).first.first { it.rutaId==r.id }
        repo.db.update("UPDATE pesaje SET fecha_hora=? WHERE id=?",service.hoy().minusDays(1).atStartOfDay(java.time.ZoneId.of("America/Lima")).toOffsetDateTime().toString(),p.id)
        mvc.put("/api/v1/pesajes/${p.id}") { header("Authorization","Bearer ${token("operario1")}"); contentType=MediaType.APPLICATION_JSON; content=body(r.id,p.puntoId) }.andExpect { status { isForbidden() } }
    }
    @Test fun `RN01 rechaza correccion en ruta cerrada`() {
        val r=repo.delDia(ruta().usuarioId,service.hoy().minusDays(1))!!
        val p=repo.pesajes(r.puntos.first().id,0,100).first.first { it.rutaId==r.id }
        mvc.put("/api/v1/pesajes/${p.id}") { header("Authorization","Bearer ${token("supervisor1")}"); contentType=MediaType.APPLICATION_JSON; content=body(r.id,p.puntoId) }.andExpect { status { isConflict() } }
    }
}
