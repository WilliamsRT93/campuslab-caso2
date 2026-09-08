#!/usr/bin/env bash
#
# Crea un AWS API Gateway (HTTP API) que:
#   - Valida el JWT de Cognito con un JWT Authorizer (issuer + audience = client_id).
#   - Enruta TODO /{proxy+} hacia el BFF en EC2 mediante integracion HTTP_PROXY (SIN Lambda).
#   - Aplica CORS para el origen del frontend.
#
# Uso:
#   export REGION=us-east-1
#   export COGNITO_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXX
#   export COGNITO_CLIENT_ID=xxxxxxxxxxxx
#   export BFF_URL=http://<IP_PUBLICA_EC2>:8080     # BFF accesible por HTTP
#   export FRONTEND_ORIGIN=http://<IP_PUBLICA_EC2>:8090
#   ./setup-apigateway.sh
#
set -euo pipefail
: "${REGION:=us-east-1}"
: "${COGNITO_ISSUER_URI:?Define COGNITO_ISSUER_URI}"
: "${COGNITO_CLIENT_ID:?Define COGNITO_CLIENT_ID}"
: "${BFF_URL:?Define BFF_URL (ej http://IP:8080)}"
: "${FRONTEND_ORIGIN:?Define FRONTEND_ORIGIN}"

echo ">> 1. Creando HTTP API con CORS..."
API_ID=$(aws apigatewayv2 create-api \
  --region "$REGION" \
  --name campuslab-http-api \
  --protocol-type HTTP \
  --cors-configuration "AllowOrigins=${FRONTEND_ORIGIN},AllowMethods=GET,POST,PUT,DELETE,OPTIONS,AllowHeaders=authorization,content-type" \
  --query ApiId --output text)
echo "   API_ID: $API_ID"

echo ">> 2. Creando JWT Authorizer (Cognito)..."
AUTH_ID=$(aws apigatewayv2 create-authorizer \
  --region "$REGION" --api-id "$API_ID" \
  --authorizer-type JWT \
  --name cognito-jwt \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration "Audience=${COGNITO_CLIENT_ID},Issuer=${COGNITO_ISSUER_URI}" \
  --query AuthorizerId --output text)
echo "   AUTH_ID: $AUTH_ID"

echo ">> 3. Creando integracion HTTP_PROXY hacia el BFF..."
INT_ID=$(aws apigatewayv2 create-integration \
  --region "$REGION" --api-id "$API_ID" \
  --integration-type HTTP_PROXY \
  --integration-method ANY \
  --integration-uri "${BFF_URL}/{proxy}" \
  --payload-format-version 1.0 \
  --query IntegrationId --output text)
echo "   INT_ID: $INT_ID"

echo ">> 4. Creando ruta protegida ANY /{proxy+}..."
aws apigatewayv2 create-route \
  --region "$REGION" --api-id "$API_ID" \
  --route-key 'ANY /{proxy+}' \
  --target "integrations/${INT_ID}" \
  --authorization-type JWT \
  --authorizer-id "$AUTH_ID" >/dev/null

echo ">> 5. Creando stage \$default con auto-deploy..."
aws apigatewayv2 create-stage \
  --region "$REGION" --api-id "$API_ID" \
  --stage-name '$default' --auto-deploy >/dev/null

INVOKE_URL="https://${API_ID}.execute-api.${REGION}.amazonaws.com"
cat <<RESUMEN

=================== API GATEWAY LISTO ===================
INVOKE URL (VITE_API_BASE_URL) = ${INVOKE_URL}
Ruta: ANY /{proxy+}  ->  ${BFF_URL}
Autorizacion: JWT de Cognito (issuer + client_id)
  - Sin token o token invalido  -> 401 (lo rechaza el API Gateway)
  - Token valido pero rol sin permiso -> 403 (lo rechaza el BFF)
========================================================
RESUMEN
