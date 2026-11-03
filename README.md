# E-Commerce Backend

A Java 17 / Spring Boot backend project exploring REST API design, JWT authentication, persistence with PostgreSQL, and domain-oriented application structure.

The repository is a learning and portfolio project. The core API areas are authentication, product catalogue, shopping cart, and order management. Additional domain models and database migrations exist for payments, shipping, reviews, and promotions, but those areas should not be treated as complete third-party integrations without further implementation and verification.

## Current API areas

- **Authentication:** registration, login, refresh, and logout
- **Products:** product listing, search, product creation, publishing, and stock updates
- **Cart:** customer and guest-session cart operations
- **Orders:** order creation, customer order history, cancellation, and administrator status operations
- **Infrastructure:** PostgreSQL, Flyway migrations, Spring Security, JWT, Docker Compose, and OpenAPI documentation

## Technology

- Java 17
- Spring Boot 3.2.x
- Spring Web and Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL and Flyway
- Redis configuration
- JWT
- JUnit 5 and Mockito
- Docker Compose
- SpringDoc OpenAPI

## Requirements

- JDK 17+
- Maven 3.8+
- Docker and Docker Compose

## Local setup

### 1. Clone the repository

```bash
git clone https://github.com/akifkeklik/ecommerce-backend.git
cd ecommerce-backend
```

### 2. Configure environment variables

Copy the template:

```bash
cp .env.example .env
```

Generate a unique Base64-encoded JWT signing key (32 random bytes):

Linux / macOS:

```bash
openssl rand -base64 32
```

PowerShell:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Paste the generated value into `.env` as `JWT_SECRET` for Docker Compose. Do not use a committed or shared signing key.

When running Maven directly, `.env` is not loaded automatically by Spring Boot. Set the variable in the terminal too:

Linux / macOS:

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

PowerShell (paste the generated value):

```powershell
$env:JWT_SECRET = "PASTE_GENERATED_VALUE_HERE"
```

### 3. Start the database and supporting services

```bash
docker compose up -d postgres redis mailhog
```

### 4. Run the API locally

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The API is available at `http://localhost:8080`.

Swagger UI: `http://localhost:8080/swagger-ui.html`

Health endpoint: `http://localhost:8080/actuator/health`

### Run the complete Docker Compose stack

After setting `JWT_SECRET` in `.env`:

```bash
docker compose up -d --build
```

The application container uses the Docker service names for PostgreSQL, Redis, and MailHog.

## Main endpoints

| Area | Endpoints |
| --- | --- |
| Authentication | `/api/v1/auth/register`, `/login`, `/refresh`, `/logout` |
| Products | `/api/v1/products`, `/search`, `/featured` |
| Cart | `/api/v1/cart` and `/api/v1/cart/items` |
| Orders | `/api/v1/orders` |

Protected endpoints require a valid JWT access token.

## Testing

Run the test suite:

```bash
mvn test
```

The test configuration uses an in-memory H2 database and a test-only JWT secret.

## Scope and limitations

- Payment and shipping domain models and migrations are present, but live Stripe, PayPal, or carrier workflows are not currently documented as verified.
- Reviews and promotions have domain/schema foundations; verify each API workflow before relying on it.
- This project has not been independently deployed or production-hardened. Configure secrets, database access, CORS, logging, monitoring, and operational controls before any public deployment.
- Never commit `.env` or real credentials.
