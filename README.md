# Bolt OTP Checkout

OTP-based user login and checkout. A user registers and receives a 6-digit login
code. At checkout, typing a registered email triggers a debounced background
lookup; if the account exists an OTP modal asks for the code, otherwise the user
continues as a guest. Both paths persist an order to MySQL.

The application demonstrates account registration, secure OTP generation,
returning-user recognition, OTP-based login, guest checkout, and data
persistence through a REST API.

> **On the technology stack.** The assignment recommended PostgreSQL. This
> implementation uses **MySQL** instead — the assignment allowed candidates to use
> the technology they were comfortable with. The only change is the persistence
> layer; the application code above it is unaffected.

---

## Live Application

| | |
|---|---|
| **Frontend** | https://bolt-otp-checkout-web.onrender.com |
| **Backend API** | https://bolt-otp-checkout-api.onrender.com |
| **Health check** | https://bolt-otp-checkout-api.onrender.com/api/health → `{"status":"UP"}` |
| **Repository** | https://github.com/Vijay417-sys/bolt-otp-checkout |

---

## Features

**Registration**
- Collects email, first name and last name
- Generates a cryptographically random 6-digit code with `SecureRandom`
- Displays the code on screen — no email or SMS delivery
- Stores only a BCrypt hash; the plain code is never persisted
- Rejects duplicate emails with `409 Conflict`

**Recognition and login**
- Real-time email validation as the user types
- 500 ms debounce, so the API is called once per pause rather than per keystroke
- Recognition runs in the background — phone and address stay fully editable
- OTP modal with six single-character digit boxes, paste support and focus trapping
- Incorrect code keeps the modal open, shows an inline error and allows a retry
- "Skip login" continues as a guest with all entered data preserved
- Signed 30-minute session token links the order to the authenticated user

**Checkout**
- Validates email, phone and shipping address on both frontend and backend
- Guest checkout persists with `user_id = NULL`; authenticated checkout links to the user
- Loading, success and error states throughout

**Platform**
- Structured JSON errors — no stack traces, SQL or internal detail reaches the client
- Email normalisation (trim + lowercase) applied consistently
- Environment-variable configuration; CORS restricted to configured origins
- Dockerfile for both services, plus Docker Compose for local development
- Accessible: labelled inputs, focus-trapped dialog, `role="alert"` / `role="status"` live regions

---

## Application Flows

**Registration**
```
User
  → React frontend
  → POST /api/auth/register
  → Spring Boot (SecureRandom → BCrypt hash)
  → MySQL  (only the hash is written)
  ← 6-digit code returned for on-screen display
```

**Recognition** (runs in the background while the user keeps typing)
```
Checkout email
  → local format validation
  → 500 ms debounce
  → GET /api/auth/recognize
  ← {"registered": true | false}
```

**Login**
```
Registered email
  → OTP modal
  → POST /api/auth/verify
  → BCrypt comparison against the stored hash
  → HMAC-SHA256 signed session token (30 min)
  → authenticated state, user's name displayed
```

**Checkout — two paths**
```
Authenticated:  frontend → POST /api/checkout (+ X-Session-Token) → Spring Boot → MySQL → user_id set
Guest:          frontend → POST /api/checkout (no token)            → Spring Boot → MySQL → user_id NULL
```

**Session handling.** A successful verification issues a compact HMAC-SHA256 signed
token. The checkout endpoint derives `user_id` from the signature, never from client
input; a missing or invalid token simply produces a guest checkout.

---

## Architecture

```
User
  |
  v
Render Static Site
React + JavaScript + Vite + Tailwind CSS
  |
  | HTTPS REST API
  v
Render Web Service
Spring Boot 3.x + Java 21
  |
  | JDBC / JPA
  v
Aiven MySQL
```

**Frontend** owns the UI, client-side validation, debouncing, the OTP modal, state
management and all API communication. It never talks to the database.

**Backend** owns REST controllers, DTO validation, business logic, repositories,
OTP generation and hashing, session tokens and centralised exception handling. It
re-validates every request independently of the frontend.

**Database** stores `users` and `checkout_records`.

### Backend layers

