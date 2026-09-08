# Guía de despliegue (Azure + AWS)

Orden recomendado para dejar el sistema operativo en la nube.

## 1. Microsoft Entra ID (Azure)

1. Definir el prefijo del dominio de Cognito (ej. `campuslab-duoc`) y la región (`us-east-1`).
2. Crear la App Registration que Cognito usará para federar:
   ```bash
   cd infra/cognito
   export DOMAIN_PREFIX=campuslab-duoc REGION=us-east-1
   ./entra-app-registration.sh
   ```
   Guardar `TENANT_ID`, `ENTRA_CLIENT_ID` y `ENTRA_CLIENT_SECRET`.
3. Crear al menos un usuario de prueba en el tenant y asignarlo a la app.

## 2. Amazon Cognito (AWS)

```bash
cd infra/cognito
export REGION=us-east-1 DOMAIN_PREFIX=campuslab-duoc
export TENANT_ID=... ENTRA_CLIENT_ID=... ENTRA_CLIENT_SECRET=...
export CALLBACK_URL=http://<IP_PUBLICA_EC2>:8090/cognito-callback
export LOGOUT_URL=http://<IP_PUBLICA_EC2>:8090
./setup-cognito-entra.sh
```

El script crea el User Pool, el dominio Hosted UI, el proveedor OIDC (Entra), los grupos
`Admin`/`Operador` y el App Client público (PKCE). Devuelve `COGNITO_ISSUER_URI`,
`COGNITO_CLIENT_ID` y `COGNITO_DOMAIN`.

> Verificar en Entra que el redirect `https://<dominio>.auth.<region>.amazoncognito.com/oauth2/idpresponse`
> quedó registrado (el primer script ya lo agrega).

Asignar el usuario de prueba al grupo `Admin` para probar el rol:
```bash
aws cognito-idp admin-add-user-to-group --user-pool-id <POOL> --username <user> --group-name Admin
```

## 3. Instancia EC2 con Docker

1. Lanzar una EC2 (Amazon Linux 2023, t2.micro/t3.micro) con un Security Group que abra:
   `22` (SSH), `8080` (BFF, para API Gateway), `8090` (frontend).
2. Dentro de la instancia:
   ```bash
   curl -O <raw>/infra/apps/deploy-ec2.sh && bash deploy-ec2.sh   # instala docker + compose
   git clone <repo> && cd Caso2-CampusLab/infra/apps
   cp .env.example .env      # completar con Cognito/Entra/API GW
   docker compose up -d --build
   ```

## 4. API Gateway (HTTP API con JWT Authorizer)

```bash
cd infra/apigateway
export REGION=us-east-1
export COGNITO_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXX
export COGNITO_CLIENT_ID=xxxx
export BFF_URL=http://<IP_PUBLICA_EC2>:8080
export FRONTEND_ORIGIN=http://<IP_PUBLICA_EC2>:8090
./setup-apigateway.sh
```

Devuelve el **INVOKE URL**. Ponerlo en `VITE_API_BASE_URL` del `.env` de `infra/apps` y
reconstruir el frontend (`docker compose up -d --build frontend`).

## 5. Verificación

- `http://<IP_PUBLICA_EC2>:8090` → abre el frontend, login con Microsoft.
- Postman → colección `postman/CampusLab.postman_collection.json` con el INVOKE URL.

## Ejecución local (sin nube)

Para desarrollo, los tres microservicios corren con perfil `local` (sin validar JWT):

```bash
# terminal 1
cd ms-campuslab-catalog && mvn spring-boot:run
# terminal 2
cd ms-campuslab-bookings && mvn spring-boot:run
# terminal 3
cd ms-campuslab-bff && mvn spring-boot:run -Dspring-boot.run.profiles=local
# terminal 4
cd frontend-campuslab && npm install && npm run dev
```
