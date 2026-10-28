package pe.upeu.ecorecicla

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.repositorio.EcoRepository
import pe.upeu.ecorecicla.servicio.*
import pe.upeu.ecorecicla.config.Fallo

@SpringBootTest @AutoConfigureMockMvc @Transactional
class HumedadTest(@Autowired val mvc: MockMvc,@Autowired val json: ObjectMapper,@Autowired val repo: EcoRepository,@Autowired val service: EcoService) {
    private fun token(): String {
        val r=mvc.post("/api/v1/auth/login") { contentType=MediaType.APPLICATION_JSON; content="""{"usuario":"operario1","password":"Eco2026*"}""" }.andReturn()
        return json.readTree(r.response.contentAsString)["token"].asText()
    }
    private fun body(h: Double?): String {
        val uid=repo.db.queryForObject("SELECT id FROM usuario WHERE username='operario1'",Long::class.java)!!
        val ruta=repo.delDia(uid,service.hoy())!!
        return json.writeValueAsString(mapOf("rutaId" to ruta.id,"puntoId" to ruta.puntos.first().id,"material" to "Papel","pesoBruto" to 20,"tara" to 2,"claveCliente" to "humedad-test","humedadEstimada" to h))
    }
    @Test fun `humedad recorre creacion correccion y persistencia sin cambiar neto`() {
        val t=token()
        val r=mvc.post("/api/v1/pesajes") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=body(12.75) }
            .andExpect { status { isCreated() }; jsonPath("$.humedadEstimada") { value(12.75) }; jsonPath("$.pesoNeto") { value(18.0) } }.andReturn()
        val id=json.readTree(r.response.contentAsString)["id"].asLong()
        assertEquals(12.75,repo.pesaje(id)!!.humedadEstimada)
        for(valor in listOf(0.0,100.0,null)) {
            mvc.put("/api/v1/pesajes/$id") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=body(valor) }.andExpect { status { isOk() } }
            assertEquals(valor,repo.pesaje(id)!!.humedadEstimada)
        }
    }
    @Test fun `rechaza rango precision y no finitos con error de campo`() {
        val t=token()
        for(valor in listOf(-0.1,100.01,10.123)) mvc.post("/api/v1/pesajes") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=body(valor) }
            .andExpect { status { isBadRequest() }; jsonPath("$.validationErrors.humedadEstimada") { isNotEmpty() } }
        for(valor in listOf(Double.NaN,Double.POSITIVE_INFINITY)) assertThrows(Fallo::class.java) { ReglasPesaje.validarHumedad(valor) }
    }
    @Test fun `semillas tienen humedad de demostracion`() {
        assertTrue(repo.db.queryForObject("SELECT COUNT(*) FROM pesaje WHERE clave_cliente LIKE 'semilla-%' AND humedad_estimada IS NOT NULL",Long::class.java)!!>=15)
    }
}