```
com.bolt.checkout
├── controller/   HTTP concerns: bind, validate, delegate, respond
│   ├── HealthController
│   ├── AuthController
│   └── CheckoutController
├── service/      business logic
│   ├── AuthService          OTP generation, hashing, recognition, verification
│   ├── CheckoutService      checkout persistence
│   └── SessionTokenService  HMAC-SHA256 signed session tokens
├── repository/   Spring Data JPA
│   ├── UserRepository
│   └── CheckoutRepository
├── entity/       JPA mappings — User, CheckoutRecord
├── dto/          request/response types; entities are never exposed
├── exception/    typed exceptions + @RestControllerAdvice
└── config/       CorsConfig, SecurityConfig (BCrypt bean only)
```

### Frontend structure

```
src
├── components/
│   ├── RegistrationForm.jsx
│   ├── CheckoutForm.jsx      debounced recognition + recognition state machine
│   ├── OtpModal.jsx          focus-trapped, digit-only, paste-aware
│   ├── UserBadge.jsx
│   ├── LoadingSpinner.jsx
│   └── Toast.jsx
├── hooks/useDebounce.js
├── services/api.js           all fetch calls in one place
├── utils/validation.js
├── App.jsx
├── main.jsx
└── index.css                 Tailwind layers + component classes
```

---

## Project Structure

