#!/usr/bin/env bash
# Limpieza inmediata de artifacts viejos del repo LupaFacil.
# Uso: ./tools/limpiar-artifacts.sh OWNER/REPO
# Requiere: gh CLI autenticado (gh auth login) con permisos de escritura en Actions.
set -euo pipefail

REPO="${1:-}"
if [ -z "$REPO" ]; then
  echo "Uso: $0 OWNER/REPO   (ej: $0 keiner/LupaFacil-V2)"
  exit 1
fi

echo "Artifacts actuales en $REPO:"
gh api "repos/$REPO/actions/artifacts?per_page=100" \
  -q '.artifacts[] | "\(.id)  \(.name)  \(.size_in_bytes/1048576 | floor)MB  creado \(.created_at)"' \
  | head -30

echo
read -rp "Borrar TODOS los artifacts 'LupaFacil-debug-apk' no expirados? [s/N] " resp
case "$resp" in
  [sS]|[sS][iI])
    gh api "repos/$REPO/actions/artifacts?name=LupaFacil-debug-apk&per_page=100" \
      --paginate -q '.artifacts[] | select(.expired == false) | .id' \
      | xargs -r -I{} sh -c 'echo "Borrando {}..."; gh api -X DELETE "repos/'"$REPO"'/actions/artifacts/{}"'
    echo "Listo. GitHub recalcula el uso cada 6-12 horas."
    ;;
  *)
    echo "Cancelado. No se borro nada."
    ;;
esac
