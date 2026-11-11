package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ResumenNutricionRespuesta;
import com.gimnasio.sistemagym.entity.EvaluacionNutricional;
import com.gimnasio.sistemagym.repository.EvaluacionNutricionalRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResumenNutricionService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final EvaluacionNutricionalRepository evaluacionRepository;
    private final SocioRepository socioRepository;
    private final Clock clock;

    public ResumenNutricionService(
            EvaluacionNutricionalRepository evaluacionRepository,
            SocioRepository socioRepository,
            Clock clock) {
        this.evaluacionRepository = evaluacionRepository;
        this.socioRepository = socioRepository;
        this.clock = clock;
    }

    public ResumenNutricionRespuesta obtenerResumen() {
        LocalDateTime ahoraUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDateTime inicioMesUtc = clock.instant().atZone(LIMA).toLocalDate()
                .withDayOfMonth(1).atStartOfDay().atZone(LIMA)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime inicioSieteDiasUtc = LocalDateTime.ofInstant(
                clock.instant().minusSeconds(7L * 24 * 60 * 60), ZoneOffset.UTC);
        LocalDateTime limiteExclusivo = ahoraUtc.plusNanos(1);

        List<ResumenNutricionRespuesta.EvaluacionReciente> recientes =
                evaluacionRepository.findTop8ByOrderByFechaEvaluacionDescIdEvaluacionDesc()
                        .stream().map(ResumenNutricionService::resumenEvaluacion).toList();

        return new ResumenNutricionRespuesta(
                socioRepository.countByEstadoIgnoreCase("ACTIVO"),
                evaluacionRepository.countByFechaEvaluacionGreaterThanEqualAndFechaEvaluacionLessThan(
                        inicioMesUtc, limiteExclusivo),
                evaluacionRepository.countDistinctSociosEvaluados(inicioMesUtc, limiteExclusivo),
                evaluacionRepository.countByFechaEvaluacionGreaterThanEqualAndFechaEvaluacionLessThan(
                        inicioSieteDiasUtc, limiteExclusivo),
                recientes);
    }

    private static ResumenNutricionRespuesta.EvaluacionReciente resumenEvaluacion(
            EvaluacionNutricional evaluacion) {
        return new ResumenNutricionRespuesta.EvaluacionReciente(
                evaluacion.getIdEvaluacion(),
                evaluacion.getSocio().getIdSocio(),
                evaluacion.getSocio().getCodigoSocio(),
                evaluacion.getSocio().getNombres(),
                evaluacion.getSocio().getApellidos(),
                evaluacion.getFechaEvaluacion().toInstant(ZoneOffset.UTC));
    }
}
