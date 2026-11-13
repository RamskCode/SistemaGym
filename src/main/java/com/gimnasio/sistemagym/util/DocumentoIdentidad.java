package com.gimnasio.sistemagym.util;

import com.gimnasio.sistemagym.entity.TipoDocumento;
import java.util.Locale;

/** Canonical DNI/CE validation and normalization rules. */
public final class DocumentoIdentidad {
    private DocumentoIdentidad() { }

    public static boolean esValido(TipoDocumento tipo, String numero) {
        if (tipo == null || numero == null) return false;
        return switch (tipo) {
            case DNI -> numero.matches("[0-9]{8}");
            case CE -> numero.matches("[0-9]{1,12}");
        };
    }

    public static boolean esValido(String tipo, String numero) {
        if (tipo == null) return false;
        try {
            return esValido(TipoDocumento.valueOf(tipo.trim().toUpperCase(Locale.ROOT)), numero);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static String normalizar(TipoDocumento tipo, String numero) {
        if (!esValido(tipo, numero)) {
            throw new IllegalArgumentException(tipo == TipoDocumento.DNI
                    ? "El DNI debe contener exactamente 8 dígitos, sin espacios ni símbolos."
                    : "El CE debe contener entre 1 y 12 dígitos, sin espacios ni símbolos.");
        }
        return numero;
    }
}
