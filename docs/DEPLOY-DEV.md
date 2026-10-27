# Deploy a Propero dev (PRO-009)

Propero dev = `https://propero.nilosolutions.com` (Cloudflare Access). Corre en el servidor Nilo
(`100.84.196.79`) como servicios Swarm `padilla-backend-dev` (:9280) y `padilla-frontend-dev` (:9282).
Base: contenedor `padilla-postgres`, base `padilla-db`, schema `padilla_dev` (datos ficticios);
leads en la base `propero_ficticio`.

No hay runner de GitHub Actions para estos repos: el deploy se hace en el servidor con
`scripts/deploy-dev.sh` (copiado en `~/propero-build/deploy-dev.sh`).

```bash
ssh nilo@100.84.196.79
~/propero-build/deploy-dev.sh backend  dev            # o una rama / tag / commit
~/propero-build/deploy-dev.sh frontend dev
~/propero-build/deploy-dev.sh rollback backend        # vuelve a la imagen anterior
```

Qué hace:
1. Clona/actualiza el repo público en `~/propero-build/<componente>` (no usa las copias de trabajo de Peter).
2. Backend: backup `pg_dump -n padilla_dev` en `~/apps/padilla/backups/db/` antes de cada deploy.
3. Construye la imagen `propero-<componente>:dev-<sha>` y actualiza solo el servicio `padilla-<componente>-dev`.
4. Espera el health (backend `/actuator/health`, frontend `/`). Si falla, vuelve solo a la imagen anterior.
5. Registra cada deploy en `~/propero-build/deploys.log`.

Al arrancar, el backend corre Flyway sobre `padilla_dev` (baseline 12 la primera vez, después V13+).

**Nunca** se usa para staging ni prod.
