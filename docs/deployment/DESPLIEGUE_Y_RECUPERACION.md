# Despliegue y recuperación de SistemaGym

Esta guía prepara el despliegue; **no autoriza ni ejecuta** migraciones, cambios
en `sistemagym_definitiva` ni un despliegue. El perfil `local` conserva MySQL
8.4; `prod` está preparado para PostgreSQL 16+ (incluido Supabase) y requiere
un esquema previo con `spring.jpa.hibernate.ddl-auto=validate`. El DDL está en
`docs/sql/postgresql/01_sistemagym_schema.sql`; revise su README antes de usarlo.

## 1. Arquitectura recomendada

- MySQL 8.4 local o PostgreSQL administrado en producción, con TLS y cuenta de
  runtime de privilegios mínimos. No exponer el motor directamente a Internet.
- Spring Boot 4 / Java 21 detrás de un reverse proxy o balanceador HTTPS.
- Servir el build estático de React/Vite detrás del mismo dominio y enrutar
  `/api/**` al backend. Esta topología same-origin evita habilitar CORS amplio;
  no configurar `Access-Control-Allow-Origin: *` con cookies.
- Solo el proxy debe ser accesible públicamente. El backend debe confiar en
  `Forwarded`/`X-Forwarded-*` únicamente cuando los sobrescriba el proxy de
  confianza. El perfil prod activa el manejo framework de headers reenviados.

## 2. Secretos y variables de entorno

Inyectar los valores desde el gestor de secretos del proveedor, no desde Git,
README, imagen Docker o argumentos visibles de proceso. Mantenerlos separados
por entorno y rotables.

| Variable | Uso |
|---|---|
| `DB_URL` | En `prod`, JDBC PostgreSQL, p. ej. `jdbc:postgresql://<host>:5432/<esquema>?sslmode=require`; no incluir credenciales. |
| `DB_USERNAME` / `DB_PASSWORD` | Cuenta de runtime restringida a las operaciones que la aplicación necesita. No usar root. |
| `JWT_SECRET_BASE64` | Clave aleatoria Base64 de al menos 32 bytes; usar distinta por entorno y proteger/rotar con plan de revocación. |
| `SISTEMAGYM_FRONTEND_URL` | Origen HTTPS público usado por enlaces de activación/recuperación; sin rutas ni barra final. |
| `SISTEMAGYM_MAIL_USERNAME`, `SISTEMAGYM_MAIL_APP_PASSWORD`, `SISTEMAGYM_MAIL_FROM` | SMTP, si se habilitan invitaciones y recuperación por correo. |
| `PORT` | Puerto asignado por el runtime; por defecto 8080. |
| `DB_POOL_MAX`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS` | Límites Hikari; dimensionar respecto de PostgreSQL/Supabase y réplicas. |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes frontend exactos separados por coma; sin comodines. Ejemplo `https://<app>.vercel.app`. |
| `AUTH_COOKIE_SECURE` | En `prod` permanece `true`; requiere HTTPS. |
| `AUTH_COOKIE_SAME_SITE` | Vercel/Render cross-site: `None` (junto con Secure); local: `Strict`. |
| `VITE_API_BASE_URL` | Origen HTTPS Render al compilar el frontend, p. ej. `https://<api>.onrender.com`; sin `/api`. Vacío mantiene same-origin. |

El perfil fija `app.auth.cookie-secure=true`. Para Vercel y Render en orígenes
distintos, usar `SameSite=None; Secure`, CORS con allowlist exacta y credenciales,
y conservar CSRF double-submit en refresh/logout. No usar `*` ni retirar la
validación de origen/CSRF. Si el navegador bloquea cookies cross-site, preferir
dominios propios bajo el mismo sitio.

## 3. Preflight de base de datos y cambios de esquema

1. Confirmar host, motor/version, catálogo, TLS y dueño operativo. Verificar el
   esquema origen con cuenta SELECT-only y compararlo con las 25 tablas maestras.
