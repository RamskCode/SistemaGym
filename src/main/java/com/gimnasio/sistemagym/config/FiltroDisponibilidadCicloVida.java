package com.gimnasio.sistemagym.config;

import com.gimnasio.sistemagym.service.DisponibilidadCicloVida;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Returns 503 for API requests until startup membership recovery has succeeded. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnBean(DisponibilidadCicloVida.class)
public class FiltroDisponibilidadCicloVida extends OncePerRequestFilter {

    private final DisponibilidadCicloVida disponibilidad;

    public FiltroDisponibilidadCicloVida(DisponibilidadCicloVida disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")
                && !disponibilidad.estaDisponible()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", "5");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(
                    "{\"mensaje\":\"El servidor está verificando las membresías. Intenta nuevamente en unos segundos.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
