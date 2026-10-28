# Multi-inmobiliaria (PRO-020 / PRO-021 / PRO-022)

Propero es un SaaS: cada inmobiliaria (**agencia**) es un tenant aislado. Padilla es la primera.

## Cómo funciona

| Pieza | Dónde | Qué hace |
|---|---|---|
| `agencies` | `V16__multi_inmobiliaria_rls.sql` | Una fila por inmobiliaria. Padilla = `00000000-0000-0000-0000-000000000001`. |
| `agency_id` | todas las tablas de negocio | NOT NULL, FK a `agencies`, índice. **DEFAULT `propero_current_agency()`**: las inserciones toman la agencia de la transacción, las entidades JPA no la conocen. |
| Políticas RLS `agency_isolation` | mismas tablas, `FORCE ROW LEVEL SECURITY` | Solo se ven/escriben filas con `agency_id = propero_current_agency()`. Sin agencia fijada: cero filas (fail-closed). |
| `SET LOCAL app.current_agency` | `AgencyAwareJpaTransactionManager` | Al iniciar **cada** transacción ejecuta `set_config('app.current_agency', ?, true)` (= `SET LOCAL`). Nunca `SET` a secas: con pool de conexiones se filtraría a la próxima. |
| Repositorios | `@Transactional(readOnly = true)` en cada interfaz | Toda consulta corre en una transacción, así siempre hay `SET LOCAL`. |
| Agencia del usuario | `AgencyResolver` + `UserProvisioningInterceptor` | Decisión D1: un solo realm de Keycloak; la agencia sale de `users.agency_id` (consulta sin filtro, con caché). Usuarios solo-Keycloak van a `app.default-agency-id` (dev: Padilla). |
| Operaciones de plataforma | `RlsBypass` | `app.rls_bypass = on` en una transacción **nueva** (REQUIRES_NEW). Para resolver la agencia del usuario, alta de inmobiliarias y jobs que recorren todas. |
| Aviso | `RlsConfigCheck` | Al arrancar loguea `RLS activo` o `=== RLS NO ACTIVO ===`. |

Se usa `agency_id` y no `tenant_id` porque `tenant_id` ya significa **inquilino** en el código.

## Requisito operativo: la app NO puede conectarse como superusuario

RLS no aplica a superusuarios ni a roles con `BYPASSRLS`. En el contenedor `padilla-postgres` el usuario
`padilla` es superusuario. V16 crea el rol `propero_app` (`NOLOGIN NOSUPERUSER NOBYPASSRLS`) con permisos
sobre el schema. Para activar el aislamiento real en un ambiente:

```sql
-- como superusuario, una sola vez por cluster
ALTER ROLE propero_app LOGIN PASSWORD '<password-generada>';
```

y en el servicio del backend:

```bash
docker service update \
  --env-add DB_APP_USERNAME=propero_app \
  --env-add DB_APP_PASSWORD='<password-generada>' \
  padilla-backend-dev
```

Flyway sigue migrando con el dueño (`spring.flyway.user=${DB_MIGRATION_USERNAME:padilla}`,
password `${DB_MIGRATION_PASSWORD:${DB_PASSWORD}}`).

Si `DB_APP_USERNAME` no está configurado, la app usa `padilla` (como antes): funciona, pero el
aislamiento depende solo del código y el log lo avisa.

## Reglas para el código nuevo

- Tabla de negocio nueva → `agency_id` con el mismo DEFAULT, FK, índice y política (copiar el bloque de V16).
- Migración que modifica datos de tablas con RLS → empezar con `SET LOCAL app.rls_bypass = 'on';`.
- Acceso a base fuera de un request (jobs, `@Async`) → `AgencyContext.callAs(agencia, ...)` o `RlsBypass`.
- Un `findById` de otra agencia devuelve vacío → 404. No hace falta validar la agencia a mano (PRO-022):
  RLS lo garantiza también en referencias cruzadas (asignar un proveedor o abrir un ticket en una propiedad de otra agencia → 404).

## Tests

`AgencyIsolationIntegrationTest` corre con `propero_app` (sin superusuario) contra Postgres real:
cada admin ve solo su agencia, acceso por id cruzado → 404, operaciones cruzadas → 404, lo creado queda
en la agencia del usuario, sin agencia no se ve nada, y el `SET LOCAL` no se filtra a la siguiente transacción.
