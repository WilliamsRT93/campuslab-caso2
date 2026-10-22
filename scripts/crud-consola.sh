#!/bin/bash
# =============================================================================
# crud-consola.sh — Demostracion por consola de los CRUD de CampusLab (EP3)
#
# Hace login contra Amazon Cognito (usuario y contrasena, sin segundo factor),
# obtiene el JWT y ejecuta TODAS las rutas del API Gateway una por una:
# catalogo y reservas (GET / POST / PUT / DELETE), mas los casos de seguridad
# (sin token = 401, cliente en ruta de admin = 403). Cada creacion/edicion/baja
# publica eventos en RabbitMQ: dejelo corriendo junto a monitor-colas.sh para
# ver como llegan las cargas a las colas.
#
# Uso:  ./crud-consola.sh [--dlq]
#         --dlq  ademas fuerza un mensaje a cada cola muerta (payload FORZAR_FALLO)
# Variables opcionales: API, CLIENT_ID, ADMIN_USER, ADMIN_PASS, CLIENTE_USER, CLIENTE_PASS
# Requiere solo bash y curl (viene con Git Bash en Windows). Sin python, sin AWS CLI.
# =============================================================================
set -u

API="${API:-https://obxz43kip7.execute-api.us-east-1.amazonaws.com}"
CLIENT_ID="${CLIENT_ID:-6r3af8ucc96okla0ujph9pru4a}"
REGION="${REGION:-us-east-1}"
ADMIN_USER="${ADMIN_USER:-admin}";     ADMIN_PASS="${ADMIN_PASS:-Admin123!}"
CLIENTE_USER="${CLIENTE_USER:-cliente}"; CLIENTE_PASS="${CLIENTE_PASS:-Cliente123!}"
FORZAR=0; [ "${1:-}" = "--dlq" ] && FORZAR=1

R='\033[0m'; B='\033[1m'; VERDE='\033[0;32m'; ROJO='\033[0;31m'; CYAN='\033[0;36m'; GRIS='\033[0;90m'
RESP=$(mktemp); trap 'rm -f "$RESP"' EXIT
OK=0; FALLOS=0

login() {
  curl -s -m 15 -X POST "https://cognito-idp.${REGION}.amazonaws.com/" \
    -H 'Content-Type: application/x-amz-json-1.1' \
    -H 'X-Amz-Target: AWSCognitoIdentityProviderService.InitiateAuth' \
    -d "{\"AuthFlow\":\"USER_PASSWORD_AUTH\",\"ClientId\":\"${CLIENT_ID}\",\"AuthParameters\":{\"USERNAME\":\"$1\",\"PASSWORD\":\"$2\"}}" \
  | sed -n 's/.*"AccessToken":"\([^"]*\)".*/\1/p'
}

# paso "descripcion" esperado METODO ruta token [cuerpo]
paso() {
  local desc="$1" esperado="$2" metodo="$3" ruta="$4" token="$5" cuerpo="${6:-}"
  local args=(-s -m 20 -o "$RESP" -w '%{http_code}' -X "$metodo" "${API}${ruta}")
  [ -n "$token" ]  && args+=(-H "Authorization: Bearer ${token}")
  [ -n "$cuerpo" ] && args+=(-H 'Content-Type: application/json' -d "$cuerpo")
  local codigo; codigo=$(curl "${args[@]}")
  if [ "$codigo" = "$esperado" ]; then
    OK=$((OK+1));       printf "  ${VERDE}✅ %-3s${R} %-7s %-34s ${GRIS}%s${R}\n" "$codigo" "$metodo" "$ruta" "$desc"
  else
    FALLOS=$((FALLOS+1)); printf "  ${ROJO}❌ %-3s${R} %-7s %-34s ${GRIS}%s (esperado %s)${R}\n" "$codigo" "$metodo" "$ruta" "$desc" "$esperado"
  fi
}
campo() { sed -n "s/.*\"$1\":\([0-9][0-9]*\).*/\1/p" "$RESP" | head -1; }

echo -e "${B}${CYAN}══════════  CRUD por consola — CampusLab (EP3)  ══════════${R}"
echo -e "${GRIS}API Gateway: ${API}${R}\n"

echo -e "${B}1) Login con Cognito (JWT)${R}"
TA=$(login "$ADMIN_USER" "$ADMIN_PASS");     [ -n "$TA" ] && echo -e "  ${VERDE}✅ ${ADMIN_USER} (Admin)${R}"   || { echo -e "  ${ROJO}❌ login de ${ADMIN_USER} fallo${R}"; exit 1; }
TC=$(login "$CLIENTE_USER" "$CLIENTE_PASS"); [ -n "$TC" ] && echo -e "  ${VERDE}✅ ${CLIENTE_USER} (Usuario)${R}" || { echo -e "  ${ROJO}❌ login de ${CLIENTE_USER} fallo${R}"; exit 1; }

