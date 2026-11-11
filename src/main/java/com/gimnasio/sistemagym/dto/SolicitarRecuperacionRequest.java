package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitarRecuperacionRequest(
        @NotBlank(message = "Ingresa tu correo electrónico.")
        @Email(message = "Ingresa un correo electrónico válido.")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
        String correo) { }
