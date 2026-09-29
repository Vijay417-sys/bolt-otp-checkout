# Browser & database verification

Everything here needs a running application. For the static checks that do not,
run `./verify.sh` first.

Work top to bottom. Each section states exactly what to do and what a correct
result looks like, so you can tell "it works" from "it appeared to work".

---

## Part 0 — Start the stack

### The quick way — one command, one terminal

```bash
./run.sh
```

It checks the toolchain, applies the schema, starts the backend and frontend,
waits for both to answer, and prints the URL. Then:

```bash
./run.sh status   # what is running
./run.sh logs     # follow both logs
./run.sh stop     # shut down
```

Everything below is for when you want to run the services by hand, or to
diagnose something. Three terminals, or use `&`:

### Terminal 1 — MySQL

```bash
# Only needed once per machine.
mysql -u root -p <<'SQL'
CREATE DATABASE IF NOT EXISTS bolt_checkout
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'bolt'@'localhost' IDENTIFIED BY 'boltpw';
GRANT ALL PRIVILEGES ON bolt_checkout.* TO 'bolt'@'localhost';
FLUSH PRIVILEGES;
SQL

mysql -u root -p bolt_checkout < database/schema.sql
```

Expect no output — that is success. To prove the schema applied:

```bash
mysql -u bolt -pboltpw bolt_checkout -e "SHOW TABLES;"
```

Must print exactly `checkout_records` and `users`.

> Re-running the schema file is safe. Run it twice and you should still get no
> error, because every statement is guarded. That idempotency is load-bearing:
> the test suite applies the file on every Spring context startup.

### Terminal 2 — backend

**First check port 8080 is free.** If something else already has it, the backend
dies with `Web server failed to start. Port 8080 was already in use.`

```bash
ss -ltn | grep ':8080 ' || echo "8080 is free"
```

If it is taken, pick another port — `PORT=8090` below and remember to use it in
`VITE_API_BASE_URL` and `CORS_ALLOWED_ORIGIN` too.

```bash
cd backend
export DATABASE_URL="jdbc:mysql://localhost:3306/bolt_checkout?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"
export DATABASE_USERNAME=bolt
export DATABASE_PASSWORD=boltpw
export SESSION_TOKEN_SECRET="$(openssl rand -base64 48)"
export CORS_ALLOWED_ORIGIN=http://localhost:5173
mvn spring-boot:run
```

Wait for `Started CheckoutApplication`. **If it fails, read the actual error** —
the most likely causes are a `bolt` user that was never created, a wrong password,
or the port above being taken.

```bash
curl -s http://localhost:8080/api/health
```

Must print `{"status":"UP"}`. If you get HTML instead, something else owns 8080.

### Terminal 3 — frontend

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>.

> If the page loads but every API call fails with a CORS error, the backend's
> `CORS_ALLOWED_ORIGIN` does not match the port the frontend is actually on.
> Check the port Vite printed and set the variable to match exactly.

---

## Part 1 — The three user scenarios

### Scenario A — registered user (§47)

| # | Action | Correct result |
|---|---|---|
| 1 | Register `Vijay` / `Hosapeti` / `vijay@example.com` | "Registration successful" and a **6-digit code** |
| 2 | Write the code down | It is the only way to log in later |
| 3 | Click **Go to checkout** | Checkout form, email pre-filled |
| 4 | Type into the **Phone** field while the email is still settling | Your keystrokes register — the form is not blocked |
| 5 | Wait for recognition | "Account recognized", then the OTP modal opens |
| 6 | Enter **000000** and press Continue | Modal **stays open**, red "Invalid login code" inside it |
| 7 | Check the Phone field | Still contains what you typed — nothing was cleared |
| 8 | Enter the **real** code | Modal closes |
| 9 | Look at the top of the form | "Welcome, Vijay Hosapeti" |
| 10 | Check Phone and Address | Both preserved |
| 11 | Submit checkout | "Checkout submitted successfully!" |

### Scenario B — unknown user (§48)

1. Go to the **Checkout** tab, type `nobody@example.com`
2. "No account found. Continue as guest." — **no modal**
3. Fill phone + address, submit
4. Success message, and the row has `user_id = NULL`

### Scenario C — skip login (§49)

