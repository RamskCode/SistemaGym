package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long idPago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @NotNull(message = "El monto del pago es obligatorio.")
    @DecimalMin(value = "0.00", inclusive = false,
            message = "El monto debe ser mayor que cero.")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_pago", nullable = false, updatable = false)
    private LocalDateTime fechaPago;

    @NotBlank(message = "El método de pago es obligatorio.")
    @Size(max = 30, message = "El método de pago no puede superar los 30 caracteres.")
    @Column(name = "metodo_pago", nullable = false, length = 30)
    private String metodoPago;

    @NotBlank(message = "El estado del pago es obligatorio.")
    @Size(max = 20, message = "El estado no puede superar los 20 caracteres.")
    @Column(nullable = false, length = 20)
    private String estado = "CONFIRMADO";

    @Size(max = 100, message = "La referencia no puede superar los 100 caracteres.")
    @Column(length = 100)
    private String referencia;

    @Size(max = 255, message = "Las observaciones no pueden superar los 255 caracteres.")
    @Column(length = 255)
    private String observaciones;

    public Pago() {
    }

    @PrePersist
    protected void alCrear() {
        if (fechaPago == null) {
            fechaPago = LocalDateTime.now();
        }
    }

    public Long getIdPago() {
        return idPago;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public LocalDateTime getFechaPago() {
        return fechaPago;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
