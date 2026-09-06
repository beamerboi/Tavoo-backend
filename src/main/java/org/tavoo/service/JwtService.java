package org.tavoo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.tavoo.dto.TokenResponse;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    private static final String ISSUER = "https://tavoo.local";

    private final JwtEncoder jwtEncoder;
    private final Duration accessTokenTtl;
    private final Duration rememberMeTokenTtl;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${tavoo.security.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl,
            @Value("${tavoo.security.jwt.remember-me-token-ttl:P30D}") Duration rememberMeTokenTtl
    ) {
        requirePositive(accessTokenTtl, "JWT access token lifetime");
        requirePositive(rememberMeTokenTtl, "JWT remember-me token lifetime");
        if (rememberMeTokenTtl.compareTo(accessTokenTtl) <= 0) {
            throw new IllegalArgumentException(
                    "JWT remember-me token lifetime must exceed the normal token lifetime"
            );
        }
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtl = accessTokenTtl;
        this.rememberMeTokenTtl = rememberMeTokenTtl;
    }

    public TokenResponse issueAccessToken(
            Authentication authentication,
            boolean rememberMe
    ) {
        Instant issuedAt = Instant.now();
        Duration tokenTtl = rememberMe ? rememberMeTokenTtl : accessTokenTtl;
        Instant expiresAt = issuedAt.plus(tokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                .claim(
                        "roles",
                        authentication.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList()
                )
                .claim("remember_me", rememberMe)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", expiresAt, rememberMe);
    }

    private void requirePositive(Duration duration, String name) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
