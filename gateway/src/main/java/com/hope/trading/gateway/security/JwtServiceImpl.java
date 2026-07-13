package com.hope.trading.gateway.security;

import com.hope.trading.gateway.dto.UserAuthenticationDto;
import com.hope.trading.gateway.helper.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService{
    private final JwtProperties jwtProperties;

    @Override
    public String generateToken(UserAuthenticationDto userAuthenticationDto) {
        return Jwts.builder()
                .subject(userAuthenticationDto.getUserId().toString())
                .claim("username", userAuthenticationDto.getUsername())
                .claim("email", userAuthenticationDto.getEmail())
                .claim("role", userAuthenticationDto.getRole().name())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(getSigningKey())
                .issuer(jwtProperties.getIssuer())
                .compact();
    }

    @Override
    public String extractUsername(String token) {
        return extractClaim(token,claims -> claims.get("username", String.class));
    }

    @Override
    public UUID extractUserId(String token){
        String id=extractClaim(token, Claims::getSubject);
        return UUID.fromString(id);}


    @Override
    public Role extractRole(String token) {
        String role = extractClaim(token, claims -> claims.get("role", String.class));
        return Role.valueOf(role);
    }

    @Override
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }


    @Override
    public boolean isTokenValid(String token) {

        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new java.util.Date());
    }

    private Key getSigningKey(){
        byte [] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Claims extractAllClaims(String token){
        return Jwts.parser()
                .verifyWith((SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    private <T> T extractClaim(String token, Function<Claims,T> claimResolver){
        final Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }
}
