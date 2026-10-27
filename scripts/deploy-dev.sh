#!/usr/bin/env bash
# =====================================================================
# PRO-009: deploy reproducible a Propero dev (servidor Nilo, Docker Swarm).
#
#   ./deploy-dev.sh backend  <rama|tag|commit>
#   ./deploy-dev.sh frontend <rama|tag|commit>
#   ./deploy-dev.sh rollback backend|frontend
#
# - Clona el repo publico de GitHub en ~/propero-build/<componente> (no usa
#   las copias de trabajo de Peter).
# - Imagen local con tag por commit: propero-<componente>:dev-<sha>.
# - Antes de un deploy de backend hace backup del schema padilla_dev.
# - Guarda la imagen anterior y vuelve atras sola si el health falla.
# Solo toca los servicios padilla-*-dev. Nunca staging ni prod.
# =====================================================================
set -euo pipefail

COMP="${1:?componente: backend|frontend|rollback}"
BASE="$HOME/propero-build"
BACKUPS="$HOME/apps/padilla/backups/db"
mkdir -p "$BASE"

servicio() { echo "padilla-$1-dev"; }
health_url() { [ "$1" = backend ] && echo "http://localhost:9280/actuator/health" || echo "http://localhost:9282/"; }

esperar_health() {
    local url; url=$(health_url "$1")
    for _ in $(seq 1 40); do
        if [ "$(curl -s -o /dev/null -w '%{http_code}' "$url")" = "200" ]; then
            echo "health OK: $url"; return 0
        fi
        sleep 5
    done
    echo "health FALLO: $url"; return 1
}

rollback() {
    local comp="$1" prev
    prev=$(cat "$BASE/$comp.previous" 2>/dev/null || true)
    [ -n "$prev" ] || { echo "no hay imagen anterior registrada para $comp"; exit 1; }
    echo "rollback de $(servicio "$comp") a $prev"
    docker service update --quiet --no-resolve-image --image "$prev" "$(servicio "$comp")"
    esperar_health "$comp"
}

if [ "$COMP" = rollback ]; then
    rollback "${2:?componente a revertir}"
    exit 0
fi

REF="${2:?rama, tag o commit}"
case "$COMP" in
    backend)  REPO=https://github.com/adrianpuche12/padilla_backend.git ;;
    frontend) REPO=https://github.com/adrianpuche12/padilla_frontend.git ;;
    *) echo "componente invalido: $COMP"; exit 1 ;;
esac

DIR="$BASE/$COMP"
[ -d "$DIR/.git" ] || git clone -q "$REPO" "$DIR"
git -C "$DIR" fetch -q --all --prune
git -C "$DIR" checkout -q --detach "origin/$REF" 2>/dev/null || git -C "$DIR" checkout -q --detach "$REF"
SHA=$(git -C "$DIR" rev-parse --short HEAD)
IMAGE="propero-$COMP:dev-$SHA"
echo "== $COMP $REF -> $SHA ($IMAGE)"

if [ "$COMP" = backend ]; then
    mkdir -p "$BACKUPS"
    DUMP="$BACKUPS/padilla_dev-$(date +%Y%m%d-%H%M%S)-pre-$SHA.dump"
    docker exec padilla-postgres pg_dump -U padilla -d padilla-db -n padilla_dev -Fc > "$DUMP"
    echo "backup: $DUMP ($(du -h "$DUMP" | cut -f1))"
fi

docker build -q -t "$IMAGE" "$DIR" >/dev/null
docker service inspect "$(servicio "$COMP")" --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}' > "$BASE/$COMP.previous"
echo "imagen anterior: $(cat "$BASE/$COMP.previous")"

docker service update --quiet --no-resolve-image --image "$IMAGE" "$(servicio "$COMP")"
if ! esperar_health "$COMP"; then
    echo "el deploy no paso el health: vuelvo a la imagen anterior"
    rollback "$COMP"
    exit 1
fi
echo "$(date -Is) $COMP $REF $SHA $IMAGE" >> "$BASE/deploys.log"
echo "== deploy OK"
