package org.tavoo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.tavoo.config.SecurityConfig;
import org.tavoo.dto.LoginRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void signsExpiringTokenWithSubjectIssuerAndRoles() {
        SecurityConfig securityConfig = new SecurityConfig();
        var secretKey = securityConfig.jwtSecretKey(SECRET);
        var decoder = securityConfig.jwtDecoder(secretKey);
        JwtService jwtService = new JwtService(
                securityConfig.jwtEncoder(secretKey),
                Duration.ofMinutes(15),
                Duration.ofDays(30)
        );
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "waiter",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WAITER"))
        );

        var response = jwtService.issueAccessToken(authentication, false);
        Jwt jwt = decoder.decode(response.accessToken());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.rememberMe()).isFalse();
        assertThat(jwt.getSubject()).isEqualTo("waiter");
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://tavoo.local");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_WAITER");
        assertThat(jwt.getClaimAsBoolean("remember_me")).isFalse();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void rememberMeIssuesLongLivedToken() {
        SecurityConfig securityConfig = new SecurityConfig();
        var secretKey = securityConfig.jwtSecretKey(SECRET);
        var decoder = securityConfig.jwtDecoder(secretKey);
        JwtService jwtService = new JwtService(
                securityConfig.jwtEncoder(secretKey),
                Duration.ofMinutes(15),
                Duration.ofDays(30)
        );
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "waiter",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WAITER"))
        );

        var response = jwtService.issueAccessToken(authentication, true);
        Jwt jwt = decoder.decode(response.accessToken());

        assertThat(response.rememberMe()).isTrue();
        assertThat(jwt.getClaimAsBoolean("remember_me")).isTrue();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofDays(30));
    }

    @Test
    void omittedRememberMeDefaultsToFalse() throws Exception {
        LoginRequest request = new ObjectMapper().readValue(
                "{\"username\":\"waiter\",\"password\":\"secret\"}",
                LoginRequest.class
        );

        assertThat(request.rememberMe()).isFalse();
    }

    @Test
    void authenticatesPasswordBeforeIssuingToken() {
        SecurityConfig securityConfig = new SecurityConfig();
        var passwordEncoder = securityConfig.passwordEncoder();
        UserDetailsService users = username -> User.withUsername("admin")
                .password(passwordEncoder.encode("secure-password"))
                .roles("ADMIN")
                .build();
        var authenticationManager = securityConfig.authenticationManager(
                users,
                passwordEncoder
        );

        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "admin",
                        "secure-password"
                )
        );

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }
}
