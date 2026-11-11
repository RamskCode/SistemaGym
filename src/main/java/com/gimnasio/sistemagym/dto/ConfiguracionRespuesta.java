package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.Configuracion;

import java.time.LocalDateTime;

public record ConfiguracionRespuesta(
        Long idConfiguracion,
        String clave,
        String valor,
        String descripcion,
        LocalDateTime fechaActualizacion) {

    public static ConfiguracionRespuesta desde(Configuracion configuracion) {
        return new ConfiguracionRespuesta(
                configuracion.getIdConfiguracion(),
                configuracion.getClave(),
                configuracion.getValor(),
                configuracion.getDescripcion(),
                configuracion.getFechaActualizacion()
        );
    }
}
