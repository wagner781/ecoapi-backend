package pe.upeu.ecorecicla

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.seguridad.UsuarioRepository

@SpringBootTest @AutoConfigureMockMvc @Transactional
class AdministracionTest(@Autowired val mvc: MockMvc,@Autowired val json: ObjectMapper,@Autowired val users: UsuarioRepository) {
    private fun token(user: String="admin",password: String="Eco2026*"): String {
        val result=mvc.post("/api/v1/auth/login") { contentType=MediaType.APPLICATION_JSON; content=json.writeValueAsString(mapOf("usuario" to user,"password" to password)) }.andExpect { status { isOk() } }.andReturn()
        return json.readTree(result.response.contentAsString)["token"].asText()
    }
    private fun body(user: String="operario_prueba",rol: String="OPERARIO",activo: Boolean=true,password: String?="Eco2026*")=json.writeValueAsString(mapOf("usuario" to user,"nombre" to "Usuario de prueba","rol" to rol,"activo" to activo,"password" to password))
    @Test fun `solo PUNTO_GESTIONAR accede a administracion`() {
        for(user in listOf("operario1","supervisor1")) {
            val t=token(user)
            for(path in listOf("usuarios","roles","privilegios")) mvc.get("/api/v1/$path") { header("Authorization","Bearer $t") }.andExpect { status { isForbidden() } }
            mvc.post("/api/v1/usuarios") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=body() }.andExpect { status { isForbidden() } }
        }
    }
    @Test fun `crea usuario cifra password y permite login sin exponer el hash`() {
        mvc.post("/api/v1/usuarios") { header("Authorization","Bearer ${token()}"); contentType=MediaType.APPLICATION_JSON; content=body() }.andExpect { status { isCreated() }; jsonPath("$.password") { doesNotExist() }; jsonPath("$.rol") { value("OPERARIO") } }
        assertNotEquals("Eco2026*",users.buscar("operario_prueba")!!.password)
        token("operario_prueba")
    }
    @Test fun `desactivar usuario invalida su token firmado anterior`() {
        val usuario=users.buscar("operario2")!!; val anterior=token("operario2")
        mvc.delete("/api/v1/usuarios/${usuario.id}") { header("Authorization","Bearer ${token()}") }.andExpect { status { isNoContent() } }
        mvc.get("/api/v1/me") { header("Authorization","Bearer $anterior") }.andExpect { status { isUnauthorized() } }
        mvc.post("/api/v1/auth/login") { contentType=MediaType.APPLICATION_JSON; content="""{"usuario":"operario2","password":"Eco2026*"}""" }.andExpect { status { isUnauthorized() } }
    }
    @Test fun `ultimo gestor activo no puede desactivarse ni perder privilegio`() {
        val t=token(); val id=users.buscar("admin")!!.id
        mvc.delete("/api/v1/usuarios/$id") { header("Authorization","Bearer $t") }.andExpect { status { isConflict() } }
        mvc.put("/api/v1/roles/ADMINISTRADOR") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content="""{"nombre":"ADMINISTRADOR","privilegios":["PUNTO_LEER"]}""" }.andExpect { status { isConflict() } }
    }
    @Test fun `roles se gestionan como datos y sus cambios invalidan permisos anteriores`() {
        val t=token()
        mvc.post("/api/v1/roles") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content="""{"nombre":"ROL_PRUEBA","privilegios":["PUNTO_LEER","PESAJE_LEER"]}""" }.andExpect { status { isCreated() } }
        mvc.post("/api/v1/usuarios") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=body(rol="ROL_PRUEBA") }.andExpect { status { isCreated() } }
        val anterior=token("operario_prueba")
        mvc.put("/api/v1/roles/ROL_PRUEBA") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content="""{"nombre":"ROL_PRUEBA","privilegios":["PUNTO_LEER"]}""" }.andExpect { status { isOk() } }
        mvc.get("/api/v1/me") { header("Authorization","Bearer $anterior") }.andExpect { status { isUnauthorized() } }
        val nuevo=token("operario_prueba")
        mvc.get("/api/v1/puntos/1/pesajes") { header("Authorization","Bearer $nuevo") }.andExpect { status { isForbidden() } }
        mvc.delete("/api/v1/roles/ROL_PRUEBA") { header("Authorization","Bearer $t") }.andExpect { status { isConflict() } }
    }
    @Test fun `valida roles desconocidos privilegios y password con error de campo`() {
        val t=token()
        mvc.post("/api/v1/usuarios") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=body(password="corta") }.andExpect { status { isBadRequest() }; jsonPath("$.validationErrors.password") { isNotEmpty() } }
        mvc.post("/api/v1/usuarios") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content=body(rol="NO_EXISTE") }.andExpect { status { isBadRequest() }; jsonPath("$.validationErrors.rol") { isNotEmpty() } }
        mvc.post("/api/v1/roles") { header("Authorization","Bearer $t"); contentType=MediaType.APPLICATION_JSON; content="""{"nombre":"ROL_PRUEBA","privilegios":["PERMISO_INEXISTENTE"]}""" }.andExpect { status { isBadRequest() }; jsonPath("$.validationErrors.privilegios") { isNotEmpty() } }
    }
    @Test fun `listados paginados de administracion no filtran password`() {
        mvc.get("/api/v1/usuarios?pagina=0&tamanio=2") { header("Authorization","Bearer ${token()}") }.andExpect { status { isOk() }; jsonPath("$.contenido.length()") { value(2) }; jsonPath("$.contenido[0].password") { doesNotExist() }; jsonPath("$.ultima") { value(false) } }
    }
}
