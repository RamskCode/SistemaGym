package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "detalles_checklist", uniqueConstraints =
        @UniqueConstraint(name = "uk_detalles_checklist_elemento",
                columnNames = {"id_checklist", "id_elemento"}))
public class DetalleChecklist {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Long idDetalle;

    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_checklist", nullable = false)
    private ChecklistSala checklist;

    @NotNull @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_elemento", nullable = false)
    private ElementoRevision elemento;

    @NotNull @Column(name = "nombre_elemento_snapshot", nullable = false, length = 120)
    private String nombreElementoSnapshot;

    @NotNull @Enumerated(EnumType.STRING)
    @Column(name = "tipo_elemento_snapshot", nullable = false, length = 20)
    private TipoElementoRevision tipoElementoSnapshot;

    @Size(max = 120) @Column(name = "ubicacion_snapshot", length = 120)
    private String ubicacionSnapshot;

    @NotNull @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoChecklist resultado;

    @Size(max = 1000) @Column(length = 1000)
    private String observaciones;

    @NotNull @Column(name = "fecha_revision", nullable = false)
    private LocalDateTime fechaRevision;

    public DetalleChecklist() {}
    public Long getIdDetalle() { return idDetalle; }
    public void setIdDetalle(Long value) { idDetalle = value; }
    public ChecklistSala getChecklist() { return checklist; }
    public void setChecklist(ChecklistSala value) { checklist = value; }
    public ElementoRevision getElemento() { return elemento; }
    public void setElemento(ElementoRevision value) { elemento = value; }
    public String getNombreElementoSnapshot() { return nombreElementoSnapshot; }
    public void setNombreElementoSnapshot(String value) { nombreElementoSnapshot = value; }
    public TipoElementoRevision getTipoElementoSnapshot() { return tipoElementoSnapshot; }
    public void setTipoElementoSnapshot(TipoElementoRevision value) { tipoElementoSnapshot = value; }
    public String getUbicacionSnapshot() { return ubicacionSnapshot; }
    public void setUbicacionSnapshot(String value) { ubicacionSnapshot = value; }
    public ResultadoChecklist getResultado() { return resultado; }
    public void setResultado(ResultadoChecklist value) { resultado = value; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String value) { observaciones = value; }
    public LocalDateTime getFechaRevision() { return fechaRevision; }
    public void setFechaRevision(LocalDateTime value) { fechaRevision = value; }
}
