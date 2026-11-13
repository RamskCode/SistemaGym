# Perfil `prod`: Supabase desde IntelliJ y Render

El perfil `prod` usa el esquema Supabase existente. No ejecuta DDL ni
migraciones: Hibernate queda en `validate`; SQL init, Flyway y Liquibase están
deshabilitados. La URL JDBC va en `DB_URL`, separada de usuario/contraseña, y
debe incluir `sslmode=require`.

## Primera ejecución controlada desde IntelliJ

En **Run → Edit Configurations…**, selecciona la aplicación, fija el perfil
activo `prod` y configura las variables de entorno:

| Variable | Valor / indicación |
|---|---|
| `DB_URL` | `jdbc:postgresql://aws-1-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require&ApplicationName=SistemaGym-prod` |
| `DB_USERNAME` | `sistemagym_app.psrwbrauakrbmoqzvwfb`, una vez creado el rol de runtime con permisos limitados según `docs/sql/postgresql/create_supabase_app_role.sql`. |
| `DB_PASSWORD` | Contraseña del usuario de aplicación, solo en Environment variables/secret store. |
| `JWT_SECRET_BASE64` | Clave aleatoria Base64 de al menos 32 bytes; no reutilizar una clave de desarrollo. |
| `APP_CORS_ALLOWED_ORIGINS` | Origen del frontend autorizado; para una primera ejecución local controlada se puede poner `http://localhost:5173`. |
| `SISTEMAGYM_FRONTEND_URL` | URL base del frontend. Debe existir aunque no se vaya a probar correo/invitaciones. |
| `AUTH_COOKIE_SAME_SITE` | `None` para frontend alojado en otro sitio junto con HTTPS; `Strict` solo si el despliegue y flujo de autenticación lo permiten. |

Opcionales: `DB_POOL_MAX`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS`,
`JWT_EXPIRATION_MS`, `SISTEMAGYM_MAIL_USERNAME`,
`SISTEMAGYM_MAIL_APP_PASSWORD` y `SISTEMAGYM_MAIL_FROM`. Render proporciona
`PORT`; no se debe fijar manualmente allí.

Antes de iniciar, confirma que `DB_URL` no tenga usuario/contraseña embebidos y
que `sslmode=require` esté presente. El perfil usa `public` como esquema JPA,
UTC y no agrega `extensions` al `search_path`; el backend invoca
`extensions.unaccent` de forma calificada.

El primer inicio validará conexión y esquema, pero no ejecutará el bootstrap de
administrador, la reconciliación de onboarding ni el ciclo automático de
membresías. Estas rutinas están deshabilitadas en `prod` para proteger la base
definitiva durante la primera conexión. Los endpoints normales de la aplicación
sí conservan sus operaciones de negocio; no los invoques durante una prueba que
solo pretende validar conexión/esquema. El rol de solo lectura creado para
validaciones puede usarse temporalmente para comprobar el arranque, pero no es
apto para el uso normal de la aplicación, cuyos endpoints requieren escritura.

## Variables de entorno de Render

Configura el mismo conjunto en el Environment del servicio backend de Render:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_BASE64`,
`APP_CORS_ALLOWED_ORIGINS` y `SISTEMAGYM_FRONTEND_URL`. Agrega los secretos de
correo si se habilita esa funcionalidad. Usa el pooler de sesión indicado,
TLS requerido, y conserva credenciales solo como variables secretas de Render.
Configura `APP_CORS_ALLOWED_ORIGINS` y `SISTEMAGYM_FRONTEND_URL` con la URL
definitiva de Vercel. No es necesario cambiar `application-prod.properties` para
cambiar entre IntelliJ y Render.

El scheduler de membresías permanece apagado. Activarlo requiere decisión y
prueba operativa separada porque procesa/cambia membresías al inicio y a diario.

Antes de configurar el usuario de runtime, sigue también
`docs/deployment/SUPABASE_APP_ROLE.md`. El rol `sistemagym_readonly` es solo para
validación de lectura y no sirve para las operaciones normales de la aplicación.
