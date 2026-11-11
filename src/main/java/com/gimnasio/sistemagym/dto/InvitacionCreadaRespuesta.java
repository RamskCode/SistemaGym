package com.gimnasio.sistemagym.dto;

public record InvitacionCreadaRespuesta(UsuarioRespuesta usuario, boolean correoEnviado,
        String mensaje) { }
