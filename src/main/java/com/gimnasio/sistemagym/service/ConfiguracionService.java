package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ConfiguracionRespuesta;
import com.gimnasio.sistemagym.entity.Configuracion;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.ConfiguracionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    public static final String CLAVE_PRECIO_PASE_DIARIO = "PRECIO_PASE_DIARIO";
    private static final String MARCADOR_ONBOARDING = "ONBOARDING_ADMIN_COMPLETADO";

    private final ConfiguracionRepository configuracionRepository;

    @Transactional(readOnly = true)
    public List<ConfiguracionRespuesta> listar() {
        return configuracionRepository.findAll()
                .stream()
                .map(ConfiguracionRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConfiguracionRespuesta obtener(String clave) {
        return ConfiguracionRespuesta.desde(buscar(normalizarClave(clave)));
    }

    @Transactional
    public ConfiguracionRespuesta actualizar(String clave, String valor) {
        String claveNormalizada = normalizarClave(clave);
        if (MARCADOR_ONBOARDING.equals(claveNormalizada)) {
            throw new IllegalArgumentException("La configuración interna no es modificable.");
        }
        String valorNormalizado = normalizarValor(valor);

        if (CLAVE_PRECIO_PASE_DIARIO.equals(claveNormalizada)) {
            validarPrecioPaseDiario(valorNormalizado);
        }

        Configuracion configuracion = configuracionRepository
                .findLockedByClave(claveNormalizada)
                .orElseGet(() -> {
                    if (!CLAVE_PRECIO_PASE_DIARIO.equals(claveNormalizada)) {
                        throw new RecursoNoEncontradoException(
                                "No se encontro la configuración con clave: " + claveNormalizada);
                    }
                    Configuracion nueva = new Configuracion();
                    nueva.setClave(CLAVE_PRECIO_PASE_DIARIO);
                    nueva.setDescripcion("Tarifa vigente del pase diario.");
                    return nueva;
                });

        configuracion.setValor(valorNormalizado);
        return ConfiguracionRespuesta.desde(
                configuracionRepository.saveAndFlush(configuracion));
    }

    /**
     * Precio vigente del pase diario. Se lee siempre desde la configuración,
     * nunca desde un valor fijo en el servicio.
     */
    @Transactional(readOnly = true)
    public BigDecimal obtenerPrecioPaseDiario() {
        Configuracion configuracion = buscar(CLAVE_PRECIO_PASE_DIARIO);

        BigDecimal precio;
        try {
            precio = new BigDecimal(configuracion.getValor().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "La configuración " + CLAVE_PRECIO_PASE_DIARIO
                            + " no contiene un precio válido.");
        }

        if (precio.signum() <= 0
                || precio.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException(
                    "La configuración " + CLAVE_PRECIO_PASE_DIARIO
                            + " debe ser un monto mayor que cero con hasta dos decimales.");
        }

        return precio.setScale(2);
    }

    private Configuracion buscar(String clave) {
        return configuracionRepository.findByClave(clave)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la configuración con clave: " + clave));
    }

    private String normalizarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalArgumentException(
                    "La clave de la configuración es obligatoria.");
        }
        return clave.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizarValor(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "El valor de la configuración es obligatorio.");
        }
        String valorNormalizado = valor.trim();
        if (valorNormalizado.length() > 255) {
            throw new IllegalArgumentException(
                    "El valor de la configuración no puede superar los 255 caracteres.");
        }
        return valorNormalizado;
    }

    private void validarPrecioPaseDiario(String valor) {
        BigDecimal precio;
        try {
            precio = new BigDecimal(valor);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "El valor de " + CLAVE_PRECIO_PASE_DIARIO
                            + " debe ser un número decimal.");
        }

        if (precio.signum() <= 0
                || precio.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "El valor de " + CLAVE_PRECIO_PASE_DIARIO
                            + " debe ser mayor que cero y tener hasta dos decimales.");
        }
    }
}
