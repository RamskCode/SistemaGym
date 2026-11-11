package com.gimnasio.sistemagym.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sesiones_refresh")
public class SesionRefresh {
    @Id
    @Column(name = "id_sesion", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "csrf_hash", length = 64, nullable = false)
    private String csrfHash;

    @Column(name = "version_credenciales", nullable = false)
    private long versionCredenciales;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    protected SesionRefresh() { }

    public SesionRefresh(String id, Usuario usuario, String tokenHash, String csrfHash,
            long versionCredenciales, Instant creadaEn, Instant expiraEn) {
        this.id = id;
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.csrfHash = csrfHash;
        this.versionCredenciales = versionCredenciales;
        this.creadaEn = creadaEn;
        this.expiraEn = expiraEn;
    }

    public String getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public String getTokenHash() { return tokenHash; }
    public String getCsrfHash() { return csrfHash; }
    public long getVersionCredenciales() { return versionCredenciales; }
    public Instant getExpiraEn() { return expiraEn; }
    public void rotar(String tokenHash, String csrfHash) {
        this.tokenHash = tokenHash;
        this.csrfHash = csrfHash;
    }
}
