package com.gimnasio.sistemagym.config;

import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.repository.SesionRefreshRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService tokenService;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectProvider<UsuarioRepository> usuarioRepository;
    private final ObjectProvider<SesionRefreshRepository> refreshSessions;

    public JwtAuthenticationFilter(
            JwtTokenService tokenService,
            CustomUserDetailsService userDetailsService,
            ObjectProvider<UsuarioRepository> usuarioRepository,
            ObjectProvider<SesionRefreshRepository> refreshSessions) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
        this.usuarioRepository = usuarioRepository;
        this.refreshSessions = refreshSessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            try {
                Claims claims = tokenService.validar(token);
                String sessionId = claims.get("sid", String.class);
                if (sessionId != null) {
                    SesionRefreshRepository sessions = refreshSessions.getIfAvailable();
                    if (sessions == null || !sessions.existsByIdAndExpiraEnAfter(
                            sessionId, java.time.Instant.now())) {
                        throw new JwtException("JWT revocado al cerrar la sesión.");
                    }
                }
                String subject = claims.getSubject();
                if (subject == null || subject.isBlank()) {
                    throw new JwtException("JWT sin subject.");
                }
                UserDetails user = userDetailsService.loadUserByUsername(subject);
                Number claimVersion = claims.get("credentialVersion", Number.class);
                long tokenVersion = claimVersion == null ? 0L : claimVersion.longValue();
                UsuarioRepository repository = usuarioRepository.getIfAvailable();
                if (repository != null) {
                    long currentVersion = repository
                            .findCredentialVersionByCorreo(subject).orElse(Long.MIN_VALUE);
                    if (tokenVersion != currentVersion) {
                        throw new JwtException("JWT revocado por cambio de credenciales.");
                    }
                }
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(
                                user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException
                    | UsernameNotFoundException exception) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("WWW-Authenticate", "Bearer");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
