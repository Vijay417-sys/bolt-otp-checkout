# Bolt OTP Checkout

OTP-based user login and checkout — React (JavaScript) + Spring Boot + MySQL.

A user registers and receives a 6-digit login code. At checkout, typing a
registered email triggers a debounced background lookup; if the account is found
an OTP modal asks for the code, otherwise the user continues as a guest. Both
paths persist a checkout record to MySQL.

---

## Overview

| | |
|---|---|
| **Frontend** | React 18 + JavaScript (ES6+) + Vite + Tailwind CSS |
| **Backend** | Java 21, Spring Boot 3.4, Spring Data JPA, Hibernate |
| **Database** | MySQL 8+ |
| **Tests** | 38 backend (JUnit 5 + MockMvc), 61 frontend (Vitest + Testing Library), 27 browser end-to-end checks |
| **Deploy** | Vercel (frontend) · Render + Docker (backend) · MySQL 8 |

---

## Features

**Registration**
- Collects email, first name, last name
- Generates a cryptographically random 6-digit code with `SecureRandom`
- Displays the code on screen (no email/SMS, per the assignment)
- Rejects duplicate emails with `409 Conflict`
- Stores **only** a BCrypt hash — the plain code is never persisted

**Recognition & login**
- Real-time email validation as the user types
- 500 ms debounce, so the API is called once per pause rather than per keystroke
- Recognition runs in the background: phone and address stay fully editable
- OTP modal for registered emails: 6 digit-only boxes, paste support, inline error
- "Skip login" continues as a guest with all entered data preserved
- Incorrect codes keep the modal open, clear the boxes and return focus for an immediate retry
- A signed, 30-minute session token links the checkout to the authenticated user

**Checkout**
- Validates email, phone and shipping address
- Guest checkouts persist with `user_id = null`
- Loading, success and error states throughout

**Platform**
- Structured JSON errors — no stack traces, SQL or internal detail ever reaches the client
- Email normalisation (trim + lowercase) applied consistently
- Environment-variable configuration, CORS restricted to configured origins
- Dockerfile for both services, Docker Compose for local development
- Accessible: labelled inputs, focus-trapped dialog, `role="alert"`/`role="status"` live regions

---

## Architecture

```
┌──────────────────────────────┐
│  Vercel — React + JavaScript │
│  (Tailwind, Vite)            │
└──────────────┬───────────────┘
               │  HTTPS  JSON  (fetch)
               │  X-Session-Token header after login
               ▼
┌──────────────────────────────┐
│  Render — Spring Boot (Docker)│
│                              │
│  controller → service →      │
│  repository → MySQL          │
└──────────────┬───────────────┘
               │  JDBC / JPA
               ▼
┌──────────────────────────────┐
│  MySQL 8                     │
│  users, checkout_records     │
└──────────────────────────────┘
```

- **Frontend** owns the UI, client-side validation, debouncing, the OTP modal and
  all user feedback. It never talks to the database.
- **Backend** owns business logic — OTP generation, BCrypt hashing, email
  normalisation, recognition and persistence — and re-validates every request
  independently of the frontend.
- **Database** stores users and checkout records. `database/schema.sql` is the
  authoritative definition; Hibernate runs with `ddl-auto=validate` and will
  refuse to start if the entities drift from the schema.

### Backend layer structure

```
com.bolt.checkout
├── controller/   HTTP concerns only: bind, validate, delegate, respond
│   ├── HealthController
│   ├── AuthController
│   └── CheckoutController
├── service/      business logic
│   ├── AuthService          OTP generation, hashing, recognition, verification
│   ├── CheckoutService      checkout persistence
│   └── SessionTokenService  HMAC-signed session tokens
├── repository/   Spring Data JPA
│   ├── UserRepository
│   └── CheckoutRepository
├── entity/       JPA mappings
│   ├── User
│   └── CheckoutRecord
├── dto/          request/response records — entities are never exposed
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
├── services/api.js           all fetch calls, one place
├── utils/validation.js
├── App.jsx
├── main.jsx
└── index.css                 Tailwind layers + component classes
```

