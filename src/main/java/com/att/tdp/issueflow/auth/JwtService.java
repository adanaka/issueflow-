package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${application.jwt.secret}")
    private String secret;

    @Value("${application.jwt.expiration}")
    private long expiration;

    public String generateToken(User user) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String extractJti(String token) {
        return getClaims(token).getId();
    }

    public Date extractExpiration(String token) {
        return getClaims(token).getExpiration();
    }

    public boolean isTokenValid(String token) {
        try {
            return !getClaims(token).getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
