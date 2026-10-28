package pe.upeu.ecorecicla

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class ContratoErroresTest(@Autowired val mvc: MockMvc) {
    @Test fun `una URL sin controlador conserva el formato comun y responde 404`() {
        mvc.get("/api/v1/recurso-inexistente") { with(jwt()) }.andExpect {
            status { isNotFound() }; jsonPath("$.status") { value(404) }
            jsonPath("$.path") { value("/api/v1/recurso-inexistente") }
            jsonPath("$.validationErrors") { isMap() }
        }
    }
    @Test fun `un verbo incorrecto no se presenta como fallo interno`() {
        mvc.get("/api/v1/auth/login") { with(jwt()) }.andExpect { status { isMethodNotAllowed() }; jsonPath("$.status") { value(405) } }
    }
}
