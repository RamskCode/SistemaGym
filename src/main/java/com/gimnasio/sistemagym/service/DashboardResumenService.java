package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.DashboardResumenRespuesta;
import com.gimnasio.sistemagym.entity.Ingreso;
import com.gimnasio.sistemagym.entity.TipoIngreso;
import com.gimnasio.sistemagym.repository.IngresoRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.PagoRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardResumenService {
    private static final ZoneId ZONA_GIMNASIO = ZoneId.of("America/Lima");

    private final SocioRepository socios;
    private final PagoRepository pagos;
    private final IngresoRepository ingresos;
    private final MembresiaRepository membresias;

    public DashboardResumenService(SocioRepository socios, PagoRepository pagos,
            IngresoRepository ingresos, MembresiaRepository membresias) {
        this.socios = socios;
        this.pagos = pagos;
        this.ingresos = ingresos;
        this.membresias = membresias;
    }

    public DashboardResumenRespuesta obtenerResumen() {
        LocalDate hoy = LocalDate.now(ZONA_GIMNASIO);
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate primerDia = hoy.minusDays(6);
        LocalDateTime inicioHoy = inicioSistema(hoy);
        LocalDateTime inicioManana = inicioSistema(hoy.plusDays(1));
        LocalDateTime inicioMesDateTime = inicioSistema(inicioMes);

        List<DashboardResumenRespuesta.IngresoDiario> porDia = new ArrayList<>(7);
        for (int day = 0; day < 7; day++) {
            LocalDate fecha = primerDia.plusDays(day);
            porDia.add(new DashboardResumenRespuesta.IngresoDiario(fecha,
                    ingresos.countBetween(inicioSistema(fecha), inicioSistema(fecha.plusDays(1)))));
        }

        List<DashboardResumenRespuesta.ActividadReciente> actividad = ingresos
                .findRecentDashboard(PageRequest.of(0, 5)).stream()
                .map(this::actividad)
                .toList();

        return new DashboardResumenRespuesta(
                socios.countByEstadoIgnoreCase("ACTIVO"),
                pagos.sumConfirmedBetween(inicioMesDateTime, inicioManana)
                        .max(BigDecimal.ZERO),
                pagos.sumConfirmedBetween(inicioHoy, inicioManana).max(BigDecimal.ZERO),
                ingresos.countByFechaHoraGreaterThanEqualAndFechaHoraLessThan(inicioHoy, inicioManana),
                ingresos.countByTipoIngresoAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                        TipoIngreso.SOCIO, inicioHoy, inicioManana),
                ingresos.countByTipoIngresoAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                        TipoIngreso.VISITANTE, inicioHoy, inicioManana),
                socios.countByFechaRegistroGreaterThanEqualAndFechaRegistroLessThan(
                        inicioMesDateTime, inicioManana),
                List.copyOf(porDia),
                actividad,
                new DashboardResumenRespuesta.MembresiasResumen(
                        membresias.countByEstadoIgnoreCase("ACTIVA"),
                        membresias.countByEstadoIgnoreCase("PENDIENTE_PAGO"),
                        membresias.countByEstadoIgnoreCase("VENCIDA"),
                        membresias.countByEstadoIgnoreCase("CANCELADA")));
    }

    private DashboardResumenRespuesta.ActividadReciente actividad(Ingreso ingreso) {
        String nombre = ingreso.getSocio() != null
                ? ingreso.getSocio().getNombres() + " " + ingreso.getSocio().getApellidos()
                : ingreso.getContacto() != null ? ingreso.getContacto().getNombre() : "Nombre no disponible";
        return new DashboardResumenRespuesta.ActividadReciente(
                ingreso.getIdIngreso(), nombre, ingreso.getTipoIngreso().name(),
                ingreso.getFechaHora().atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZONA_GIMNASIO).toOffsetDateTime());
    }

    private LocalDateTime inicioSistema(LocalDate fechaGimnasio) {
        return fechaGimnasio.atStartOfDay(ZONA_GIMNASIO)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
