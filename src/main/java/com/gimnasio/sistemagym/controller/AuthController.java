package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.LoginRequest;
import com.gimnasio.sistemagym.dto.LoginResponse;
import com.gimnasio.sistemagym.service.RefreshSessionService;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final RefreshSessionService refreshSessions;
    private final boolean secureCookies;
    private final String cookieSameSite;
    private final Set<String> allowedOrigins;

    public AuthController(AuthenticationManager authenticationManager,
            UsuarioRepository usuarioRepository,
            RefreshSessionService refreshSessions,
            @Value("${app.auth.cookie-secure:false}") boolean secureCookies,
            @Value("${app.auth.cookie-same-site:Strict}") String cookieSameSite,
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String corsOrigins) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.refreshSessions = refreshSessions;
        this.secureCookies = secureCookies;
        if (!Set.of("Strict", "Lax", "None").contains(cookieSameSite)) {
            throw new IllegalArgumentException("AUTH_COOKIE_SAME_SITE debe ser Strict, Lax o None.");
        }
        if ("None".equals(cookieSameSite) && !secureCookies) {
            throw new IllegalArgumentException("SameSite=None requiere cookies Secure.");
        }
        this.cookieSameSite = cookieSameSite;
        this.allowedOrigins = Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(this::normalizeOrigin)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        try {
            Authentication authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.correo().trim().toLowerCase(Locale.ROOT),
                            request.contrasena()));
            var principal = (org.springframework.security.core.userdetails.UserDetails)
                    authenticated.getPrincipal();
            var usuario = usuarioRepository.findByCorreoIgnoreCase(principal.getUsername())
                    .orElseThrow();
            var tokens = refreshSessions.crear(usuario);
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                            cookies(tokens, requestSecure(servletRequest)))
                    .body(new LoginResponse(tokens.accessToken(), "Bearer", tokens.accessLifetimeMs()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = "SG_REFRESH", required = false) String refresh,
            @CookieValue(name = "SG_CSRF", required = false) String csrfCookie,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrfHeader,
            @RequestHeader(name = "Origin", required = false) String origin,
            HttpServletRequest request) {
        if (!originAllowed(origin, request) || csrfCookie == null || !csrfCookie.equals(csrfHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            var tokens = refreshSessions.renovar(refresh, csrfHeader);
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                            cookies(tokens, requestSecure(request)))
                    .body(new LoginResponse(tokens.accessToken(), "Bearer", tokens.accessLifetimeMs()));
        } catch (RefreshSessionService.SesionInvalidaException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).headers(limpiarCookies(request)).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "SG_REFRESH", required = false) String refresh,
            @CookieValue(name = "SG_CSRF", required = false) String csrfCookie,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrfHeader,
            @RequestHeader(name = "Origin", required = false) String origin,
            HttpServletRequest request) {
        if (originAllowed(origin, request) && csrfCookie != null && csrfCookie.equals(csrfHeader)) {
            refreshSessions.revocar(refresh, csrfHeader);
        }
        return ResponseEntity.noContent().headers(limpiarCookies(request)).build();
    }

    private boolean requestSecure(HttpServletRequest request) {
        return secureCookies || (request != null && request.isSecure());
    }

    private String[] cookies(RefreshSessionService.Tokens tokens, boolean secure) {
        return new String[] {
            ResponseCookie.from("SG_REFRESH", tokens.refreshToken()).httpOnly(true).secure(secure)
                    .sameSite(cookieSameSite).path("/api/auth").maxAge(tokens.refreshLifetime()).build().toString(),
            ResponseCookie.from("SG_CSRF", tokens.csrfToken()).httpOnly(false).secure(secure)
                    .sameSite(cookieSameSite).path("/").maxAge(tokens.refreshLifetime()).build().toString()
        };
    }

    private HttpHeaders limpiarCookies(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        boolean secure = requestSecure(request);
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie.from("SG_REFRESH", "").httpOnly(true)
                .secure(secure).sameSite(cookieSameSite).path("/api/auth").maxAge(0).build().toString());
        headers.add(HttpHeaders.SET_COOKIE, ResponseCookie.from("SG_CSRF", "").secure(secure)
                .sameSite(cookieSameSite).path("/").maxAge(0).build().toString());
        return headers;
    }

    private boolean originAllowed(String origin, HttpServletRequest request) {
        if (origin == null || origin.isBlank()) return true;
        try {
            java.net.URI parsed = java.net.URI.create(origin);
            String scheme = parsed.getScheme();
            String path = parsed.getRawPath();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || parsed.getRawUserInfo() != null || (path != null && !path.isEmpty() && !"/".equals(path))
                    || parsed.getRawQuery() != null || parsed.getRawFragment() != null
                    || parsed.getRawAuthority() == null) {
                return false;
            }
            String normalized = normalizeOrigin(parsed.getScheme() + "://" + parsed.getRawAuthority());
            return allowedOrigins.contains(normalized)
                    || parsed.getRawAuthority().equalsIgnoreCase(request.getHeader("Host"));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String normalizeOrigin(String origin) {
        java.net.URI parsed = java.net.URI.create(origin);
        return parsed.getScheme().toLowerCase(Locale.ROOT) + "://"
                + parsed.getRawAuthority().toLowerCase(Locale.ROOT);
    }
}
