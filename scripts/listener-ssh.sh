#!/bin/bash
# =============================================================================
# listener-ssh.sh — Listener por SSH para validar las cargas a las colas (EP3)
#
# Se conecta por SSH a las EC2 de AWS y muestra en vivo, en esta terminal:
#   colas  → el monitor de colas corriendo DENTRO de la EC2 de RabbitMQ
#            (cargas que entran, ACK de los workers y mensajes en las DLQ)
#   logs   → los logs de los microservicios (productor, consumidores, ACK/NACK
#            y los mensajes que se envian a la DLQ), filtrados y con colores
#   estado → foto rapida: contenedores del cluster y mensajes por cola
#
# Uso:  ./listener-ssh.sh colas|logs|estado
# Variables (con valores por defecto):
#   KEY_RABBIT  clave .pem de la EC2 de RabbitMQ   (~/.ssh/campuslab_rabbitmq_key.pem)
#   KEY_APPS    clave .pem de la EC2 de las apps   (~/.ssh/campuslab_persistente)
#   HOST_RABBIT IP de la EC2 de RabbitMQ           (54.208.235.239)
#   HOST_APPS   IP de la EC2 de las apps           (100.60.125.97)
# Requiere solo bash y ssh (ambos vienen con Git Bash en Windows). Sin python.
# =============================================================================
set -u

KEY_RABBIT="${KEY_RABBIT:-$HOME/.ssh/campuslab_rabbitmq_key.pem}"
KEY_APPS="${KEY_APPS:-$HOME/.ssh/campuslab_persistente}"
HOST_RABBIT="${HOST_RABBIT:-54.208.235.239}"
HOST_APPS="${HOST_APPS:-100.60.125.97}"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ServerAliveInterval=15 -o ConnectTimeout=10 -o LogLevel=ERROR)

modo="${1:-}"
case "$modo" in
  colas)
    echo "🐰 SSH → ${HOST_RABBIT}: monitor de colas en vivo (Ctrl+C para salir)"
    exec ssh -tt "${SSH_OPTS[@]}" -i "$KEY_RABBIT" "ec2-user@${HOST_RABBIT}" "./monitor-colas.sh localhost admin admin123 2"
    ;;
  logs)
    echo "📜 SSH → ${HOST_APPS}: logs de bookings y catalog en vivo (Ctrl+C para salir)"
    echo "   Filtro: eventos publicados, mensajes consumidos, ACK y envios a la DLQ"
    exec ssh "${SSH_OPTS[@]}" -i "$KEY_APPS" "ec2-user@${HOST_APPS}" \
      "cd /home/ec2-user/apps && sudo docker compose -f compose-ec2.yml logs -f --no-log-prefix --tail 0 bookings catalog 2>&1 \
        | grep --line-buffered -E 'Evento publicado|publisher confirm|Mensaje devuelto|Error recuperable|Enviando notificacion|Generando documento|Registrando auditoria|confirmad|DLQ' \
        | sed -u -e 's/^[0-9T:.Z-]* *//' \
                 -e 's/.*Evento publicado/\x1b[32m📥 Evento publicado/' \
                 -e 's/.*\(Broker confirm[^ ]*\)/\x1b[32m📨 \1/' \
                 -e 's/.*Mensaje devuelto/\x1b[31m↩️ Mensaje devuelto/' \
                 -e 's/.*\(Error recuperable\)/\x1b[33m🔁 \1/' \
                 -e 's/.*\(Enviando notificacion\)/\x1b[36m📧 \1/' \
                 -e 's/.*\(Generando documento\)/\x1b[36m📄 \1/' \
                 -e 's/.*\(Registrando auditoria\)/\x1b[36m📋 \1/' \
                 -e 's/.*\(confirmad[ao]\)/\x1b[32m✅ \1/' \
                 -e 's/.*enviando a DLQ/\x1b[31m💀 ENVIADO A DLQ/' \
                 -e 's/$/\x1b[0m/'"
    ;;
  estado)
    echo "📊 Cluster RabbitMQ (${HOST_RABBIT})"
    ssh "${SSH_OPTS[@]}" -i "$KEY_RABBIT" "ec2-user@${HOST_RABBIT}" \
      "sudo docker ps --format '  {{.Names}}  {{.Status}}'; echo; echo '  cola                       mensajes  consumidores'; sudo docker exec rabbitmq-1 rabbitmqctl -q list_queues name messages consumers 2>/dev/null | sort | awk 'NF==3 && \$1!=\"name\" {printf \"  %-26s %-9s %s\\n\", \$1, \$2, \$3}'"
    echo
    echo "📊 Microservicios (${HOST_APPS})"
    ssh "${SSH_OPTS[@]}" -i "$KEY_APPS" "ec2-user@${HOST_APPS}" "sudo docker ps --format '  {{.Names}}  {{.Status}}'"
    ;;
  *)
    echo "Uso: $0 colas|logs|estado"
    exit 1
    ;;
esac
