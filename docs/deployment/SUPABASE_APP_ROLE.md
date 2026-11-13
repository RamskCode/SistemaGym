# Usuario de runtime SistemaGym para Supabase

`docs/sql/postgresql/create_supabase_app_role.sql` es un borrador para revisión
y ejecución manual por un administrador en Supabase SQL Editor. **No se ejecuta
desde Spring Boot ni forma parte del DDL de las 25 tablas.** Crea el login
`sistemagym_app` y concede `SELECT`, `INSERT`, `UPDATE` y `DELETE` solo sobre las
25 tablas de SistemaGym en `public`.

También concede `USAGE`/`SELECT` sobre las secuencias identity de esas tablas,
`USAGE` del esquema `extensions` y `EXECUTE` únicamente sobre
`extensions.unaccent(text)`. Para el generador de códigos y el bloqueo del ciclo
de vida se autorizan las tres funciones concretas de advisory lock usadas por
el backend. El resto de las funciones integradas reside en `pg_catalog` y está
disponible según los permisos estándar de PostgreSQL.

El rol no es superusuario, no puede crear bases ni roles, tiene `NOINHERIT` y,
al crearse, no pertenece a otros roles. Solo recibe `USAGE` (no `CREATE`) en
`public` y `extensions`; el borrador no concede `USAGE` ni privilegios de datos
en los demás esquemas. Supabase puede conceder `USAGE` sobre esquemas internos a
`PUBLIC`; `USAGE` permite resolver nombres, pero por sí solo no otorga acceso a
las tablas de esos esquemas. Los permisos heredados de `PUBLIC` no pueden
denegarse a un solo rol sin cambiar ACL globales, por lo que el script no las
revoca. No recibe `TRUNCATE`, `REFERENCES`, `TRIGGER` ni `UPDATE` sobre
secuencias (`setval`). La validación aborta si detecta `CREATE` efectivo en la
base o esquemas persistentes.

El permiso de base de datos `TEMPORARY` puede heredarse desde `PUBLIC`. El
script solo emite un `NOTICE` si está presente; no modifica privilegios globales.
Ese permiso permite objetos temporales en `pg_temp`, no DDL persistente sobre
`public`. Si se exige prohibir incluso objetos temporales, hace falta una
decisión administrativa separada: revocar `TEMPORARY` a `PUBLIC` podría afectar
otros servicios de Supabase.

Hibernate en `prod` usa `ddl-auto=validate`; SQL init, Flyway y Liquibase están
desactivados. Por tanto, el runtime no necesita permisos DDL. Los triggers de
timestamps existentes se ejecutan al hacer UPDATE y el usuario no necesita
crear/modificar triggers. El backend invoca `extensions.unaccent` con el esquema
calificado y no añade `extensions` al `search_path`.

## Ejecución manual segura

1. Revisa el script completo y confirma en Supabase que el rol
   `sistemagym_app` no existe todavía. Es de una sola ejecución; no es un
   script idempotente de rotación de permisos.
2. Ejecuta el script en Supabase SQL Editor como administrador. No contiene
   contraseña ni marcador secreto; crea el rol sin contraseña y aplica sus ACL
   dentro de una transacción. SQL Editor puede conservar el texto del query,
   pero no habrá una contraseña que quede en su historial.
3. Genera una contraseña aleatoria localmente desde CMD de Windows (64 dígitos
   hexadecimales, 256 bits) y cópiala al portapapeles sin imprimirla. El
   portapapeles contendrá el secreto hasta que lo reemplaces:

   ```cmd
   powershell -NoProfile -Command "$b=New-Object byte[] 32; $r=[Security.Cryptography.RandomNumberGenerator]::Create(); $r.GetBytes($b); -join ($b | ForEach-Object { $_.ToString('x2') }); $r.Dispose()" | clip
   ```

4. Desde CMD, conecta `psql` al Session Pooler como administrador, sin poner
   ninguna contraseña en la línea de comandos:

   ```cmd
   psql "host=aws-1-us-east-1.pooler.supabase.com port=5432 dbname=postgres user=postgres.psrwbrauakrbmoqzvwfb sslmode=require"
   ```

   `psql` pedirá la contraseña del administrador interactivamente. Ya conectado,
   ejecuta `\password sistemagym_app`; pega el secreto generado en ambos prompts.
   `\password` evita incluir la contraseña en SQL, historial de comandos o logs
   del servidor como texto claro. Sal con `\q`.

5. En IntelliJ, mantén el perfil `prod` y configura en Environment variables:
   - `DB_USERNAME=sistemagym_app.psrwbrauakrbmoqzvwfb` (formato del Session
     Pooler `rol.project-ref`).
   - `DB_PASSWORD` con el secreto recién asignado.
   - Conserva `DB_URL` con host/puerto/base del Session Pooler y
     `sslmode=require`; mantén `JWT_SECRET_BASE64` y las variables de CORS y
     frontend documentadas en `PROD_SUPABASE_RENDER.md`.

Si cualquier comprobación genera excepción, no uses el rol ni amplíes
   privilegios automáticamente. Revisa el motivo; en particular, un permiso
   heredado de `PUBLIC` no debe corregirse con un `REVOKE` global sin evaluar el
   efecto en otros usuarios de la base.

## Alcance y comprobaciones pendientes

El script otorga permisos sobre las 25 tablas presentes en `public` al momento
de ejecutarlo. No establece permisos por defecto para tablas/secuencias futuras;
si se autoriza una evolución de esquema, sus ACL se deberán revisar por separado.
No concede `BYPASSRLS`. Según la verificación del operador en Supabase, las 25
tablas de `public` tienen RLS desactivado y ninguna está forzada; no se requieren
políticas RLS para este usuario en esas tablas. Si se habilita RLS en el futuro,
las políticas deberán autorizar explícitamente las operaciones del rol.

El usuario debe ser propietario no de las tablas ni de las secuencias. La cuenta
que ejecuta el script en SQL Editor sí necesita permiso de administración para
crear roles y otorgar ACL.