---

## Technology Stack

**Frontend** — React 18, JavaScript (no TypeScript), Vite 6, Tailwind CSS 3,
Testing Library, Vitest. No state library; React hooks only.

**Backend** — Java 21, Spring Boot 3.4, Spring Web, Spring Data JPA, Hibernate,
Jakarta Bean Validation, `spring-security-crypto` (BCrypt only), MySQL JDBC.

> The full `spring-boot-starter-security` auto-configuration is deliberately **not**
> used. The assignment targets the OTP flow, not an enterprise auth stack, and
> pulling it in would add a filter chain that has nothing to protect here.
> `spring-security-crypto` supplies the `BCryptPasswordEncoder` bean.

---

## Project Structure

```
bolt-otp-checkout/
├── frontend/
│   ├── src/{components,hooks,services,utils,test}/
│   ├── public/
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
│   ├── openapi.json
│   └── .env.example
├── database/schema.sql
├── screenshots/
├── docker-compose.yml
├── README.md
├── prompts.md
└── .gitignore
```

---

## Local Setup

**Prerequisites** — Java 21, Maven 3.8+, Node 18+ (22 recommended), MySQL 8+
(or Docker).

### 1. Database

```bash
# Create the database, and a dedicated application user. The app never connects
# as root - only the least-privilege `bolt` user owns the schema.
mysql -u root -p <<'SQL'
CREATE DATABASE IF NOT EXISTS bolt_checkout
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'bolt'@'localhost' IDENTIFIED BY 'boltpw';
GRANT ALL PRIVILEGES ON bolt_checkout.* TO 'bolt'@'localhost';
FLUSH PRIVILEGES;
SQL

# Apply the schema. Safe to re-run: every statement is guarded.
mysql -u root -p bolt_checkout < database/schema.sql
```

Use the same `bolt` / `boltpw` values below, or change both to match.

Or let Docker do it: `docker compose up -d db` — the compose file creates the
user, mounts `database/schema.sql` as an init script, and waits for the server to
become healthy.

### 2. Backend

```bash
cd backend
cp .env.example .env        # then fill in the values
export DATABASE_URL="jdbc:mysql://localhost:3306/bolt_checkout?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"
export DATABASE_USERNAME=bolt
export DATABASE_PASSWORD=your-password
export SESSION_TOKEN_SECRET="$(openssl rand -base64 48)"
mvn spring-boot:run
```

API is now on <http://localhost:8080> — check `GET /api/health`.

### 3. Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

App is now on <http://localhost:5173>.

### 4. Everything in Docker

```bash
cp backend/.env.example backend/.env   # optional; compose has local defaults
docker compose up --build
```

Frontend <http://localhost:5173>, API <http://localhost:8080>, MySQL on `3306`.

> **Note:** if port 8080 is already taken on your machine, set `PORT=8081` for the
> backend and point `VITE_API_BASE_URL` at `http://localhost:8081`.

---

## Environment Variables

### Frontend (`frontend/.env.example`)

| Variable | Required | Description |
|---|---|---|
| `VITE_API_BASE_URL` | Yes | Base URL of the API, no trailing slash. Inlined at build time — **redeploy after changing it**. |

### Backend (`backend/.env.example`)

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | Yes | JDBC connection string. |
| `DATABASE_USERNAME` | Yes | Database user. |
| `DATABASE_PASSWORD` | Yes | Database password. |
| `CORS_ALLOWED_ORIGIN` | Yes | Comma-separated allowed browser origins. |
| `SESSION_TOKEN_SECRET` | Yes | HMAC signing secret. Generate with `openssl rand -base64 48`. |
| `DB_POOL_SIZE` | No | HikariCP pool size (default `5`). |
| `PORT` | No | HTTP port (default `8080`; Render sets it automatically). |

No real credentials are committed. `.env` files are git-ignored; only
`.env.example` files are tracked.

---

## Database Setup

`database/schema.sql` is the authoritative schema and runs on a clean MySQL 8
database.

