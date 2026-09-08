#!/usr/bin/env bash
#
# Crea en AWS el User Pool de Cognito federado con Microsoft Entra ID.
# Requisitos: AWS CLI configurada y una App Registration en Entra ya creada
#             (ver ../azure/entra-app-registration.sh) para obtener ENTRA_CLIENT_ID/SECRET.
#
# Uso:
#   export REGION=us-east-1
#   export DOMAIN_PREFIX=campuslab-duoc          # debe ser unico a nivel de region
#   export TENANT_ID=<entra-tenant-id>
#   export ENTRA_CLIENT_ID=<app-id-de-la-app-de-cognito-en-entra>
#   export ENTRA_CLIENT_SECRET=<secret-de-esa-app>
#   export CALLBACK_URL=http://localhost:5173/cognito-callback
#   export LOGOUT_URL=http://localhost:5173
#   ./setup-cognito-entra.sh
#
set -euo pipefail

: "${REGION:=us-east-1}"
: "${DOMAIN_PREFIX:?Define DOMAIN_PREFIX}"
: "${TENANT_ID:?Define TENANT_ID}"
: "${ENTRA_CLIENT_ID:?Define ENTRA_CLIENT_ID}"
: "${ENTRA_CLIENT_SECRET:?Define ENTRA_CLIENT_SECRET}"
: "${CALLBACK_URL:=http://localhost:5173/cognito-callback}"
: "${LOGOUT_URL:=http://localhost:5173}"
PROVIDER_NAME=EntraID

echo ">> 1. Creando User Pool 'campuslab-userpool'..."
POOL_ID=$(aws cognito-idp create-user-pool \
  --region "$REGION" \
  --pool-name campuslab-userpool \
  --auto-verified-attributes email \
  --query 'UserPool.Id' --output text)
echo "   User Pool: $POOL_ID"

echo ">> 2. Creando dominio Hosted UI '$DOMAIN_PREFIX'..."
aws cognito-idp create-user-pool-domain \
  --region "$REGION" --user-pool-id "$POOL_ID" --domain "$DOMAIN_PREFIX"
COGNITO_DOMAIN="https://${DOMAIN_PREFIX}.auth.${REGION}.amazoncognito.com"
echo "   Dominio: $COGNITO_DOMAIN"
echo "   >> IMPORTANTE: la App Registration de Entra debe permitir el redirect:"
echo "      ${COGNITO_DOMAIN}/oauth2/idpresponse"

echo ">> 3. Registrando a Entra ID como proveedor OIDC federado..."
aws cognito-idp create-identity-provider \
  --region "$REGION" --user-pool-id "$POOL_ID" \
  --provider-name "$PROVIDER_NAME" \
  --provider-type OIDC \
  --provider-details "oidc_issuer=https://login.microsoftonline.com/${TENANT_ID}/v2.0,client_id=${ENTRA_CLIENT_ID},client_secret=${ENTRA_CLIENT_SECRET},attributes_request_method=GET,authorize_scopes=openid email profile" \
  --attribute-mapping email=email,username=sub

echo ">> 4. Creando grupo 'Admin' (se refleja en el claim cognito:groups)..."
aws cognito-idp create-group --region "$REGION" --user-pool-id "$POOL_ID" --group-name Admin || true
aws cognito-idp create-group --region "$REGION" --user-pool-id "$POOL_ID" --group-name Operador || true

echo ">> 5. Creando App Client publico (SPA, Authorization Code + PKCE, sin secret)..."
CLIENT_ID=$(aws cognito-idp create-user-pool-client \
  --region "$REGION" --user-pool-id "$POOL_ID" \
  --client-name campuslab-spa \
  --no-generate-secret \
  --allowed-o-auth-flows-user-pool-client \
  --allowed-o-auth-flows code \
  --allowed-o-auth-scopes openid email profile \
  --supported-identity-providers "$PROVIDER_NAME" \
  --callback-urls "$CALLBACK_URL" \
  --logout-urls "$LOGOUT_URL" \
  --query 'UserPoolClient.ClientId' --output text)
echo "   App Client: $CLIENT_ID"

ISSUER_URI="https://cognito-idp.${REGION}.amazonaws.com/${POOL_ID}"

cat <<RESUMEN

=================== RESUMEN (usar en .env) ===================
COGNITO_ISSUER_URI = ${ISSUER_URI}
COGNITO_CLIENT_ID  = ${CLIENT_ID}
COGNITO_DOMAIN     = ${COGNITO_DOMAIN}
IDP (identity_provider) = ${PROVIDER_NAME}
Callback           = ${CALLBACK_URL}
=============================================================
Redirect a registrar en Entra: ${COGNITO_DOMAIN}/oauth2/idpresponse
RESUMEN
