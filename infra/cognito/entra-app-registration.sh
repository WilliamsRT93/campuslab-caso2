#!/usr/bin/env bash
#
# Crea en Microsoft Entra ID la App Registration que Amazon Cognito usara para federar.
# Requiere Azure CLI logueada (az login) con permiso para registrar aplicaciones en el tenant.
#
# Uso:
#   export DOMAIN_PREFIX=campuslab-duoc
#   export REGION=us-east-1
#   ./entra-app-registration.sh
#
set -euo pipefail
: "${DOMAIN_PREFIX:?Define DOMAIN_PREFIX}"
: "${REGION:=us-east-1}"

REDIRECT="https://${DOMAIN_PREFIX}.auth.${REGION}.amazoncognito.com/oauth2/idpresponse"

echo ">> Creando App Registration 'CampusLab-Cognito' con redirect: $REDIRECT"
APP_ID=$(az ad app create \
  --display-name "CampusLab-Cognito" \
  --web-redirect-uris "$REDIRECT" \
  --sign-in-audience AzureADMyOrg \
  --query appId --output tsv)

echo ">> Generando client secret..."
SECRET=$(az ad app credential reset --id "$APP_ID" --append --query password --output tsv)
TENANT_ID=$(az account show --query tenantId --output tsv)

cat <<RESUMEN

=================== ENTRA (usar en setup-cognito-entra.sh) ===================
TENANT_ID          = ${TENANT_ID}
ENTRA_CLIENT_ID    = ${APP_ID}
ENTRA_CLIENT_SECRET= ${SECRET}
Redirect URI       = ${REDIRECT}
=============================================================================
Nota: agrega permisos delegados Microsoft Graph (openid, email, profile, User.Read)
      y otorga consentimiento de administrador si el tenant lo exige.
RESUMEN