```sql
CREATE TABLE IF NOT EXISTS users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    otp_hash   VARCHAR(255) NOT NULL,   -- BCrypt hash, never the plain code
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS checkout_records (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    user_id          BIGINT        NULL,   -- NULL for guest checkout
    email            VARCHAR(255)  NOT NULL,
    phone            VARCHAR(50)   NOT NULL,
    shipping_address VARCHAR(1000) NOT NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_checkout_email (email),
    INDEX idx_checkout_user_id (user_id),
    CONSTRAINT fk_checkout_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
```

**Design notes**

- `InnoDB` — required for the foreign key and for `ON DELETE SET NULL`; MyISAM
  silently ignores both.
- `utf8mb4_unicode_ci` — 4-byte Unicode, and a case-insensitive collation, which
  is what makes the `uq_users_email` uniqueness check case-insensitive. The
  backend also normalises emails to lowercase, so the two agree.
- `AUTO_INCREMENT` — matches `GenerationType.IDENTITY` on the JPA `@Id`, so
  Hibernate reads the key back from the driver instead of maintaining a separate
  sequence table.
- `ON DELETE SET NULL` — deleting a user never destroys order history; the record
  simply becomes a guest checkout.
- `uq_users_email` — enforces uniqueness at the database level, so two concurrent
  registrations cannot both succeed.
- **No separate index on `users.email`** — the `UNIQUE` constraint already creates
  one, and the recognition endpoint queries by email on every debounced keystroke.
- **Indexes are declared inline** — MySQL has no `CREATE INDEX IF NOT EXISTS`, so
  separate `CREATE INDEX` statements would not be idempotent and a second run of
  the script would fail. Keeping them inside `CREATE TABLE` means the
  `IF NOT EXISTS` guard covers them too.

### Managed MySQL

Any MySQL 8 provider works — PlanetScale, AWS RDS, Aiven, DigitalOcean, or a
self-hosted `mysql:8.4` container. There is no vendor-specific integration: the
application connects over standard JDBC/JPA.

1. Create a MySQL 8 database and a dedicated application user.
2. Run `database/schema.sql` against it (via the provider's SQL console, a client
   from your machine, or a one-off `mysql` container).
3. Set `DATABASE_URL`, `DATABASE_USERNAME` and `DATABASE_PASSWORD` from the
   credentials it gave you.

Most managed providers terminate TLS. In that case use
`?useSSL=true&serverTimezone=UTC` and drop `allowPublicKeyRetrieval`, which is
only needed for MySQL 8's `caching_sha2_password` handshake over a plaintext
connection.

---

## API Documentation

Full machine-readable specs: [`backend/openapi.json`](backend/openapi.json) and
[`backend/openapi.yaml`](backend/openapi.yaml).

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

Liveness probe for Render and uptime checks.

```bash
curl http://localhost:8080/api/health
```
```json
{ "status": "UP" }
```
`200 OK`

---

### `POST /api/auth/register`

Registers a user and issues a login code. The plain code is returned **only here**,
because the assignment requires it to be displayed on screen.

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"vijay@example.com","firstName":"Vijay","lastName":"Hosapeti"}'
```
```json
{ "message": "Registration successful", "code": "482193" }
```
`201 Created`

| Status | When |
|---|---|
| `400` | Invalid email, or a missing/over-long name |
| `409` | Email already registered (matched case-insensitively) |

`otp_hash` is never part of any response.

---

### `GET /api/auth/recognize`

Background check run by the checkout form. Returns nothing beyond a boolean, so no
personal data is exposed.

```bash
curl "http://localhost:8080/api/auth/recognize?email=vijay@example.com"
```
```json
{ "registered": true }
```

Unknown email → `{"registered": false}`. `200 OK`, `400` for a malformed address.

---

### `POST /api/auth/verify`

Verifies the code and signs the user in.

```bash
curl -X POST http://localhost:8080/api/auth/verify \
  -H 'Content-Type: application/json' \
  -d '{"email":"vijay@example.com","code":"482193"}'