1. Type a **registered** email in checkout
2. When the modal opens, click **Skip login**
3. Modal closes, fields intact, top badge reads "Guest checkout"
4. An "Enter login code" link is offered in case they change their mind
5. Submit — succeeds as a guest

### The bug I fixed — re-recognise the same address

Worth testing, because it was broken and is now covered by a regression test.

1. On the Checkout tab, type a registered email, let the modal open
2. Click **Skip login**
3. Select the email field, delete everything, retype the **same** address
4. Recognition must run again: "Account recognized" reappears

If nothing happens, that regression is back.

---

## Part 2 — Errors (§50)

| Trigger | Expected |
|---|---|
| Register an email you already used | `409`, "Email is already registered" |
| Register with `not-an-email` | `400`, "Invalid email address" |
| Type an invalid email into checkout | Inline error **immediately**, no API call |
| Wrong OTP | `401`, modal stays open |
| Submit checkout with empty fields | `400`, each missing field named |
| Malformed JSON | `400`, no stack trace, no SQL text |

Confirm the backend never leaks internals:

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' -d '{"email":"bad","firstName":"","lastName":""}'
```

Should return JSON with `timestamp` / `status` / `message` / `path` — and no
`Exception`, no `org.springframework`, no SQL.

---

## Part 3 — API directly (curl)

```bash
# health
curl -s localhost:8080/api/health

# register — note the code
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"curl@example.com","firstName":"Curl","lastName":"Test"}'

# recognition is case-insensitive
curl -s "localhost:8080/api/auth/recognize?email=CURL@EXAMPLE.COM"   # registered:true
curl -s "localhost:8080/api/auth/recognize?email=ghost@example.com"   # registered:false

# duplicate -> 409
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"curl@example.com","firstName":"A","lastName":"B"}'

# wrong OTP -> 401
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/auth/verify \
  -H 'Content-Type: application/json' -d '{"email":"curl@example.com","code":"000000"}'

# malformed OTP -> 400
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/auth/verify \
  -H 'Content-Type: application/json' -d '{"email":"curl@example.com","code":"12"}'
```

### The code must keep working

The assignment requires the code shown at registration to be usable **later**.
Verify the same code a second time returns `200` — if this ever starts failing,
something has added a single-use or expiry rule that the spec does not ask for.

```bash
CODE=482193   # whatever you registered
curl -s -o /dev/null -w 'first:  %{http_code}\n' -X POST localhost:8080/api/auth/verify \
  -H 'Content-Type: application/json' -d "{\"email\":\"curl@example.com\",\"code\":\"$CODE\"}"
curl -s -o /dev/null -w 'again:  %{http_code}\n' -X POST localhost:8080/api/auth/verify \
  -H 'Content-Type: application/json' -d "{\"email\":\"curl@example.com\",\"code\":\"$CODE\"}"
```

Both must be `200`.

---

## Part 4 — Database proof (§73)

```bash
mysql -u bolt -pboltpw bolt_checkout -e "SELECT id, email, LEFT(otp_hash,7) AS hash,
  LENGTH(otp_hash) AS len FROM users;"
```

- `hash` starts with `$2a$10$`
- `len` is **60**
- the email is stored lowercase and trimmed

```bash
mysql -u bolt -pboltpw bolt_checkout -e "SELECT id, user_id, email, phone
  FROM checkout_records ORDER BY id;"
```

- your logged-in order has a `user_id`
- your guest orders have `NULL`
- emails are lowercase

```bash
# The critical one: no plaintext OTP anywhere.
mysql -u bolt -pboltpw bolt_checkout -e "SELECT COUNT(*) AS six_char_hashes
  FROM users WHERE LENGTH(otp_hash) = 6;"
```

Must be **0**.

```bash
# Deleting the user must NOT destroy order history (§11).
mysql -u bolt -pboltpw bolt_checkout -e "
  DELETE FROM users WHERE email='curl@example.com';
  SELECT id, user_id, email FROM checkout_records WHERE email='curl@example.com';"
```

The order row must still be there, with `user_id` now `NULL`.

### Constraints actually bite

```bash
# unique email — pick an address that already EXISTS in `users`
mysql -u bolt -pboltpw bolt_checkout -e \
  "INSERT INTO users (email,first_name,last_name,otp_hash) VALUES ('curl@example.com','A','B','x');"
