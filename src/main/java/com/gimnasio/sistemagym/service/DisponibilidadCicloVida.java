package com.gimnasio.sistemagym.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Gates API traffic until the mandatory startup reconciliation succeeds. */
@Component
public class DisponibilidadCicloVida {

    private final AtomicBoolean disponible;

    public DisponibilidadCicloVida(
            @Value("${sistemagym.membresias.scheduler.enabled:false}") boolean schedulerHabilitado) {
        this.disponible = new AtomicBoolean(!schedulerHabilitado);
    }

    public boolean estaDisponible() {
        return disponible.get();
    }

    public void marcarDisponible() {
        disponible.set(true);
    }
}
