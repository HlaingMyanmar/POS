package org.sspd.servicemgmt.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String STEP_UP_TOKEN_TYPE = "stepup";

    private final String secretKey;
    private final long jwtExpiration;
    private final long refreshExpirationMs;
    private final long stepUpExpirationMs;

    public JwtService(
            @Value("${application.security.jwt.secret-key}") String secretKey,
            @Value("${application.security.jwt.expiration}") long jwtExpiration,
            @Value("${application.security.jwt.refresh-expiration:36000000}") long refreshExpirationMs,
            @Value("${application.security.auth.step-up-expiration-minutes:5}") long stepUpExpirationMinutes) {
        this.secretKey = secretKey;
        this.jwtExpiration = jwtExpiration;
        this.refreshExpirationMs = refreshExpirationMs;
        this.stepUpExpirationMs = Duration.ofMinutes(Math.max(1, stepUpExpirationMinutes)).toMillis();
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE);
        return buildToken(extraClaims, userDetails, jwtExpiration, null);
    }

    public String generateToken(UserDetails userDetails, int tokenVersion) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("tv", tokenVersion);
        extraClaims.put(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE);
        return buildToken(extraClaims, userDetails, jwtExpiration, null);
    }

    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> claims.get("tv", Integer.class));
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public ParsedToken parseToken(String token) {
        Claims claims = extractAllClaims(token);
        return new ParsedToken(
                claims.getSubject(),
                claims.get("tv", Integer.class),
                claims.get(TOKEN_TYPE_CLAIM, String.class),
                claims.getId()
        );
    }

    public record ParsedToken(String username, Integer tokenVersion, String tokenType, String jti) {
        public boolean isAccessToken() {
            return ACCESS_TOKEN_TYPE.equals(tokenType);
        }

        public boolean isRefreshToken() {
            return REFRESH_TOKEN_TYPE.equals(tokenType);
        }

        public boolean isStepUpToken() {
            return STEP_UP_TOKEN_TYPE.equals(tokenType);
        }
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration,
            String jti) {
        var builder = Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256);
        if (jti != null && !jti.isBlank()) {
            builder.setId(jti);
        }
        return builder.compact();
    }

    public String generateRefreshToken(UserDetails userDetails, int tokenVersion) {
        return generateRefreshToken(userDetails, tokenVersion, UUID.randomUUID().toString(), refreshExpirationMs);
    }

    public String generateRefreshToken(UserDetails userDetails, int tokenVersion, String jti) {
        return generateRefreshToken(userDetails, tokenVersion, jti, refreshExpirationMs);
    }

    /**
     * Issues a refresh JWT whose lifetime is capped by {@code remainingTtlMs}
     * (used so cookie / JWT expire with the absolute session window).
     */
    public String generateRefreshToken(
            UserDetails userDetails, int tokenVersion, String jti, long remainingTtlMs) {
        long ttl = Math.max(1_000L, Math.min(refreshExpirationMs, remainingTtlMs));
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("tv", tokenVersion);
        extraClaims.put(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE);
        return buildToken(extraClaims, userDetails, ttl, jti);
    }

    public String generateStepUpToken(UserDetails userDetails, int tokenVersion) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("tv", tokenVersion);
        extraClaims.put(TOKEN_TYPE_CLAIM, STEP_UP_TOKEN_TYPE);
        return buildToken(extraClaims, userDetails, stepUpExpirationMs, UUID.randomUUID().toString());
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    public long getStepUpExpirationMs() {
        return stepUpExpirationMs;
    }

    /** Seconds until JWT expiry (floored at 0). */
    public long remainingSeconds(String token) {
        Date exp = extractExpiration(token);
        long seconds = Duration.between(Instant.now(), exp.toInstant()).getSeconds();
        return Math.max(0L, seconds);
    }

    public boolean isAccessToken(String token) {
        return ACCESS_TOKEN_TYPE.equals(extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class)));
    }

    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN_TYPE.equals(extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class)));
    }

    public boolean isStepUpToken(String token) {
        return STEP_UP_TOKEN_TYPE.equals(extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class)));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(getSignInKey()).build().parseClaimsJws(token).getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}
