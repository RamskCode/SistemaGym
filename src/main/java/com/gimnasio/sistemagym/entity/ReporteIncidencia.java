package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "reportes_incidencias", uniqueConstraints =
        @UniqueConstraint(name = "uk_reportes_detalle_checklist",
                columnNames = "id_detalle_checklist"))
public class ReporteIncidencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reporte")
    private Long idReporte;

    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_entrenador", nullable = false)
    private Usuario entrenador;

    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_elemento", nullable = false)
    private ElementoRevision elemento;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_detalle_checklist", unique = true)
    private DetalleChecklist detalleChecklist;

    @NotNull @Column(nullable = false, length = 4000)
    private String descripcion;

    @NotNull @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrioridadIncidencia prioridad;

    @NotNull @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoIncidencia estado;

    @Column(name = "observaciones_actuales", length = 2000)
    private String observacionesActuales;

    @NotNull @Column(name = "fecha_reporte", nullable = false, updatable = false)
    private LocalDateTime fechaReporte;

    @NotNull @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "fecha_solucion")
    private LocalDateTime fechaSolucion;

    public ReporteIncidencia() {}
    public Long getIdReporte() { return idReporte; }
    public void setIdReporte(Long value) { idReporte = value; }
    public Usuario getEntrenador() { return entrenador; }
    public void setEntrenador(Usuario value) { entrenador = value; }
    public ElementoRevision getElemento() { return elemento; }
    public void setElemento(ElementoRevision value) { elemento = value; }
    public DetalleChecklist getDetalleChecklist() { return detalleChecklist; }
    public void setDetalleChecklist(DetalleChecklist value) { detalleChecklist = value; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String value) { descripcion = value; }
    public PrioridadIncidencia getPrioridad() { return prioridad; }
    public void setPrioridad(PrioridadIncidencia value) { prioridad = value; }
    public EstadoIncidencia getEstado() { return estado; }
    public void setEstado(EstadoIncidencia value) { estado = value; }
    public String getObservacionesActuales() { return observacionesActuales; }
    public void setObservacionesActuales(String value) { observacionesActuales = value; }
    public LocalDateTime getFechaReporte() { return fechaReporte; }
    public void setFechaReporte(LocalDateTime value) { fechaReporte = value; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime value) { fechaActualizacion = value; }
    public LocalDateTime getFechaSolucion() { return fechaSolucion; }
    public void setFechaSolucion(LocalDateTime value) { fechaSolucion = value; }
}
