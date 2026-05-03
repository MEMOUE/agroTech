package com.agrotech.agroauth.security;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;

@Component
public class JwtUtil {

    private static final SecretKey JWT_SECRET = Keys.hmacShaKeyFor(
            "MaSuperCleSecreteTresLongueEtSecurisee123!".getBytes()
    );

    @Value("${jwt.expiration}")
    private int JwtExpiration;

    public String genererToken(String username){

        JwtBuilder builder = Jwts.builder()
                .setSubject(username)
                .issuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + JwtExpiration))
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET);
        return builder.compact();

    }

    public String extractToken(String token){
        return Jwts.parser()
                .verifyWith(JWT_SECRET)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateToken(String token){
        try{
            Jwts.parser()
                    .setSigningKey(JWT_SECRET)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return true;
        }catch(Exception e){
            return false;
        }
    }

}
