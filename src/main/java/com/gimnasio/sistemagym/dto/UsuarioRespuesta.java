package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.TipoDocumento;
import java.time.LocalDateTime;

public record UsuarioRespuesta(
        Long idUsuario,
        String nombres,
        String apellidos,
        String correo,
        String rol,
        String estado,
        LocalDateTime fechaCreacion,
        TipoDocumento tipoDocumento,
        String numeroDocumento) {

    public UsuarioRespuesta(Long idUsuario, String nombres, String apellidos,
            String correo, String rol, String estado, LocalDateTime fechaCreacion) {
        this(idUsuario, nombres, apellidos, correo, rol, estado, fechaCreacion,
                null, null);
    }
}
