package pe.upeu.ecorecicla

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class SeguridadTest(@Autowired val mvc: MockMvc) {
    @Test fun `la expiracion comunicada coincide con el claim firmado`() {
        val r=mvc.post("/api/v1/auth/login") { contentType=org.springframework.http.MediaType.APPLICATION_JSON; content="""{"usuario":"operario1","password":"Eco2026*"}""" }.andExpect { status { isOk() } }.andReturn()
        val json=com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val body=json.readTree(r.response.contentAsString)
        val claims=json.readTree(java.util.Base64.getUrlDecoder().decode(body["token"].asText().split('.')[1]))
        org.junit.jupiter.api.Assertions.assertEquals(claims["exp"].asLong(),body["expiresAt"].asLong())
        org.junit.jupiter.api.Assertions.assertEquals(3600,claims["exp"].asLong()-claims["iat"].asLong())
    }
    @Test fun `rechaza peticion anonima y token alterado`() {
        mvc.get("/api/v1/me").andExpect { status { isUnauthorized() }; jsonPath("$.status") { value(401) } }
        mvc.get("/api/v1/me") { header("Authorization","Bearer alterado") }.andExpect { status { isUnauthorized() } }
    }
    @Test fun `valida credenciales contra base y firma token`() {
        mvc.post("/api/v1/auth/login") { contentType=org.springframework.http.MediaType.APPLICATION_JSON; content="""{"usuario":"operario1","password":"Eco2026*"}""" }.andExpect { status { isOk() }; jsonPath("$.token") { isNotEmpty() }; jsonPath("$.expiresIn") { value(3600) } }
        mvc.post("/api/v1/auth/login") { contentType=org.springframework.http.MediaType.APPLICATION_JSON; content="""{"usuario":"operario1","password":"incorrecta"}""" }.andExpect { status { isUnauthorized() } }
    }
}
