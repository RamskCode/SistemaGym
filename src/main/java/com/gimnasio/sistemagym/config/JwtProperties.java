package com.gimnasio.sistemagym.config;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtProperties {

    @Bean
    public SecretKey jwtSigningKey(
            @Value("${app.jwt.secret-base64}") String encodedSecret) {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(encodedSecret);
            if (keyBytes.length < 32) {
                throw new IllegalStateException(
                        "JWT_SECRET_BASE64 debe decodificar al menos 32 bytes.");
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET_BASE64 debe ser una clave Base64 válida de al menos 32 bytes.",
                    exception);
        }
    }
}
