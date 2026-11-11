package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "aplicaciones_pago_membresia")
public class AplicacionPagoMembresia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aplicacion")
    private Long idAplicacion;

    @NotNull(message = "El pago es obligatorio.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pago", nullable = false)
    private Pago pago;

    @NotNull(message = "La membresía es obligatoria.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_membresia", nullable = false)
    private Membresia membresia;

    @NotNull(message = "El monto aplicado es obligatorio.")
    @DecimalMin(value = "0.00", inclusive = false,
            message = "El monto aplicado debe ser mayor que cero.")
    @Column(name = "monto_aplicado", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoAplicado;

    @NotNull(message = "El tipo de aplicación es obligatorio.")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_aplicacion", nullable = false, length = 10)
    private TipoAplicacionPago tipoAplicacion;

    @Column(name = "fecha_aplicacion", nullable = false, updatable = false)
    private LocalDateTime fechaAplicacion;

    public AplicacionPagoMembresia() {
    }

    @PrePersist
    protected void alCrear() {
        if (fechaAplicacion == null) {
            fechaAplicacion = LocalDateTime.now();
        }
    }

    public Long getIdAplicacion() {
        return idAplicacion;
    }

    public Pago getPago() {
        return pago;
    }

    public void setPago(Pago pago) {
        this.pago = pago;
    }

    public Membresia getMembresia() {
        return membresia;
    }

    public void setMembresia(Membresia membresia) {
        this.membresia = membresia;
    }

    public BigDecimal getMontoAplicado() {
        return montoAplicado;
    }

    public void setMontoAplicado(BigDecimal montoAplicado) {
        this.montoAplicado = montoAplicado;
    }

    public LocalDateTime getFechaAplicacion() {
        return fechaAplicacion;
    }
    public TipoAplicacionPago getTipoAplicacion() {
        return tipoAplicacion;
    }

    public void setTipoAplicacion(TipoAplicacionPago tipoAplicacion) {
        this.tipoAplicacion = tipoAplicacion;
    }
}