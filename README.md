# Tavoo

Tavoo is a REST backend for restaurant and bar operations: menus, dining areas, tables, orders, kitchen preparation, payments, and archived checks. It is a university software architecture project and does not include a frontend.

The backend uses Java 17, Spring Boot 3.5, Spring Data JPA, PostgreSQL, Flyway, and Spring Security with JWT authentication. Swagger UI provides an interactive way to use the API.

## Requirements

- JDK 17.
- Maven 3.6.3 or newer, available as `mvn`. The repository does not include a Maven wrapper.
- PostgreSQL, either installed locally or running in Docker. The Docker instructions and integration tests use PostgreSQL 16.
- Docker Desktop or Docker Engine if you choose the container setup or run integration tests. On Windows, use Linux containers.

Clone or download the repository, open a terminal in the directory containing [pom.xml](pom.xml), and check your Java and Maven installations:

```shell
java -version
mvn -version
```

Make sure Maven reports the intended JDK; configure `JAVA_HOME` if necessary.

## Local setup

### 1. Configure the environment

Choose the commands for your shell. Replace the two example passwords before running them. For an existing PostgreSQL installation, `DB_PASSWORD` must match the password of your database account.

PowerShell:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/tavoodb'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'change-this-database-password'
$env:TAVOO_ADMIN_USERNAME = 'admin'
$env:TAVOO_ADMIN_PASSWORD = 'change-this-admin-password'

$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$env:TAVOO_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$jwtRng.Dispose()
```

Bash on Linux or macOS (requires OpenSSL for secret generation):

```bash
export DB_URL='jdbc:postgresql://localhost:5432/tavoodb'
export DB_USERNAME='postgres'
export DB_PASSWORD='change-this-database-password'
export TAVOO_ADMIN_USERNAME='admin'
export TAVOO_ADMIN_PASSWORD='change-this-admin-password'
export TAVOO_JWT_SECRET="$(openssl rand -base64 32)"
```

The JWT secret must be Base64 encoding of at least 32 random bytes. Save your generated value privately and reuse it across restarts; generating a different secret invalidates existing tokens.

These variables apply to the current terminal session. Run Maven from that terminal. If you start the application from an IDE, add the same variables to the run configuration for `org.tavoo.TavooApplication` and select JDK 17.

[application.yml](src/main/resources/application.yml) contains fallback credentials and a signing secret; the commands above override them with your own values. Spring Boot does not automatically read a `.env` file, and this repository does not include a `.env.example`.

### 2. Create the database

Choose either an existing PostgreSQL installation or Docker.

#### Existing PostgreSQL installation

Start PostgreSQL and create an empty database. Skip this command if `tavoodb` already exists:

```shell
psql -h localhost -p 5432 -U postgres -d postgres -c "CREATE DATABASE tavoodb;"
```

Enter your PostgreSQL account password when prompted. You can also create `tavoodb` with pgAdmin. If you use a different host, port, database name, or account, update `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` accordingly.

#### Docker

Start Docker, then run the command for your shell from the terminal configured in step 1.

PowerShell:

```powershell
docker run --detach --name tavoo-postgres --publish 127.0.0.1:5432:5432 --env POSTGRES_DB=tavoodb --env POSTGRES_USER=postgres --env "POSTGRES_PASSWORD=$env:DB_PASSWORD" --volume tavoo-postgres-data:/var/lib/postgresql/data postgres:16-alpine
```

Bash:

```bash
docker run --detach --name tavoo-postgres --publish 127.0.0.1:5432:5432 --env POSTGRES_DB=tavoodb --env POSTGRES_USER=postgres --env "POSTGRES_PASSWORD=$DB_PASSWORD" --volume tavoo-postgres-data:/var/lib/postgresql/data postgres:16-alpine
```

Check readiness before starting Tavoo:

```shell
docker exec tavoo-postgres pg_isready -U postgres -d tavoodb
```

Continue when PostgreSQL reports `accepting connections`. The named volume `tavoo-postgres-data` persists your database. On later runs, use `docker start tavoo-postgres`; use `docker stop tavoo-postgres` when finished.

If port 5432 is already occupied, use `--publish 127.0.0.1:5433:5432` when creating the container and change the port in `DB_URL` to `5433`.

### 3. Start the backend

From the project root, in the terminal with your environment variables:

```shell
mvn spring-boot:run
```

The first run downloads dependencies. On startup, Flyway applies the SQL migrations in [src/main/resources/db/migration](src/main/resources/db/migration), Hibernate validates the schema, and the bootstrap process creates an administrator if none exists. You do not need to create tables or run migration files manually.

Wait for `Started TavooApplication`, then open:

| Resource | URL |
| --- | --- |
| Swagger UI | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) |
| OpenAPI JSON | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |
| API base | `http://localhost:8080/api` |

