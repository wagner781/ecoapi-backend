package pe.upeu.ecorecicla

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest @AutoConfigureMockMvc
class AuditorTest(@Autowired val mvc: MockMvc,@Autowired val json: ObjectMapper) {
    private fun token(): String {
        val r=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""{"usuario":"auditor1","password":"Eco2026*"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.privilegios.length()").value(2)).andReturn()
        return json.readTree(r.response.contentAsString)["token"].asText()
    }
    @Test fun `auditor consulta perfil puntos y pesajes`() {
        val t=token()
        for(path in listOf("/api/v1/me","/api/v1/puntos","/api/v1/puntos/1/pesajes"))
            mvc.perform(get(path).header("Authorization","Bearer $t")).andExpect(status().isOk)
    }
    @Test fun `auditor no modifica pesajes puntos usuarios roles ni rutas`() {
        val t=token()
        val peso="""{"rutaId":1,"puntoId":1,"material":"Papel","pesoBruto":20,"tara":2,"claveCliente":"auditor-no-escribe"}"""
        val punto="""{"razonSocial":"Prueba","direccion":"Av 1","zona":"Sur","materialHabitual":"Papel"}"""
        val solicitudes=listOf(post("/api/v1/pesajes").content(peso),put("/api/v1/pesajes/1").content(peso),
            delete("/api/v1/pesajes/1").content("""{"motivo":"Prueba"}"""),post("/api/v1/rutas/1/cierre"),
            put("/api/v1/rutas/1/puntos/1/omision").content("""{"motivo":"Prueba"}"""),
            post("/api/v1/puntos").content(punto),put("/api/v1/puntos/1").content(punto),delete("/api/v1/puntos/1"),
            post("/api/v1/usuarios").content("""{"usuario":"auditorqa","nombre":"Prueba","rol":"OPERARIO","password":"Eco2026*"}"""),
            post("/api/v1/roles").content("""{"nombre":"ROL_QA","privilegios":["PUNTO_LEER"]}"""),
            post("/api/v1/rutas").content("""{"usuarioId":1,"fecha":"2026-10-28","puntoIds":[1]}"""))
        for(req in solicitudes) mvc.perform(req.contentType(MediaType.APPLICATION_JSON).header("Authorization","Bearer $t")).andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer $t")).andExpect(status().isOk)
    }
}