```
bolt-otp-checkout/
├── frontend/
│   ├── src/{components,hooks,services,utils,test}/
│   ├── index.html
│   ├── package.json
│   ├── vite.config.js
│   ├── tailwind.config.js
│   ├── postcss.config.js
│   ├── nginx.conf
│   ├── Dockerfile
│   └── .env.example
├── backend/
│   ├── src/main/java/com/bolt/checkout/
│   ├── src/main/resources/application.properties
│   ├── src/test/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── openapi.json          OpenAPI 3.0.3 spec
│   └── .env.example
├── database/
│   ├── schema.sql            authoritative MySQL schema
│   └── verify-checkout.sql   database verification queries
├── screenshots/
├── DEPLOY.md
├── VERIFY-BROWSER.md
├── docker-compose.yml
├── run.sh                    start everything with one command
├── verify.sh                 automated pre-flight checks
├── prompts.md
├── README.md
└── .gitignore
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, JavaScript (ES6+), Vite 6, Tailwind CSS 3 |
| Backend | Java 21, Spring Boot 3.4, Spring Web |
| Persistence | Spring Data JPA, Hibernate |
| Validation | Jakarta Bean Validation |
| Security | BCrypt (Spring Security Crypto) |
| Database | MySQL 8 |
| Database hosting | Aiven |
| Backend hosting | Render Web Service (Docker) |
| Frontend hosting | Render Static Site |
| Containerization | Docker |
| Tests | JUnit 5 + MockMvc (backend), Vitest + Testing Library (frontend) |

> Only `spring-security-crypto` is used, for `BCryptPasswordEncoder`. The full
> `spring-boot-starter-security` auto-configuration is deliberately excluded: the
> assignment targets the OTP flow rather than an enterprise auth stack.

---

## API Documentation

Machine-readable spec: [`backend/openapi.json`](backend/openapi.json) (OpenAPI 3.0.3).

All errors share one shape:

```json
{
  "timestamp": "2026-09-29T10:20:30",
  "status": 400,
  "message": "Invalid email address",
  "path": "/api/auth/register"
}
```

### `GET /api/health`

Liveness probe used by Render and uptime checks. No parameters.

```json
{ "status": "UP" }
```

`200 OK`

### `POST /api/auth/register`

Registers a user and returns the generated code, which the UI displays.

Request body — `email`, `firstName`, `lastName`:

```json
{
  "email": "user@example.com",
  "firstName": "Vijay",
  "lastName": "Test"
}
```

```json
{
  "message": "Registration successful",
  "code": "482193"
}
```

`201 Created`

| Status | When |
|---|---|
| `400` | Invalid email, or a missing / over-long name |
| `409` | Email already registered (matched case-insensitively) |

`otp_hash` is never part of any response.

### `GET /api/auth/recognize`

Background check run by the checkout form. Returns nothing beyond a boolean, so no
personal data is exposed.

Query parameter: `email`

```bash
curl "https://bolt-otp-checkout-api.onrender.com/api/auth/recognize?email=user@example.com"
```

```json
{ "registered": true }
```

`200 OK` · `400` for a malformed address

### `POST /api/auth/verify`

Verifies the code and signs the user in.

Request body — `email`, `code`:

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

```json
{
  "success": true,
  "userId": 1,
  "firstName": "Vijay",
  "lastName": "Test",
  "sessionToken": "<signed token>"
}
```

`200 OK`

| Status | When |
|---|---|
| `400` | Code is not exactly 6 digits |
| `401` | Code does not match |
| `404` | No account for that email |

Send the returned `sessionToken` as the `X-Session-Token` header on
`POST /api/checkout` to link the order to the user.

### `POST /api/checkout`

Persists a checkout. No payment processing is performed.

Request body — `email`, `phone`, `shippingAddress`:

```json
{
  "email": "user@example.com",
  "phone": "9876543210",
  "shippingAddress": "Bengaluru, Karnataka, India"
}
```

```json
{ "success": true, "message": "Checkout submitted successfully" }
```

`201 Created` · `400` on validation failure

Omit the header (or send an invalid one) and the record is stored as a guest
checkout with `user_id = NULL`.

---

## Security Decisions

**SecureRandom OTP generation.** Codes come from `java.security.SecureRandom`, a
cryptographically strong generator, formatted to exactly six digits with
`String.format("%06d", …)`. `Math.random()` is never used.

**BCrypt hashing.** Only the hash is stored:

```
Generated OTP ──► BCryptPasswordEncoder.encode() ──► users.otp_hash
User code     ──► BCryptPasswordEncoder.matches()  ─► true / false
```

The plain code is returned in exactly one place — the registration response —
because the assignment requires it to be displayed. It is never written to the
database and never logged.

**Email normalisation.** `trim()` + `lowercase` at three layers (DTO setter,
`@InitBinder`, service), so `Vijay@Example.com` and `vijay@example.com` are one
account.

**Validation on both sides.** Frontend validation is for fast feedback and is never
the security boundary. The backend independently applies Jakarta Bean Validation
(`@NotBlank`, `@Email`, `@Size`, `@Pattern`) to every request.

**Session tokens.** Compact HMAC-SHA256 signed tokens with a 30-minute lifetime. The
`user_id` is derived from the signature, never from request input. On the `prod`
profile the application refuses to start if `SESSION_TOKEN_SECRET` is still the
built-in development default.

**CORS.** Restricted to explicitly configured origins via `CORS_ALLOWED_ORIGIN`
(comma-separated). Wildcard `*` is not used, and `allowedHeaders` is an explicit list
(`Content-Type`, `X-Session-Token`).

**Environment variables.** All credentials and deployment settings come from the
environment. `.env` files are git-ignored; only `.env.example` files are tracked. No
credential appears in any committed file.

**Error responses.** `server.error.include-stacktrace=never`, and
`@RestControllerAdvice` returns a uniform JSON body. Stack traces, SQL and internal
detail are logged server-side only and never returned to the client.

---

## Future Production Improvements

**Not implemented.** The following are deliberate scope decisions, listed so the gap is
visible rather than assumed away:

- **OTP expiry** — codes are currently valid until the next registration for that email.
  An `otp_expires_at` column with a short TTL would bound this.
- **Rate limiting** — there is no per-IP throttle on `/api/auth/verify` or
  `/api/auth/recognize`. This is the most significant gap, because a 6-digit code has
  only a million possible values.
- **Maximum verification attempts** — no per-account lockout or backoff.
- **Session expiration and rotation** — tokens are stateless with a 30-minute lifetime;
  there is no revocation list or refresh path.
- **Stronger account-enumeration protection** — `/api/auth/recognize` and the `409` on
  registration both reveal whether an email exists. This cannot be fully closed without
  changing the product, because the checkout form is specified to branch on that boolean.
- **Monitoring and audit logging** — no request IDs, metrics, or a record of login
  attempts and verification failures.

---

## Database

MySQL is used because the assignment permits using the technology the candidate is
comfortable with.

### `users`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | Primary key, auto-increment |
| `email` | `VARCHAR(255)` | **Unique**, not null |
| `first_name` | `VARCHAR(100)` | Not null |
| `last_name` | `VARCHAR(100)` | Not null |
| `otp_hash` | `VARCHAR(255)` | BCrypt hash, not the plain code |
| `created_at` | `DATETIME` | Defaults to `CURRENT_TIMESTAMP` |

The unique constraint on `email` is what prevents two concurrent registrations for the
same address from both succeeding. It is also the index used by the recognition
endpoint, so no separate index is needed.

### `checkout_records`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | Primary key, auto-increment |
| `user_id` | `BIGINT` | **Nullable** — `NULL` for guest checkout |
| `email` | `VARCHAR(255)` | Not null |
| `phone` | `VARCHAR(50)` | Not null |
| `shipping_address` | `VARCHAR(1000)` | Not null |
| `created_at` | `DATETIME` | Defaults to `CURRENT_TIMESTAMP` |

`user_id` references `users(id)`. The foreign key uses **`ON DELETE SET NULL`**, so
deleting a user never destroys order history — the record simply becomes a guest
checkout. Indexes exist on `email` and `user_id`.

### Schema management

[`database/schema.sql`](database/schema.sql) is the authoritative definition. The
application runs with `spring.jpa.hibernate.ddl-auto=validate`, so the JPA entities are
checked against this schema on startup and any drift fails fast rather than being
silently patched.

Every statement is guarded with `IF NOT EXISTS` and indexes are declared inline, so the
file is safe to re-run.

---

## Local Development

**Prerequisites** — Java 21, Maven 3.8+, Node 18+ (22 recommended), MySQL 8 (or Docker).

### 1. Database

Run these one at a time; each prompts for your MySQL root password.

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS bolt_checkout CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

```bash
# Replace <local-db-password> with a password of your own choosing.
mysql -u root -p -e "CREATE USER IF NOT EXISTS 'bolt'@'localhost' IDENTIFIED BY '<local-db-password>'; GRANT ALL PRIVILEGES ON bolt_checkout.* TO 'bolt'@'localhost'; FLUSH PRIVILEGES;"
```

```bash
mysql -u root -p bolt_checkout < database/schema.sql
```

### 2. Backend

There is no Maven wrapper in this repository, so use `mvn`:

`docker-compose.yml` supplies its own local defaults, so if you use it you can skip the
step above and run `docker compose up --build` instead.

```bash
cd backend
export DATABASE_URL="jdbc:mysql://localhost:3306/bolt_checkout?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DATABASE_USERNAME=bolt
export DATABASE_PASSWORD=<local-db-password>
export SESSION_TOKEN_SECRET="$(openssl rand -base64 48)"
export CORS_ALLOWED_ORIGIN=http://localhost:5173
mvn spring-boot:run
```

API on <http://localhost:8080> — check `GET /api/health`.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

App on <http://localhost:5173>.

### Convenience script

`./run.sh` performs all of the above in one terminal — it checks the toolchain, applies
the schema, starts both services, waits for them to answer, and picks free ports
automatically.

```bash
./run.sh          # start
./run.sh status   # what is running
./run.sh logs     # follow both logs
./run.sh stop     # shut down
```

---

## Environment Variables

Nothing below is committed with a real value. See the `.env.example` files.

### Frontend — `frontend/.env.example`

| Variable | Required | Description |
|---|---|---|
| `VITE_API_BASE_URL` | Yes | Base URL of the API, no trailing slash. Inlined at build time, so a change needs a redeploy. |

Local development example: `VITE_API_BASE_URL=http://localhost:8080`

