package com.example.chat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtTokenProvider {

    // Các giá trị đọc từ application.yml
    private String accessSecret;
    private String refreshSecret;
    private long accessTokenExpirationMs;
    private long refreshTokenExpirationMs;

    // Key thực tế được khởi tạo sau khi load cấu hình
    private Key accessSecretKey;
    private Key refreshSecretKey;

    @PostConstruct
    public void init() {
        accessSecretKey = Keys.hmacShaKeyFor(accessSecret.getBytes());
        refreshSecretKey = Keys.hmacShaKeyFor(refreshSecret.getBytes());
    }

    public String generateAccessToken(Long accountId, String username, String role) {
        return generateToken(accountId, username, role, accessTokenExpirationMs, accessSecretKey);
    }

    public String generateRefreshToken(Long accountId, String username, String role) {
        return generateToken(accountId, username, role, refreshTokenExpirationMs, refreshSecretKey);
    }

    private String generateToken(Long accountId, String username, String role, long validityInMs, Key key) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityInMs);

        return Jwts.builder()
                .claim("username", username)
                .claim("accountId", accountId)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isValidAccessToken(String token) {
        return validateToken(token, accessSecretKey);
    }

    public boolean isValidateRefreshToken(String token) {
        return validateToken(token, refreshSecretKey);
    }

    private boolean validateToken(String token, Key key) {
        try {
            getClaims(token, key);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaims(String token, Key key) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getAccountIdFromAccessToken(String token) {
        return getClaims(token, accessSecretKey).get("accountId", Long.class);
    }

    public String getUsernameFromAccessToken(String token) {
        return getClaims(token, accessSecretKey).get("username", String.class);
    }

    public String getRoleFromAccessToken(String token) {
        return getClaims(token, accessSecretKey).get("role", String.class);
    }

    public Long getAccountIdFromRefreshToken(String token) {
        return getClaims(token, refreshSecretKey).get("accountId", Long.class);
    }

    public long getExpirationTime(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(accessSecretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.getExpiration().getTime();

        } catch (ExpiredJwtException e) {
            return e.getClaims().getExpiration().getTime();
        }
    }


    public Long extractUserIdIgnoreExpiration(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(accessSecretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return Long.parseLong(claims.get("accountId").toString());

        } catch (ExpiredJwtException e) {
            Claims claims = e.getClaims();
            return Long.parseLong(claims.get("accountId").toString());
        }
    }

}