```

Expect `ERROR 1062 ... Duplicate entry`. This is what stops two simultaneous
registrations for the same address.

> Check `SELECT id, email FROM users;` first — a **guest checkout** email is *not*
> in `users`, so inserting it will succeed and prove nothing.

Because the column collation is `utf8mb4_unicode_ci`, an address differing only
in case is also a duplicate, which matches how the application normalises email
before it ever gets here.

```bash
# NOT NULL
mysql -u bolt -pboltpw bolt_checkout -e \
  "INSERT INTO users (email,first_name,last_name,otp_hash) VALUES (NULL,'A','B','x');"
```

Expect `ERROR 1048 ... cannot be null`.

---

## Part 5 — Automated suites

```bash
cd backend && mvn clean test     # expect: 38 tests, 0 failures
cd frontend && npm test          # expect: 61 tests, 0 failures
```

> The backend suite loads the **real** `database/schema.sql` into H2 in MySQL
> mode and runs `ddl-auto=validate`. If the schema and the JPA entities ever drift
> apart, these tests fail. That is deliberate — it is how the original
> `SERIAL`/`bigint` mismatch was caught.

Production builds:

```bash
cd frontend && npm run build     # must succeed
cd backend  && mvn clean package # must succeed
```

Optional: test the built frontend rather than the dev server.

```bash
cd frontend
VITE_API_BASE_URL=http://localhost:8080 npm run build
npx vite preview --port 4173
# backend: export CORS_ALLOWED_ORIGIN=http://localhost:4173 and restart
```

---

## Part 6 — Docker (§67)

```bash
docker compose up --build
```

- frontend <http://localhost:5173>
- API <http://localhost:8080>
- MySQL on `3306`

```bash
curl -s localhost:8080/api/health   # {"status":"UP"}
```

Watch the first 60 seconds: `bolt-db` runs the schema as an init script, then
`bolt-backend` starts only after the database reports healthy. Then repeat
Scenarios A and B in the browser.

Tear down including the volume:

```bash
docker compose down -v
```

> I could not run any of this — the Docker daemon is not reachable from my
> shell. Treat Part 6 as genuinely unverified.

---

## Part 7 — Console and network (§65)

Open DevTools → Console and Network, then walk Scenario A.

- Console: clean. A `favicon.ico` 404 is harmless.
- Network: `recognize` should fire **once** after you pause, not per keystroke.
- The only `4xx` should be the deliberate `401` from the wrong-code attempt.
- A second `recognize` for a single address is expected only if you edited the
  field again.

Press `F12` and confirm no `401` appears anywhere except that one deliberate
attempt, and no `500` ever.

---

## Part 8 — Deployment (only after local is green)

1. **Database** — provision MySQL 8, run `database/schema.sql`, create a least-privilege user
2. **Backend on Render** — Dockerfile at repo root or `backend/Dockerfile`; set
   `DATABASE_URL` (with `?useSSL=true&serverTimezone=UTC`), `DATABASE_USERNAME`,
   `DATABASE_PASSWORD`, `CORS_ALLOWED_ORIGIN`, `SESSION_TOKEN_SECRET`
   (`openssl rand -base64 48`). Health check path `/api/health`
3. **Frontend on Vercel** — root directory `frontend`, set `VITE_API_BASE_URL`
   to the Render URL
4. **Deploy the backend again** after step 3, with the Vercel origin added to
   `CORS_ALLOWED_ORIGIN`. This is the step everyone forgets, and the app will
   fail every request until you do it.
5. Push to GitHub and invite `boltapp-hiring` as a collaborator

Verify the deployed URL end to end, not just `/api/health`.

---

## Quick reference — what each symptom means

| Symptom | Likely cause |
|---|---|
| Backend won't start, `Access denied` | `bolt` user not created, or wrong password |
| Backend won't start, `Table ... doesn't exist` | `schema.sql` not applied |
| Browser shows "Unable to reach the server" | CORS origin mismatch, or backend not running |
| Modal never opens | Email invalid, or the re-recognise regression is back |
| "Account recognized" but no modal | Already skipped login for this address — use "Enter login code" |
| `ddl-auto=validate` fails on boot | Schema and entities have drifted; re-apply `schema.sql` |
| Everything works locally, fails on Render | `CORS_ALLOWED_ORIGIN` missing the Vercel origin |
