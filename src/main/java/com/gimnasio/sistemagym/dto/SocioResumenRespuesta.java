package com.gimnasio.sistemagym.dto;

public record SocioResumenRespuesta(
        Long idSocio,
        String codigoSocio,
        String nombres,
        String apellidos,
        String estado) {

    public static SocioResumenRespuesta desde(SocioRespuesta socio) {
        return new SocioResumenRespuesta(
                socio.idSocio(), socio.codigoSocio(), socio.nombres(), socio.apellidos(),
                socio.estado());
    }
}
