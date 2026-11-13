# Base local MySQL de SistemaGym

Scripts preparados para crear una instalación local vacía de SistemaGym sobre **MySQL 8.4**. No copian datos ni modifican bases anteriores. La estructura se modeló con las entidades JPA actuales y se contrastó con el DDL MySQL existente en pruebas; las cuatro tablas auxiliares del fixture que no corresponden a entidades actuales (`asignaciones_entrenador`, `asistencias`, `credenciales_qr` y `notificaciones`) se excluyeron.

## Archivos

1. `01_sistemagym_estructura_completa.sql`: crea la base y sus 25 tablas con índices, FK y restricciones.
2. `02_sistemagym_datos_iniciales.sql`: carga los cuatro roles requeridos y `PRECIO_PASE_DIARIO`.

No se crea ningún usuario administrador: debe crearse mediante el flujo de onboarding de la aplicación. El precio inicial `8.00` procede del valor predeterminado de `EntradaDiaria` y del esquema MySQL de pruebas; **confírmalo o cámbialo antes de habilitar cobros reales**. El servicio necesita esa configuración y no usa un fallback.

## Destino y precauciones

Estos son scripts históricos de instalación limpia y su destino codificado es `sistemagym`; no son necesarios para el arranque normal de la base local principal `sistemagym_definitiva`. El perfil `local` apunta únicamente a `sistemagym_definitiva`. No cambies ni ejecutes estos scripts contra esa base como parte del arranque o de la consolidación.

La base local principal ya existente debe conservar su esquema y datos. Las bases antiguas `sistemagym` y `sistemagym_pruebas` no se eliminan en esta etapa.

## Antes de ejecutar los scripts históricos

La instancia MySQL local debe ser 8.4 y el usuario de Workbench necesita permiso para crear esquemas/tablas. Comprueba primero si el nombre está libre con una consulta de solo lectura:

```sql
SELECT SCHEMA_NAME
FROM information_schema.SCHEMATA
 WHERE SCHEMA_NAME IN ('sistemagym', 'sistemagym_definitiva');
```

El script usa deliberadamente `CREATE DATABASE sistemagym` **sin** `IF NOT EXISTS`, y no contiene `DROP`. Si existe `sistemagym`, no ejecutes el script ni lo selecciones en Workbench. No sustituyas el destino por `sistemagym_definitiva`: el script es un instalador para una base nueva y no una migración. No renombres ni elimines bases como parte de su uso.

## Ejecución en MySQL Workbench

1. Conéctate a una instancia local y abre `01_sistemagym_estructura_completa.sql` solo si necesitas crear una instalación nueva e independiente.
2. Confirma que `sistemagym` no existe. Ejecuta el script solo para esa instalación nueva. Si Workbench ofrece continuar después de un error, desactiva esa opción; un error al crear el esquema debe detener el proceso.
3. Comprueba que el esquema activo sea el recién creado:

   ```sql
   SELECT DATABASE(), VERSION();
   SHOW TABLES;
   ```

   Deben aparecer las 25 tablas descritas por el modelo.
4. Abre y ejecuta `02_sistemagym_datos_iniciales.sql` seleccionando la base destino correcta. Las inserciones son repetibles y no duplican roles/configuración ya existentes.
5. Revisa el precio inicial de pase diario antes de hacer operaciones reales.

La tabla `contactos_promocionales` incluye `id_socio` nullable, su índice UNIQUE y FK a `socios(id_socio)`. `operaciones_pago_idempotentes` incluye la clave única por usuario, hash, estado, referencias de resultado, payload y restricciones endurecidas para cada estado.

`ingresos.fecha_ingreso` y su índice único por documento/día se conservan porque la implementación mantiene un modo anterior seleccionable (`sistemagym.pases-diarios.esquema-v2-habilitado=false` es el valor por defecto). El modo V2 usa además `fecha_venta_pase`, `id_pase_origen` e idempotencia. No apliqué las propuestas de migración que eliminan/reemplazan la unicidad anterior: cambiar esa compatibilidad requeriría activar V2 y aprobar la transición aparte.

## Conectar Spring Boot (sin editar configuración aquí)

El perfil `local` apunta a `jdbc:mysql://localhost:3306/sistemagym_definitiva` y conserva la contraseña fuera de Git mediante `DB_PASSWORD`. No cambies ese destino para usar estos scripts históricos.

En PowerShell, define el secreto sin imprimirlo y activa el perfil:

```powershell
$env:DB_PASSWORD = '<contraseña-local>'
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:JWT_SECRET_BASE64 = '<clave-base64-aleatoria-de-al-menos-32-bytes>'
mvn spring-boot:run
```

Hibernate debe permanecer en `ddl-auto=validate`. Las pruebas automatizadas usan H2 temporal o MySQL Testcontainers; no necesitan `sistemagym_pruebas`. Producción usa PostgreSQL y requiere una migración independiente; estos scripts MySQL no son portables directamente.

## Estado de validación y límites

- Revisión estática: 25 entidades con `@Table` y 25 tablas seleccionadas; incluye tablas de asistencia laboral, evaluaciones nutricionales, sesiones, invitaciones, recuperaciones, checklists/incidencias y pagos/idempotencia.
- Se contrastaron también los repositorios con consultas nativas MySQL para códigos de socios e idempotencia, y las consultas SQL de bloqueo `GET_LOCK`/`RELEASE_LOCK`.
- No se ejecutó este DDL ni se inició Spring Boot contra MySQL: el servidor local está instalado/en ejecución, pero la conexión de esta sesión fue rechazada por falta de credenciales disponibles (`root` sin contraseña). Por ello no se afirma que `ddl-auto=validate`, las consultas ni los endpoints hayan pasado contra esta base. Tampoco se ejecutaron migraciones sobre ninguna base.
- El esquema MySQL no es directamente portable a Supabase/PostgreSQL. Requiere una migración y validación aparte, especialmente por `UNSIGNED`/`AUTO_INCREMENT`, columnas generadas, expresiones de CHECK/regex, JSON/LONGTEXT y los bloqueos nombrados exclusivos de MySQL.

Antes de usar la base como definitiva, ejecuta los scripts localmente, verifica el arranque con `validate` y prueba los flujos principales. Este script crea una base limpia; no migra datos históricos.
