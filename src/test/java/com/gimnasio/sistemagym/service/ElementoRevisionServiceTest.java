package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ElementoRevisionRequest;
import com.gimnasio.sistemagym.entity.ElementoRevision;
import com.gimnasio.sistemagym.entity.TipoElementoRevision;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.ElementoRevisionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElementoRevisionServiceTest {

    @Mock
    private ElementoRevisionRepository repository;

    @InjectMocks
    private ElementoRevisionService service;

    @Test
    void crearNormalizaCamposYActivaElemento() {
        when(repository.save(any(ElementoRevision.class))).thenAnswer(invocation -> {
            ElementoRevision elemento = invocation.getArgument(0);
            elemento.setIdElemento(12L);
            return elemento;
        });

        var respuesta = service.crear(new ElementoRevisionRequest(
                "  Cinta  ", TipoElementoRevision.MAQUINA, "  Cardio  ", "  Piso 1 "));

        assertEquals(12L, respuesta.idElemento());
        assertEquals("Cinta", respuesta.nombre());
        assertEquals("Cardio", respuesta.descripcion());
        assertEquals("Piso 1", respuesta.ubicacion());
        assertTrue(respuesta.activo());
        assertEquals(respuesta.fechaCreacion(), respuesta.fechaActualizacion());
    }

    @Test
    void listarActivosSoloUsaConsultaDeActivos() {
        when(repository.findByActivoTrueOrderByNombreAscIdElementoAsc())
                .thenReturn(List.of(elemento(true)));

        var elementos = service.listarActivos();

        assertEquals(1, elementos.size());
        assertTrue(elementos.getFirst().activo());
        verify(repository).findByActivoTrueOrderByNombreAscIdElementoAsc();
    }

    @Test
    void desactivarEsLogicoYConservaElRegistro() {
        ElementoRevision elemento = elemento(true);
        when(repository.findById(7L)).thenReturn(Optional.of(elemento));
        when(repository.save(elemento)).thenReturn(elemento);

        var respuesta = service.desactivar(7L);

        assertFalse(respuesta.activo());
        verify(repository).save(elemento);
    }

    @Test
    void idInexistenteDevuelveRecursoNoEncontrado() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.buscarPorId(99L));
    }

    @Test
    void rechazaIdNoPositivo() {
        assertThrows(IllegalArgumentException.class, () -> service.buscarPorId(0L));
    }

    private ElementoRevision elemento(boolean activo) {
        ElementoRevision elemento = new ElementoRevision();
        elemento.setIdElemento(7L);
        elemento.setNombre("Cinta");
        elemento.setTipo(TipoElementoRevision.MAQUINA);
        elemento.setActivo(activo);
        elemento.setFechaCreacion(LocalDateTime.of(2026, 1, 1, 10, 0));
        elemento.setFechaActualizacion(LocalDateTime.of(2026, 1, 1, 10, 0));
        return elemento;
    }
}
