package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.Socio;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SocioRespuesta(
        Long idSocio,
        String codigoSocio,
        String dni,
        String tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String celular,
        String correo,
        String estado,
        LocalDateTime fechaRegistro) {

    public static SocioRespuesta desde(Socio socio) {
        return new SocioRespuesta(
                socio.getIdSocio(),
                socio.getCodigoSocio(),
                socio.getDni(),
                socio.getTipoDocumento() != null ? socio.getTipoDocumento() : (socio.getDni() == null ? null : "DNI"),
                socio.getNumeroDocumento() != null ? socio.getNumeroDocumento() : socio.getDni(),
                socio.getNombres(),
                socio.getApellidos(),
                socio.getFechaNacimiento(),
                socio.getCelular(),
                socio.getCorreo(),
                socio.getEstado(),
                socio.getFechaRegistro()
        );
    }
}
