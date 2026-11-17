#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE=(docker compose -f docker-compose.prod.yml)

if ! command -v docker >/dev/null 2>&1; then
  echo "✗ Docker no está instalado. Instalalo con:"
  echo "    curl -fsSL https://get.docker.com | sh"
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "✗ Falta el plugin 'docker compose' (viene con la instalación oficial de Docker)."
  exit 1
fi

if [[ "${1:-}" == "--pull" ]]; then
  echo "→ Actualizando el código (git pull)…"
  git pull --ff-only
fi

if [[ ! -f .env ]]; then
  echo "→ Primera vez: vamos a crear el archivo .env"
  read -rp "  Subdominio (ej: saga.patricioponcini.com.ar): " DOMAIN
  read -rp "  Email para Let's Encrypt: " ACME_EMAIL
  read -rp "  Reinicio automático de la demo [15m]: " RESET
  umask 077
  cat > .env <<EOF
DOMAIN=${DOMAIN}
ACME_EMAIL=${ACME_EMAIL}
DB_PASSWORD=$(openssl rand -hex 24)
RABBIT_PASSWORD=$(openssl rand -hex 24)
DEMO_RESET_INTERVAL=${RESET:-15m}
TRUSTED_PROXIES=private_ranges
EOF
  echo "  ✓ .env creado (permisos 600). Guardalo: si lo perdés, la base no va a aceptar una contraseña nueva."
fi

DOMAIN=$(grep -E '^DOMAIN=' .env | cut -d= -f2-)

echo "→ Construyendo y levantando (la primera vez tarda unos minutos: compila Kotlin y la UI)…"
"${COMPOSE[@]}" up -d --build --remove-orphans

echo "→ Limpiando imágenes viejas…"
docker image prune -f >/dev/null

echo "→ Esperando a que la web responda…"
for _ in $(seq 1 60); do
  if curl -fsS -o /dev/null "https://${DOMAIN}/api/orchestrator/inspect/reset" 2>/dev/null; then
    echo
    echo "  ✓ Listo: https://${DOMAIN}"
    "${COMPOSE[@]}" ps --format 'table {{.Service}}\t{{.Status}}'
    exit 0
  fi
  sleep 5
  printf '.'
done

echo
echo "  ⚠ La web todavía no responde en https://${DOMAIN}. Revisá:"
echo "    - que el registro DNS apunte a este servidor:   dig +short ${DOMAIN}"
echo "    - que los puertos 80 y 443 estén abiertos:      sudo ufw status"
echo "    - los logs:                                      ${COMPOSE[*]} logs -f caddy saga-orchestrator"
exit 1
