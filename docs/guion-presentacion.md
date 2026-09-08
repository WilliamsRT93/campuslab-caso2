# Guion de presentación — EP2 · CampusLab (Caso 2)

Duración objetivo: **5–10 minutos**. Hilo conductor: *identidad → puerta de entrada → consumo seguro → despliegue*.
Recursos reales usados en la demo:

- Cognito User Pool: `us-east-1_dBOQeniQq` · dominio `campuslab-wrivast`
- App Client (PKCE): `6r3af8ucc96okla0ujph9pru4a`
- API Gateway: `https://3wwncomjhk.execute-api.us-east-1.amazonaws.com`
- EC2 (Docker): `3.237.97.7` · `i-0448753bab6e8173b`
- Entra tenant: `d84b2661-…` (WRIVAST)

---

## 0:00 – 0:45 · Apertura y arquitectura
- "CampusLab es una plataforma de reserva de laboratorios y equipos. Hoy muestro la arquitectura base segura, en la nube."
- Mostrar el **diagrama de arquitectura** (`docs/arquitectura.md`).
- Frase clave: **"El flujo seguro es siempre JWT → API Gateway → BFF → microservicio."**

## 0:45 – 2:00 · Identidad: tenant IDaaS + federación
- **Consola Azure (Entra ID):** mostrar la App Registration `CampusLab-Cognito` (clientId, redirect `…/oauth2/idpresponse`).
- **Consola AWS (Cognito):** User Pool `campuslab-userpool` → **Identity providers**: `EntraID` (OIDC) → grupos `Admin` / `Operador` → App Client con Authorization Code + PKCE.
- Mensaje: "Cognito es nuestro IDaaS y está **federado a Microsoft**; delega el login en Entra."

## 2:00 – 3:30 · Login OIDC (Authorization Code + PKCE)
- En el **frontend** (`http://localhost:5173`): botón **"Iniciar sesión con Microsoft"**.
- Se abre la **Hosted UI de Cognito** → redirige al **login de Microsoft** (federación).
- Al volver, la app tiene un **access token JWT** de Cognito. Mostrar en el token: `token_use=access`, `client_id`, `cognito:groups`.
- Mensaje: "PKCE: el cliente genera `code_verifier`/`code_challenge`; nunca hay secreto en el navegador."

## 3:30 – 5:30 · API Gateway valida el JWT (200/401/403)
- **Consola AWS (API Gateway):** mostrar la ruta `ANY /{proxy+}`, el **JWT Authorizer** (Issuer del pool + Audience = client_id) y **CORS**.
- **Postman** contra el Invoke URL:
  - `GET /api/catalog/resources` **sin** token → **401** (lo rechaza el API Gateway).
  - Con token válido → **200** con el JSON del catálogo.
  - `POST /api/catalog/resources` con usuario **no Admin** → **403** (lo rechaza el BFF por rol).
- Mensaje: "El API Gateway valida firma/issuer/audience; el BFF revalida y además controla el rol."

## 5:30 – 7:30 · CRUD end-to-end y validación de esquema
- En el frontend (rol **Admin**): crear/editar/eliminar un **producto**; crear una **reserva**.
- Mostrar la **validación de esquema**: enviar RUT inválido → **400** con el detalle por campo (`nombre`, `rut`).
- Mostrar el cambio de estado de la reserva (regla: no pasar a `EN_USO` sin `APROBADA`).
- Mensaje: "Los datos viajan reales por API Gateway → BFF → microservicios `catalog`/`bookings`."

## 7:30 – 9:00 · Despliegue y cierre
- **Consola AWS (EC2):** instancia `campuslab-ec2` **running**; `docker compose` con `catalog`, `bookings`, `bff` (y `frontend`).
- Recordar el **alcance**: sin Kafka, sin KPIs/analytics, sin auditoría por streaming y **sin Lambda** (integración HTTP directa).
- Cierre: "Sistema funcional y seguro, con identidad federada a Microsoft, puerta única en API Gateway y microservicios desplegados en EC2."

---

### Preguntas típicas del docente (preparadas)
- **¿Por qué un BFF si el API Gateway ya valida el JWT?** Doble control: el API Gateway filtra en el borde; el BFF aplica **autorización por rol** por endpoint y desacopla el frontend de los microservicios.
- **¿Dónde está PKCE?** En el frontend (`pkce.ts`): `code_verifier` + `code_challenge` S256; el App Client es público (sin secreto).
- **¿Cómo se obtiene el rol Admin?** Del claim `cognito:groups` (grupo `Admin` del User Pool), mapeado a `ROLE_ADMIN` en Spring Security.
- **¿Qué pasa si el token expira o es de otro cliente?** El BFF valida `token_use=access` y `client_id`; responde 401 con el motivo.
