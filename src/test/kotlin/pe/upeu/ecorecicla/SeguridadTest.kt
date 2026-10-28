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
    @Test fun `rechaza peticion anonima y token alterado`() {
        mvc.get("/api/v1/me").andExpect { status { isUnauthorized() }; jsonPath("$.status") { value(401) } }
        mvc.get("/api/v1/me") { header("Authorization","Bearer alterado") }.andExpect { status { isUnauthorized() } }
    }
    @Test fun `valida credenciales contra base y firma token`() {
        mvc.post("/api/v1/auth/login") { contentType=org.springframework.http.MediaType.APPLICATION_JSON; content="""{"usuario":"operario1","password":"Eco2026*"}""" }.andExpect { status { isOk() }; jsonPath("$.token") { isNotEmpty() }; jsonPath("$.expiresIn") { value(3600) } }
        mvc.post("/api/v1/auth/login") { contentType=org.springframework.http.MediaType.APPLICATION_JSON; content="""{"usuario":"operario1","password":"incorrecta"}""" }.andExpect { status { isUnauthorized() } }
    }
}
