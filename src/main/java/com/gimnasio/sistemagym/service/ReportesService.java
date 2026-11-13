package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ReporteFilaRespuesta;
import com.gimnasio.sistemagym.dto.ReporteResumenRespuesta;
import com.gimnasio.sistemagym.dto.ReporteResumenRespuesta.PuntoReporte;
import com.gimnasio.sistemagym.dto.PlanReporteRespuesta;
import com.gimnasio.sistemagym.dto.DetalleIngresoClienteRespuesta;
import com.gimnasio.sistemagym.entity.Ingreso;
import com.gimnasio.sistemagym.entity.Pago;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.Pago;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.repository.IngresoRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.PagoRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.PlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportesService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final PagoRepository pagos;
    private final SocioRepository socios;
    private final MembresiaRepository membresias;
    private final IngresoRepository ingresos;
    private final PlanRepository planes;

    public ReportesService(PagoRepository pagos, SocioRepository socios,
            MembresiaRepository membresias, IngresoRepository ingresos, PlanRepository planes) {
        this.pagos = pagos;
        this.socios = socios;
        this.membresias = membresias;
        this.ingresos = ingresos;
        this.planes = planes;
    }

    public List<PlanReporteRespuesta> planes() {
        return planes.findAll(org.springframework.data.domain.Sort.by("nombre")).stream()
                .map(plan -> new PlanReporteRespuesta(plan.getIdPlan(), plan.getNombre())).toList();
    }

    public ReporteResumenRespuesta resumen(LocalDate desde, LocalDate hasta, Long planId) {
        validar(desde, hasta);
        LocalDateTime inicio = inicio(desde);
        LocalDateTime fin = inicio(hasta.plusDays(1));
        LocalDate hoy = LocalDate.now(LIMA);
        Map<String, PagoAgg> pagosPorDia = new LinkedHashMap<>();
        for (Object[] fila : pagos.reportConfirmedTimeline(inicio, fin)) {
            String mes = mes(((Number) fila[0]).intValue(), ((Number) fila[1]).intValue());
            PagoAgg item = pagosPorDia.computeIfAbsent(mes, ignored -> new PagoAgg());
            item.cantidad += ((Number) fila[2]).longValue();
            item.monto = item.monto.add((BigDecimal) fila[3]);
        }
        Map<String, Long> sociosPorDia = new LinkedHashMap<>();
        for (Object[] fila : socios.reportNewTimeline(inicio, fin)) {
            sociosPorDia.merge(mes(((Number) fila[0]).intValue(), ((Number) fila[1]).intValue()), ((Number) fila[2]).longValue(), Long::sum);
        }
        Map<String, long[]> ingresosPorDia = new LinkedHashMap<>();
        for (Object[] fila : ingresos.reportTimeline(inicio, fin)) {
            String mes = mes(((Number) fila[0]).intValue(), ((Number) fila[1]).intValue());
            long[] item = ingresosPorDia.computeIfAbsent(mes, ignored -> new long[2]);
            int tipo = "SOCIO".equals(fila[2].toString()) ? 0 : 1;
            item[tipo] += ((Number) fila[3]).longValue();
        }
        List<PuntoReporte> planes = membresias.reportPlansFiltered(inicio, fin, planId).stream()
                .map(row -> new PuntoReporte(String.valueOf(row[1]), ((Number) row[2]).longValue(), BigDecimal.ZERO, ((Number) row[0]).longValue())).toList();
        long activas = membresias.countByEstadoIgnoreCase("ACTIVA");
        long vencidas = membresias.countByEstadoIgnoreCase("VENCIDA");
        long proximas = membresias.reportUpcoming(hoy, hoy.plusDays(7));
        java.util.Set<String> personasUnicas = new java.util.HashSet<>();
        java.util.Set<String> personasSocios = new java.util.HashSet<>();
        java.util.Set<String> personasVisitantes = new java.util.HashSet<>();
        for (Object[] persona : ingresos.reportDistinctPeople(inicio, fin)) {
            String clavePersona = persona[1] + ":" + persona[2].toString().trim().toUpperCase(java.util.Locale.ROOT);
            personasUnicas.add(clavePersona);
            if ("SOCIO".equals(persona[0].toString())) personasSocios.add(clavePersona);
            else personasVisitantes.add(clavePersona);
        }
        List<com.gimnasio.sistemagym.dto.ReporteResumenRespuesta.ResponsableReporte> responsables =
                ingresos.reportRegistrars(inicio, fin).stream()
                        .map(fila -> new com.gimnasio.sistemagym.dto.ReporteResumenRespuesta.ResponsableReporte(
                                ((Number) fila[0]).longValue(), fila[1] + " " + fila[2]))
                        .toList();
        return new ReporteResumenRespuesta(
                pagos.sumConfirmedBetween(inicio, fin),
                pagos.countByEstadoIgnoreCaseAndFechaPagoGreaterThanEqualAndFechaPagoLessThan("CONFIRMADO", inicio, fin),
                socios.countByFechaRegistroGreaterThanEqualAndFechaRegistroLessThan(inicio, fin),
                activas, vencidas, proximas,
                ingresos.reportSocioCount(inicio, fin), ingresos.reportVisitanteCount(inicio, fin),
                personasUnicas.size(), personasSocios.size(), personasVisitantes.size(), responsables,
                puntosPagos(pagosPorDia), puntos(sociosPorDia), puntosIngresos(ingresosPorDia),
                planes);
    }

    public List<ReporteFilaRespuesta> detalle(String tipo, LocalDate desde, LocalDate hasta,
            Long planId, int pagina, int tamano) {
        return detalle(tipo, desde, hasta, planId, pagina, tamano, "", "");
    }

    public org.springframework.data.domain.Page<ReporteFilaRespuesta> historialIngresos(
            LocalDate desde, LocalDate hasta, int pagina, int tamano,
            String buscar, String modalidad) {
        validar(desde, hasta);
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new IllegalArgumentException("Paginación inválida.");
        }
        if (buscar == null || buscar.length() > 100 || modalidad == null
                || !java.util.Set.of("", "MEMBRESIA", "PASE_DIARIO").contains(modalidad)) {
            throw new IllegalArgumentException("Filtro de ingresos inválido.");
        }
        LocalDateTime inicio = inicio(desde);
        LocalDateTime fin = inicio(hasta.plusDays(1));
        PageRequest page = PageRequest.of(pagina, tamano, Sort.unsorted());
        return ingresos.reportPageFiltered(inicio, fin, buscar.trim().toLowerCase(),
                modalidad, null, page).map(this::filaIngreso);
    }

    public DetalleIngresoClienteRespuesta detalleIngresoCliente(Long idIngreso) {
        Ingreso ingreso = ingresos.findForAdministrativeDetail(idIngreso)
                .orElseThrow(() -> new com.gimnasio.sistemagym.exception.RecursoNoEncontradoException(
                        "No se encontró el acceso solicitado."));
        if (ingreso.getTipoIngreso() == com.gimnasio.sistemagym.entity.TipoIngreso.SOCIO) {
            LocalDate fechaAcceso = lima(ingreso.getFechaHora()).toLocalDate();
            List<Membresia> posibles = ingreso.getSocio() == null ? List.of()
                    : membresias.findWithPlanBySocioId(ingreso.getSocio().getIdSocio()).stream()
                            .filter(m -> m.getFechaCreacion() != null
                                    && !m.getFechaCreacion().isAfter(ingreso.getFechaHora()))
                            .filter(m -> m.getFechaInicio() != null && m.getFechaVencimiento() != null)
                            .filter(m -> !fechaAcceso.isBefore(m.getFechaInicio())
                                    && !fechaAcceso.isAfter(m.getFechaVencimiento()))
                            .toList();
            String plan = posibles.size() == 1 && posibles.get(0).getPlan() != null
                    ? posibles.get(0).getPlan().getNombre() : null;
            String mensaje = plan == null
                    ? "No se puede determinar el plan con seguridad: el ingreso no guarda un vínculo directo con la membresía específica."
                    : null;
            return new DetalleIngresoClienteRespuesta(idIngreso, "MEMBRESIA", plan, null, mensaje);
        }

        Ingreso ventaOriginal = ingreso.getPaseOrigen() == null ? ingreso : ingreso.getPaseOrigen();
        Pago pagoOriginal = ventaOriginal.getPago();
        if (pagoOriginal == null || !"CONFIRMADO".equalsIgnoreCase(pagoOriginal.getEstado())
                || pagoOriginal.getMonto() == null) {
            return new DetalleIngresoClienteRespuesta(idIngreso, "PASE_DIARIO", null, null,
                    "No hay un pago confirmado vinculado a la venta original de este pase.");
        }
        return new DetalleIngresoClienteRespuesta(idIngreso, "PASE_DIARIO", null,
                pagoOriginal.getMonto(), null);
    }

    public List<ReporteFilaRespuesta> detalle(String tipo, LocalDate desde, LocalDate hasta,
            Long planId, int pagina, int tamano, String buscar, String modalidad) {
        return detalle(tipo, desde, hasta, planId, pagina, tamano, buscar, modalidad, null);
    }

    public List<ReporteFilaRespuesta> detalle(String tipo, LocalDate desde, LocalDate hasta,
            Long planId, int pagina, int tamano, String buscar, String modalidad, Long responsableId) {
        validar(desde, hasta);
        if (pagina < 0 || tamano < 1 || tamano > 100) throw new IllegalArgumentException("Paginación inválida.");
        if (buscar == null || modalidad == null || !java.util.Set.of("", "MEMBRESIA", "PASE_DIARIO").contains(modalidad))
            throw new IllegalArgumentException("Filtro de ingresos inválido.");
        LocalDateTime inicio = inicio(desde), fin = inicio(hasta.plusDays(1));
        PageRequest page = PageRequest.of(pagina, tamano, Sort.unsorted());
        return switch (tipo.toLowerCase()) {
            case "pagos" -> pagos.reportConfirmedBetween(inicio, fin, page).stream().map(this::filaPago).toList();
            case "socios" -> socios.reportNewBetween(inicio, fin, page).stream().map(this::filaSocio).toList();
            case "membresias" -> membresias.reportBetween(inicio, fin, planId, page).stream().map(this::filaMembresia).toList();
            case "ingresos" -> ingresos.reportPageFiltered(inicio, fin, buscar.trim().toLowerCase(), modalidad, responsableId, page)
                    .stream().map(this::filaIngreso).toList();
            default -> throw new IllegalArgumentException("Tipo de reporte inválido.");
        };
    }

    private ReporteFilaRespuesta filaPago(Pago p) { return new ReporteFilaRespuesta(p.getIdPago(), "PAGO", "Pago #" + p.getIdPago(), p.getMetodoPago(), p.getEstado(), p.getMonto(), lima(p.getFechaPago())); }
    private ReporteFilaRespuesta filaSocio(Socio s) { return new ReporteFilaRespuesta(s.getIdSocio(), "SOCIO", s.getNombres() + " " + s.getApellidos(), s.getCodigoSocio(), s.getEstado(), null, lima(s.getFechaRegistro())); }
    private ReporteFilaRespuesta filaMembresia(Membresia m) { return new ReporteFilaRespuesta(m.getIdMembresia(), "MEMBRESÍA", m.getSocio().getCodigoSocio(), m.getPlan().getNombre(), m.getEstado(), m.getPrecioAcordado(), m.getFechaCreacion()); }
    private ReporteFilaRespuesta filaIngreso(Ingreso i) {
        String persona = i.getSocio() != null
                ? i.getSocio().getNombres() + " " + i.getSocio().getApellidos()
                : i.getContacto() != null ? i.getContacto().getNombre() : "Visitante histórico";
        String referencia = i.getTipoIngreso() == com.gimnasio.sistemagym.entity.TipoIngreso.VISITANTE
                ? "Pase diario" : "Ingreso con membresía";
        String modalidad = i.getTipoIngreso() == com.gimnasio.sistemagym.entity.TipoIngreso.SOCIO
                ? "MEMBRESIA"
                : i.getPaseOrigen() != null || i.getPago() != null ? "PASE_DIARIO" : null;
        String operacion = i.getPaseOrigen() != null ? "REINGRESO" : "PRIMER_INGRESO";
        String numero = i.getNumeroDocumento();
        String documento = numero == null || numero.length() < 4 ? "No disponible"
                : i.getTipoDocumento().name() + " · ••••" + numero.substring(numero.length() - 4);
        String responsable = i.getUsuario() == null ? null
                : i.getUsuario().getNombres() + " " + i.getUsuario().getApellidos();
        return new ReporteFilaRespuesta(i.getIdIngreso(), i.getTipoIngreso().name(), persona,
                referencia, "REGISTRADO", null, lima(i.getFechaHora()), documento,
                modalidad, operacion, responsable);
    }
    private LocalDateTime lima(LocalDateTime date) { return date.atZone(ZoneId.systemDefault()).withZoneSameInstant(LIMA).toLocalDateTime(); }
    private LocalDateTime inicio(LocalDate date) { return date.atStartOfDay(LIMA).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(); }
    private String mes(int year, int month) { return year + "-" + String.format("%02d", month); }
    private void validar(LocalDate desde, LocalDate hasta) { if (desde == null || hasta == null || hasta.isBefore(desde) || hasta.toEpochDay() - desde.toEpochDay() > 730) throw new IllegalArgumentException("El período debe ser válido y no superar dos años."); }
    private List<PuntoReporte> puntosPagos(Map<String, PagoAgg> map) { return map.entrySet().stream().map(e -> new PuntoReporte(e.getKey(), e.getValue().cantidad, e.getValue().monto)).toList(); }
    private List<PuntoReporte> puntos(Map<String, Long> map) { return map.entrySet().stream().map(e -> new PuntoReporte(e.getKey(), e.getValue(), BigDecimal.ZERO)).toList(); }
    private List<PuntoReporte> puntosIngresos(Map<String, long[]> map) { return map.entrySet().stream().map(e -> new PuntoReporte(e.getKey(), e.getValue()[0] + e.getValue()[1], BigDecimal.ZERO)).toList(); }
    private static final class PagoAgg { long cantidad; BigDecimal monto = BigDecimal.ZERO; }
}
