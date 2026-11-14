package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.TipoDocumento;

public record MiCuentaRespuesta(
        String nombres,
        String apellidos,
        String correo,
        String rol,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        boolean contrasenaInicial) {
    public MiCuentaRespuesta(String nombres, String apellidos, String correo,
            String rol, TipoDocumento tipoDocumento, String numeroDocumento) {
        this(nombres, apellidos, correo, rol, tipoDocumento, numeroDocumento, false);
    }
}
