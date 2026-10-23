#!/bin/bash
# =============================================================================
# validar-ep3.sh — Valida desde la consola (bash + curl, sin python) lo mismo que Swagger:
#   1) rutas del API Gateway: JWT obligatorio y NINGUNA ruta proxy (las no declaradas dan 404)
#   2) colas: listener activo, binding, DLX, TTL y cola muerta (y opcionalmente la prueba extremo a extremo)
#
# Uso:  ./validar-ep3.sh [rutas|colas|prueba|todo]      (por defecto: todo)
# Variables (con valores por defecto): USUARIO=admin  CLAVE=Admin123!  HOST_ADMIN=54.208.235.239
#   COGNITO_CLIENT_ID=6r3af8ucc96okla0ujph9pru4a  REGION=us-east-1
# =============================================================================
set -u
USUARIO="${USUARIO:-admin}"; CLAVE="${CLAVE:-Admin123!}"
HOST_ADMIN="${HOST_ADMIN:-54.208.235.239}"
CLIENT_ID="${COGNITO_CLIENT_ID:-6r3af8ucc96okla0ujph9pru4a}"; REGION="${REGION:-us-east-1}"
API="http://${HOST_ADMIN}:8083/api/rabbitmq/validacion"
modo="${1:-todo}"

echo "🔑 Token de Cognito (${USUARIO})..."
TOKEN=$(curl -s -m 15 -X POST "https://cognito-idp.${REGION}.amazonaws.com/" \
  -H 'Content-Type: application/x-amz-json-1.1' -H 'X-Amz-Target: AWSCognitoIdentityProviderService.InitiateAuth' \
  -d "{\"AuthFlow\":\"USER_PASSWORD_AUTH\",\"ClientId\":\"${CLIENT_ID}\",\"AuthParameters\":{\"USERNAME\":\"${USUARIO}\",\"PASSWORD\":\"${CLAVE}\"}}" \
  | sed -n 's/.*"AccessToken":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "❌ No se pudo obtener el token"; exit 1; }
echo "   token obtenido (${#TOKEN} caracteres)"; echo

llamar() { curl -s -m 60 -X "$1" -H "Authorization: Bearer $TOKEN" "$API/$2"; }
campo()  { sed -n "s/.*\"$1\":\"\\([^\"]*\\)\".*/\\1/p" | head -1; }

rutas() {
  echo "🛣️  GET /api/rabbitmq/validacion/rutas"
  J=$(llamar GET rutas)
  echo "$J" | sed 's/},{/}\n{/g' | grep -o '"metodo":"[A-Z]*","ruta":"[^"]*","esperadoConToken":[0-9]*,"sinToken":[0-9]*,"conToken":[0-9]*,"ok":[a-z]*' \
    | sed 's/"metodo":"\([A-Z]*\)","ruta":"\([^"]*\)","esperadoConToken":\([0-9]*\),"sinToken":\([0-9]*\),"conToken":\([0-9]*\),"ok":\(true\|false\)/  \1 \2  sin-token=\4 con-token=\5 (esperado \3) ok=\6/' | awk '{printf "%-7s %-34s %s %s %s %s\n", $1, $2, $3, $4, $5, $6}'
  echo "  -- rutas NO declaradas (deben dar 404: no hay proxy):"
  echo "$J" | sed 's/},{/}\n{/g' | grep -o '"metodo":"[A-Z]*","ruta":"[^"]*","sinToken":[0-9]*,"conToken":[0-9]*,"ok":[a-z]*' \
    | sed 's/"metodo":"\([A-Z]*\)","ruta":"\([^"]*\)","sinToken":\([0-9]*\),"conToken":\([0-9]*\),"ok":\(true\|false\)/\1 \2 sin-token=\3 con-token=\4 ok=\5/' | awk '{printf "  %-7s %-36s %s %s %s\n", $1, $2, $3, $4, $5}'
  echo; echo "  $(echo "$J" | campo conclusion)"; echo
}
colas() {
  local metodo="$1" ruta="$2" titulo="$3"
  echo "📋 $metodo /api/rabbitmq/validacion/$ruta   ($titulo)"
  J=$(llamar "$metodo" "$ruta")
  echo "$J" | sed 's/{"cola"/\n{"cola"/g' | grep '^{"cola"' | while read -r linea; do
    v() { echo "$linea" | sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\).*/\1/p" | head -1; }
    printf "  %-22s [%s · %s]\n      consumidores=%s listener=%s binding=%s dlx=%s ttl=%s dlq=%s  ok=%s\n" \
      "$(v cola)" "$(v exchange)" "$(v routingKey)" "$(v consumidores)" "$(v listenerActivo)" "$(v bindingPrincipal)" \
      "$(v tieneDlx)" "$(v tieneTtl)" "$(v colaMuertaConBinding)" "$(v ok)"
  done
  if [ "$ruta" = "colas/prueba" ]; then echo "$J" | grep -o '"detalle":"[^"]*"' | sed 's/"detalle":"\(.*\)"/   · \1/'; fi
  echo; echo "  $(echo "$J" | campo conclusion)"; echo
}

case "$modo" in
  rutas)  rutas ;;
  colas)  colas GET colas "solo lectura" ;;
  prueba) colas POST colas/prueba "publica 1 mensaje a cada cola" ;;
  todo)   rutas; colas GET colas "solo lectura"; colas POST colas/prueba "publica 1 mensaje a cada cola" ;;
  *) echo "Uso: $0 [rutas|colas|prueba|todo]"; exit 1 ;;
esac