```
```json
{
  "success": true,
  "userId": 1,
  "firstName": "Vijay",
  "lastName": "Hosapeti",
  "sessionToken": "MXxWbWxxWVhrfFNHOXpZWEJsZEdrfGRtbHFZWGxB..."
}
```
`200 OK`

| Status | When |
|---|---|
| `400` | Code is not exactly 6 digits |
| `401` | Code does not match |
| `404` | No account for that email |

The `sessionToken` is a short-lived HMAC-SHA256 signed token. Send it as
`X-Session-Token` on `POST /api/checkout` to link the order to the user.

---

### `POST /api/checkout`

Persists a checkout. No payment processing is performed.

```bash
curl -X POST http://localhost:8080/api/checkout \
  -H 'Content-Type: application/json' \
  -H "X-Session-Token: $TOKEN" \
  -d '{"email":"vijay@example.com","phone":"+919876543210","shippingAddress":"Bengaluru, Karnataka, India"}'
```
```json
{ "success": true, "message": "Checkout submitted successfully" }
```
`201 Created` · `400` on validation failure.

Omit the header (or send an invalid one) and the record is stored as a guest
checkout with `user_id = null`.

---

---

## Application Flow

```
1. REGISTER
   email + first + last  ──►  201 { code: "482193" }
   (code displayed on screen, only its BCrypt hash is stored)

2. CHECKOUT — user types the email
   invalid/partial  ──►  local validation only, no API call
   valid            ──►  500 ms debounce, then GET /api/auth/recognize
                        the phone and address fields stay editable throughout

3a. registered = false  ──►  "Continue as guest"
3b. registered = true   ──►  OTP modal
      ├─ wrong code     ──►  401, modal stays open, error shown, retry allowed
      ├─ correct code   ──►  200, modal closes, "Welcome, First Last" shown,
      │                      entered checkout data preserved, session token held
      └─ Skip login     ──►  modal closes, "Guest checkout", data preserved
                            (the modal is not reopened for the same email)

4. SUBMIT  ──►  POST /api/checkout  ──►  201  ──►  row in checkout_records
               user_id set when signed in, NULL for a guest
