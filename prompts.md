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
| 10 | Browser preflight returned **403 Invalid CORS request** | Playwright browser run | Stale JAR was running; rebuilt and restarted with `CORS_ALLOWED_ORIGIN` |

Bug #5 is the most significant: it would have made the application **fail to
start in production**, because `ddl-auto=validate` compares the JPA entities
against the real PostgreSQL schema on boot.
