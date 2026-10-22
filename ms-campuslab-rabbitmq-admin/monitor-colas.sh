#!/bin/bash
# =============================================================================
# monitor-colas.sh — Listener en vivo de las colas de RabbitMQ (CampusLab, EP3)
#
# Valida en la terminal que las cargas LLEGAN a las colas y se procesan, con
# colores, iconos y texto claro (no JSON crudo). Detecta cada carga comparando
# los contadores de la API de management de RabbitMQ (publicados / ACK / en
# cola), asi que ve el mensaje aunque el worker lo consuma en milisegundos.
# Para las colas muertas (DLQ) ademas muestra el contenido de los mensajes nuevos.
#
# No consume ni altera nada: solo lee. RabbitMQ refresca sus estadisticas cada
# ~5 segundos, por eso los eventos pueden verse con ese retraso.
#
# Uso:  ./monitor-colas.sh [host] [usuario] [password] [segundos]
#       (por defecto: 54.208.235.239  admin  admin123  2)
# Requiere solo bash y curl (viene con Git Bash en Windows). Sin python.
# =============================================================================
set -u

HOST="${1:-54.208.235.239}"
USER="${2:-admin}"
PASS="${3:-admin123}"
INTERVALO="${4:-2}"
API="http://${HOST}:15672/api"

R='\033[0m'; B='\033[1m'; VERDE='\033[0;32m'; AMA='\033[1;33m'; ROJO='\033[0;31m'
CYAN='\033[0;36m'; GRIS='\033[0;90m'

declare -A ETIQUETA=(
  ["cola-notificaciones"]="📧 NOTIFICACIÓN"
  ["cola-notificaciones-dlq"]="📧 NOTIFICACIÓN"
  ["cola-documentos"]="📄 DOCUMENTO"
  ["cola-documentos-dlq"]="📄 DOCUMENTO"
  ["cola-auditoria"]="📋 AUDITORÍA"
  ["cola-auditoria-dlq"]="📋 AUDITORÍA"
)
declare -A PUB ACK MSG

echo -e "${B}${CYAN}╔══════════════════════════════════════════════════════════════╗${R}"
echo -e "${B}${CYAN}║      🐰  MONITOR EN VIVO — RabbitMQ CampusLab (EP3)            ║${R}"
echo -e "${B}${CYAN}╚══════════════════════════════════════════════════════════════╝${R}"
echo -e "${GRIS}Servidor: ${HOST}  |  Panel web: http://${HOST}:15672  |  Ctrl+C para salir${R}"
echo -e "${GRIS}Esperando cargas... (realice una reserva o un cambio de catálogo en la app)${R}"
echo

COLAS_CONOCIDAS=(cola-auditoria cola-auditoria-dlq cola-documentos cola-documentos-dlq cola-notificaciones cola-notificaciones-dlq)

# extrae un contador numerico de un JSON (lee de stdin)
num() { sed -n "s/.*\"$1\":\([0-9][0-9]*\).*/\1/p" | head -1; }

instantanea() {
  local c j p a m
  for c in "${COLAS_CONOCIDAS[@]}"; do
    j=$(curl -s -m 6 -u "${USER}:${PASS}" "${API}/queues/%2F/${c}") || return 1
    case "$j" in *'"name"'*) ;; *) return 1 ;; esac
    p=$(printf '%s' "$j" | num publish)
    a=$(printf '%s' "$j" | num ack)
    m=$(printf '%s' "$j" | num messages)
    echo "$c ${p:-0} ${a:-0} ${m:-0}"
  done
}

mostrar_dlq() {
  local cola="$1" n="$2"
  curl -s -m 6 -u "${USER}:${PASS}" -X POST "${API}/queues/%2F/${cola}/get" \
    -H "Content-Type: application/json" \
    -d '{"count":50,"ackmode":"ack_requeue_true","encoding":"auto","truncate":2000}' \
  | grep -o '"payload":"[^"]*"' | tail -n "$n" | sed 's/^"payload":"//; s/"$//' \
  | while IFS= read -r linea; do
      echo -e "     ${GRIS}contenido:${R} ${linea:0:220}"
    done
}

primera=1
while true; do
  datos=$(instantanea) || { echo -e "${ROJO}⚠ Sin respuesta de ${HOST}:15672 (¿cluster apagado o puerto cerrado?)${R}"; sleep "$INTERVALO"; continue; }
  hora=$(date "+%H:%M:%S")
  while read -r cola pub ack msgs; do
    [ -z "$cola" ] && continue
    if [ "$primera" = "1" ] || [ -z "${PUB[$cola]+x}" ]; then
      PUB[$cola]=$pub; ACK[$cola]=$ack; MSG[$cola]=$msgs
      if [ "$primera" = "1" ]; then
        echo -e "${GRIS}  estado inicial ${cola}: publicados=${pub} procesados=${ack} en_cola=${msgs}${R}"
      fi
      continue
    fi
    dp=$((pub - PUB[$cola])); da=$((ack - ACK[$cola])); dm=$((msgs - MSG[$cola]))
    et="${ETIQUETA[$cola]:-$cola}"
    if [[ "$cola" == *-dlq ]]; then
      if [ "$dm" -gt 0 ]; then
        echo -e "${ROJO}${B}[$hora] 💀 +${dm} MENSAJE(S) EN COLA MUERTA  ${et}${R} ${GRIS}→ ${cola} (total en DLQ: ${msgs})${R}"
        mostrar_dlq "$cola" "$dm"; echo
      elif [ "$dm" -lt 0 ]; then
        echo -e "${AMA}[$hora] 🧹 ${cola} vaciada (quedan ${msgs})${R}\n"
      fi
    else
      if [ "$dp" -gt 0 ]; then
        echo -e "${VERDE}${B}[$hora] 📥 +${dp} CARGA(S) ENTRARON  ${et}${R} ${GRIS}→ ${cola} (total publicados: ${pub})${R}"
      fi
      if [ "$da" -gt 0 ]; then
        echo -e "${VERDE}[$hora] ✅ +${da} procesado(s) por el worker (ACK)  ${et}${R} ${GRIS}→ ${cola} (total ACK: ${ack})${R}"
      fi
      if [ "$dp" -gt 0 ] || [ "$da" -gt 0 ]; then
        if [ "$msgs" -gt 0 ]; then echo -e "     ${AMA}⏳ pendientes en cola: ${msgs}${R}"; fi
        echo
      fi
    fi
    PUB[$cola]=$pub; ACK[$cola]=$ack; MSG[$cola]=$msgs
  done <<< "$datos"
  if [ "$primera" = "1" ]; then echo; fi
  primera=0
  sleep "$INTERVALO"
done