2. Transferir y reconciliar datos en PostgreSQL aislado (conteos, relaciones,
   checks, columnas generadas, secuencias e importes). No aplicar scripts
   históricos contra producción durante el arranque.
3. Preparar un plan de migración revisado, ventana, duración, impacto, rollback
   y criterios de abortar. Algunas tablas/operaciones idempotentes no admiten un
   rollback seguro después de registrar operaciones.
4. Obtener aprobación explícita, probar el artefacto en staging PostgreSQL y
   validar constraints, índices, conteos y flujos de negocio.
5. La aplicación nunca debe arrancar con `ddl-auto=update/create`; `validate`
   debe fallar si el esquema no coincide.

## 4. Build y despliegue de aplicación

### Backend

```powershell
mvn clean verify
java -Dspring.profiles.active=prod -jar target\SistemaGym-0.0.1-SNAPSHOT.jar
```

El contenedor `Dockerfile` selecciona `prod`. Construir la imagen desde un
artefacto revisado, inyectar secretos al iniciar y verificar readiness/logs sin
imprimir valores secretos. Antes de desplegar, probar `prod` en PostgreSQL
aislado con Testcontainers; las pruebas MySQL locales permanecen separadas.

### Frontend

```powershell
cd frontend
npm ci
npm test -- --testTimeout=15000
npm run build
```

Publicar solo `frontend/dist` en el hosting estático. Configurar fallback de SPA
a `index.html` y servir por HTTPS. En Vercel/Render, compilar con
`VITE_API_BASE_URL` y configurar `APP_CORS_ALLOWED_ORIGINS` en Render. El proxy
Vite es solo para desarrollo/pruebas.

### Puesta en servicio / rollback

- Hacer deploy gradual/canary si el hosting lo admite; mantener disponible la
  versión anterior de backend y frontend.
- Verificar health/readiness, autenticación, refresh/logout, consultas de solo
  lectura, SMTP (si aplica), logs y métricas. No usar cuentas/filas reales para
  smoke tests que escriban.
- Si falla la aplicación sin cambio de esquema, volver a la imagen y assets
  anteriores. Si se aplicó un cambio de esquema, no improvisar rollback: seguir
  el plan aprobado, especialmente cuando haya operaciones o pagos nuevos.

## 5. Backup y restauración

El repositorio no demuestra actualmente un backup operativo. Antes de producción
el operador debe habilitar backups automatizados cifrados y PITR/binlogs según
RPO/RTO aprobados, con retención y acceso restringido.

Ejemplo ilustrativo para un entorno MySQL administrado; ajustar TLS, permisos,
retención y herramienta del proveedor. Las credenciales se obtienen de un
gestor, no de la línea de comandos:

```powershell
mysqldump --host <host> --user <backup-user> --single-transaction --routines --triggers --events --databases <catalogo> --result-file <ruta-segura> --ssl-mode=VERIFY_IDENTITY
```

1. Cifrar y almacenar el backup fuera del servidor/host de aplicación; restringir
   lectura y registrar resultado/fecha/tamaño sin registrar secretos.
2. Restaurar periódicamente a una instancia aislada de MySQL 8.4, nunca sobre
   `sistemagym_definitiva` durante una prueba.
3. Verificar integridad, esquema, conteos y una prueba funcional; medir el tiempo
   real de recuperación contra RTO y documentar evidencias.
4. No declarar recuperación lista hasta completar y aprobar una restauración
   ensayada. Definir responsables y pasos de failover junto con el proveedor.

## Bloqueos conocidos antes de producción

- Falta validar por SELECT el esquema real de `sistemagym_definitiva`, definir la
  transferencia de datos y cerrar el plan de migración PostgreSQL.
- Falta configurar dominio, TLS/proxy de confianza, variables y gestor de
  secretos del proveedor real; no se ha desplegado ni probado ese entorno.
- Falta realizar un ejercicio de backup/restauración y acordar RPO/RTO.
- El esquema real de Supabase no se ha verificado y no se ha conectado a Supabase.
- No publicar hasta completar las suites de negocio PostgreSQL/MySQL y builds.
