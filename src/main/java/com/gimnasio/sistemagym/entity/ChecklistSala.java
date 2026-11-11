package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "checklists_sala")
public class ChecklistSala {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_checklist")
    private Long idChecklist;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_entrenador", nullable = false)
    private Usuario entrenador;

    @NotNull
    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private LocalDateTime fechaHora;

    @Column(length = 1000)
    private String observaciones;

    public ChecklistSala() {}
    public Long getIdChecklist() { return idChecklist; }
    public void setIdChecklist(Long value) { idChecklist = value; }
    public Usuario getEntrenador() { return entrenador; }
    public void setEntrenador(Usuario value) { entrenador = value; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime value) { fechaHora = value; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String value) { observaciones = value; }
}
