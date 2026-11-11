package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.gimnasio.sistemagym.dto.IngresoRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarIngresoRequest;
import com.gimnasio.sistemagym.entity.Ingreso;
import com.gimnasio.sistemagym.entity.Pago;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.ContactoPromocionalRepository;
import com.gimnasio.sistemagym.repository.IngresoRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IngresoPaseDiarioSocioTest {

    @Test
    void reingresoDelMismoDiaUsaElPasePagadoSinCrearOtroPago() {
        SocioRepository socios = mock(SocioRepository.class);
        MembresiaRepository membresias = mock(MembresiaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        ContactoPromocionalRepository contactos = mock(ContactoPromocionalRepository.class);
        IngresoRepository ingresos = mock(IngresoRepository.class);
        PagoService pagos = mock(PagoService.class);
        ConfiguracionService configuracion = mock(ConfiguracionService.class);
        GeneradorCodigoSocio codigos = mock(GeneradorCodigoSocio.class);
        Socio socio = new Socio();
        ReflectionTestUtils.setField(socio, "idSocio", 42L);
        socio.setTipoDocumento("DNI");
        socio.setNumeroDocumento("12345678");
        socio.setDni("12345678");
        socio.setEstado("ACTIVO");
        Usuario usuario = new Usuario();
        Ingreso pasePagado = new Ingreso();
        pasePagado.setSocio(socio);
        pasePagado.setTipoIngreso(com.gimnasio.sistemagym.entity.TipoIngreso.VISITANTE);
        Pago pagoOriginal = new Pago();
        pagoOriginal.setMonto(new BigDecimal("12.50"));
        pasePagado.setPago(pagoOriginal);
        String clave = "e4850054-3075-4a6b-916f-3ec900ba8339";

        when(usuarios.findByCorreo("recepcion@example.test")).thenReturn(Optional.of(usuario));
        when(ingresos.findByClaveIdempotencia(clave)).thenReturn(Optional.empty());
        when(socios.findById(42L)).thenReturn(Optional.of(socio));
        when(membresias.findBySocio_IdSocio(42L)).thenReturn(java.util.List.of());
        when(contactos.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, "12345678"))
                .thenReturn(Optional.empty());
        when(ingresos.buscarPaseDiarioVigente(eq(42L), any(), any())).thenReturn(Optional.of(pasePagado));
        when(ingresos.saveAndFlush(any(Ingreso.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IngresoService servicio = new IngresoService(
                socios, codigos, membresias, usuarios, contactos, ingresos, null, pagos, configuracion,
                Clock.system(ZoneId.of("America/Lima")));
        ReflectionTestUtils.setField(servicio, "esquemaPaseDiarioV2Habilitado", true);
        RegistrarIngresoRequest solicitud = new RegistrarIngresoRequest(
                "DNI", "12345678", 42L, null, null, null, null,
                "EFECTIVO", null, clave);

        IngresoRespuesta respuesta = servicio.registrar(solicitud, "recepcion@example.test");

        assertEquals("REINGRESO_AUTORIZADO", respuesta.resultado());
        assertEquals(false, respuesta.paseDiarioCobrado());
        assertEquals(null, respuesta.idPago());
        var captor = org.mockito.ArgumentCaptor.forClass(Ingreso.class);
        verify(ingresos).saveAndFlush(captor.capture());
        assertSame(pasePagado, captor.getValue().getPaseOrigen());
        assertSame(socio, captor.getValue().getSocio());
        verify(pagos, never()).registrarPago(any(), any(), any(), any(), any());
        verify(configuracion, never()).obtenerPrecioPaseDiario();
    }

    @Test
    void registraPersonaCanonicaYPaseSinCrearContactoNiMembresia() {
        SocioRepository socios = mock(SocioRepository.class);
        MembresiaRepository membresias = mock(MembresiaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        ContactoPromocionalRepository contactos = mock(ContactoPromocionalRepository.class);
        IngresoRepository ingresos = mock(IngresoRepository.class);
        PagoService pagos = mock(PagoService.class);
        ConfiguracionService configuracion = mock(ConfiguracionService.class);
        GeneradorCodigoSocio codigos = mock(GeneradorCodigoSocio.class);
        Usuario usuario = new Usuario();
        Socio personaGuardada = new Socio();

        when(usuarios.findByCorreo("recepcion@example.test")).thenReturn(Optional.of(usuario));
        Ingreso[] ingresoPersistido = new Ingreso[1];
        String clave = "d9428888-122b-44c6-8aa2-aed4fedb4d23";
        when(ingresos.findByClaveIdempotencia(clave))
                .thenAnswer(ignored -> Optional.ofNullable(ingresoPersistido[0]));
        when(socios.findByTipoDocumentoAndNumeroDocumento("CE", "123456789012"))
                .thenReturn(Optional.empty());
        when(socios.saveAndFlush(any(Socio.class))).thenAnswer(invocation -> {
            Socio persona = invocation.getArgument(0);
            personaGuardada.setCodigoSocio(persona.getCodigoSocio());
            personaGuardada.setTipoDocumento(persona.getTipoDocumento());
            personaGuardada.setNumeroDocumento(persona.getNumeroDocumento());
            personaGuardada.setNombres(persona.getNombres());
            personaGuardada.setApellidos(persona.getApellidos());
            personaGuardada.setEstado(persona.getEstado());
            return persona;
        });
        when(codigos.crearConSiguienteCodigo(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<String, Socio> crear = invocation.getArgument(0);
            return crear.apply("SOC-0001");
        });
        when(contactos.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CE, "123456789012"))
                .thenReturn(Optional.empty());
        when(configuracion.obtenerPrecioPaseDiario()).thenReturn(new BigDecimal("12.50"));
        Pago pago = new Pago();
        pago.setMonto(new BigDecimal("12.50"));
        when(pagos.registrarPago(isNull(), eq(new BigDecimal("12.50")), eq("EFECTIVO"), eq("REF-1"), eq(null)))
                .thenReturn(pago);
        when(ingresos.buscarPaseDiarioVigente(isNull(), any(), any())).thenReturn(Optional.empty());
        when(ingresos.saveAndFlush(any(Ingreso.class))).thenAnswer(invocation -> {
            ingresoPersistido[0] = invocation.getArgument(0);
            return ingresoPersistido[0];
        });

        IngresoService servicio = new IngresoService(
                socios, codigos, membresias, usuarios, contactos, ingresos, null, pagos, configuracion,
                Clock.system(ZoneId.of("America/Lima")));
        ReflectionTestUtils.setField(servicio, "esquemaPaseDiarioV2Habilitado", true);
        RegistrarIngresoRequest solicitud = new RegistrarIngresoRequest(
                "CE", "123456789012", null, "Ana", "Quispe", "999888777", null,
                "EFECTIVO", "REF-1", clave);

        IngresoRespuesta respuesta = servicio.registrar(solicitud, "recepcion@example.test");
        IngresoRespuesta retry = servicio.registrar(solicitud, "recepcion@example.test");

        assertEquals("VISITANTE", respuesta.tipoIngreso());
        assertEquals("IDEMPOTENTE", retry.resultado());
        assertEquals("CE", personaGuardada.getTipoDocumento());
        assertEquals("123456789012", personaGuardada.getNumeroDocumento());
        assertEquals("SOC-0001", personaGuardada.getCodigoSocio());
        assertEquals(new BigDecimal("12.50"), respuesta.montoCobrado());
        ArgumentCaptor<Ingreso> ingresoCapturado = ArgumentCaptor.forClass(Ingreso.class);
        verify(ingresos, times(1)).saveAndFlush(ingresoCapturado.capture());
        assertEquals("123456789012", ingresoCapturado.getValue().getSocio().getNumeroDocumento());
        assertEquals(pago, ingresoCapturado.getValue().getPago());
        verify(contactos, never()).saveAndFlush(any());
        verify(membresias, never()).save(any());
        verify(pagos).registrarPago(isNull(), eq(new BigDecimal("12.50")), eq("EFECTIVO"), eq("REF-1"), eq(null));
        verify(ingresos).saveAndFlush(any(Ingreso.class));
    }
}
