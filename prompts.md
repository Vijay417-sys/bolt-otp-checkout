# LLM Prompts Used During Development

This file records the prompts actually used while building the Bolt OTP Checkout
take-home assignment. Nothing here is invented — each entry corresponds to a real
request made during development.

---

## Prompt 1 — Master Project Build Prompt

The initial prompt (abbreviated to the operative instructions; the full text
followed the structure below):

> You are the primary senior full-stack engineer responsible for completing this
> take-home assignment end-to-end.
>
> Your job is NOT to only provide suggestions, explanations, or code snippets.
>
> You must inspect the workspace, understand the assignment, create the complete
> application, create/update all required files, run the application, execute
> tests, verify the complete user flows, identify and fix errors, prepare
> deployment configuration, update documentation, and leave the repository in a
> submission-ready state.
>
> Do not stop after generating files. Actually execute the commands. Actually test
> the application. Actually fix errors. Do not merely tell me what should be done.
>
> Key constraints from the assignment:
> - Registration flow: email, first name, last name → generate a random 6-digit
>   numeric code → display it on screen. No email/SMS delivery.
> - Recognition & login flow: checkout collects email, phone, shipping address.
>   Real-time email validation, debounced background recognition, OTP modal with
>   Skip login, guest checkout, PostgreSQL persistence.
> - Frontend: React + JavaScript ES6+ + Vite + Tailwind CSS. NO TypeScript — no
>   .ts/.tsx files, no tsconfig.json.
> - Backend: Java 21, Spring Boot 3.x, Spring Data JPA, Jakarta Bean Validation,
>   BCrypt via Spring Security Crypto, PostgreSQL. Clean controller/service/
>   repository layering. Do not introduce full Spring Security auth infra.
> - Deployment target: Vercel (frontend), Render + Docker (backend),
>   Supabase PostgreSQL (database). Environment variables, no hardcoded secrets.

---

## Prompt 2 — Continue the Interrupted Backend Build

> complete previous one

Response: the agent resumed the Maven build that had failed on a missing
`ErrorResponse` import in `GlobalExceptionHandler` and continued implementation.

---

## Prompt 3 — Continue After the Handoff Point

> complete the requiremnt accorind to what i given

Response: the agent resumed the remaining work (Docker build verification,
documentation, prompts.md, README, deployment configuration, secret scanning and
the final engineering report) without restarting the task.

---

## Prompt 4 — Migrate the Database from PostgreSQL to MySQL

> insted of using postgrace sql use my sql remove the postgrace sql and complete
> the what are the things are pending

Response: the backend, schema, Docker configuration and documentation were moved
to MySQL 8 end to end.

> **Note on Prompts 1–3.** Those are recorded verbatim, so they still say
> PostgreSQL — that was the original assignment. Prompt 4 superseded the database
> choice and the delivered application now targets MySQL 8. The quoted text is
> left as-is because this file is a record of what was actually asked.

### What the migration touched

| Area | Change |
|---|---|
| `database/schema.sql` | `BIGSERIAL`/`TIMESTAMP`/`TEXT` → `AUTO_INCREMENT`/`DATETIME`/`VARCHAR(1000)`; added `ENGINE = InnoDB` and `utf8mb4_unicode_ci`; dropped the redundant `idx_users_email` |
| `backend/pom.xml` | `org.postgresql:postgresql` → `com.mysql:mysql-connector-j` |
| `application.properties` | `jdbc:postgresql://…` → `jdbc:mysql://…`; driver → `com.mysql.cj.jdbc.Driver`; dialect → `MySQLDialect` |
| `application-test.properties` | H2 `MODE=PostgreSQL` → `MODE=MySQL` |
| `docker-compose.yml` | `postgres:16-alpine` → `mysql:8.4`; `POSTGRES_*` → `MYSQL_*`; healthcheck → `mysqladmin ping`; volume → `mysqldata`; added a non-root app user |
| `backend/.env.example` | Rewrote connection-string guidance for MySQL 8 |
| `backend/Dockerfile`, `frontend/src/App.jsx` | Stale "PostgreSQL" references |
| `README.md` | Overview, architecture diagram, local setup, schema docs, deployment |

### Two MySQL-specific issues found while doing it

