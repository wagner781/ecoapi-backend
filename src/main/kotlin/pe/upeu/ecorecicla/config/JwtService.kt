package pe.upeu.ecorecicla.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import java.time.Instant

@Configuration
class JwtService(@param:Value("\${eco.jwt-secret}") private val secret: String,private val usuarios: pe.upeu.ecorecicla.seguridad.UsuarioRepository) {
    @Bean fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(SecretKeySpec(secret.toByteArray(), "HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build().also {
        it.setJwtValidator(org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator(
            JwtTimestampValidator(java.time.Duration.ZERO), JwtIssuerValidator("ecoapi"),
            org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> { jwt ->
                val user=jwt.subject?.toLongOrNull()?.let(usuarios::porId)
                if(user!=null && user.activo && user.versionSesion==(jwt.claims["version"] as? Number)?.toLong()) org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                else org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(org.springframework.security.oauth2.core.OAuth2Error("invalid_token","La cuenta o sus permisos cambiaron; inicia sesion nuevamente",null))
            }
        ))
    }
    @Bean fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secret.toByteArray()))
    fun emitir(encoder: JwtEncoder, id: Long, username: String, privilegios: Set<String>,version: Long=0,now: Instant=Instant.now()): String {
        val claims = JwtClaimsSet.builder().issuer("ecoapi").subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("username", username).claim("version",version).claim("privilegios", privilegios.toList()).build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }
}
