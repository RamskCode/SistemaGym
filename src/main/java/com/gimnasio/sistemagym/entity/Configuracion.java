package com.gimnasio.sistemagym.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuraciones")
@Getter
@Setter
@NoArgsConstructor
public class Configuracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_configuracion")
    private Long idConfiguracion;

    @NotBlank(message = "La clave de la configuración es obligatoria.")
    @Size(max = 80, message = "La clave no puede superar los 80 caracteres.")
    @Column(nullable = false, unique = true, length = 80)
    private String clave;

    @NotBlank(message = "El valor de la configuración es obligatorio.")
    @Size(max = 255, message = "El valor no puede superar los 255 caracteres.")
    @Column(nullable = false, length = 255)
    private String valor;

    @Size(max = 255, message = "La descripción no puede superar los 255 caracteres.")
    @Column(length = 255)
    private String descripcion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void alCrear() {
        fechaActualizacion = LocalDateTime.now();
    }

    @PreUpdate
    protected void alActualizar() {
        fechaActualizacion = LocalDateTime.now();
    }

}
