# Deployment guide

The assignment asks for the app to be **deployed**. This is the order to do it in,
with the traps called out. Follow it top to bottom — the order matters, because
each step needs the URL from the previous one.

This describes the deployment that is **live**. For the running application see the
[Live Demo](README.md#live-demo) section of the README.

```
Render Static Site  ──HTTPS──►  Render Web Service  ──JDBC──►  Aiven MySQL
   (React, Vite)                   (Spring Boot, Docker)
```

| Component | Platform | URL |
|---|---|---|
| Frontend | Render Static Site | https://bolt-otp-checkout-web.onrender.com |
| Backend | Render Web Service (Docker) | https://bolt-otp-checkout-api.onrender.com |
| Database | Aiven MySQL (`defaultdb`) | — |

> **On the database choice.** The assignment recommended PostgreSQL (via Supabase).
> Supabase hosts PostgreSQL only and cannot serve MySQL, so this project uses **MySQL**
> on Aiven instead. The assignment permitted using the technology the candidate was
> comfortable with; only the persistence layer differs.

---

## Step 0 — Push to GitHub first

Render deploys from a Git repository, so this has to happen first. It is already done
for the live deployment; the commands are kept for reference.

```bash
cd /home/vijay-hosapeti/Desktop/BOLT/bolt-otp-checkout
git remote add origin https://github.com/Vijay417-sys/bolt-otp-checkout.git
git push -u origin main
```

Create the repo on github.com first (empty, **no** README or .gitignore — this
project already has them).

Then invite the reviewer:

**Repository → Settings → Collaborators → Add people → `boltapp-hiring`**

> Nothing has been pushed from this environment — there were no GitHub credentials.
> No URL below is real; each is a placeholder for the one you get.

---

## Step 1 — Database

1. Create a MySQL 8 database and note: **host, port, database name, username,
   password**.
2. Apply the schema. Use the web SQL console, or from your machine:

```bash
mysql -h <host> -P <port> -u <user> -p <database> < database/schema.sql
```

No output means success. Confirm:

```bash
mysql -h <host> -P <port> -u <user> -p -e "SHOW TABLES FROM <database>;"
```

Must print `checkout_records` and `users`.

3. Find your provider's **connection limits**. Render opens several connections
   (default pool size 5). If your plan allows only a few, set `DB_POOL_SIZE=2`.

---

## Step 2 — Backend on Render

1. **New → Web Service** → connect the repo.
2. Because the Dockerfile is inside `backend/`, set:
   - **Root Directory**: `backend`
   - **Dockerfile Path**: `./Dockerfile`
   - *(or leave Root Directory blank if Render detects `backend/Dockerfile` itself)*
3. **Environment** — add all five, marking the secret ones *Secret*:

| Key | Value |
|---|---|
| `DATABASE_URL` | `jdbc:mysql://<host>:3306/<database>?useSSL=true&serverTimezone=UTC` |
| `DATABASE_USERNAME` | your MySQL user |
| `DATABASE_PASSWORD` | your MySQL password |
| `CORS_ALLOWED_ORIGIN` | `http://localhost:5173` **for now** — step 4 replaces this |
| `SESSION_TOKEN_SECRET` | `openssl rand -base64 48` |

> **SSL:** most managed MySQL terminates TLS, so `useSSL=true`. If yours does not,
> add `allowPublicKeyRetrieval=true` — MySQL 8's `caching_sha2_password` needs it on
> a plaintext connection.

4. **Health Check Path**: `/api/health`
5. Deploy. Note the URL: `https://<your-service>.onrender.com`

**Verify before moving on:**

```bash
curl -s https://<your-service>.onrender.com/api/health
```

Must print `{"status":"UP"}`.

> Free Render instances **sleep after 15 minutes idle** and take ~50 s to wake. Your
> first browser request may hang briefly. That is the platform, not your code.

---

## Step 3 — Frontend as a Render Static Site

1. **New → Static Site** → connect the same repository.
2. **Root Directory**: `frontend`
3. Build command `npm run build`, publish directory `dist`.
4. **Environment Variables**:

| Key | Value |
|---|---|
| `VITE_API_BASE_URL` | `https://bolt-otp-checkout-api.onrender.com` — **no trailing slash** |

5. Deploy. The live site is https://bolt-otp-checkout-web.onrender.com

> `VITE_*` values are **baked in at build time**. Changing it later needs a
> redeploy, not just a save.

---

## Step 4 — Fix CORS (the step everyone forgets)

The frontend origin did not exist when `CORS_ALLOWED_ORIGIN` was first set on the
backend, so the deployed app cannot call the API until this is done. Every request
fails with a CORS error.

1. On Render, edit the backend's env var:
   `CORS_ALLOWED_ORIGIN` = `https://bolt-otp-checkout-web.onrender.com`
2. **Save and redeploy the backend.**

Multiple origins are comma-separated.

---

## Step 5 — Verify the deployed app

Work through all three, on the deployed URL, not localhost:

| Check | Expected |
|---|---|
| `curl -s https://<backend>/api/health` | `{"status":"UP"}` |
| Register a user | 6-digit code appears |
| Checkout with the same email | OTP modal opens after the debounce |
| Wrong code | modal stays open, inline error |
| Correct code | modal closes, "Welcome, First Last" |
| Skip login | fields preserved, submits as guest |
| Unknown email | no modal, "Continue as guest", submits |
| Browser console | clean, except a possible favicon 404 |
| Network tab | only non-2xx is the deliberate 401 from the wrong-code test |

---

## Step 6 — Prove the database is real

```bash
mysql -h <host> -P <port> -u <user> -p <database> -e "SELECT id, user_id, email, created_at FROM checkout_records ORDER BY id DESC LIMIT 5;"
```

Rows must appear from your browser test. Confirm the security requirements held:

```bash
mysql -h <host> -P <port> -u <user> -p <database> < database/verify-checkout.sql
```

Or run `database/verify-checkout.sql` against the deployed database — every check
should return the value in its comment.

---

## Step 7 — Tidy before submitting

- [ ] `./verify.sh` passes
- [ ] README says which database provider you used, and why
- [ ] Screenshots are from the deployed app
- [ ] `boltapp-hiring` invited
- [ ] No `.env` committed: `git ls-files | grep '\.env$'` must be empty
- [ ] Deployed URL added to the README

---

## If something fails

| Symptom | Cause | Fix |
|---|---|---|
| Frontend says "Unable to reach the server" | `VITE_API_BASE_URL` wrong, or the backend is asleep | Check the value has no trailing slash; wake the Render service |
| Every API call fails CORS | Step 4 skipped | Add the frontend origin to `CORS_ALLOWED_ORIGIN`, redeploy |
| Backend won't start | Bad `DATABASE_URL` or credentials | Read the Render log; check the JDBC params |
| Backend exits at once on `prod` | `SESSION_TOKEN_SECRET` left at the dev default | Generate a real one — the app refuses to start otherwise |
| `Table 'bolt_checkout.users' doesn't exist` | Schema not applied | Run step 1.2 against the deployed database |
| First request hangs ~50 s | Free Render instance woke up | Expected; it warms up |
| Changes not showing | `VITE_*` baked at build | Redeploy the static site, don't just save |
| `ddl-auto=validate` failure | Schema and entities drifted | Re-apply `schema.sql`; do not set `ddl-auto=update` |