### Backend — `backend/.env.example`

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | Yes | JDBC connection string. `DATABASE_URL=jdbc:mysql://<host>:3306/<database>?useSSL=true&serverTimezone=UTC` |
| `DATABASE_USERNAME` | Yes | `DATABASE_USERNAME=<your-db-user>` |
| `DATABASE_PASSWORD` | Yes | `DATABASE_PASSWORD=<your-password>` |
| `CORS_ALLOWED_ORIGIN` | Yes | Comma-separated allowed browser origins. |
| `SESSION_TOKEN_SECRET` | Yes | `SESSION_TOKEN_SECRET=<generate-a-random-secret>` — e.g. `openssl rand -base64 48` |
| `SPRING_PROFILES_ACTIVE` | No | Set to `prod` on Render to enable production guards. |
| `DB_POOL_SIZE` | No | HikariCP pool size (default `5`). |
| `PORT` | No | HTTP port (default `8080`; Render sets it automatically). |

> `.env` files are git-ignored. Only `.env.example` files are tracked, and they contain
> placeholders only. **Never commit real credentials.**

---

## Docker

### Backend

`backend/Dockerfile` is a two-stage build — Maven compiles the jar, then a minimal
Java 21 JRE image runs it as a non-root user:

```bash
cd backend
docker build -t bolt-otp-checkout-backend .
docker run --rm -p 8080:8080 \
  -e DATABASE_URL="jdbc:mysql://<host>:3306/<database>?useSSL=true&serverTimezone=UTC" \
  -e DATABASE_USERNAME=<your-db-user> \
  -e DATABASE_PASSWORD=<your-password> \
  -e CORS_ALLOWED_ORIGIN=https://bolt-otp-checkout-web.onrender.com \
  -e SESSION_TOKEN_SECRET=<generate-a-random-secret> \
  bolt-otp-checkout-backend
curl http://localhost:8080/api/health     # {"status":"UP"}
```

