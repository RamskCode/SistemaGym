package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "historial_reportes_incidencia")
public class HistorialReporteIncidencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;

    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_reporte", nullable = false)
    private ReporteIncidencia reporte;

    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoIncidencia estadoAnterior;

    @NotNull @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoIncidencia estadoNuevo;

    @Size(max = 2000) @Column(length = 2000)
    private String observacion;

    @NotNull @Column(name = "fecha_hora", nullable = false, updatable = false)
    private LocalDateTime fechaHora;

    public HistorialReporteIncidencia() {}
    public Long getIdHistorial() { return idHistorial; }
    public void setIdHistorial(Long value) { idHistorial = value; }
    public ReporteIncidencia getReporte() { return reporte; }
    public void setReporte(ReporteIncidencia value) { reporte = value; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario value) { usuario = value; }
    public EstadoIncidencia getEstadoAnterior() { return estadoAnterior; }
    public void setEstadoAnterior(EstadoIncidencia value) { estadoAnterior = value; }
    public EstadoIncidencia getEstadoNuevo() { return estadoNuevo; }
    public void setEstadoNuevo(EstadoIncidencia value) { estadoNuevo = value; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String value) { observacion = value; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime value) { fechaHora = value; }
}
