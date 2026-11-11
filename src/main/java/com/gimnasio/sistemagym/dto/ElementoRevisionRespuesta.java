package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.ElementoRevision;
import com.gimnasio.sistemagym.entity.TipoElementoRevision;
import java.time.LocalDateTime;

public record ElementoRevisionRespuesta(
        Long idElemento,
        String nombre,
        TipoElementoRevision tipo,
        String descripcion,
        String ubicacion,
        boolean activo,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion) {

    public static ElementoRevisionRespuesta desde(ElementoRevision elemento) {
        return new ElementoRevisionRespuesta(
                elemento.getIdElemento(), elemento.getNombre(), elemento.getTipo(),
                elemento.getDescripcion(), elemento.getUbicacion(), elemento.isActivo(),
                elemento.getFechaCreacion(), elemento.getFechaActualizacion());
    }
}
