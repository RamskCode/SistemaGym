package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ElementoRevisionRequest;
import com.gimnasio.sistemagym.dto.ElementoRevisionRespuesta;
import com.gimnasio.sistemagym.entity.ElementoRevision;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.ElementoRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ElementoRevisionService {

    private final ElementoRevisionRepository repository;

    public ElementoRevisionService(ElementoRevisionRepository repository) {
        this.repository = repository;
    }

    public List<ElementoRevisionRespuesta> listarTodos() {
        return repository.findAllByOrderByNombreAscIdElementoAsc().stream()
                .map(ElementoRevisionRespuesta::desde)
                .toList();
    }

    public List<ElementoRevisionRespuesta> listarActivos() {
        return repository.findByActivoTrueOrderByNombreAscIdElementoAsc().stream()
                .map(ElementoRevisionRespuesta::desde)
                .toList();
    }

    public ElementoRevisionRespuesta buscarPorId(Long id) {
        return ElementoRevisionRespuesta.desde(obtener(id));
    }

    public ElementoRevisionRespuesta buscarActivoPorId(Long id) {
        ElementoRevision elemento = obtener(id);
        if (!elemento.isActivo()) {
            throw new RecursoNoEncontradoException(
                    "No existe un elemento activo con el ID " + id + ".");
        }
        return ElementoRevisionRespuesta.desde(elemento);
    }

    @Transactional
    public ElementoRevisionRespuesta crear(ElementoRevisionRequest solicitud) {
        LocalDateTime ahoraUtc = ahoraUtc();
        ElementoRevision elemento = new ElementoRevision();
        aplicar(elemento, solicitud);
        elemento.setActivo(true);
        elemento.setFechaCreacion(ahoraUtc);
        elemento.setFechaActualizacion(ahoraUtc);
        return ElementoRevisionRespuesta.desde(repository.save(elemento));
    }

    @Transactional
    public ElementoRevisionRespuesta actualizar(Long id, ElementoRevisionRequest solicitud) {
        ElementoRevision elemento = obtener(id);
        aplicar(elemento, solicitud);
        elemento.setFechaActualizacion(ahoraUtc());
        return ElementoRevisionRespuesta.desde(repository.save(elemento));
    }

    @Transactional
    public ElementoRevisionRespuesta activar(Long id) {
        return cambiarEstado(id, true);
    }

    @Transactional
    public ElementoRevisionRespuesta desactivar(Long id) {
        return cambiarEstado(id, false);
    }

    private ElementoRevisionRespuesta cambiarEstado(Long id, boolean activo) {
        ElementoRevision elemento = obtener(id);
        elemento.setActivo(activo);
        elemento.setFechaActualizacion(ahoraUtc());
        return ElementoRevisionRespuesta.desde(repository.save(elemento));
    }

    private ElementoRevision obtener(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor que cero.");
        }
        return repository.findById(id).orElseThrow(() ->
                new RecursoNoEncontradoException(
                        "No existe un elemento de revisión con el ID " + id + "."));
    }

    private void aplicar(ElementoRevision elemento, ElementoRevisionRequest solicitud) {
        if (solicitud.nombre() == null || solicitud.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }
        if (solicitud.tipo() == null) {
            throw new IllegalArgumentException("El tipo de elemento es obligatorio.");
        }
        elemento.setNombre(solicitud.nombre().trim());
        elemento.setTipo(solicitud.tipo());
        elemento.setDescripcion(normalizarOpcional(solicitud.descripcion()));
        elemento.setUbicacion(normalizarOpcional(solicitud.ubicacion()));
    }

    private String normalizarOpcional(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private LocalDateTime ahoraUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
