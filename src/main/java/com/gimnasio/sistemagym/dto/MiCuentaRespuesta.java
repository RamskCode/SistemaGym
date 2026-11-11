package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.TipoDocumento;

public record MiCuentaRespuesta(
        String nombres,
        String apellidos,
        String correo,
        String rol,
        TipoDocumento tipoDocumento,
        String numeroDocumento) {
}
