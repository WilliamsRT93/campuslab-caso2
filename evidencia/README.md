# Evidencia real

Evidencia obtenida contra los recursos efectivamente desplegados en AWS y Azure.

## capturas/
- `01-frontend-login.png` — Frontend React: inicio de sesión con Microsoft.
- `02-cognito-hosted-ui.png` — Hosted UI de Amazon Cognito.
- `03-microsoft-login.png` — Federación real: Cognito redirige al login de Microsoft
  (`login.microsoftonline.com`, tenant WRIVAST).

## crud-evidencia.txt
Batería CRUD ejecutada contra el **API Gateway real**
(`https://3wwncomjhk.execute-api.us-east-1.amazonaws.com`) con tokens reales de Cognito.
Demuestra los códigos **401, 200, 201, 400, 403, 204** de extremo a extremo.

## recursos-cli/
Salidas de la AWS CLI que describen los recursos creados: User Pool y proveedor de
identidad de Cognito, grupos, JWT authorizer del API Gateway y la instancia EC2.

> Las capturas de las consolas web de AWS/Azure se agregan siguiendo
> `docs/checklist-capturas-consola.md`.