```

**Race-condition handling.** Each recognition request carries an incrementing id;
a response is applied only if it is still the newest, so a slow reply for an old
email can never overwrite the state for the address currently being typed.

**Email normalisation.** `trim()` + `lowercase` is applied in the DTO setter, in
the `@InitBinder`, and again in `AuthService.normalizeEmail` before every database
lookup — so `Vijay@Example.com` and `vijay@example.com` are the same account.

**Re-running recognition for an unchanged address.** Recognition is keyed off a
counter that increments each time the debounce settles, not off the debounced string
itself. React cannot distinguish "set to the value it already holds" from "never
changed", so without that counter, clearing the email field and retyping the *same*
address would leave the form permanently inactive — no status, no modal, no way to
log in. `useDebounce` therefore takes an `onSettle` callback for exactly this case.

---

## Testing

### Backend — 38 tests

```bash
cd backend
mvn test
```

| Suite | Tests | Covers |
|---|---|---|
| `AuthApiTest` | 12 | Registration (success, duplicate 409, invalid 400, normalisation, hash-only storage), recognition (registered/unknown), verification (correct, wrong 401, malformed 400, unknown 404), health |
| `CheckoutApiTest` | 8 | Guest checkout (`user_id` null), authenticated checkout, forged token fallback, validation 400, malformed JSON, repeated orders, history preserved on user delete |
| `AuthServiceTest` | 9 | 2,000 generated codes are always 6 digits, codes differ, hash matches, BCrypt salting, case-insensitive duplicates, typed exceptions, name trimming |
| `SessionTokenServiceTest` | 5 | Round-trip, tampered payload, tampered signature, malformed tokens, opacity |
| `SessionTokenSecretGuardTest` | 4 | Refuses to start on the prod profile with the development default secret |

The test profile loads the **real `database/schema.sql`** into H2 in MySQL
compatibility mode (`MODE=MySQL`) and runs Hibernate with `validate` — the same
setting as production. This means the suite fails if the committed schema and the
JPA entities ever diverge, which is how the `SERIAL`/`bigint` bug was originally
caught.

> Because the schema file is applied on every Spring context startup, the test
> suite is written so that only **one** Spring context is created per JVM. Any
> test that needs different properties builds its objects directly (see
> `SessionTokenSecretGuardTest`) rather than adding a `@TestPropertySource`,
> which would trigger a second context and re-run `schema.sql`.

### Frontend — 61 tests

```bash
cd frontend
npm test
```

| Suite | Tests | Covers |
|---|---|---|
| `CheckoutForm.test.jsx` | 18 | Invalid email triggers no request, one debounced request, modal opens, digits-only, wrong-code error, correct-code login, skip login, no modal reopen, session token sent, background typing, recognition failure, recognition re-runs after the email is cleared and retyped |
| `OtpModal.test.jsx` | 12 | Dialog semantics, autofocus, digit spreading, paste, duplicate-submit guard, focus restored after failure, Escape |
| `RegistrationForm.test.jsx` | 7 | Renders, validation, API call, code display, error surfacing, loading state |
| `validation.test.js` | 17 | Every validator, edge cases, whole-form aggregation |
| `useDebounce.test.js` | 7 | Delay respected, rapid changes publish only the final value, settling on an unchanged value, callback identity does not restart the timer |

### Manual end-to-end

Driven in a real browser (Chromium) against the **production frontend build** and a
**real MySQL 8 database** — not the H2 test double. 27 checks covering:

| Scenario | Checks |
|---|---|
| A — registered user | Registration, 6-digit code shown, checkout renders, phone stays editable *while recognition runs*, modal opens, wrong code keeps the modal open with an inline error, checkout data survives the failure, correct code closes the modal, name shown, data preserved, order submitted |
| B — unknown user | No modal, guest status, order submitted |
| C — skip login | Modal closes, fields intact, "Guest checkout" shown, retry offered, order submitted |
| Errors | Invalid email rejected client-side and triggers no API call |
| Layout | 390 px mobile viewport renders |
| Console/network | No unexpected console errors; the only non-2xx responses are the dev-server favicon and the deliberate `401` from the wrong-OTP attempt |

The resulting rows were then confirmed directly in MySQL: the authenticated order
carries the `user_id`, the guest orders carry `NULL`, and no `otp_hash` is six
characters long. See `screenshots/`, which are captured from that same run.

---

## Docker

**Backend** (multi-stage, Java 21, non-root, `${PORT}` aware):

```bash
cd backend
docker build -t bolt-otp-checkout-backend .
docker run --rm -p 8080:8080 \
  -e DATABASE_URL="jdbc:mysql://host.docker.internal:3306/bolt_checkout?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -e DATABASE_USERNAME=bolt \
  -e DATABASE_PASSWORD=your-password \
  -e CORS_ALLOWED_ORIGIN=http://localhost:5173 \
  -e SESSION_TOKEN_SECRET="$(openssl rand -base64 48)" \
  bolt-otp-checkout-backend
curl http://localhost:8080/api/health     # {"status":"UP"}
```

The image does **not** create the schema — `database/schema.sql` must be applied to
the database first, and Hibernate only validates it.

**Frontend** (build + nginx):

```bash
cd frontend
docker build --build-arg VITE_API_BASE_URL=https://your-api.onrender.com -t bolt-otp-checkout-frontend .
docker run --rm -p 5173:80 bolt-otp-checkout-frontend
```

**All services:** `docker compose up --build`

---

## Deployment

Target architecture: Vercel → Render (Docker) → MySQL 8.

### 1. MySQL (database)

1. Provision a MySQL 8 database (any provider, or a `mysql:8.4` container) and
   create a dedicated application user.
2. Run `database/schema.sql` against it.
3. Note the host, port, database name and credentials for step 2.

### 2. Render (backend)

1. **New → Web Service** → connect the repository.
   *If the repository has a `backend/` Dockerfile, Render detects it automatically;
   otherwise set **Root Directory** to `backend` and **Dockerfile Path** to
   `./Dockerfile`.*
2. **Environment** (all marked *Secret* where applicable):

   | Key | Value |
   |---|---|
   | `DATABASE_URL` | `jdbc:mysql://<host>:3306/bolt_checkout?useSSL=true&serverTimezone=UTC` |
   | `DATABASE_USERNAME` | your MySQL application user |
   | `DATABASE_PASSWORD` | your MySQL password |
   | `CORS_ALLOWED_ORIGIN` | `https://<your-vercel-domain>` |
   | `SESSION_TOKEN_SECRET` | `openssl rand -base64 48` |