There is no homepage at `/`; use Swagger UI to explore the backend. Stop the application with `Ctrl+C`.

### 4. Sign in

In Swagger UI, expand `POST /api/auth/login`, select **Try it out**, and submit the credentials you configured in step 1:

```json
{
  "username": "admin",
  "password": "change-this-admin-password",
  "rememberMe": false
}
```

Replace the password with your chosen `TAVOO_ADMIN_PASSWORD`, and the username if you changed it. Copy the response's `accessToken`, click **Authorize**, and paste the token without the `Bearer ` prefix. Execute `GET /api/auth/me` to verify your identity and `ADMIN` role.

Other API clients must send:

```http
Authorization: Bearer <accessToken>
```

Tokens last 15 minutes by default, or 30 days when `rememberMe` is `true`. Log in again when a token expires.

Bootstrap credentials create an account only when no `ADMIN` exists. Changing the environment variables after an administrator has been created does not change that account's username or password. Bootstrap passwords must contain at least eight characters.

### 5. Add initial restaurant data

A fresh database contains the bootstrap administrator and restaurant settings with a coperto (cover charge) unit price of `2.50`. Create staff, locations, tables, and menu items through Swagger UI while authorized as the administrator.

The following requests provide a minimal starting point. Replace the staff password and use the actual location ID returned by the location request when creating a table.

| Method and endpoint | Example JSON body |
| --- | --- |
| `POST /api/admin/users` | `{"username":"waiter","password":"change-this-waiter-password","role":"WAITER"}` |
| `POST /api/admin/users` | `{"username":"kitchen","password":"change-this-kitchen-password","role":"KITCHEN"}` |
| `POST /api/locations` | `{"name":"Main Hall","type":"INSIDE"}` |
| `POST /api/tables` | `{"tableNumber":1,"seatCount":4,"locationId":1}` |
| `POST /api/menu` | `{"name":"Pasta","price":12.50,"category":"FOOD","courseType":"PRIMO","available":true}` |
| `PUT /api/admin/settings/coperto-price` | `{"unitPrice":2.50}` |

To try the order workflow, log in as the waiter and replace the token in **Authorize**. Submit `POST /api/orders`, using the table and menu item IDs returned by your earlier requests:

```json
{
  "tableId": 1,
  "copertoCount": 2,
  "items": [
    {
      "menuItemId": 1,
      "quantity": 2,
      "preparationPriority": 1
    }
  ]
}
```

An order belongs to the waiter who creates it. Kitchen users manage preparation through `/api/kitchen/orders`; the waiter delivers items and pays the order through `/api/orders`. Payment accepts `{"paymentMethod":"POS"}` or `{"paymentMethod":"CHECK"}`, archives the order, and frees the table. Use Swagger UI for the full endpoint list and request schemas.

## Configuration reference

Set these environment variables in the terminal or IDE that launches the application.