echo -e "\n${B}2) Seguridad${R}"
paso "sin token" 401 GET /api/me ""
paso "identidad del admin" 200 GET /api/me "$TA"
paso "identidad del cliente" 200 GET /api/me "$TC"

echo -e "\n${B}3) CRUD Catalogo (solo Admin escribe)${R}"
paso "listar" 200 GET /api/catalog/resources "$TC"
paso "cliente NO puede crear" 403 POST /api/catalog/resources "$TC" '{"nombre":"x","tipo":"EQUIPO","stock":1,"disponible":true}'
paso "crear" 201 POST /api/catalog/resources "$TA" '{"nombre":"Osciloscopio demo consola","tipo":"EQUIPO","stock":3,"disponible":true}'
PID=$(campo id)
paso "obtener por id" 200 GET "/api/catalog/resources/${PID}" "$TA"
paso "actualizar" 200 PUT "/api/catalog/resources/${PID}" "$TA" '{"nombre":"Osciloscopio demo editado","tipo":"EQUIPO","stock":5,"disponible":true}'
paso "validacion de esquema (tipo invalido)" 400 POST /api/catalog/resources "$TA" '{"nombre":"x","tipo":"OTRO","stock":1,"disponible":true}'

echo -e "\n${B}4) CRUD Reservas${R}"
paso "listar" 200 GET /api/bookings "$TC"
paso "crear (publica 2 eventos a RabbitMQ)" 201 POST /api/bookings "$TC" "{\"nombre\":\"Cliente Demo\",\"rut\":\"11111111-1\",\"fecha\":\"2026-11-02T10:00:00\",\"productoId\":${PID}}"
RID=$(campo id)
paso "obtener por id" 200 GET "/api/bookings/${RID}" "$TC"
paso "actualizar" 200 PUT "/api/bookings/${RID}" "$TC" "{\"nombre\":\"Cliente Demo\",\"rut\":\"11111111-1\",\"fecha\":\"2026-11-03T10:00:00\",\"productoId\":${PID}}"
paso "cliente NO puede cambiar estado" 403 PUT "/api/bookings/${RID}/status" "$TC" '{"status":"APROBADA"}'
paso "admin cambia estado" 200 PUT "/api/bookings/${RID}/status" "$TA" '{"status":"APROBADA"}'
paso "validacion de esquema (rut invalido)" 400 POST /api/bookings "$TC" "{\"nombre\":\"x\",\"rut\":\"123\",\"fecha\":\"2026-11-02T10:00:00\",\"productoId\":${PID}}"

if [ "$FORZAR" = "1" ]; then
  echo -e "\n${B}5) Colas muertas (DLQ): payload FORZAR_FALLO${R}"
  paso "reserva que fuerza NACK (DLQ notificaciones + documentos)" 201 POST /api/bookings "$TA" "{\"nombre\":\"FORZAR_FALLO\",\"rut\":\"11111111-1\",\"fecha\":\"2026-11-04T10:00:00\",\"productoId\":${PID}}"
  DLQ_RID=$(campo id)
  paso "producto que fuerza NACK (DLQ auditoria)" 201 POST /api/catalog/resources "$TA" '{"nombre":"FORZAR_FALLO","tipo":"EQUIPO","stock":0,"disponible":false}'
  DLQ_PID=$(campo id)
fi

echo -e "\n${B}6) Limpieza (DELETE)${R}"
paso "eliminar reserva" 204 DELETE "/api/bookings/${RID}" "$TC"
paso "eliminar producto" 204 DELETE "/api/catalog/resources/${PID}" "$TA"
if [ "$FORZAR" = "1" ]; then
  paso "eliminar reserva FORZAR_FALLO" 204 DELETE "/api/bookings/${DLQ_RID}" "$TA"
  paso "eliminar producto FORZAR_FALLO" 204 DELETE "/api/catalog/resources/${DLQ_PID}" "$TA"
fi

echo
if [ "$FALLOS" -eq 0 ]; then
  echo -e "${B}${VERDE}RESULTADO: ${OK}/${OK} pruebas OK ✅${R}"
else
  echo -e "${B}${ROJO}RESULTADO: ${OK} OK, ${FALLOS} con fallo ❌${R}"
fi
exit "$FALLOS"
