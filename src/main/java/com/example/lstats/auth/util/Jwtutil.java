package com.example.lstats.auth.util;

import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class Jwtutil {
    private static final SecretKey Secret_key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final long Expiration_Time = 86400000;

    public static String generateToken(String username) {

        return Jwts.builder().setSubject(username).setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + Expiration_Time))
                .signWith(Secret_key)
                .compact();
    }
    public static String extractUsername(String token){
        return Jwts.parserBuilder().setSigningKey(Secret_key)
        .build().parseClaimsJws(token).getBody().getSubject();
    }
}
