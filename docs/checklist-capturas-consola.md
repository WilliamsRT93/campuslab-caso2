# Checklist de capturas de consola (para la presentación)

Estas capturas se toman desde las consolas web de AWS y Azure (requieren tu sesión iniciada).
Las capturas de la app y de la federación a Microsoft ya están en `evidencia/capturas/`.

## AWS — Amazon Cognito
- [ ] User Pool `campuslab-userpool` (`us-east-1_dBOQeniQq`) — vista general.
- [ ] **Sign-in experience / Identity providers**: proveedor `EntraID` (OIDC).
- [ ] **Groups**: `Admin` y `Operador`.
- [ ] **App integration → App clients**: `campuslab-spa` (Authorization Code + PKCE, sin secreto).
- [ ] **Users**: `demo.admin` (en grupo Admin) y `demo.user`.

## AWS — API Gateway
- [ ] API `campuslab-http-api` (`3wwncomjhk`) — vista general.
- [ ] **Routes**: `ANY /{proxy+}`.
- [ ] **Authorization**: JWT authorizer `cognito-jwt` (Issuer + Audience).
- [ ] **CORS**: origen `http://localhost:5173`, métodos GET/POST/PUT/DELETE/OPTIONS.

## AWS — EC2
- [ ] Instancia `campuslab-ec2` (`i-0448753bab6e8173b`) en estado **running**, IP `3.237.97.7`.
- [ ] **Security Group** `campuslab-sg`: puertos 22, 8080, 8090.
- [ ] (Opcional) SSH a la instancia: `sudo docker compose ps` mostrando los contenedores.

## Azure — Microsoft Entra ID
- [ ] App registration `CampusLab-Cognito` — Overview (Application/Client ID, Tenant).
- [ ] **Authentication**: redirect Web `…/oauth2/idpresponse` y SPA `http://localhost:5173`.
- [ ] **API permissions**: Microsoft Graph (openid, email, profile, User.Read).
- [ ] **Users** del tenant (usuarios de prueba).

## Postman (evidencia de rutas)
- [ ] Ejecutar la colección `postman/CampusLab.postman_collection.json` (Runner) y capturar los
      resultados: **401** (sin token), **200/201** (con token Admin), **403** (usuario no Admin),
      **400** (validación de esquema).

> Sugerencia: guardar cada captura en `evidencia/capturas/` con nombre descriptivo
> (ej. `cognito-idp-entra.png`, `apigw-authorizer.png`, `ec2-running.png`).
