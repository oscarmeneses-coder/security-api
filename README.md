# Security API — Spring Boot 4.1 + JWT + PostgreSQL

API base: `http://localhost:8081`

## Stack tecnológico

- Spring Boot 4.1.2
- Spring Security 7.x / OAuth2 Resource Server
- JWT HS256 access tokens
- Opaque rotating refresh tokens
- PostgreSQL + JPA/Hibernate
- Flyway migrations
- BCrypt password hashing
- Stateless HTTP security
- PostgreSQL-backed login sessions
- Role-based authorization (`USER`, `ADMIN`)

El soporte para Resource Server de Spring Security valida tokens JWT de tipo «Bearer» mediante un `JwtDecoder`; este proyecto emplea el mismo mecanismo, utilizando una clave HMAC compartida, para ofrecer un servicio de autenticación autónomo.

## Iniciar PostgreSQL

```bash
docker compose up postgres -d
```

Crear un secreto fuerte:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
```

Correr:

```bash
./mvnw spring-boot:run
```

o, si Maven está instalado:

```bash
mvn spring-boot:run
```

## Endpoints

### Registro

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "oscar@example.com",
  "password": "Password123!"
}
```

### Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "oscar@example.com",
  "password": "Password123!"
}
```

Respuesta:

```json
{
  "tokenType": "Bearer",
  "accessToken": "...",
  "expiresIn": 900,
  "refreshToken": "..."
}
```

### Refresh

Los tokens de actualización se almacenan en PostgreSQL únicamente como hashes SHA-256 y se rotan en cada actualización exitosa.

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "..."
}
```

### Logout

```http
POST /api/auth/logout
Content-Type: application/json

{
  "refreshToken": "..."
}
```

### Usuario actual

```http
GET /api/auth/me
Authorization: Bearer <accessToken>
```

### Endpoint protegido

```http
GET /api/private/ping
Authorization: Bearer <accessToken>
```

###  Endpoint de Admin

```http
GET /api/admin/ping
Authorization: Bearer <admin-accessToken>
```

## Modelo de sesión de base de datos

`user_sessions` records:

- session UUID
- user
- SHA-256 hash of refresh token
- created / expiry / last-used timestamps
- revocation timestamp
- client IP
- user-agent

El token de actualización (refresh token) en su forma original nunca se almacena de forma persistente.

## Recomendaciones en producción

1. Configura `JWT_SECRET` a través de un gestor de secretos; nunca lo incluyas en el control de versiones.
2. Prioriza HTTPS en todas partes.
3. Coloca la API detrás de un proxy inverso y configura las cabeceras de reenvío de confianza.
4. Considera el uso de firmas JWT asimétricas (RSA/EC) si varios servicios independientes necesitan verificar los tokens.
5. Añade limitación de tasa y bloqueo de cuenta a `/login`, `/register` y `/refresh`.
6. Almacena los tokens de actualización en cookies seguras de tipo «HttpOnly» cuando la arquitectura del cliente lo permita.
7. Añade registro de auditoría y un endpoint de gestión de sesiones por si los usuarios necesitan revocar dispositivos concretos.
8. Configura CORS a partir de variables de entorno en lugar de utilizar orígenes definidos de forma estática en el código.

## Decisión de diseño importante

El token de acceso es un JWT de corta duración (15 minutos por defecto). El token de actualización es un valor aleatorio opaco con una vigencia de 7 días, que se almacena de forma persistente como un hash. La rotación de tokens de actualización implica que, tras una actualización exitosa, el token de sesión anterior queda revocado y se genera uno nuevo.

En una arquitectura distribuida, este patrón permite que las instancias de la API permanezcan sin estado (stateless) para la validación de tokens de acceso, mientras que PostgreSQL actúa como la fuente de verdad para las sesiones de tokens de actualización (refresh tokens).

# Implementación de la aplicación

Se implementa una aplicación base completa con **Spring Boot 4.1.2**, Spring Security, JWT, refresh tokens y PostgreSQL.

La arquitectura incluye:

- **Registro de usuarios**
- **Login**
- **JWT Access Token** de corta duración — 15 min por defecto
- **Refresh Token** de 7 días
- Rotación de refresh tokens
- Revocación de sesiones
- Registro de sesiones en **PostgreSQL**
- Los refresh tokens se almacenan únicamente como **SHA-256**, nunca en texto plano
- BCrypt para contraseñas
- Roles `USER` y `ADMIN`
- Protección de endpoints mediante Spring Security
- Flyway para migraciones
- Docker Compose para PostgreSQL
- Endpoint `/api/auth/me`
- Logout
- Limpieza automática de sesiones expiradas
- CORS configurable
- Actuator health check

La documentación oficial confirma que Spring Boot 4.1.x está actualmente en línea estable y que Spring Security proporciona soporte de Resource Server para validar JWT Bearer tokens. [Home](https://docs.spring.io/spring-boot/reference/?utm_source=chatgpt.com)

### Descargar proyecto

security-api-springboot-4.1.git

### Estructura principal

```text
security-api/
├── src/main/java/org/unsch/securityapi/
│   ├── config/
│   │   ├── AuthenticationConfig.java
│   │   ├── GlobalExceptionHandler.java
│   │   └── SecurityConfig.java
│   ├── controller/
│   │   ├── ApiController.java
│   │   └── AuthController.java
│   ├── dto/
│   ├── entity/
│   │   ├── AppUser.java
│   │   ├── Role.java
│   │   └── UserSession.java
│   ├── repository/
│   ├── security/
│   │   ├── JwtProperties.java
│   │   └── JwtService.java
│   └── service/
│       ├── AuthService.java
│       └── SessionCleanupJob.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__init.sql
├── docker-compose.yml
├── Dockerfile
├── .env.example
├── pom.xml
└── README.md
```

### Flujo de autenticación

```text
                  ┌───────────────┐
                  │    Cliente    │
                  └───────┬───────┘
                          │
                    POST /login
                          │
                          ▼
                 ┌─────────────────┐
                 │ Spring Security │
                 └────────┬────────┘
                          │
                    BCrypt password
                          │
                          ▼
                    ┌──────────┐
                    │  User DB │
                    └────┬─────┘
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
       Access JWT             Refresh Token
        15 minutos                7 días
              │                     │
              │                     ▼
              │              PostgreSQL Session
              │                SHA-256(token)
              │
              ▼
       API protegida
       Authorization:
       Bearer <JWT>
```

Para arrancarlo:

```bash
docker compose up postgres -d

export JWT_SECRET="$(openssl rand -base64 48)"

mvn spring-boot:run
```

Luego:

```http
POST http://localhost:8081/api/auth/register
Content-Type: application/json

{
  "email": "usuario@example.com",
  "password": "Password123!"
}
```

y:

```http
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{
  "email": "usuario@example.com",
  "password": "Password123!"
}
```

La respuesta contiene `accessToken` y `refreshToken`.

**Importante:** para producción recomiendo evolucionar esta base a **JWT firmado con RSA/ECDSA**, especialmente si vamos a tener varios microservicios. También conviene añadir rate limiting, recuperación de contraseña, verificación de email y administración de sesiones/dispositivos.

Spring Security soporta JWT mediante `JwtDecoder` y Resource Server precisamente para este tipo de protección de APIs. 

[Referencia](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/index.html)