The image does **not** create the schema — `database/schema.sql` must be applied to the
database first, and Hibernate only validates it.

### Frontend

`frontend/Dockerfile` builds the Vite bundle and serves it with nginx:

```bash
cd frontend
docker build --build-arg VITE_API_BASE_URL=https://bolt-otp-checkout-api.onrender.com -t bolt-otp-checkout-frontend .
docker run --rm -p 5173:80 bolt-otp-checkout-frontend
```

### Docker Compose

`docker-compose.yml` runs MySQL 8, the backend and the frontend together. It is a
**local development convenience** — it is not used for the deployed environment, which
runs on Render and Aiven.

```bash
docker compose up --build
```

The MySQL service mounts `database/schema.sql` as an init script, and the backend waits
for the database to report healthy before starting.

---

## Deployment

| Component | Platform |
|---|---|
| Frontend | Render Static Site |
| Backend | Render Web Service (Docker) |
| Database | Aiven MySQL (`defaultdb`) |

The sequence, and why the order matters:

1. **Push to GitHub** — Render and the database console both work from the repository.
2. **Create the Render Web Service** for the backend. Root directory `backend`,
   Dockerfile path `./Dockerfile`. Health check path `/api/health`.
3. **Configure the Aiven connection** using the environment variables above. Aiven
   supplies the host, port, database name and credentials; the database is `defaultdb`.
4. **Set `SESSION_TOKEN_SECRET`** to a generated value. The `prod` profile refuses to
   start on the development default.
5. **Apply `database/schema.sql`** to the Aiven database, and set `CORS_ALLOWED_ORIGIN`
   to the frontend origin.
6. **Create the Render Static Site** for the frontend. Build `npm run build`, publish
   directory `dist`, and set `VITE_API_BASE_URL` to the backend URL.
7. **Verify** `https://bolt-otp-checkout-api.onrender.com/api/health` returns
   `{"status":"UP"}`, then walk the full flow on the frontend URL.

> `CORS_ALLOWED_ORIGIN` must include the deployed frontend origin. `VITE_API_BASE_URL` is
> inlined at build time, so changing it requires a frontend redeploy.

Full instructions, including troubleshooting, are in [`DEPLOY.md`](DEPLOY.md).

---

## Testing

### Automated tests

Both suites are committed and were executed against this repository.

**Backend** — 39 tests, 0 failures:

```bash
cd backend && mvn clean test
```

| Suite | Tests | Covers |
|---|---|---|
| `AuthApiTest` | 13 | Registration, duplicate `409`, invalid `400`, normalisation, hash-only storage, recognition, verification (`200`/`401`/`400`/`404`), health, unmapped URL `404` |
| `AuthServiceTest` | 9 | 2,000 generated codes are always exactly 6 digits, codes differ, hash matches, BCrypt salting, case-insensitive duplicates, typed exceptions, name trimming |
| `CheckoutApiTest` | 8 | Guest checkout (`user_id` null), authenticated checkout, forged token fallback, validation `400`, malformed JSON, repeated orders, history preserved on user delete |
| `SessionTokenServiceTest` | 5 | Round-trip, tampered payload, tampered signature, malformed tokens, opacity |
| `SessionTokenSecretGuardTest` | 4 | Refuses to start on the prod profile with the development default secret |