3. **Health Check Path**: `/api/health`
4. Deploy and note the URL, e.g. `https://bolt-otp-checkout-backend.onrender.com`.

> If your provider does not terminate TLS, add `allowPublicKeyRetrieval=true` to
> `DATABASE_URL` — MySQL 8's default `caching_sha2_password` authentication needs
> it on a plaintext connection. Allow-list Render's outbound IPs if your provider
> supports IP restrictions, and confirm the pool size (`DB_POOL_SIZE`, default
> `5`) fits within your plan's connection limit.

### 3. Vercel (frontend)

1. **New Project** → import the repository. Set **Root Directory** to `frontend`.
   The build command and output directory are detected automatically
   (`npm run build` → `dist`).
2. **Settings → Environment Variables**:

   | Key | Value |
   |---|---|
   | `VITE_API_BASE_URL` | `https://bolt-otp-checkout-backend.onrender.com` |

3. Deploy, then **add the resulting Vercel origin to the backend's
   `CORS_ALLOWED_ORIGIN` on Render and redeploy the backend** — the origin is
   baked in at build time, so this last step is easy to forget.
4. Smoke test `https://<your-vercel-domain>/api/health` indirectly by loading the
   app and registering a user.

### 4. GitHub access

Grant **`boltapp-hiring`** collaborator access:

**Settings → Collaborators → Add people → `boltapp-hiring`.**

This requires repository admin rights. It has **not** been performed from this
environment — no GitHub credentials were available, so no repository was created
or pushed. The CI workflow in `.github/workflows/ci.yml` is committed and will run
once the repository is pushed; it has not been executed remotely.

---

## Security Decisions

### SecureRandom

Login codes come from `java.security.SecureRandom` — a cryptographically strong
generator — formatted to exactly six digits with `String.format("%06d", …)`.
`Math.random()` is never used.

Verified by test: 2,000 consecutive registrations all produced exactly six
numeric digits.

### BCrypt

Only the hash is stored:

```
Generated OTP ──► BCryptPasswordEncoder.encode() ──► users.otp_hash
User code     ──► BCryptPasswordEncoder.matches()  ─► true / false
```

The plain code is returned in exactly one place — the registration response —
because the assignment requires it to be displayed. It is never written to the
database and never logged. `application.properties` sets
`server.error.include-stacktrace=never`, and the global handler logs unexpected
exceptions server-side only.

### Validation on both sides

Frontend validation is for fast feedback; it is never the security boundary. The
backend independently applies Jakarta Bean Validation (`@NotBlank`, `@Email`,
`@Size`, `@Pattern`) to every request and returns `400` with a field-level message.
`@RequestParam` and `@RequestBody` failures are handled separately, because
Spring raises different exception types for each.

### Email normalisation

`trim()` + `lowercase` at three layers (DTO setter, `@InitBinder`, service), so
`Vijay@Example.com` and `vijay@example.com` are one account — for registration,
recognition, verification and checkout.

### Environment variables

All credentials and deployment settings come from the environment. `.env` is
git-ignored, only `.env.example` is tracked, and no credential appears in any
committed file.

### CORS

Restricted to explicitly configured origins — the local Vite server in development
and the deployed Vercel domain in production. Wildcard `*` is never used in the
committed configuration, and `allowedHeaders` is an explicit list
(`Content-Type`, `X-Session-Token`) rather than `*`.

### Login-code limitations

A 6-digit code has a million possible values and the only protection applied here is a
BCrypt comparison per attempt. There is **no expiry, no attempt limit and no rate limit** —
these are listed in *Production Improvements* as deliberately out of scope for this
assignment rather than quietly assumed to be in place.

