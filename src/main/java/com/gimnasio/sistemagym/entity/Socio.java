package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "socios")
@Getter
@Setter
@NoArgsConstructor
public class Socio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_socio")
    private Long idSocio;

    @NotBlank(message = "El código del socio es obligatorio.")
    @Size(max = 20, message = "El código no puede superar los 20 caracteres.")
    @Column(name = "codigo_socio", nullable = false, unique = true, length = 20)
    private String codigoSocio;

    @Size(max = 15, message = "El DNI no puede superar los 15 caracteres.")
    @Column(name = "dni", unique = true, length = 15)
    private String dni;

    @Column(name = "tipo_documento", length = 3)
    private String tipoDocumento;

    @Column(name = "numero_documento", length = 20)
    private String numeroDocumento;

    @NotBlank(message = "Los nombres son obligatorios.")
    @Size(max = 100, message = "Los nombres no pueden superar los 100 caracteres.")
    @Column(name = "nombres", nullable = false, length = 100)
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios.")
    @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
    @Column(name = "apellidos", nullable = false, length = 100)
    private String apellidos;

    @PastOrPresent(message = "La fecha de nacimiento no puede ser futura.")
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Size(max = 20, message = "El celular no puede superar los 20 caracteres.")
    @Column(name = "celular", length = 20)
    private String celular;

    @Email(message = "El correo electrónico no tiene un formato válido.")
    @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
    @Column(name = "correo", length = 150)
    private String correo;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @PrePersist
    protected void alCrear() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }

}
