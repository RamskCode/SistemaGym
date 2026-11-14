package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.Usuario;

/** Negative versions flag generated initial credentials; JWT validation compares exact versions. */
public final class VersionCredenciales {
    private VersionCredenciales() { }

    public static void marcarContrasenaInicial(Usuario usuario) {
        long current = usuario.getVersionCredenciales();
        if (current == Long.MIN_VALUE || current == Long.MAX_VALUE) {
            throw new IllegalStateException("La versión de credenciales alcanzó su límite.");
        }
        usuario.setVersionCredenciales(-(Math.abs(current) + 1));
    }

    public static void avanzarTrasCambio(Usuario usuario) {
        long current = usuario.getVersionCredenciales();
        if (current < 0) {
            if (current == Long.MIN_VALUE) {
                throw new IllegalStateException("La versión de credenciales alcanzó su límite.");
            }
            usuario.setVersionCredenciales(Math.abs(current));
        } else {
            if (current == Long.MAX_VALUE) {
                throw new IllegalStateException("La versión de credenciales alcanzó su límite.");
            }
            usuario.setVersionCredenciales(current + 1);
        }
    }
}