**Frontend** — 61 tests, 0 failures:

```bash
cd frontend && npm test
```

| Suite | Tests | Covers |
|---|---|---|
| `CheckoutForm.test.jsx` | 18 | Invalid email triggers no request, one debounced request, modal opens, digits-only, wrong-code error, correct-code login, skip login, no modal reopen, session token sent, background typing, recognition failure |
| `OtpModal.test.jsx` | 12 | Dialog semantics, autofocus, digit spreading, paste, duplicate-submit guard, focus restored after failure, Escape |
| `RegistrationForm.test.jsx` | 7 | Renders, validation, API call, code display, error surfacing, loading state |
| `validation.test.js` | 17 | Every validator, edge cases, whole-form aggregation |
| `useDebounce.test.js` | 7 | Delay respected, rapid changes publish only the final value, settling on an unchanged value |

The backend test profile loads the **real `database/schema.sql`** into H2 in MySQL
compatibility mode and runs Hibernate with `validate` — the same setting as production.
The suite therefore fails if the committed schema and the JPA entities ever drift apart.

### Manual Verification

The following were exercised against the **deployed** application at
[bolt-otp-checkout-web.onrender.com](https://bolt-otp-checkout-web.onrender.com), with
the REST endpoints and status codes confirmed directly against
[bolt-otp-checkout-api.onrender.com](https://bolt-otp-checkout-api.onrender.com):

1. Register a user and record the displayed 6-digit code
2. Enter the same email at checkout → "Account recognized"
3. OTP modal opens after the debounce period
4. Enter a wrong code → modal stays open with an inline error
5. Enter the correct code → modal closes, "Welcome, First Last" is displayed
6. Checkout data entered before login is preserved
7. Authenticated checkout succeeds and persists with a non-null `user_id`
8. An unregistered email shows "No account found. Continue as guest." and checks out with `user_id = NULL`
9. "Skip login" preserves the form and submits as a guest
10. Rows appear in MySQL

### How to Verify the Database

**The database is not publicly exposed**, and that is deliberate. There is no endpoint
that returns table contents, and database credentials exist only as Render environment
variables. Nothing in this repository contains a password, connection string with a
password, or an Aiven DSN.

Persistence is verified through the application instead:

1. Register a user at the [live frontend](https://bolt-otp-checkout-web.onrender.com) and
   note the displayed 6-digit code.
2. Enter the same email at checkout, enter the code, and submit — this produces a
   **checkout record with a non-null `user_id`**.
3. Enter an unregistered email and submit — this produces a **guest record with
   `user_id = NULL`**.
4. Both rows are persisted to MySQL by the API; neither is held in browser state.
5. A reviewer with authorised Aiven access can confirm the rows using the read-only SQL
   below.

```sql
SELECT id, email, first_name, last_name, created_at
FROM users
ORDER BY id DESC;

SELECT id, user_id, email, phone, shipping_address, created_at
FROM checkout_records
ORDER BY id DESC;
```

[`database/verify-checkout.sql`](database/verify-checkout.sql) contains the full set of
read-only checks: guest vs. authenticated `user_id`, orphan `user_id` values against the
foreign key, `NOT NULL` on every required column, email normalisation, absence of
plaintext OTPs, and confirmation that order history survives user deletion.

---

## Screenshots

| File | Shows |
|---|---|
| [`registration.png`](screenshots/registration.png) | Empty registration form |
| [`registration-success.png`](screenshots/registration-success.png) | Generated 6-digit code displayed prominently |
| [`otp-modal.png`](screenshots/otp-modal.png) | OTP modal for a recognised email |
| [`invalid-otp.png`](screenshots/invalid-otp.png) | Inline error after a wrong code; modal stays open |
| [`logged-in-checkout.png`](screenshots/logged-in-checkout.png) | "Welcome, Vijay Hosapeti" with checkout data preserved |
| [`checkout-success.png`](screenshots/checkout-success.png) | Successful authenticated checkout |
| [`guest-checkout.png`](screenshots/guest-checkout.png) | "No account found. Continue as guest." |
| [`mobile-registration.png`](screenshots/mobile-registration.png) | 390 px viewport |

All screenshots were captured from the running application — the built frontend driven in
a real browser against the Spring Boot API and a real MySQL 8 database — using synthetic
`@example.com` addresses. They contain no credentials, tokens or real personal data.

---

## Assignment Compliance

| Requirement | Status | Evidence |
|---|---|---|
| Registration collects email, first name, last name | Implemented | `RegistrationForm.jsx` + `POST /api/auth/register` |
| Random 6-digit numeric code | Implemented | `SecureRandom` + `String.format("%06d", …)` |
| Code displayed to the user | Implemented | Registration success screen |
| Only a BCrypt hash stored | Implemented | `BCryptPasswordEncoder`, verified by test |
| Checkout collects email, phone, address | Implemented | `CheckoutForm.jsx` + `POST /api/checkout` |
| Real-time email validation | Implemented | `utils/validation.js`, live inline feedback |
| Debounced background recognition | Implemented | `hooks/useDebounce.js`, 500 ms |
| Recognition does not block typing | Implemented | Phone and address remain editable during lookup |
| Registered email recognised | Implemented | `GET /api/auth/recognize` |
| OTP modal with skip login | Implemented | `OtpModal.jsx` |
| Wrong code keeps modal open and allows retry | Implemented | Inline error, boxes cleared, focus restored |
| Correct code logs the user in | Implemented | `POST /api/auth/verify` → signed session token |
| User's name displayed after login | Implemented | `UserBadge.jsx` |
| Checkout data preserved through login | Implemented | Verified by test |
| Guest checkout | Implemented | `user_id` is `NULL` |
| Authenticated checkout linked to user | Implemented | `user_id` set from the signed token |
| Data saved to the database | Implemented | MySQL via Spring Data JPA |
| No payment processing | Implemented | Not implemented, by design |
| Separate frontend / backend / database | Implemented | Render Static Site, Render Web Service, Aiven |
| Frontend never accesses the database | Implemented | All DB access is behind the REST API |
| Controller / service / repository separation | Implemented | See *Backend layers* |
| DTOs, entities never exposed | Implemented | `dto/` package |
| Centralised error handling | Implemented | `@RestControllerAdvice` |
| Frontend + backend validation | Implemented | Bean Validation independent of the UI |
| Environment-variable configuration | Implemented | No credentials committed |
| CORS restricted | Implemented | `CORS_ALLOWED_ORIGIN` |
| No stack traces exposed | Implemented | `include-stacktrace=never` + handler |
| Docker support | Implemented | Both Dockerfiles + Compose for local use |
| Public frontend | Implemented | Render Static Site |
| Public backend | Implemented | Render Web Service |
| Authoritative schema file | Implemented | `database/schema.sql` |
| Automated tests | Implemented | 39 backend, 61 frontend |
| LLM prompts recorded | Implemented | `prompts.md` |
| PostgreSQL as recommended | Deviation | MySQL used instead, permitted by the assignment |

---

## Known Limitations

These are deliberate scope decisions, not defects. The security-related items are also
listed under *Future Production Improvements*.

- **No OTP expiration.** A code stays valid until the next registration for the same
  email. Adding an `otp_expires_at` column with a short TTL is the first change to make.
- **No rate limiting.** A 6-digit code has a million possible values, and `/api/auth/verify`
  is unthrottled. This is the most significant gap for a real deployment.
- **No maximum verification attempts.** Nothing prevents repeated guessing per account.
- **Stateless session tokens.** 30-minute lifetime, but no revocation or refresh path.
- **Account enumeration.** `/api/auth/recognize` reveals whether an email is registered.
  This is inherent to the specified flow, which requires the checkout form to branch on
  that boolean.
- **No audit logging or monitoring.** Login attempts and verification failures are not
  recorded, and there are no request IDs or metrics.
- **No CI workflow.** The suites run locally via `mvn test` and `npm test` rather than a
  hosted pipeline.

---

## AI Assistance

AI/LLM tools were used as development assistance. Generated output was reviewed,
adapted, tested and integrated into the application.

[`prompts.md`](prompts.md) contains the prompts actually used during development,
including the original project brief and the database migration from PostgreSQL to
MySQL. Bugs found by running the builds, tests and a real browser — rather than by
reading the code — are recorded in the same file.
