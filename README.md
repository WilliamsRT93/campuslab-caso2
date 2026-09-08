# CampusLab — Caso 2 · Desarrollo Cloud Native I (DSY1107)

Plataforma de **reserva de laboratorios y equipos académicos**. Implementa el núcleo del
Caso 2 con **frontend React**, **microservicios Spring Boot**, un **BFF** con Spring
Security detrás de **AWS API Gateway**, e identidad con **Amazon Cognito federado a
Microsoft Entra ID** (login con el autenticador de Microsoft).

## Arquitectura (resumen)

```
Navegador (React)
   │  1) Login Microsoft (MSAL / Entra ID)
   │  2) Authorization Code + PKCE contra Cognito (federado a Entra)
   │  3) Cognito emite un access token (JWT)
   ▼
AWS API Gateway (HTTP API)  ──JWT Authorizer (issuer + client_id)──►  ms-campuslab-bff
   401 si el token es inválido                                          (Spring Security)
                                                                          │ valida JWT + rol + CORS
                                                                          ├─► ms-campuslab-catalog (productos)
                                                                          └─► ms-campuslab-bookings (reservas)
```

Diagrama completo y flujo detallado en [`docs/arquitectura.md`](docs/arquitectura.md).

## Estructura del repositorio

```
Caso2-CampusLab/
├── frontend-campuslab/      Frontend React + Vite + TypeScript (MSAL + Cognito PKCE)
├── ms-campuslab-bff/        BFF Spring Boot + Spring Security (valida JWT, roles, CORS)
├── ms-campuslab-catalog/    Microservicio de catálogo (CRUD productos, JPA/H2)
├── ms-campuslab-bookings/   Microservicio de reservas (CRUD + validación nombre/rut/fecha)
├── infra/
│   ├── apps/                docker-compose para EC2 + script de aprovisionamiento
│   ├── cognito/             scripts Cognito + federación Entra ID
│   └── apigateway/          script API Gateway (HTTP API + JWT Authorizer, sin Lambda)
├── postman/                 colección de pruebas (200 / 400 / 401 / 403)
└── docs/                    arquitectura, guía de despliegue y de demostración
```

## Qué incluye

- **Federación de identidad**: Cognito como IDaaS/emisor del JWT, **federado a Entra ID**.
- **Frontend React** con login de Microsoft (MSAL) y flujo **OIDC Authorization Code + PKCE**.
- **Backend Spring Boot + Spring Security** que valida el JWT (issuer, `token_use`, `client_id`).
- **AWS API Gateway** con **JWT Authorizer** (sin Lambda) → **BFF** → microservicios.
- **CORS** configurado en el BFF (y en el API Gateway) solo para el origen del frontend.
- **CRUD** completo con los cuatro verbos: `GET`, `POST`, `PUT`, `DELETE`.
- **Validación de esquema**: `nombre` (string), `rut` (string), `fecha` (datetime).
- **EC2 + Docker Compose** para el despliegue.
- **Rol Admin** (más Operador y usuario autenticado) derivado de `cognito:groups`.
- **Productos de prueba** y **reservas de prueba** precargados.
- **Diagrama de arquitectura** y **colección Postman** para la demostración.

## Fuera de alcance (por decisión del encargo)

Kafka / streaming, KPIs / reportería, auditoría por streaming y **AWS Lambda**.

## Puesta en marcha rápida (local)

Requisitos: Java 17, Maven 3.9+, Node 20+, Docker.

```bash
# Microservicios (perfil local, sin validar JWT)
cd ms-campuslab-catalog  && mvn spring-boot:run          # :8081
cd ms-campuslab-bookings && mvn spring-boot:run          # :8082
cd ms-campuslab-bff      && mvn spring-boot:run -Dspring-boot.run.profiles=local   # :8080

# Frontend
cd frontend-campuslab && cp .env.example .env && npm install && npm run dev   # :5173
```

Despliegue completo en la nube: [`docs/guia-despliegue.md`](docs/guia-despliegue.md).
Demostración con Postman: [`docs/guia-demostracion.md`](docs/guia-demostracion.md).

## Endpoints principales (vía API Gateway → BFF)

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/me` | autenticado | Identidad y roles del token |
| GET | `/api/catalog/resources` | autenticado | Lista productos |
| POST | `/api/catalog/resources` | Admin | Crea producto |
| PUT | `/api/catalog/resources/{id}` | Admin | Actualiza producto |
| DELETE | `/api/catalog/resources/{id}` | Admin | Elimina producto |
| GET | `/api/bookings` | autenticado | Lista reservas |
| POST | `/api/bookings` | autenticado | Crea reserva (valida esquema) |
| PUT | `/api/bookings/{id}/status` | Admin / Operador | Cambia estado |
| DELETE | `/api/bookings/{id}` | autenticado | Elimina reserva |

## Códigos de respuesta

- **200 / 201** solicitud válida.
- **400** validación de esquema fallida (nombre/rut/fecha).
- **401** token ausente o inválido (API Gateway y BFF).
- **403** token válido pero el rol no puede usar el endpoint (BFF).
