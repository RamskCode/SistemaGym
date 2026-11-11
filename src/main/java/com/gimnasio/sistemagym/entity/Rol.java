
package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Long idRol;

    @NotBlank(message = "El nombre del rol es obligatorio.")
    @Size(max = 30, message = "El nombre no puede superar los 30 caracteres.")
    @Column(nullable = false, unique = true, length = 30)
    private String nombre;

    @Size(max = 150, message = "La descripción no puede superar los 150 caracteres.")
    @Column(length = 150)
    private String descripcion;

}