| # | Issue | Why it matters | Fix |
|---|-------|----------------|-----|
| 12 | MySQL has no `CREATE INDEX IF NOT EXISTS`, so a standalone-index schema is not idempotent — a second run (e.g. a second Spring context in the test suite) fails on duplicate indexes | The test profile applies `schema.sql` on every context startup | Declared all indexes inline inside `CREATE TABLE`, so the existing `IF NOT EXISTS` guard covers them |
| 13 | `docker-compose.yml` defaulted `SESSION_TOKEN_SECRET` to `local-dev-secret-…` while the application guarded against `local-dev-only-change-me-…` | A deployment that inherited the compose default and ran the `prod` profile would have **passed** the fail-fast guard while signing tokens with a publicly known secret | Made the compose default match the application's dev default exactly, so the guard fires |

Bug #12 is also why `SessionTokenSecretGuardTest` no longer carries a
`@TestPropertySource`: a second Spring context would re-run `schema.sql` and fail.
The secret under test is passed straight to the constructor instead.

### One frontend bug found by driving the real application

| # | Bug | How it was found | Fix |
|---|-----|------------------|-----|
| 14 | **Clearing the email field and retyping the same address killed recognition permanently.** `useDebounce` publishes the settled value, and React cannot tell "set to the value it already holds" from "never changed", so the recognition effect never re-ran. The status line went blank and the OTP modal stopped appearing for that email — with no way to log in. | Driving all three assignment scenarios in a real browser against a real MySQL instance; the modal simply did not open | `useDebounce` now takes an `onSettle` callback, and `CheckoutForm` keys recognition off a counter that increments on each settle |

The same browser pass also found that an invalid email produced **no feedback at all**
until submit, which §23 requires in real time. The checkout email field now shows the
format error as soon as something has been typed.

### One documentation defect fixed

`openapi.yaml` declared `"openapi": "2.0.0"` (Swagger 2.0) while being written in
JSON syntax despite the `.yaml` extension, and `openapi.json` was OpenAPI 3.0.3. The
README pointed at both as the machine-readable spec, and they disagreed. Both are now
OpenAPI 3.0.3, and the YAML is generated from the JSON so they cannot drift apart.

---

## Notes

Development was largely **agent-driven**: the master prompt above was executed
directly, and the agent wrote the code, ran the builds, and fixed the failures
that surfaced. The other two entries above are the literal follow-up messages
sent to resume the work after the session was interrupted.

### Bugs found by actually running things

These are recorded here because they are the substance of the "debugging" prompt
category — each was discovered by executing a command, not by reading code:

| # | Bug | How it was found | Fix |
|---|-----|------------------|-----|
| 1 | `GlobalExceptionHandler` referenced `ErrorResponse` without importing it | `mvn clean compile` failed | Added `com.bolt.checkout.dto.ErrorResponse` import |
| 2 | `SessionTokenService` passed a `String` where `byte[]` was required | `mvn clean compile` failed | Encoded the payload to UTF-8 bytes before Base64 |
| 3 | Email validation ran *before* trimming, so `" vijay@example.com "` returned 400 | Backend test `emailIsNormalizedOnRegistration` | Trim in the DTO setter + `@InitBinder StringTrimmerEditor` |
| 4 | `ConstraintViolationException` from `@RequestParam` validation fell through to a **500** | Backend test `registeredEmailRecognized` | Added a dedicated `ConstraintViolationException` handler returning 400 |
| 5 | **`SERIAL` vs `bigint` mismatch** — `ddl-auto=validate` rejected the real schema | Backend test run against `database/schema.sql` | Changed `SERIAL`→`BIGSERIAL` and `user_id INTEGER`→`BIGINT` in `schema.sql` |
| 6 | OTP boxes collapsed the whole code on each keystroke | Frontend test `allows at most six digits` | Rewrote to index-based `setDigitAt`, `maxLength={1}`, dedicated `onPaste` |
| 7 | Typing a 2nd digit into an occupied OTP box was misread as a paste | Playwright browser run | Same fix as #6 |
| 8 | After a failed OTP, focus was lost because `focus()` ran while inputs were `disabled` | Playwright browser run | `pendingFocusRef` + effect that focuses after re-enable |
| 9 | "Go to checkout" prefilled the **OTP code** into the email field | Playwright browser run | Pass the normalised email to `onRegistered` |
| 10 | Browser preflight returned **403 Invalid CORS request** | Playwright browser run | A stale JAR was still running; rebuilt and restarted with `CORS_ALLOWED_ORIGIN` |
| 11 | `SessionTokenService` allowed the dev-default signing secret on the prod profile | Self-review of the security posture | Fail-fast guard that refuses to start when `prod` is active and the default is in use |

Bug #5 is the most significant: it would have made the application **fail to
start in production**, because `ddl-auto=validate` compares the JPA entities
against the real database schema on boot.
