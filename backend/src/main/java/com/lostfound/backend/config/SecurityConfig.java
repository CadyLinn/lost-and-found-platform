package com.lostfound.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Browsers may only load scripts, styles and data from this same site. Even if someone managed to
     * inject HTML into a page, it could not run scripts from elsewhere or send data to another server.
     * blob: is for photo previews before upload.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "img-src 'self' blob: data:",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/error").permitAll()
                // Actuator lives on the internal management port (8081), not the public one.
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/items", "/api/items/*", "/api/items/*/matches", "/api/categories", "/api/photos/*").permitAll()
                // The web pages themselves (React app, when served from this jar) are public;
                // what they show is protected by the /api rules.
                .requestMatchers(request -> !request.getRequestURI().startsWith("/api/")
                        && !request.getRequestURI().startsWith("/actuator/")).permitAll()
                // Admin pages: the JWT's "role" claim must be ADMIN (see jwtRoles below).
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtRoles())))
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)));
        return http.build();
    }

    /**
     * Turns the token's "role" claim (USER or ADMIN, written at login from the database) into a Spring
     * Security authority such as ROLE_ADMIN. A role change applies at the next login or token refresh,
     * so at most 15 minutes later.
     */
    private static JwtAuthenticationConverter jwtRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
