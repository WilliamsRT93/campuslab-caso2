#!/bin/bash
# =============================================================================
# monitor-colas.sh — Listener en vivo de las colas de RabbitMQ para CampusLab (EP3)
#
# Muestra en la terminal, de forma MUY intuitiva (colores + iconos + texto claro,
# no JSON crudo), cada mensaje nuevo que entra a las colas de negocio y a las
# colas muertas (DLQ). Pensado para usarse durante la demo en vivo, junto al
# dashboard web nativo de RabbitMQ (puerto 15672).
#
# No consume los mensajes de verdad: los vuelve a poner en la cola (requeue=true)
# via la API HTTP de management, asi que no interfiere con el procesamiento real
# de los workers — es solo un monitor de lectura.
#
# Uso:
#   ./monitor-colas.sh [host] [usuario] [password]
#   (por defecto: localhost, admin, admin123)
# =============================================================================
set -u

HOST="${1:-localhost}"
USER="${2:-admin}"
PASS="${3:-admin123}"
API="http://${HOST}:15672/api"
VHOST="%2F"

# Colores
C_RESET='\033[0m'
C_BOLD='\033[1m'
C_VERDE='\033[0;32m'
C_AMARILLO='\033[1;33m'
C_ROJO='\033[0;31m'
C_AZUL='\033[0;34m'
C_CYAN='\033[0;36m'
C_GRIS='\033[0;90m'

declare -A COLAS=(
  ["cola-notificaciones"]="📧 NOTIFICACIÓN"
  ["cola-documentos"]="📄 DOCUMENTO"
  ["cola-auditoria"]="📋 AUDITORÍA"
)
declare -A DLQS=(
  ["cola-notificaciones-dlq"]="📧 NOTIFICACIÓN"
  ["cola-documentos-dlq"]="📄 DOCUMENTO"
  ["cola-auditoria-dlq"]="📋 AUDITORÍA"
)

echo -e "${C_BOLD}${C_CYAN}╔══════════════════════════════════════════════════════════════╗${C_RESET}"
echo -e "${C_BOLD}${C_CYAN}║        🐰  MONITOR EN VIVO — RabbitMQ CampusLab (EP3)          ║${C_RESET}"
echo -e "${C_BOLD}${C_CYAN}╚══════════════════════════════════════════════════════════════╝${C_RESET}"
echo -e "${C_GRIS}Conectado a: ${HOST}:15672  |  Dashboard web: http://${HOST}:15672  |  Ctrl+C para salir${C_RESET}"
echo

mostrar_mensaje() {
  local cola="$1" etiqueta="$2" color="$3" tipo="$4" payload="$5" hora
  hora=$(date "+%H:%M:%S")
  echo -e "${color}${C_BOLD}[$hora] ${tipo} ${etiqueta}${C_RESET} ${C_GRIS}→ cola: ${cola}${C_RESET}"
  echo -e "   ${C_GRIS}contenido:${C_RESET} $(echo "$payload" | head -c 200)"
  echo
}

verificar_cola() {
  local cola="$1" etiqueta="$2" color="$3" tipo="$4"
  local resp
  resp=$(curl -s -u "${USER}:${PASS}" -X POST \
    "${API}/queues/${VHOST}/${cola}/get" \
    -H "Content-Type: application/json" \
    -d '{"count":5,"ackmode":"ack_requeue_true","encoding":"auto","truncate":5000}' 2>/dev/null)

  [ -z "$resp" ] && return
  [ "$resp" = "[]" ] && return

  echo "$resp" | python3 -c "
import json, sys
try:
    msgs = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for m in msgs:
    print(m.get('payload', ''))
" 2>/dev/null | while IFS= read -r payload; do
    [ -n "$payload" ] && mostrar_mensaje "$cola" "$etiqueta" "$color" "$tipo" "$payload"
  done
}

while true; do
  for cola in "${!COLAS[@]}"; do
    verificar_cola "$cola" "${COLAS[$cola]}" "$C_VERDE" "✅ MENSAJE RECIBIDO"
  done
  for dlq in "${!DLQS[@]}"; do
    verificar_cola "$dlq" "${DLQS[$dlq]}" "$C_ROJO" "💀 MENSAJE EN COLA MUERTA (DLQ)"
  done
  sleep 2
done
