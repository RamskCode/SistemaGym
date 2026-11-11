package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "entradas_diarias")
public class EntradaDiaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entrada")
    private Long idEntrada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_contacto", nullable = false)
    private ContactoPromocional contacto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pago", nullable = false)
    private Pago pago;

    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private LocalDateTime fechaHora;

    @NotNull(message = "El monto de la entrada es obligatorio.")
    @DecimalMin(value = "0.00", message = "El monto no puede ser negativo.")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto = new BigDecimal("8.00");

    @NotNull(message = "El estado de la entrada es obligatorio.")
    @jakarta.validation.constraints.Pattern(
            regexp = "^(PENDIENTE|PAGADA|ANULADA|APLICADA_A_MEMBRESIA)$",
            message = "El estado de la entrada no es válido."
    )

    @Column(nullable = false, length = 20)
    private String estado = "PAGADA";

    public EntradaDiaria() {
    }

    @PrePersist
    protected void alCrear() {
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
    }

    public Long getIdEntrada() {
        return idEntrada;
    }

    public ContactoPromocional getContacto() {
        return contacto;
    }

    public void setContacto(ContactoPromocional contacto) {
        this.contacto = contacto;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Pago getPago() {
        return pago;
    }

    public void setPago(Pago pago) {
        this.pago = pago;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
