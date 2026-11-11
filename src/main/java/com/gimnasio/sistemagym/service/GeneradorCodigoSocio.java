package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.Socio;

import java.util.function.Function;

public interface GeneradorCodigoSocio {

    Socio crearConSiguienteCodigo(Function<String, Socio> crearSocio);
}
