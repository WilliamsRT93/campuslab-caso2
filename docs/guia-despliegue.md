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

## 6. Base de datos RDS PostgreSQL (EP3)

1. Crear una instancia RDS PostgreSQL (`campuslab-postgres`, clase `db.t3.micro`) con acceso público solo para la demostración.
2. En su Security Group abrir el puerto **5432**.
3. Crear una base por microservicio: `CREATE DATABASE campuslab_bookings;` y `CREATE DATABASE campuslab_catalog;`.
4. Entregar a cada microservicio `DB_URL` (`jdbc:postgresql://<endpoint>:5432/<base>`), `DB_DRIVER=org.postgresql.Driver`, `DB_USER` y `DB_PASSWORD`
   (ver `infra/apps/.env.example`). Hibernate crea las tablas al arrancar; sin esas variables se usa H2 en memoria.
5. Visualizar las bases con DBeaver (conexión PostgreSQL al endpoint de RDS).

## 7. RabbitMQ en una EC2 exclusiva (EP3)

1. Lanzar una EC2 Amazon Linux 2023 (`campuslab-rabbitmq`) con su propio par de claves.
2. Security Group: **22** (SSH), **5672-5673** (AMQP), **15672-15673** (panel nativo), **8083** (API administrativa y Swagger);
   **4369** y **25672** solo desde el mismo Security Group (comunicación entre los nodos del cluster).
3. Asociar una **IP elástica** (EC2 → Direcciones IP elásticas → Asignar → Asociar): se mantiene aunque el laboratorio se apague.
4. Instalar Docker con el plugin Compose, copiar `infra/rabbitmq-cluster/` y ejecutar `docker compose up -d`.
   Los dos nodos (`rabbitmq-1` y `rabbitmq-2`) comparten cookie y se descubren con `rabbitmq.conf`; el servicio usa `restart: unless-stopped`
   para subir solo al reiniciar el laboratorio.
5. Verificar el cluster: `docker exec rabbitmq-1 rabbitmqctl cluster_status` debe listar ambos nodos en *Running Nodes*.
6. Microservicio administrador: `mvn clean package` en `ms-campuslab-rabbitmq-admin`, copiar el JAR a `/home/ec2-user/rabbitmq-admin.jar`
   e instalar `infra/rabbitmq-cluster/rabbitmq-admin.service` con systemd. Al arrancar **declara los exchanges, las colas, las DLQ y los bindings**.
7. Apuntar los microservicios: `RABBITMQ_HOST=<IP elástica>`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASS`.
8. Verificar: panel `http://<IP>:15672`, Swagger `http://<IP>:8083/swagger-ui.html` y `ms-campuslab-rabbitmq-admin/monitor-colas.sh`.

Las 6 colas son *classic* y viven en el nodo `rabbitmq-1`; el nodo 2 replica solo los metadatos (ver el README, sección Arquitectura).

## 8. Rutas del API Gateway con CloudFormation (EP3)

Reemplaza los comandos manuales de la sección 4. La plantilla `infra/cloudformation/api-gateway-rutas.yaml` define las **14 rutas explícitas** (12 del negocio y 2 de prueba de mensajería hacia el administrador de RabbitMQ, parámetro `RabbitAdminBaseUrl`)
(sin `{proxy+}`), el autorizador JWT de Cognito, una integración por recurso y el CORS.

```bash
aws cloudformation deploy --stack-name campuslab-api-gateway \
  --template-file infra/cloudformation/api-gateway-rutas.yaml \
  --parameter-overrides BackendBaseUrl=http://<IP-ELASTICA-EC2>:8080 FrontendOriginNube=https://<IP-ELASTICA-EC2>
aws apigatewayv2 get-routes --api-id <ApiId> --query "Items[].RouteKey" --output text   # debe listar las 14 rutas
```

Si cambia la IP del BFF solo se actualiza `BackendBaseUrl` y se vuelve a ejecutar `deploy`.

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
