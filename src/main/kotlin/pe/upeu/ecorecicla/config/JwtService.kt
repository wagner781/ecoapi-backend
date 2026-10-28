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
class JwtService(@Value("\${eco.jwt-secret}") private val secret: String) {
    @Bean fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(SecretKeySpec(secret.toByteArray(), "HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build().also {
        it.setJwtValidator(org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator(
            JwtTimestampValidator(java.time.Duration.ZERO), JwtIssuerValidator("ecoapi")
        ))
    }
    @Bean fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secret.toByteArray()))
    fun emitir(encoder: JwtEncoder, id: Long, username: String, privilegios: Set<String>): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().issuer("ecoapi").subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("username", username).claim("privilegios", privilegios.toList()).build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }
}
