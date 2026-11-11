CREATE TABLE `roles` (
  `id_rol` bigint unsigned NOT NULL AUTO_INCREMENT,
  `nombre` varchar(30) NOT NULL,
  `descripcion` varchar(150) DEFAULT NULL,
  PRIMARY KEY (`id_rol`),
  UNIQUE KEY `nombre` (`nombre`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `usuarios` (
  `id_usuario` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_rol` bigint unsigned NOT NULL,
  `nombres` varchar(100) NOT NULL,
  `apellidos` varchar(100) NOT NULL,
  `correo` varchar(150) NOT NULL,
  `contrasena_hash` varchar(255) NOT NULL,
  `version_credenciales` bigint NOT NULL DEFAULT 0,
  `tipo_documento` varchar(3) DEFAULT NULL,
  `numero_documento` varchar(20) DEFAULT NULL,
  `estado` varchar(20) NOT NULL DEFAULT 'ACTIVO',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_usuario`),
  UNIQUE KEY `correo` (`correo`),
  UNIQUE KEY `uk_usuarios_documento` (`tipo_documento`,`numero_documento`),
  KEY `fk_usuarios_roles` (`id_rol`),
  CONSTRAINT `fk_usuarios_roles` FOREIGN KEY (`id_rol`) REFERENCES `roles` (`id_rol`),
  CONSTRAINT `chk_usuarios_estado` CHECK ((`estado` in (_utf8mb4'ACTIVO',_utf8mb4'INACTIVO',_utf8mb4'PENDIENTE'))),
  CONSTRAINT `chk_usuarios_documento_mayusculas` CHECK (`numero_documento` IS NULL OR BINARY `numero_documento` = BINARY UPPER(`numero_documento`)),
  CONSTRAINT `chk_usuarios_tipo_documento` CHECK ((`tipo_documento` is null or `tipo_documento` COLLATE utf8mb4_0900_as_cs in (_utf8mb4'DNI',_utf8mb4'CE'))),
  CONSTRAINT `chk_usuarios_documento_par` CHECK (((`tipo_documento` is null) and (`numero_documento` is null)) or ((`tipo_documento` is not null) and (`numero_documento` is not null))),
  CONSTRAINT `chk_usuarios_formato_documento` CHECK ((`numero_documento` is null) or ((`tipo_documento` COLLATE utf8mb4_0900_as_cs = _utf8mb4'DNI') and (`numero_documento` regexp _utf8mb4'^[0-9]{8}$')) or ((`tipo_documento` COLLATE utf8mb4_0900_as_cs = _utf8mb4'CE') and (`numero_documento` regexp _utf8mb4'^[0-9]{1,12}$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sesiones_refresh` (
  `id_sesion` varchar(36) NOT NULL,
  `id_usuario` bigint unsigned NOT NULL,
  `token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `csrf_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version_credenciales` bigint NOT NULL,
  `creada_en` timestamp(6) NOT NULL,
  `expira_en` timestamp(6) NOT NULL,
  PRIMARY KEY (`id_sesion`),
  UNIQUE KEY `uk_sesiones_refresh_token_hash` (`token_hash`),
  KEY `idx_sesiones_refresh_usuario` (`id_usuario`),
  KEY `idx_sesiones_refresh_expira` (`expira_en`),
  CONSTRAINT `fk_sesiones_refresh_usuario` FOREIGN KEY (`id_usuario`)
    REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `invitaciones_usuario` (
  `id_invitacion` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_usuario` bigint unsigned NOT NULL,
  `token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `fecha_creacion` datetime(6) NOT NULL,
  `fecha_expiracion` datetime(6) NOT NULL,
  `fecha_uso` datetime(6) DEFAULT NULL,
  `fecha_invalidacion` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id_invitacion`),
  UNIQUE KEY `uk_invitaciones_usuario_token` (`token_hash`),
  KEY `ix_invitaciones_usuario_pendiente` (`id_usuario`,`fecha_expiracion`),
  CONSTRAINT `fk_invitaciones_usuario_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_invitaciones_usuario_fechas` CHECK (`fecha_expiracion` > `fecha_creacion`),
  CONSTRAINT `chk_invitaciones_usuario_hash` CHECK (`token_hash` regexp '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recuperaciones_contrasena` (
  `id_recuperacion` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_usuario` bigint unsigned NOT NULL,
  `token_hash` char(64) NOT NULL,
  `fecha_creacion` datetime(6) NOT NULL,
  `fecha_expiracion` datetime(6) NOT NULL,
  `fecha_uso` datetime(6) DEFAULT NULL,
  `fecha_invalidacion` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id_recuperacion`),
  UNIQUE KEY `uk_recuperaciones_token_hash` (`token_hash`),
  KEY `idx_recuperaciones_usuario_estado` (`id_usuario`,`fecha_uso`,`fecha_invalidacion`),
  KEY `idx_recuperaciones_expiracion` (`fecha_expiracion`),
  CONSTRAINT `fk_recuperaciones_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asistencias_laborales` (
  `id_asistencia` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `id_usuario` BIGINT UNSIGNED NOT NULL,
  `fecha_entrada_utc` DATETIME(6) NOT NULL,
  `fecha_salida_utc` DATETIME(6) DEFAULT NULL,
  `fecha_laboral` DATE NOT NULL,
  `estado` VARCHAR(10) NOT NULL DEFAULT 'ACTIVA',
  `id_registrado_entrada` BIGINT UNSIGNED NOT NULL,
  `id_registrado_salida` BIGINT UNSIGNED DEFAULT NULL,
  `id_usuario_sesion_abierta_activa` BIGINT UNSIGNED
    GENERATED ALWAYS AS (
      CASE WHEN `estado` = 'ACTIVA' AND `fecha_salida_utc` IS NULL
        THEN `id_usuario` ELSE NULL END
    ) STORED,
  PRIMARY KEY (`id_asistencia`),
  UNIQUE KEY `uk_asistencias_laborales_sesion_abierta_activa`
    (`id_usuario_sesion_abierta_activa`),
  KEY `idx_asistencias_laborales_usuario_entrada`
    (`id_usuario`, `fecha_entrada_utc`),
  KEY `idx_asistencias_laborales_usuario_fecha`
    (`id_usuario`, `fecha_laboral`),
  KEY `idx_asistencias_laborales_usuario_estado_entrada`
    (`id_usuario`, `estado`, `fecha_entrada_utc`, `id_asistencia`),
  KEY `idx_asistencias_laborales_registrador_entrada`
    (`id_registrado_entrada`),
  KEY `idx_asistencias_laborales_registrador_salida`
    (`id_registrado_salida`),
  CONSTRAINT `fk_asistencias_laborales_usuario`
    FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_asistencias_laborales_registrador_entrada`
    FOREIGN KEY (`id_registrado_entrada`) REFERENCES `usuarios` (`id_usuario`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_asistencias_laborales_registrador_salida`
    FOREIGN KEY (`id_registrado_salida`) REFERENCES `usuarios` (`id_usuario`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_asistencias_laborales_salida_posterior`
    CHECK (`fecha_salida_utc` IS NULL OR `fecha_salida_utc` > `fecha_entrada_utc`),
  CONSTRAINT `chk_asistencias_laborales_registrador_salida`
    CHECK ((`fecha_salida_utc` IS NULL AND `id_registrado_salida` IS NULL)
        OR (`fecha_salida_utc` IS NOT NULL AND `id_registrado_salida` IS NOT NULL)),
  CONSTRAINT `chk_asistencias_laborales_estado`
    CHECK (`estado` IN ('ACTIVA', 'ANULADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `eventos_asistencia_laboral` (
  `id_evento` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `id_asistencia` BIGINT UNSIGNED NOT NULL,
  `tipo_evento` VARCHAR(10) NOT NULL,
  `fecha_hora_utc` DATETIME(6) NOT NULL,
  `id_usuario_registrador` BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (`id_evento`),
  KEY `idx_eventos_asistencia_fecha` (`id_asistencia`, `fecha_hora_utc`),
  KEY `idx_eventos_asistencia_registrador` (`id_usuario_registrador`),
  CONSTRAINT `fk_eventos_asistencia_laboral_asistencia`
    FOREIGN KEY (`id_asistencia`) REFERENCES `asistencias_laborales` (`id_asistencia`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_eventos_asistencia_laboral_registrador`
    FOREIGN KEY (`id_usuario_registrador`) REFERENCES `usuarios` (`id_usuario`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_eventos_asistencia_tipo`
    CHECK (`tipo_evento` IN ('ENTRADA', 'SALIDA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auditorias_asistencia_laboral` (
  `id_auditoria` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `id_asistencia` BIGINT UNSIGNED NOT NULL,
  `id_usuario_actor` BIGINT UNSIGNED NOT NULL,
  `fecha_hora_utc` DATETIME(6) NOT NULL,
  `tipo_operacion` VARCHAR(12) NOT NULL,
  `motivo` VARCHAR(500) NOT NULL,
  `estado_anterior` VARCHAR(10) NOT NULL,
  `estado_nuevo` VARCHAR(10) NOT NULL,
  `fecha_entrada_anterior_utc` DATETIME(6) NOT NULL,
  `fecha_salida_anterior_utc` DATETIME(6) DEFAULT NULL,
  `fecha_entrada_nueva_utc` DATETIME(6) NOT NULL,
  `fecha_salida_nueva_utc` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id_auditoria`),
  KEY `idx_auditoria_asistencia_operacion` (`id_asistencia`, `tipo_operacion`),
  KEY `idx_auditoria_asistencia_fecha`
    (`id_asistencia`, `fecha_hora_utc`, `id_auditoria`),
  KEY `idx_auditoria_actor_fecha` (`id_usuario_actor`, `fecha_hora_utc`),
  CONSTRAINT `fk_auditoria_asistencia_laboral_asistencia`
    FOREIGN KEY (`id_asistencia`) REFERENCES `asistencias_laborales` (`id_asistencia`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_auditoria_asistencia_laboral_actor`
    FOREIGN KEY (`id_usuario_actor`) REFERENCES `usuarios` (`id_usuario`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_auditoria_asistencia_tipo`
    CHECK (`tipo_operacion` IN ('CORRECCION', 'ANULACION')),
  CONSTRAINT `chk_auditoria_asistencia_estados`
    CHECK ((`tipo_operacion` = 'CORRECCION'
        AND `estado_anterior` = 'ACTIVA' AND `estado_nuevo` = 'ACTIVA')
      OR (`tipo_operacion` = 'ANULACION'
        AND `estado_anterior` = 'ACTIVA' AND `estado_nuevo` = 'ANULADA')),
  CONSTRAINT `chk_auditoria_asistencia_intervalos`
    CHECK ((`fecha_salida_anterior_utc` IS NULL
        OR `fecha_salida_anterior_utc` > `fecha_entrada_anterior_utc`)
      AND (`fecha_salida_nueva_utc` IS NULL
        OR `fecha_salida_nueva_utc` > `fecha_entrada_nueva_utc`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `socios` (
  `id_socio` bigint unsigned NOT NULL AUTO_INCREMENT,
  `codigo_socio` varchar(20) NOT NULL,
  `dni` varchar(15) DEFAULT NULL,
  `tipo_documento` varchar(3) DEFAULT NULL,
  `numero_documento` varchar(20) DEFAULT NULL,
  `nombres` varchar(100) NOT NULL,
  `apellidos` varchar(100) NOT NULL,
  `fecha_nacimiento` date DEFAULT NULL,
  `celular` varchar(20) DEFAULT NULL,
  `correo` varchar(150) DEFAULT NULL,
  `estado` varchar(20) NOT NULL DEFAULT 'ACTIVO',
  `fecha_registro` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_socio`),
  UNIQUE KEY `codigo_socio` (`codigo_socio`),
  UNIQUE KEY `dni` (`dni`),
  UNIQUE KEY `uk_socios_tipo_numero` (`tipo_documento`,`numero_documento`),
  CONSTRAINT `chk_socios_documento_par` CHECK (((`tipo_documento` IS NULL) AND (`numero_documento` IS NULL)) OR ((`tipo_documento` IS NOT NULL) AND (`numero_documento` IS NOT NULL))),
  CONSTRAINT `chk_socios_documento_formato` CHECK (((`tipo_documento` IS NULL AND `numero_documento` IS NULL) OR (`tipo_documento` = 'DNI' AND REGEXP_LIKE(`numero_documento`, '^[0-9]{8}$', 'c')) OR (`tipo_documento` = 'CE' AND REGEXP_LIKE(`numero_documento`, '^[0-9]{1,12}$', 'c')))),
  CONSTRAINT `chk_socios_estado` CHECK ((`estado` in (_utf8mb4'ACTIVO',_utf8mb4'INACTIVO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `evaluaciones_nutricionales` (
  `id_evaluacion` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `id_socio` BIGINT UNSIGNED NOT NULL,
  `id_nutricionista` BIGINT UNSIGNED NOT NULL,
  `fecha_evaluacion` DATETIME NOT NULL,
  `peso_kg` DECIMAL(6,2) NOT NULL,
  `talla_m` DECIMAL(4,2) NOT NULL,
  `porcentaje_grasa` DECIMAL(5,2) NOT NULL,
  `masa_muscular_kg` DECIMAL(6,2) NOT NULL,
  `habitos_alimenticios` TEXT NOT NULL,
  `objetivos_nutricionales` TEXT NOT NULL,
  `recomendaciones_nutricionales` TEXT NOT NULL,
  `observaciones_seguimiento` TEXT DEFAULT NULL,
  PRIMARY KEY (`id_evaluacion`),
  KEY `idx_evaluaciones_socio_fecha_id`
    (`id_socio`, `fecha_evaluacion`, `id_evaluacion`),
  CONSTRAINT `fk_evaluaciones_socio`
    FOREIGN KEY (`id_socio`) REFERENCES `socios` (`id_socio`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_evaluaciones_nutricionista`
    FOREIGN KEY (`id_nutricionista`) REFERENCES `usuarios` (`id_usuario`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `planes` (
  `id_plan` bigint unsigned NOT NULL AUTO_INCREMENT,
  `nombre` varchar(80) NOT NULL,
  `descripcion` varchar(255) DEFAULT NULL,
  `precio` decimal(10,2) NOT NULL,
  `duracion_dias` int unsigned NOT NULL DEFAULT '30',
  `estado` varchar(20) NOT NULL DEFAULT 'ACTIVO',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_plan`),
  UNIQUE KEY `nombre` (`nombre`),
  CONSTRAINT `chk_planes_duracion` CHECK ((`duracion_dias` > 0)),
  CONSTRAINT `chk_planes_estado` CHECK ((`estado` in (_utf8mb4'ACTIVO',_utf8mb4'INACTIVO'))),
  CONSTRAINT `chk_planes_precio` CHECK ((`precio` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `contactos_promocionales` (
  `id_contacto` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_socio` bigint unsigned DEFAULT NULL,
  `tipo_documento` varchar(3) DEFAULT NULL,
  `numero_documento` varchar(20) DEFAULT NULL,
  `nombre` varchar(100) NOT NULL,
  `celular` varchar(20) NOT NULL,
  `correo` varchar(150) DEFAULT NULL,
  `acepta_whatsapp` tinyint(1) NOT NULL DEFAULT '0',
  `acepta_correo` tinyint(1) NOT NULL DEFAULT '0',
  `fecha_registro` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `observaciones` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_contacto`),
  UNIQUE KEY `uk_contactos_documento` (`tipo_documento`,`numero_documento`),
  UNIQUE KEY `uk_contactos_socio` (`id_socio`),
  KEY `idx_contactos_celular` (`celular`),
  CONSTRAINT `fk_contactos_socio` FOREIGN KEY (`id_socio`) REFERENCES `socios` (`id_socio`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_contactos_tipo_documento` CHECK ((`tipo_documento` is null or `tipo_documento` in (_utf8mb4'DNI',_utf8mb4'CE'))),
  CONSTRAINT `chk_contactos_documento_completo` CHECK (((`tipo_documento` is null) and (`numero_documento` is null)) or ((`tipo_documento` is not null) and (`numero_documento` is not null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `pagos` (
  `id_pago` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_usuario` bigint unsigned NOT NULL,
  `monto` decimal(10,2) NOT NULL,
  `fecha_pago` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `metodo_pago` varchar(30) NOT NULL,
  `estado` varchar(20) NOT NULL DEFAULT 'CONFIRMADO',
  `referencia` varchar(100) DEFAULT NULL,
  `observaciones` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_pago`),
  KEY `fk_pagos_usuarios` (`id_usuario`),
  KEY `idx_pagos_fecha` (`fecha_pago`),
  KEY `idx_pagos_estado` (`estado`),
  CONSTRAINT `fk_pagos_usuarios` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`),
  CONSTRAINT `chk_pagos_estado` CHECK ((`estado` in (_utf8mb4'PENDIENTE',_utf8mb4'CONFIRMADO',_utf8mb4'ANULADO'))),
  CONSTRAINT `chk_pagos_metodo` CHECK ((`metodo_pago` in (_utf8mb4'EFECTIVO',_utf8mb4'YAPE',_utf8mb4'PLIN',_utf8mb4'TRANSFERENCIA',_utf8mb4'TARJETA',_utf8mb4'OTRO'))),
  CONSTRAINT `chk_pagos_monto` CHECK ((`monto` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auditoria` (
  `id_auditoria` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_usuario` bigint unsigned DEFAULT NULL,
  `accion` varchar(80) NOT NULL,
  `entidad` varchar(80) NOT NULL,
  `id_registro` varchar(80) DEFAULT NULL,
  `detalle` json DEFAULT NULL,
  `fecha_hora` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_auditoria`),
  KEY `fk_auditoria_usuarios` (`id_usuario`),
  KEY `idx_auditoria_fecha` (`fecha_hora`),
  KEY `idx_auditoria_entidad` (`entidad`,`id_registro`),
  CONSTRAINT `fk_auditoria_usuarios` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `membresias` (
  `id_membresia` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_socio` bigint unsigned NOT NULL,
  `id_plan` bigint unsigned NOT NULL,
  `precio_acordado` decimal(10,2) NOT NULL,
  `fecha_inicio` date NOT NULL,
  `fecha_vencimiento` date NOT NULL,
  `fecha_limite_pago` date DEFAULT NULL,
  `estado` varchar(25) NOT NULL DEFAULT 'PENDIENTE_PAGO',
  `motivo_cancelacion` varchar(255) DEFAULT NULL,
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_membresia`),
  KEY `fk_membresias_planes` (`id_plan`),
  KEY `idx_membresias_socio` (`id_socio`),
  KEY `idx_membresias_vencimiento` (`fecha_vencimiento`),
  KEY `idx_membresias_estado` (`estado`),
  CONSTRAINT `fk_membresias_planes` FOREIGN KEY (`id_plan`) REFERENCES `planes` (`id_plan`),
  CONSTRAINT `fk_membresias_socios` FOREIGN KEY (`id_socio`) REFERENCES `socios` (`id_socio`),
  CONSTRAINT `chk_membresias_estado` CHECK ((`estado` in (_utf8mb4'PENDIENTE_PAGO',_utf8mb4'ACTIVA',_utf8mb4'VENCIDA',_utf8mb4'CANCELADA'))),
  CONSTRAINT `chk_membresias_fechas` CHECK ((`fecha_vencimiento` >= `fecha_inicio`)),
  CONSTRAINT `chk_membresias_precio` CHECK ((`precio_acordado` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `aplicaciones_pago_membresia` (
  `id_aplicacion` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_pago` bigint unsigned NOT NULL,
  `id_membresia` bigint unsigned NOT NULL,
  `monto_aplicado` decimal(10,2) NOT NULL,
  `fecha_aplicacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tipo_aplicacion` enum('INICIAL','FINAL') NOT NULL,
  PRIMARY KEY (`id_aplicacion`),
  KEY `idx_aplicaciones_membresia` (`id_membresia`),
  KEY `idx_aplicaciones_pago` (`id_pago`),
  CONSTRAINT `fk_aplicaciones_membresias` FOREIGN KEY (`id_membresia`) REFERENCES `membresias` (`id_membresia`),
  CONSTRAINT `fk_aplicaciones_pagos` FOREIGN KEY (`id_pago`) REFERENCES `pagos` (`id_pago`),
  CONSTRAINT `chk_aplicaciones_monto` CHECK ((`monto_aplicado` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `operaciones_pago_idempotentes` (
  `id_operacion` CHAR(36) NOT NULL,
  `id_usuario` BIGINT UNSIGNED NOT NULL,
  `clave_idempotencia` CHAR(36) NOT NULL,
  `tipo_operacion` VARCHAR(24) NOT NULL,
  `hash_solicitud` CHAR(64) NOT NULL,
  `estado` VARCHAR(10) NOT NULL DEFAULT 'PENDING',
  `fecha_creacion` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `fecha_actualizacion` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `id_membresia_resultado` BIGINT UNSIGNED DEFAULT NULL,
  `id_pago_resultado` BIGINT UNSIGNED DEFAULT NULL,
  `id_aplicacion_resultado` BIGINT UNSIGNED DEFAULT NULL,
  `resultado_payload` LONGTEXT DEFAULT NULL,
  `rechazo_tipo` VARCHAR(40) DEFAULT NULL,
  `rechazo_mensaje` VARCHAR(1000) DEFAULT NULL,
  PRIMARY KEY (`id_operacion`),
  UNIQUE KEY `uk_operaciones_pago_usuario_clave`
    (`id_usuario`, `clave_idempotencia`),
  KEY `idx_operaciones_pago_estado_actualizacion`
    (`estado`, `fecha_actualizacion`),
  CONSTRAINT `fk_operaciones_pago_usuario`
    FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_operaciones_pago_membresia`
    FOREIGN KEY (`id_membresia_resultado`) REFERENCES `membresias` (`id_membresia`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_operaciones_pago_pago`
    FOREIGN KEY (`id_pago_resultado`) REFERENCES `pagos` (`id_pago`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_operaciones_pago_aplicacion`
    FOREIGN KEY (`id_aplicacion_resultado`) REFERENCES `aplicaciones_pago_membresia` (`id_aplicacion`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_operaciones_pago_tipo`
    CHECK (`tipo_operacion` IN ('MEMBRESIA_INICIAL', 'PAGO_POSTERIOR')),
  CONSTRAINT `chk_operaciones_pago_estado`
    CHECK (`estado` IN ('PENDING', 'CONFIRMED', 'REJECTED')),
  CONSTRAINT `chk_operaciones_pago_hash`
    CHECK (`hash_solicitud` REGEXP '^[a-f0-9]{64}$'),
  CONSTRAINT `chk_operaciones_pago_resultado`
    CHECK ((`estado` = 'PENDING'
        AND `id_membresia_resultado` IS NULL
        AND `id_pago_resultado` IS NULL
        AND `id_aplicacion_resultado` IS NULL
        AND `resultado_payload` IS NULL
        AND `rechazo_tipo` IS NULL
        AND `rechazo_mensaje` IS NULL)
      OR (`estado` = 'CONFIRMED'
        AND `id_membresia_resultado` IS NOT NULL
        AND `id_pago_resultado` IS NOT NULL
        AND `id_aplicacion_resultado` IS NOT NULL
        AND `resultado_payload` IS NOT NULL
        AND `rechazo_tipo` IS NULL
        AND `rechazo_mensaje` IS NULL)
      OR (`estado` = 'REJECTED'
        AND `id_membresia_resultado` IS NULL
        AND `id_pago_resultado` IS NULL
        AND `id_aplicacion_resultado` IS NULL
        AND `resultado_payload` IS NULL
        AND `rechazo_tipo` IS NOT NULL
        AND `rechazo_mensaje` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `entradas_diarias` (
  `id_entrada` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_contacto` bigint unsigned NOT NULL,
  `id_usuario` bigint unsigned NOT NULL,
  `id_pago` bigint unsigned NOT NULL,
  `fecha_hora` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `monto` decimal(10,2) NOT NULL DEFAULT '8.00',
  `estado` varchar(20) NOT NULL DEFAULT 'PAGADA',
  PRIMARY KEY (`id_entrada`),
  KEY `fk_entradas_usuarios` (`id_usuario`),
  KEY `fk_entradas_pagos` (`id_pago`),
  KEY `idx_entradas_fecha` (`fecha_hora`),
  KEY `idx_entradas_contacto` (`id_contacto`),
  CONSTRAINT `fk_entradas_contactos` FOREIGN KEY (`id_contacto`) REFERENCES `contactos_promocionales` (`id_contacto`),
  CONSTRAINT `fk_entradas_pagos` FOREIGN KEY (`id_pago`) REFERENCES `pagos` (`id_pago`),
  CONSTRAINT `fk_entradas_usuarios` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`),
  CONSTRAINT `chk_entradas_estado` CHECK ((`estado` in (_utf8mb4'PENDIENTE',_utf8mb4'PAGADA',_utf8mb4'ANULADA',_utf8mb4'APLICADA_A_MEMBRESIA'))),
  CONSTRAINT `chk_entradas_monto` CHECK ((`monto` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asignaciones_entrenador` (
  `id_asignacion` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_usuario_entrenador` bigint unsigned NOT NULL,
  `id_socio` bigint unsigned NOT NULL,
  `fecha_inicio` date NOT NULL,
  `fecha_fin` date DEFAULT NULL,
  `estado` varchar(20) NOT NULL DEFAULT 'ACTIVA',
  PRIMARY KEY (`id_asignacion`),
  KEY `idx_asignaciones_entrenador` (`id_usuario_entrenador`),
  KEY `idx_asignaciones_socio` (`id_socio`),
  CONSTRAINT `fk_asignaciones_socios` FOREIGN KEY (`id_socio`) REFERENCES `socios` (`id_socio`),
  CONSTRAINT `fk_asignaciones_usuarios` FOREIGN KEY (`id_usuario_entrenador`) REFERENCES `usuarios` (`id_usuario`),
  CONSTRAINT `chk_asignaciones_estado` CHECK ((`estado` in (_utf8mb4'ACTIVA',_utf8mb4'FINALIZADA'))),
  CONSTRAINT `chk_asignaciones_fechas` CHECK (((`fecha_fin` is null) or (`fecha_fin` >= `fecha_inicio`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asistencias` (
  `id_asistencia` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_socio` bigint unsigned NOT NULL,
  `id_usuario` bigint unsigned DEFAULT NULL,
  `fecha_hora` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `metodo_registro` varchar(20) NOT NULL,
  `resultado` varchar(20) NOT NULL DEFAULT 'ACEPTADA',
  `observaciones` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_asistencia`),
  KEY `fk_asistencias_usuarios` (`id_usuario`),
  KEY `idx_asistencias_socio_fecha` (`id_socio`,`fecha_hora`),
  KEY `idx_asistencias_fecha` (`fecha_hora`),
  CONSTRAINT `fk_asistencias_socios` FOREIGN KEY (`id_socio`) REFERENCES `socios` (`id_socio`),
  CONSTRAINT `fk_asistencias_usuarios` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`),
  CONSTRAINT `chk_asistencias_metodo` CHECK ((`metodo_registro` in (_utf8mb4'QR',_utf8mb4'MANUAL'))),
  CONSTRAINT `chk_asistencias_resultado` CHECK ((`resultado` in (_utf8mb4'ACEPTADA',_utf8mb4'RECHAZADA')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ingresos` (
  `id_ingreso` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_socio` bigint unsigned DEFAULT NULL,
  `id_contacto` bigint unsigned DEFAULT NULL,
  `tipo_ingreso` varchar(10) NOT NULL,
  `tipo_documento` varchar(3) NOT NULL,
  `numero_documento` varchar(20) NOT NULL,
  `id_usuario` bigint unsigned NOT NULL,
  `id_pago` bigint unsigned DEFAULT NULL,
  `id_pase_origen` bigint unsigned DEFAULT NULL,
  `fecha_hora` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- Test-only legacy guard remains for id_socio IS NULL; the production
  -- migration replaces this index. V2 Socio-backed accesses have NULL here.
  `fecha_ingreso` date GENERATED ALWAYS AS ((case when (`id_socio` is null) then cast(`fecha_hora` as date) else NULL end)) STORED,
  `fecha_venta_pase` date GENERATED ALWAYS AS ((case when ((`tipo_ingreso` = _utf8mb4'VISITANTE') and (`id_pago` is not null)) then cast(`fecha_hora` as date) else NULL end)) STORED,
  `clave_idempotencia` varchar(36) DEFAULT NULL,
  `hash_solicitud` char(64) DEFAULT NULL,
  PRIMARY KEY (`id_ingreso`),
  UNIQUE KEY `uk_ingresos_documento_fecha` (`tipo_documento`,`numero_documento`,`fecha_ingreso`),
  UNIQUE KEY `uk_ingresos_pase_dia` (`id_socio`,`fecha_venta_pase`),
  UNIQUE KEY `uk_ingresos_clave_idempotencia` (`clave_idempotencia`),
  KEY `idx_ingresos_pase_origen` (`id_pase_origen`),
  KEY `fk_ingresos_socios` (`id_socio`),
  KEY `fk_ingresos_contactos` (`id_contacto`),
  KEY `fk_ingresos_usuarios` (`id_usuario`),
  KEY `fk_ingresos_pagos` (`id_pago`),
  CONSTRAINT `fk_ingresos_socios` FOREIGN KEY (`id_socio`) REFERENCES `socios` (`id_socio`),
  CONSTRAINT `fk_ingresos_contactos` FOREIGN KEY (`id_contacto`) REFERENCES `contactos_promocionales` (`id_contacto`),
  CONSTRAINT `fk_ingresos_usuarios` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`),
  CONSTRAINT `fk_ingresos_pagos` FOREIGN KEY (`id_pago`) REFERENCES `pagos` (`id_pago`),
  CONSTRAINT `fk_ingresos_pase_origen` FOREIGN KEY (`id_pase_origen`) REFERENCES `ingresos` (`id_ingreso`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_ingresos_tipo` CHECK ((`tipo_ingreso` in (_utf8mb4'SOCIO',_utf8mb4'VISITANTE'))),
  CONSTRAINT `chk_ingresos_documento` CHECK ((`tipo_documento` in (_utf8mb4'DNI',_utf8mb4'CE'))),
  CONSTRAINT `chk_ingresos_referencia` CHECK (((`tipo_ingreso` = _utf8mb4'SOCIO') and (`id_socio` is not null) and (`id_contacto` is null) and (`id_pago` is null) and (`id_pase_origen` is null)) or ((`tipo_ingreso` = _utf8mb4'VISITANTE') and (`id_socio` is null) and (`id_contacto` is not null) and (`id_pago` is not null) and (`id_pase_origen` is null)) or ((`tipo_ingreso` = _utf8mb4'VISITANTE') and (`id_socio` is not null) and (`id_contacto` is null) and (`id_pago` is not null) and (`id_pase_origen` is null)) or ((`tipo_ingreso` = _utf8mb4'VISITANTE') and (`id_socio` is not null) and (`id_contacto` is null) and (`id_pago` is null) and (`id_pase_origen` is not null))),
  CONSTRAINT `chk_ingresos_idempotencia` CHECK (((`clave_idempotencia` is null and `hash_solicitud` is null) or (`clave_idempotencia` is not null and `hash_solicitud` is not null and regexp_like(`clave_idempotencia`,_utf8mb4'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',_utf8mb4'c') and regexp_like(`hash_solicitud`,_utf8mb4'^[0-9a-f]{64}$',_utf8mb4'c'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `credenciales_qr` (
  `id_credencial` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_socio` bigint unsigned NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_revocacion` datetime DEFAULT NULL,
  `estado` varchar(20) NOT NULL DEFAULT 'ACTIVA',
  PRIMARY KEY (`id_credencial`),
  UNIQUE KEY `token_hash` (`token_hash`),
  KEY `idx_credenciales_socio` (`id_socio`),
  CONSTRAINT `fk_credenciales_socios` FOREIGN KEY (`id_socio`) REFERENCES `socios` (`id_socio`),
  CONSTRAINT `chk_credenciales_estado` CHECK ((`estado` in (_utf8mb4'ACTIVA',_utf8mb4'REVOCADA')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `configuraciones` (
  `id_configuracion` bigint unsigned NOT NULL AUTO_INCREMENT,
  `clave` varchar(80) NOT NULL,
  `valor` varchar(255) NOT NULL,
  `descripcion` varchar(255) DEFAULT NULL,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_configuracion`),
  UNIQUE KEY `clave` (`clave`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notificaciones` (
  `id_notificacion` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_socio` bigint unsigned NOT NULL,
  `id_membresia` bigint unsigned DEFAULT NULL,
  `canal` varchar(20) NOT NULL,
  `tipo` varchar(40) NOT NULL,
  `fecha_programada` datetime DEFAULT NULL,
  `fecha_intento` datetime DEFAULT NULL,
  `estado` varchar(25) NOT NULL DEFAULT 'PENDIENTE',
  `resultado_proveedor` varchar(255) DEFAULT NULL,
  `mensaje_error` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_notificacion`),
  KEY `fk_notificaciones_membresias` (`id_membresia`),
  KEY `idx_notificaciones_programada` (`fecha_programada`),
  KEY `idx_notificaciones_socio` (`id_socio`),
  CONSTRAINT `fk_notificaciones_membresias` FOREIGN KEY (`id_membresia`) REFERENCES `membresias` (`id_membresia`),
  CONSTRAINT `fk_notificaciones_socios` FOREIGN KEY (`id_socio`) REFERENCES `socios` (`id_socio`),
  CONSTRAINT `chk_notificaciones_canal` CHECK ((`canal` in (_utf8mb4'WHATSAPP',_utf8mb4'CORREO'))),
  CONSTRAINT `chk_notificaciones_estado` CHECK ((`estado` in (_utf8mb4'PENDIENTE',_utf8mb4'PREPARADA',_utf8mb4'INTENTADA',_utf8mb4'ENVIADA',_utf8mb4'FALLIDA')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `configuraciones` (`clave`, `valor`, `descripcion`)
VALUES ('PRECIO_PASE_DIARIO', '8.00', 'Precio vigente del pase diario para visitantes')
ON DUPLICATE KEY UPDATE `clave` = `clave`;

CREATE TABLE `elementos_revision` (
  `id_elemento` bigint unsigned NOT NULL AUTO_INCREMENT,
  `nombre` varchar(120) NOT NULL,
  `tipo` varchar(20) NOT NULL,
  `descripcion` varchar(500) DEFAULT NULL,
  `ubicacion` varchar(120) DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  `fecha_creacion` datetime NOT NULL,
  `fecha_actualizacion` datetime NOT NULL,
  PRIMARY KEY (`id_elemento`),
  KEY `idx_elementos_revision_activo_nombre` (`activo`,`nombre`),
  KEY `idx_elementos_revision_tipo` (`tipo`),
  CONSTRAINT `chk_elementos_revision_tipo` CHECK ((`tipo` in (_utf8mb4'MAQUINA',_utf8mb4'AREA'))),
  CONSTRAINT `chk_elementos_revision_activo` CHECK ((`activo` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `checklists_sala` (
  `id_checklist` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_entrenador` bigint unsigned NOT NULL,
  `fecha_hora` datetime NOT NULL,
  `observaciones` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id_checklist`),
  KEY `idx_checklists_entrenador_fecha` (`id_entrenador`,`fecha_hora`),
  KEY `idx_checklists_fecha` (`fecha_hora`),
  CONSTRAINT `fk_checklists_entrenador` FOREIGN KEY (`id_entrenador`) REFERENCES `usuarios` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `detalles_checklist` (
  `id_detalle` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_checklist` bigint unsigned NOT NULL,
  `id_elemento` bigint unsigned NOT NULL,
  `nombre_elemento_snapshot` varchar(120) NOT NULL,
  `tipo_elemento_snapshot` varchar(20) NOT NULL,
  `ubicacion_snapshot` varchar(120) DEFAULT NULL,
  `resultado` varchar(20) NOT NULL,
  `observaciones` varchar(1000) DEFAULT NULL,
  `fecha_revision` datetime NOT NULL,
  PRIMARY KEY (`id_detalle`),
  UNIQUE KEY `uk_detalles_checklist_elemento` (`id_checklist`,`id_elemento`),
  KEY `idx_detalles_elemento_fecha` (`id_elemento`,`fecha_revision`),
  CONSTRAINT `fk_detalles_checklist` FOREIGN KEY (`id_checklist`) REFERENCES `checklists_sala` (`id_checklist`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_detalles_elemento` FOREIGN KEY (`id_elemento`) REFERENCES `elementos_revision` (`id_elemento`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_detalles_resultado` CHECK ((`resultado` in (_utf8mb4'CORRECTO',_utf8mb4'CON_PROBLEMA'))),
  CONSTRAINT `chk_detalles_tipo_snapshot` CHECK ((`tipo_elemento_snapshot` in (_utf8mb4'MAQUINA',_utf8mb4'AREA')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reportes_incidencias` (
  `id_reporte` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_entrenador` bigint unsigned NOT NULL,
  `id_elemento` bigint unsigned NOT NULL,
  `id_detalle_checklist` bigint unsigned DEFAULT NULL,
  `descripcion` varchar(4000) NOT NULL,
  `prioridad` varchar(10) NOT NULL,
  `estado` varchar(20) NOT NULL DEFAULT 'PENDIENTE',
  `observaciones_actuales` varchar(2000) DEFAULT NULL,
  `fecha_reporte` datetime NOT NULL,
  `fecha_actualizacion` datetime NOT NULL,
  `fecha_solucion` datetime DEFAULT NULL,
  PRIMARY KEY (`id_reporte`),
  UNIQUE KEY `uk_reportes_detalle_checklist` (`id_detalle_checklist`),
  KEY `idx_reportes_entrenador_fecha` (`id_entrenador`,`fecha_reporte`),
  KEY `idx_reportes_elemento_fecha` (`id_elemento`,`fecha_reporte`),
  KEY `idx_reportes_fecha` (`fecha_reporte`),
  KEY `idx_reportes_estado_prioridad` (`estado`,`prioridad`),
  CONSTRAINT `fk_reportes_entrenador` FOREIGN KEY (`id_entrenador`) REFERENCES `usuarios` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_reportes_elemento` FOREIGN KEY (`id_elemento`) REFERENCES `elementos_revision` (`id_elemento`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_reportes_detalle_checklist` FOREIGN KEY (`id_detalle_checklist`) REFERENCES `detalles_checklist` (`id_detalle`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_reportes_prioridad` CHECK ((`prioridad` in (_utf8mb4'BAJA',_utf8mb4'MEDIA',_utf8mb4'ALTA'))),
  CONSTRAINT `chk_reportes_estado` CHECK ((`estado` in (_utf8mb4'PENDIENTE',_utf8mb4'EN_REVISION',_utf8mb4'SOLUCIONADO'))),
  CONSTRAINT `chk_reportes_fecha_solucion` CHECK (((`estado` = _utf8mb4'SOLUCIONADO') and (`fecha_solucion` is not null)) or ((`estado` <> _utf8mb4'SOLUCIONADO') and (`fecha_solucion` is null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `historial_reportes_incidencia` (
  `id_historial` bigint unsigned NOT NULL AUTO_INCREMENT,
  `id_reporte` bigint unsigned NOT NULL,
  `id_usuario` bigint unsigned NOT NULL,
  `estado_anterior` varchar(20) DEFAULT NULL,
  `estado_nuevo` varchar(20) NOT NULL,
  `observacion` varchar(2000) DEFAULT NULL,
  `fecha_hora` datetime NOT NULL,
  PRIMARY KEY (`id_historial`),
  KEY `idx_historial_reporte_fecha` (`id_reporte`,`fecha_hora`),
  KEY `idx_historial_usuario_fecha` (`id_usuario`,`fecha_hora`),
  CONSTRAINT `fk_historial_reporte` FOREIGN KEY (`id_reporte`) REFERENCES `reportes_incidencias` (`id_reporte`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_historial_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_historial_estado_anterior` CHECK ((`estado_anterior` is null or `estado_anterior` in (_utf8mb4'PENDIENTE',_utf8mb4'EN_REVISION',_utf8mb4'SOLUCIONADO'))),
  CONSTRAINT `chk_historial_estado_nuevo` CHECK ((`estado_nuevo` in (_utf8mb4'PENDIENTE',_utf8mb4'EN_REVISION',_utf8mb4'SOLUCIONADO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
