package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.EvaluacionNutricionalRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarEvaluacionNutricionalRequest;
import com.gimnasio.sistemagym.entity.EvaluacionNutricional;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.EvaluacionNutricionalRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EvaluacionNutricionalService {

    private final EvaluacionNutricionalRepository evaluacionRepository;
    private final SocioRepository socioRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public EvaluacionNutricionalService(
            EvaluacionNutricionalRepository evaluacionRepository,
            SocioRepository socioRepository,
            UsuarioRepository usuarioRepository,
            Clock clock) {
        this.evaluacionRepository = evaluacionRepository;
        this.socioRepository = socioRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Transactional
    public EvaluacionNutricionalRespuesta registrar(
            Long idSocio, RegistrarEvaluacionNutricionalRequest request,
            String correoAutenticado) {
        Socio socio = socioRepository.findById(idSocio)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un socio con el ID " + idSocio + "."));
        if (!"ACTIVO".equals(socio.getEstado())) {
            throw new ConflictoException(
                    "No se puede registrar una evaluación para un socio inactivo.");
        }
        Usuario nutricionista = usuarioRepository.findByCorreo(correoAutenticado)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario con el correo autenticado."));

        EvaluacionNutricional evaluacion = new EvaluacionNutricional();
        evaluacion.setSocio(socio);
        evaluacion.setNutricionista(nutricionista);
        evaluacion.setFechaEvaluacion(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        evaluacion.setPesoKg(request.pesoKg());
        evaluacion.setTallaM(request.tallaM());
        evaluacion.setPorcentajeGrasa(request.porcentajeGrasa());
        evaluacion.setMasaMuscularKg(request.masaMuscularKg());
        evaluacion.setHabitosAlimenticios(request.habitosAlimenticios().trim());
        evaluacion.setObjetivosNutricionales(request.objetivosNutricionales().trim());
        evaluacion.setRecomendacionesNutricionales(
                request.recomendacionesNutricionales().trim());
        evaluacion.setObservacionesSeguimiento(request.observacionesSeguimiento() == null
                ? null : request.observacionesSeguimiento().trim());

        return EvaluacionNutricionalRespuesta.desde(
                evaluacionRepository.save(evaluacion));
    }

    public List<EvaluacionNutricionalRespuesta> historial(Long idSocio) {
        socioRepository.findById(idSocio)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un socio con el ID " + idSocio + "."));

        return evaluacionRepository
                .findBySocio_IdSocioOrderByFechaEvaluacionDescIdEvaluacionDesc(idSocio)
                .stream()
                .map(EvaluacionNutricionalRespuesta::desde)
                .toList();
    }
}
