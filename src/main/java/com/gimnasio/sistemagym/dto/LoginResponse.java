package com.gimnasio.sistemagym.dto;

public record LoginResponse(String token, String tipo, long expiraEnMs) {
}
