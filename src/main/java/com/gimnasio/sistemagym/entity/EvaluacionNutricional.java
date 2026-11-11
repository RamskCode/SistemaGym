package com.gimnasio.sistemagym.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "evaluaciones_nutricionales")
public class EvaluacionNutricional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evaluacion")
    private Long idEvaluacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_socio", nullable = false)
    private Socio socio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_nutricionista", nullable = false)
    private Usuario nutricionista;

    @Column(name = "fecha_evaluacion", nullable = false)
    private LocalDateTime fechaEvaluacion;

    @Column(name = "peso_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal pesoKg;

    @Column(name = "talla_m", nullable = false, precision = 4, scale = 2)
    private BigDecimal tallaM;

    @Column(name = "porcentaje_grasa", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeGrasa;

    @Column(name = "masa_muscular_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal masaMuscularKg;

    @Column(name = "habitos_alimenticios", nullable = false, columnDefinition = "TEXT")
    private String habitosAlimenticios;

    @Column(name = "objetivos_nutricionales", nullable = false, columnDefinition = "TEXT")
    private String objetivosNutricionales;

    @Column(name = "recomendaciones_nutricionales", nullable = false,
            columnDefinition = "TEXT")
    private String recomendacionesNutricionales;

    @Column(name = "observaciones_seguimiento", columnDefinition = "TEXT")
    private String observacionesSeguimiento;

    public EvaluacionNutricional() {
    }

    public Long getIdEvaluacion() {
        return idEvaluacion;
    }

    public Socio getSocio() {
        return socio;
    }

    public void setSocio(Socio socio) {
        this.socio = socio;
    }

    public Usuario getNutricionista() {
        return nutricionista;
    }

    public void setNutricionista(Usuario nutricionista) {
        this.nutricionista = nutricionista;
    }

    public LocalDateTime getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    public void setFechaEvaluacion(LocalDateTime fechaEvaluacion) {
        this.fechaEvaluacion = fechaEvaluacion;
    }

    public BigDecimal getPesoKg() {
        return pesoKg;
    }

    public void setPesoKg(BigDecimal pesoKg) {
        this.pesoKg = pesoKg;
    }

    public BigDecimal getTallaM() {
        return tallaM;
    }

    public void setTallaM(BigDecimal tallaM) {
        this.tallaM = tallaM;
    }

    public BigDecimal getPorcentajeGrasa() {
        return porcentajeGrasa;
    }

    public void setPorcentajeGrasa(BigDecimal porcentajeGrasa) {
        this.porcentajeGrasa = porcentajeGrasa;
    }

    public BigDecimal getMasaMuscularKg() {
        return masaMuscularKg;
    }

    public void setMasaMuscularKg(BigDecimal masaMuscularKg) {
        this.masaMuscularKg = masaMuscularKg;
    }

    public String getHabitosAlimenticios() {
        return habitosAlimenticios;
    }

    public void setHabitosAlimenticios(String habitosAlimenticios) {
        this.habitosAlimenticios = habitosAlimenticios;
    }

    public String getObjetivosNutricionales() {
        return objetivosNutricionales;
    }

    public void setObjetivosNutricionales(String objetivosNutricionales) {
        this.objetivosNutricionales = objetivosNutricionales;
    }

    public String getRecomendacionesNutricionales() {
        return recomendacionesNutricionales;
    }

    public void setRecomendacionesNutricionales(String recomendacionesNutricionales) {
        this.recomendacionesNutricionales = recomendacionesNutricionales;
    }

    public String getObservacionesSeguimiento() {
        return observacionesSeguimiento;
    }

    public void setObservacionesSeguimiento(String observacionesSeguimiento) {
        this.observacionesSeguimiento = observacionesSeguimiento;
    }
}
