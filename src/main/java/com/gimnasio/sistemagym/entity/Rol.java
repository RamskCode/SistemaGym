
package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "roles")
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

    public Rol() {
    }

    public Long getIdRol() {
        return idRol;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
