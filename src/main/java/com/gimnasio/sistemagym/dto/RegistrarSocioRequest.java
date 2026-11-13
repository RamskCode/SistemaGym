package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import com.gimnasio.sistemagym.util.DocumentoIdentidad;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrarSocioRequest {

    @Size(max = 15, message = "El DNI no puede superar los 15 caracteres.")
    private String dni;
    private String tipoDocumento;
    private String numeroDocumento;

    @jakarta.validation.constraints.NotBlank(
            message = "Los nombres son obligatorios."
    )
    @Size(max = 100, message = "Los nombres no pueden superar los 100 caracteres.")
    private String nombres;

    @jakarta.validation.constraints.NotBlank(
            message = "Los apellidos son obligatorios."
    )
    @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
    private String apellidos;

    @PastOrPresent(message = "La fecha de nacimiento no puede ser futura.")
    private LocalDate fechaNacimiento;

    @Size(max = 20, message = "El celular no puede superar los 20 caracteres.")
    private String celular;

    @Email(message = "El correo electrónico no tiene un formato válido.")
    @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
    private String correo;

    @AssertTrue(message = "El documento debe cumplir el formato de DNI o CE.")
    public boolean isDocumentoValido() {
        boolean legacyPresent = dni != null && !dni.isEmpty();
        boolean typedPresent = tipoDocumento != null || numeroDocumento != null;
        if (!typedPresent) return !legacyPresent || DocumentoIdentidad.esValido("DNI", dni);
        if (tipoDocumento == null || numeroDocumento == null
                || !DocumentoIdentidad.esValido(tipoDocumento, numeroDocumento)) return false;
        return !legacyPresent || ("DNI".equalsIgnoreCase(tipoDocumento)
                && dni.equals(numeroDocumento));
    }

}
