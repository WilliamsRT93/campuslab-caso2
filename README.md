# CampusLab — Caso 2 · Desarrollo Cloud Native I (DSY1107) · EP3

Plataforma de **reserva de laboratorios y equipos académicos**, construida con **microservicios Spring Boot**,
un **BFF** con Spring Security detrás de **AWS API Gateway**, un **frontend React** e identidad con
**Amazon Cognito** (federado a Microsoft Entra ID). En la **EP3** se agrega **mensajería asíncrona con
RabbitMQ** (cluster de 2 nodos, 3 colas con sus colas muertas), base de datos **RDS PostgreSQL** y las rutas del
API Gateway gestionadas con **CloudFormation**.

> **Para el docente — inicio rápido:** abra el [entorno ya desplegado en AWS](#1-probar-el-entorno-desplegado-en-aws)
> (no requiere instalar nada) o [levante todo en su equipo](#2-levantar-todo-en-local). La
> [validación por consola](#4-validar-que-las-cargas-llegan-a-las-colas) muestra en vivo las cargas a las colas.

---

## Arquitectura

```
Navegador (React)                                  Usuario y contraseña ─┐
   │  Login Microsoft (Entra ID) ──► Cognito (JWT) ◄─────────────────────┘
   ▼
AWS API Gateway (HTTP API, 14 rutas explícitas, JWT Authorizer)     <- definido en CloudFormation
   ▼
ms-campuslab-bff (valida JWT + rol + CORS) ──► ms-campuslab-catalog  (productos)  ──► RDS campuslab_catalog
                                           └─► ms-campuslab-bookings (reservas)   ──► RDS campuslab_bookings
                                                      │ publican eventos
                                                      ▼
              ┌──────────── Cluster RabbitMQ (2 nodos, EC2 exclusiva, IP elástica) ────────────┐
              │ campuslab.exchange (direct)                                                    │
              │   reserva.notificacion ─► cola-notificaciones ─► NotificacionListener (bookings)│
              │   reserva.documento    ─► cola-documentos     ─► DocumentoListener    (bookings)│
              │   reserva.auditoria    ─► cola-auditoria      ─► AuditoriaListener    (catalog) │
              │ campuslab.dlx (dead-letter) ─► cola-*-dlq  (una cola muerta por cada cola)     │
              └─────────────────────────────────────────────────────────────────────────────────┘
ms-campuslab-rabbitmq-admin: dueño de la topología (declara colas, exchanges y bindings) y API REST + Swagger
```

### Topología de RabbitMQ

| Exchange | Tipo | Routing key | Cola destino | Cola muerta (DLQ) |
|---|---|---|---|---|
| `campuslab.exchange` | direct | `reserva.notificacion` | `cola-notificaciones` | `cola-notificaciones-dlq` |
| `campuslab.exchange` | direct | `reserva.documento` | `cola-documentos` | `cola-documentos-dlq` |
| `campuslab.exchange` | direct | `reserva.auditoria` | `cola-auditoria` | `cola-auditoria-dlq` |
| `campuslab.dlx` | direct | las mismas 3 routing keys | las 3 colas `*-dlq` | — |

- **Productores:** crear una reserva publica 2 eventos (notificación y documento); crear, editar o eliminar un
  producto del catálogo publica 1 evento de auditoría.
- **Consumidores:** `@RabbitListener` con **ACK/NACK manual** (`prefetch: 1`). Distinguen dos tipos de error (`ManejadorErroresListener`):
  **no recuperable** (mensaje inválido, p. ej. el texto `FORZAR_FALLO` o un payload vacío) → NACK sin reencolar y RabbitMQ lo envía a su DLQ;
  **recuperable** (transitorio, p. ej. `FALLO_TRANSITORIO`, `TransientDataAccessException` o un timeout) → se reintenta hasta `max-reintentos` (3) republicando
  el mensaje con la cabecera `x-intentos`, y si se agotan va a la DLQ. Cada decisión queda registrada en los logs del microservicio.
- **Publisher confirms:** los productores usan `publisher-confirm-type: correlated` y `publisher-returns: true`; cada mensaje lleva un
  `CorrelationData` y el log registra `📨 Broker confirmó la recepción (publisher confirm ACK)` (o el NACK / la devolución si el broker no lo acepta).
- **TTL:** las 3 colas de negocio tienen `x-message-ttl` (60 s, parámetro `message-ttl-ms` / `MESSAGE_TTL_MS` del administrador). Un mensaje que espera más
  sin consumidor expira y RabbitMQ lo envía a su DLQ con `reason: expired`. Cambiar el TTL exige borrar y recrear las colas (RabbitMQ no permite redeclararlas con otros argumentos).
- **Centralización:** los nombres viven en `application.yml` y se leen con `@ConfigurationProperties`; solo
  `ms-campuslab-rabbitmq-admin` declara la topología, los demás microservicios únicamente la referencian.

---

## 1. Probar el entorno desplegado en AWS

| Qué | Dónde | Acceso |
|---|---|---|
| **Frontend en AWS** | https://100.60.125.97 (certificado autofirmado: aceptar la advertencia) | botón **"Entrar con usuario y contraseña"** |
| **Swagger** (administrador de RabbitMQ) | http://54.208.235.239:8083/swagger-ui.html | botón **"Obtener token"** (ya trae el usuario Admin) |
| **Panel nativo de RabbitMQ** | http://54.208.235.239:15672 | `admin` / `admin123` |
| **API Gateway** | https://obxz43kip7.execute-api.us-east-1.amazonaws.com | JWT de Cognito |

**Usuarios de demostración** (existen en Amazon Cognito, sin segundo factor; solo para este entorno académico):

| Usuario | Contraseña | Correo | Rol en la app |
|---|---|---|---|
| `admin` | `Admin123!` | admin@WRIVAST.onmicrosoft.com | Admin: CRUD completo, cambio de estado y pestaña **"Mensajería (demo DLQ)"** |
| `cliente` | `Cliente123!` | cliente@WRIVAST.onmicrosoft.com | Usuario: consulta y crea reservas |

- La pestaña **Mensajería** (solo Admin) tiene un panel **"Probar exchange, bindings y las 3 colas"**: muestra en vivo (cada 2,5 s) los exchanges, los bindings reales y las colas con sus mensajes, consumidores, publicados, procesados (ACK) y TTL. Permite enviar un mensaje por el exchange con una routing key (el broker lo confirma y el binding lo enruta a su cola), enviar a las 3 colas, marcar `FORZAR_FALLO` para que caiga en la cola muerta o probar una routing key sin binding (el broker la devuelve). Debajo, dos botones envían `FORZAR_FALLO` a las colas muertas.
- **Swagger:** el botón *Obtener token* hace login contra Cognito, captura el JWT y lo aplica a todas las consultas
  (*Try it out → Execute*); además hay botones de **consulta rápida** del estado de cada cola con un clic. La API del
  administrador valida el JWT y exige el rol Admin (sin token responde 401; con el usuario `cliente`, 403).
- **API del administrador (códigos de respuesta):** valida el cuerpo con Bean Validation (nombres de 3 a 100 caracteres, sin espacios ni símbolos y sin el prefijo
  reservado `amq.`) y responde `{status, error, message, errores}`: **400** validación o nombre reservado, **404** cola o exchange inexistente (al consultar, borrar, purgar o crear
  un binding), **409** al crear una cola o exchange que ya existe. `POST /api/rabbitmq/queues` acepta `conDeadLetter: true` para crear también su cola muerta `<nombre>-dlq`
  enlazada al DLX. Las pruebas unitarias del administrador (`mvn test`) cubren estas reglas.
- El botón "Iniciar sesión con Microsoft" también existe (federación Entra ID → Cognito), pero el tenant de Microsoft
  tiene *Security defaults* y exige verificación en dos pasos; por eso la demostración usa el acceso directo.

### Rutas del API Gateway (CloudFormation, sin proxy)

14 rutas explícitas (12 del negocio y 2 de prueba de mensajería), todas con el autorizador JWT de Cognito (`infra/cloudformation/api-gateway-rutas.yaml`):

| Ruta | Quién |
|---|---|
| `GET /api/me` | autenticado |
| `GET`, `POST /api/catalog/resources` · `GET`, `PUT`, `DELETE /api/catalog/resources/{id}` | lectura: autenticado · escritura: **Admin** |
| `GET`, `POST /api/bookings` · `GET`, `PUT`, `DELETE /api/bookings/{id}` | autenticado |
| `PUT /api/bookings/{id}/status` | **Admin** u **Operador** |
| `GET /api/rabbitmq/topologia` · `POST /api/rabbitmq/publicar` | **Admin** (van al administrador de RabbitMQ, puerto 8083) |

Códigos: **200/201/204** correcto · **400** esquema inválido (`nombre`, `rut` con formato `12345678-9` y dígito verificador válido (módulo 11), `fecha` ISO) ·
**401** sin token o inválido · **403** rol sin permiso.

---

## 2. Levantar todo en local

**Requisitos:** Java 17, Maven 3.9+, Node 20+, Docker con Compose, y Git Bash (Windows) o una terminal bash.

```bash
git clone https://github.com/WilliamsRT93/campuslab-caso2.git && cd campuslab-caso2

# 1) RabbitMQ: cluster de 2 nodos (panel en http://localhost:15672, admin / admin123)
cd infra/rabbitmq-cluster && docker compose up -d && cd ../..

# 2) Compilar los 4 microservicios con Maven
./scripts/compilar-todo.sh                 # o, de a uno:  cd ms-campuslab-catalog && mvn spring-boot:run

# 3) Levantar los microservicios (una terminal cada uno; por defecto usan H2 en memoria y RabbitMQ en localhost)
#    El administrador va PRIMERO: declara las colas, exchanges y bindings que necesitan los consumidores.
cd ms-campuslab-rabbitmq-admin && SECURITY_ENABLED=false mvn spring-boot:run                # :8083
cd ms-campuslab-catalog        && mvn spring-boot:run                                        # :8081
cd ms-campuslab-bookings       && mvn spring-boot:run                                        # :8082
cd ms-campuslab-bff            && mvn spring-boot:run -Dspring-boot.run.profiles=local       # :8080 (sin validar JWT)

# 4) Frontend (usa los identificadores públicos del entorno demo; el login requiere internet)
cd frontend-campuslab && cp .env.example .env && npm install && npm run dev                 # http://localhost:5173
```

- Swagger local: http://localhost:8083/swagger-ui.html (con `SECURITY_ENABLED=false` no pide token).
- Para usar **PostgreSQL** en vez de H2 se definen `DB_URL`, `DB_DRIVER`, `DB_USER` y `DB_PASSWORD`; para otro broker,
  `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASS` (ver `infra/apps/.env.example`).
- El frontend local contra el entorno demo funciona porque el CORS y los *callback URLs* de Cognito incluyen
  `http://localhost:5173`.

---

## 3. Infraestructura en AWS

| Recurso | Detalle |
|---|---|
| **EC2 `campuslab-ec2`** (IP elástica `100.60.125.97`) | BFF `:8080`, catalog, bookings y frontend (nginx con HTTPS) en Docker |
| **EC2 `campuslab-rabbitmq`** (IP elástica `54.208.235.239`) | Cluster RabbitMQ en Docker (`restart: unless-stopped`) y el administrador (`systemd`, `:8083`) |
| **RDS PostgreSQL** | bases `campuslab_bookings` y `campuslab_catalog` (visualizables con DBeaver) |
| **API Gateway + Cognito** | HTTP API con JWT Authorizer; User Pool `us-east-1_dBOQeniQq` |
| **CloudFormation** | stack `campuslab-api-gateway` (rutas, integraciones, autorizador y CORS) |

Las dos EC2 se conectan entre sí por **SSH** con IP privada (alias `ssh rabbit` desde la EC2 de apps y `ssh apps`
desde la de RabbitMQ).

```bash
# Crear / actualizar las rutas del API Gateway (solo cambia BackendBaseUrl si cambia la IP del BFF)
aws cloudformation deploy --stack-name campuslab-api-gateway \
  --template-file infra/cloudformation/api-gateway-rutas.yaml \
  --parameter-overrides BackendBaseUrl=http://<IP-ELASTICA-EC2>:8080 FrontendOriginNube=https://<IP-ELASTICA-EC2>
```

Otros archivos de despliegue: `infra/apps/compose.yml` (microservicios + frontend), `infra/rabbitmq-cluster/`
(`docker-compose.yml`, `rabbitmq.conf`, `rabbitmq-admin.service`), `infra/cognito/` y `infra/apigateway/`.

---

## 4. Validar que las cargas llegan a las colas

Los scripts usan **solo bash y curl** (incluidos en Git Bash de Windows): sin Python, sin AWS CLI. Se ejecutan desde
la raíz del repositorio con **dos terminales**:

```bash
# Terminal 1 — listener de colas en vivo (cargas que entran, ACK de los workers y mensajes en las DLQ)
./ms-campuslab-rabbitmq-admin/monitor-colas.sh                      # por defecto apunta a AWS
./ms-campuslab-rabbitmq-admin/monitor-colas.sh localhost admin admin123   # contra RabbitMQ local

# Terminal 2 — CRUD completo por consola contra el API Gateway (login Cognito + 22 verificaciones)
./scripts/crud-consola.sh --dlq          # --dlq además fuerza un mensaje a cada cola muerta
```

| Script | Para qué sirve |
|---|---|
| `ms-campuslab-rabbitmq-admin/monitor-colas.sh` | Muestra con colores e iconos cada **📥 carga**, cada **✅ ACK** y cada **💀 mensaje en cola muerta** (con su contenido) |
| `scripts/crud-consola.sh` | Login en Cognito y prueba de todas las rutas: CRUD de catálogo y reservas, 401, 403 y 400 |
| `scripts/listener-ssh.sh colas\|logs\|estado` | Lo mismo **por SSH** en las EC2: monitor dentro del servidor, logs en vivo de los microservicios (productor, consumidor, ACK y DLQ) y foto del estado (requiere las claves `.pem` del equipo) |
| `scripts/compilar-todo.sh` | `mvn clean package` de los 4 microservicios |

Colección Postman en `postman/` y guías en `docs/`.

---

## Estructura del repositorio

```
campuslab-caso2/
├── frontend-campuslab/            React + Vite + TypeScript (login Microsoft o usuario/contraseña, PKCE con Cognito)
├── ms-campuslab-bff/              BFF Spring Boot + Spring Security (valida JWT, roles y CORS)
├── ms-campuslab-catalog/          Catálogo (CRUD, JPA); publica auditoría
├── ms-campuslab-bookings/         Reservas (CRUD, validación); publica notificaciones y documentos
├── ms-campuslab-rabbitmq-admin/   Topología RabbitMQ (REST + Swagger con JWT) y monitor-colas.sh
├── scripts/                       crud-consola.sh, listener-ssh.sh, compilar-todo.sh
├── infra/
│   ├── cloudformation/            Rutas del API Gateway (CloudFormation)
│   ├── rabbitmq-cluster/          Cluster de 2 nodos (docker compose) y servicio systemd del administrador
│   ├── apps/                      docker-compose de los microservicios y el frontend para la EC2
│   ├── cognito/  apigateway/      Scripts de Cognito/Entra y del API Gateway (versión inicial, CLI)
├── postman/                       Colección de pruebas (200 / 400 / 401 / 403)
└── docs/                          Arquitectura, despliegue y demostración
```

## Tecnologías

Java 17 · Spring Boot 3.5 (Web, Data JPA, AMQP, Security OAuth2 Resource Server, Validation, Actuator) · springdoc-openapi
(Swagger) · RabbitMQ 3.12 (cluster) · PostgreSQL (RDS) / H2 · React + Vite + TypeScript · Amazon Cognito · Microsoft Entra
ID · AWS API Gateway · CloudFormation · EC2 · Docker Compose · nginx.

## Notas

- El tráfico API Gateway → BFF viaja por HTTP (el BFF escucha en `:8080` sin TLS); el frontend sí se sirve por HTTPS
  con un certificado autofirmado.
- Las credenciales de la tabla son de **demostración** de este entorno académico (AWS Academy); no se reutilizan en
  otros sistemas. Las claves de AWS, la contraseña de la base de datos y las claves SSH **no** se versionan.
- Las IP elásticas se mantienen mientras el laboratorio de AWS Academy esté activo.
