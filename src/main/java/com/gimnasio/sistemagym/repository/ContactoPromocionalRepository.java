package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.ContactoPromocional;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContactoPromocionalRepository
        extends JpaRepository<ContactoPromocional, Long> {

    Optional<ContactoPromocional> findByTipoDocumentoAndNumeroDocumento(
            TipoDocumento tipoDocumento, String numeroDocumento);
}