One consequence is worth stating plainly: a 6-digit code is brute-forceable in roughly a
million requests, which is minutes of traffic against an endpoint with no throttle. That is
acceptable for an assignment demonstrating the OTP flow, and it is the first thing to add
before this faced real users.

### Session token

A successful OTP verification returns a compact HMAC-SHA256 signed, 30-minute
token. The checkout endpoint derives `user_id` **from the signature**, never from
client input — a tampered or forged token is silently treated as a guest checkout
rather than granting access to another user's orders. This is a deliberately small
application-level mechanism; see *Production Improvements* for the full
session strategy a production system would need.

### What is intentionally not implemented

The following are acknowledged gaps, listed in *Production Improvements* rather than
claimed as features: OTP expiry, attempt limiting, rate limiting, session revocation,
audit logging, and account-enumeration protection.

---

## Production Improvements

**Not implemented.** These are deliberately left out of the assignment's scope —
each is noted so the trade-off is visible rather than hidden:

- **OTP expiry** — add `otp_expires_at` to `users`; generate with a 10-minute TTL.
  Currently a code stays valid until the next registration for that email.
- **Maximum OTP attempts** — a per-user attempt counter with a lockout window.
- **Rate limiting** — bucket limits on `/api/auth/verify` and `/api/auth/recognize`.
- **Token revocation** — the session token is stateless; add a denylist or move to
  server-side sessions if immediate logout is required.
- **Refresh flow** — the token expires after 30 minutes; a refresh path would let
  a signed-in user keep checking out without re-entering their code.
- **Server-side sessions** — replace the compact token with a session store if
  multi-device tracking or revocation is needed.
- **Account enumeration protection** — `/api/auth/recognize` and the `409` on
  registration both reveal whether an email exists. Note this one **cannot** be
  closed without changing the product: the checkout form is specified to branch
  on exactly that boolean to choose between the OTP modal and guest checkout.
- **Refresh the code on each login** — rotating the code per session would shorten
  the useful life of a leaked code. It is not done here because the assignment
  requires the code displayed at registration to keep working for "later checkout
  login", and there is no delivery channel to show a replacement.
- **Structured logging + monitoring** — request IDs, correlation IDs, metrics for
  verification failures and checkout latency. The production log pattern already
  prints a `%X{requestId}` field, so adding a filter that populates the MDC would
  complete it.
- **Audit logging** — record login attempts, successful verifications and checkouts.
- **HTTPS + secure headers** — HSTS, `X-Content-Type-Options`, CSP at the edge.
- **Database migration tool** — Flyway or Liquibase once more than one environment
  exists; `schema.sql` is fine for a single schema.
- **CI** — run `mvn test` and `npm test` on every push.
- **Pagination** — checkout history is currently unpaged.
- **Phone validation** — the backend only checks that the phone is present and
  bounded; stricter per-country rules belong behind a library.

---

## Screenshots

Captured from the running application in a real browser (Chromium, 1280×900 unless
noted), driving the **production frontend build** against a **real MySQL 8 database**:

| File | What it shows |
|---|---|
| `registration.png` | Empty registration form |
| `registration-success.png` | Generated 6-digit code displayed prominently |
| `otp-modal.png` | OTP modal for a recognised email |
| `invalid-otp.png` | Inline error after a wrong code; modal stays open |
| `logged-in-checkout.png` | "Welcome, Vijay Hosapeti" with data preserved |
| `checkout-success.png` | Success confirmation |
| `guest-checkout.png` | Guest checkout for an unknown email |
| `mobile-registration.png` | 390 px viewport |

---

## AI Assistance

Built with AI assistance under direct engineering supervision. The prompts actually
used are recorded in [`prompts.md`](prompts.md), including the fourteen concrete
bugs that were found by running the builds, tests and a real browser rather than by
reading the code — most notably a `SERIAL` vs `bigint` schema mismatch that would
have prevented the backend from starting in production, and a debounce that silently
disabled email recognition when a user cleared the field and retyped the same address.
