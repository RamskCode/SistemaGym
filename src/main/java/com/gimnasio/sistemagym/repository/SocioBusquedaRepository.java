package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.Socio;

import java.util.List;

public interface SocioBusquedaRepository {

    List<Socio> buscarGlobal(String texto);
}
