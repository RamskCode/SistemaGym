package com.gimnasio.sistemagym.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Component
@ConditionalOnProperty(prefix = "sistemagym.membresias.scheduler", name = "enabled", havingValue = "true")
// En el perfil pruebas, PruebasDatasourceGuard debe validar el catálogo real antes de esta tarea.
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TareaMembresiasProgramada implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(TareaMembresiasProgramada.class);

    private final MembresiaCicloVidaService cicloVidaService;
    private final BloqueoDistribuidoCicloVida bloqueoDistribuido;
    private final DisponibilidadCicloVida disponibilidad;

    public TareaMembresiasProgramada(
            MembresiaCicloVidaService cicloVidaService,
            BloqueoDistribuidoCicloVida bloqueoDistribuido,
            DisponibilidadCicloVida disponibilidad) {
        this.cicloVidaService = cicloVidaService;
        this.bloqueoDistribuido = bloqueoDistribuido;
        this.disponibilidad = disponibilidad;
    }

    @Scheduled(
            cron = "${sistemagym.membresias.scheduler.cron:0 0 0 * * *}",
            zone = "${sistemagym.membresias.scheduler.zone:America/Lima}")
    public void revisarMembresiasVencidas() {
        if (!disponibilidad.estaDisponible()) {
            log.info("Ciclo de vida diario omitido mientras termina la revisión obligatoria de inicio.");
            return;
        }
        try {
            MembresiaCicloVidaService.ResultadoCicloVida resultado =
                    bloqueoDistribuido.ejecutarSiDisponible(cicloVidaService::procesarCicloVida);
            if (resultado == null) {
                log.warn("Ciclo diario omitido: otra instancia mantiene el bloqueo distribuido.");
                return;
            }

            log.info("Ciclo de vida completado para {}: {} canceladas por falta de pago y {} vencidas por vigencia.",
                    resultado.fecha(), resultado.canceladas(), resultado.vencidas());
        } catch (Exception error) {
            log.error("Falló la ejecución programada del ciclo de vida de membresías. causa={}",
                    error.getClass().getSimpleName());
        }
    }

    /** Runs synchronously before Spring Boot publishes readiness/accepting traffic. */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Iniciando revisión obligatoria del ciclo de vida antes de habilitar operaciones.");
        try {
            MembresiaCicloVidaService.ResultadoCicloVida resultado =
                    bloqueoDistribuido.ejecutarSiDisponible(cicloVidaService::procesarCicloVida);
            if (resultado == null) {
                throw new IllegalStateException("bloqueo distribuido ocupado");
            }
            log.info("Revisión inicial completada para {}: {} canceladas por falta de pago y {} vencidas por vigencia.",
                    resultado.fecha(), resultado.canceladas(), resultado.vencidas());
            disponibilidad.marcarDisponible();
        } catch (Exception error) {
            log.error("Falló la revisión obligatoria de membresías al iniciar; el backend no quedará disponible. causa={}",
                    error.getClass().getSimpleName());
            throw new IllegalStateException(
                    "No se pudo completar la revisión inicial de membresías; inicio cancelado.");
        }
    }
}
