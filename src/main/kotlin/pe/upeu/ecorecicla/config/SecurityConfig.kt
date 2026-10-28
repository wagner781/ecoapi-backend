package pe.upeu.ecorecicla.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfig(private val mapper: ObjectMapper) {
    @Bean fun passwordEncoder() = BCryptPasswordEncoder()
    @Bean fun security(http: HttpSecurity): SecurityFilterChain {
        val converter = JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter { jwt -> jwt.getClaimAsStringList("privilegios").orEmpty().map(::SimpleGrantedAuthority) }
        }
        val entry = org.springframework.security.web.AuthenticationEntryPoint { req, res, _ ->
            res.status = 401; res.contentType = "application/json"
            mapper.writeValue(res.outputStream, ErrorRespuesta(status=401, error="Unauthorized", message="Token ausente, invalido o vencido", path=req.requestURI))
        }
        http.csrf { it.disable() }.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll().anyRequest().authenticated() }
            .exceptionHandling { it.authenticationEntryPoint(entry); it.accessDeniedHandler { req, res, _ ->
                res.status=403; res.contentType="application/json"
                mapper.writeValue(res.outputStream, ErrorRespuesta(status=403,error="Forbidden",message="No posee el privilegio requerido",path=req.requestURI))
            } }
            .oauth2ResourceServer { it.jwt { config -> config.jwtAuthenticationConverter(converter) }; it.authenticationEntryPoint(entry) }
        return http.build()
    }
}
