#!/bin/bash
# =============================================================================
# probar-topic.sh — Prueba del exchange TOPIC (bash + curl, sin python)
#
# Publica por el API Gateway (POST /api/rabbitmq/publicar) en el exchange topic "campuslab.eventos" con tres
# routing keys y muestra a que cola(s) las enruto el broker segun el patron del binding:
#   reserva.creada             -> cola-eventos-reservas   (patron reserva.#)
#   catalogo.producto.creacion -> cola-eventos-catalogo   (patron catalogo.#)
#   otro.dominio               -> ninguna: el broker la devuelve (sin coincidencia de patron)
#
# Uso:  ./probar-topic.sh      Variables: USUARIO=admin CLAVE=Admin123! COGNITO_CLIENT_ID=... API_GATEWAY=...
# =============================================================================
set -u
USUARIO="${USUARIO:-admin}"; CLAVE="${CLAVE:-Admin123!}"
CLIENT_ID="${COGNITO_CLIENT_ID:-6r3af8ucc96okla0ujph9pru4a}"; REGION="${REGION:-us-east-1}"
API="${API_GATEWAY:-https://obxz43kip7.execute-api.us-east-1.amazonaws.com}"
TOKEN=$(curl -s -m 15 -X POST "https://cognito-idp.${REGION}.amazonaws.com/" -H 'Content-Type: application/x-amz-json-1.1' \
  -H 'X-Amz-Target: AWSCognitoIdentityProviderService.InitiateAuth' \
  -d "{\"AuthFlow\":\"USER_PASSWORD_AUTH\",\"ClientId\":\"${CLIENT_ID}\",\"AuthParameters\":{\"USERNAME\":\"${USUARIO}\",\"PASSWORD\":\"${CLAVE}\"}}" \
  | sed -n 's/.*"AccessToken":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "❌ No se pudo obtener el token"; exit 1; }
echo "Exchange topic: campuslab.eventos   (reserva.# -> cola-eventos-reservas | catalogo.# -> cola-eventos-catalogo)"
for clave in reserva.creada catalogo.producto.creacion otro.dominio; do
  echo
  echo "\$ curl -X POST $API/api/rabbitmq/publicar -d '{\"exchange\":\"campuslab.eventos\",\"routingKey\":\"$clave\",...}'"
  R=$(curl -s -m 20 -X POST "$API/api/rabbitmq/publicar" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
      -d "{\"exchange\":\"campuslab.eventos\",\"routingKey\":\"$clave\",\"mensaje\":\"Prueba del topic con $clave\"}")
  echo "  routing key : $clave"
  echo "  ruteado     : $(echo "$R" | sed -n 's/.*"ruteado":\([a-z]*\).*/\1/p')   colas: $(echo "$R" | sed -n 's/.*"colasDestino":\[\([^]]*\)\].*/\1/p')"
  echo "  $(echo "$R" | sed -n 's/.*"detalle":"\([^"]*\)".*/\1/p')"
done