| Variable | Purpose / default |
| --- | --- |
| `DB_URL` | JDBC connection URL; defaults to `jdbc:postgresql://localhost:5432/tavoodb`. |
| `DB_USERNAME` | Database account; defaults to `postgres`. |
| `DB_PASSWORD` | Database account password; set it to match your PostgreSQL instance. |
| `TAVOO_ADMIN_USERNAME` | Initial administrator username; the setup above uses `admin`. |
| `TAVOO_ADMIN_PASSWORD` | Initial administrator password, at least eight characters. |
| `TAVOO_JWT_SECRET` | Base64 signing key containing at least 32 decoded bytes. |
| `TAVOO_JWT_TTL` | Normal token lifetime as an ISO-8601 duration; defaults to `PT15M`. |
| `TAVOO_JWT_REMEMBER_ME_TTL` | Remembered token lifetime; defaults to `P30D` and must exceed the normal lifetime. |
| `TAVOO_BUSINESS_ZONE` | Time zone used to group archived orders; defaults to `Europe/Rome`. |
| `JPA_DDL_AUTO` | Hibernate schema handling; defaults to `validate`. Keep this value when using Flyway. |
| `SERVER_PORT` | HTTP port; defaults to `8080`. |

## Build and test

Run these commands from the project root:

| Command | Result |
| --- | --- |
| `mvn test` | Runs unit and controller tests without PostgreSQL or Docker. |
| `mvn package` | Runs those tests and builds the executable JAR. |
| `mvn verify` | Builds the JAR, runs those tests, and generates the JaCoCo coverage report. |
| `mvn verify -Pintegration` | Also runs persistence integration tests against an isolated PostgreSQL 16 container; requires Docker. |

Integration tests use Testcontainers to supply their own database connection and do not modify your local `tavoodb`. The coverage report is generated at `target/site/jacoco/index.html`; test reports are in `target/surefire-reports` and, for integration tests, `target/failsafe-reports`.

After packaging, you can run the backend without Maven, using the same environment variables and running PostgreSQL instance as in local setup:

```shell
java -jar target/tavoo-0.0.1-SNAPSHOT.jar
```

## Troubleshooting

| Problem | What to check |
| --- | --- |
| `mvn` is not recognized, or Java compilation fails | Install Maven, add its `bin` directory to `PATH`, and check that `mvn -version` reports JDK 17. |
| Connection refused or database does not exist | Check that PostgreSQL is running, create `tavoodb`, and verify the host and port in `DB_URL`. For Docker, check `docker logs tavoo-postgres`. |
| PostgreSQL password authentication fails | Match `DB_USERNAME` and `DB_PASSWORD` to the database account. A persisted Docker database retains its original password; changing container environment variables does not reset it. |
| Port 8080 is occupied | Set `SERVER_PORT` to another free port before starting Tavoo, then use that port in the URLs. |
| JWT secret is rejected | Generate a Base64 secret with the step 1 commands and ensure the launching terminal or IDE has `TAVOO_JWT_SECRET` set. |
| Login returns 401 | Use the credentials of the existing Tavoo account. Bootstrap variables do not reset an administrator created on an earlier run. |
| A protected request returns 401 or 403 | For 401, supply a valid, unexpired token. For 403, log in with the role required by the endpoint. |
| Integration tests cannot find Docker | Start Docker Desktop or Docker Engine, confirm `docker info` succeeds, and rerun `mvn verify -Pintegration`. |

## Architecture and further documentation

Tavoo runs as one Spring Boot application with one PostgreSQL database. Its layers follow `Controller -> Service -> Repository -> JPA/Hibernate -> PostgreSQL`. Services own business transactions; DTOs define the API boundary; row locks protect order and table updates.

- [Backend architecture](docs/architecture.md): layers, persistence, transactions, and design decisions.
- [Architecture exam notes](docs/architecture-exam-notes.md): project walkthrough and discussion questions.
- [Annotation reference](docs/backend-annotations.md): Spring, JPA, validation, and test annotations.
- [UML sources](docs/uml/): domain, package, class, and sequence diagrams in PlantUML.
