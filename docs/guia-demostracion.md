# Guía de demostración (Postman + frontend)

Secuencia sugerida para la presentación (EP2). Mostrar **solo** lo que se indica.

## A. Identidad (Entra + Cognito)

1. En Cognito: mostrar el **User Pool**, el proveedor federado **EntraID** y el **App Client** con PKCE.
2. En Entra: mostrar la **App Registration** y los **usuarios** de prueba.
3. En el frontend (`/`): clic en **Iniciar sesión con Microsoft** → se abre el login de Microsoft.
4. Clic en **Autorizar y entrar** → Cognito Hosted UI (federado) → vuelve con el token.

## B. API Gateway valida el JWT

Con la colección de Postman (variable `base_url` = INVOKE URL del API Gateway):

| Caso | Request | Resultado esperado |
|---|---|---|
| Sin token | `401 - GET sin token` | **401** (lo rechaza el API Gateway) |
| Token válido | `GET listar productos` | **200** con JSON |
| Rol sin permiso | `403 - POST producto con usuario NO Admin` | **403** (lo rechaza el BFF) |
| Validación de esquema | `400 - POST reserva con RUT inválido` | **400** con detalle por campo |

> Para obtener el `token` (Cognito access token): iniciar sesión en el frontend y copiarlo
> desde las herramientas del navegador (sessionStorage → `cognito_access_token`), o usar el
> Hosted UI de Cognito. El `user_token` es el de un usuario **sin** grupo Admin.

## C. CRUD end-to-end (frontend → API Gateway → BFF → dominio)

1. Pestaña **Catálogo**: crear, editar y eliminar un producto (como Admin).
2. Pestaña **Reservas**: crear una reserva (nombre, RUT, fecha, producto) y cambiar su estado.
3. Mostrar en el BFF/`GET /api/me` los **roles** leídos del token.

## D. Evidencia de cada ruta

Ejecutar la colección completa (Runner de Postman) y mostrar los resultados de los tests
(200 / 400 / 401 / 403) como evidencia de que cada ruta llama al backend correcto y
devuelve el JSON esperado.
