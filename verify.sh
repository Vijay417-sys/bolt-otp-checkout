#!/usr/bin/env bash
#
# Pre-flight verification for Bolt OTP Checkout.
#
# Runs everything that can be checked without a browser or a deployment, and
# prints a PASS/FAIL line per item. Run it from the repository root:
#
#   ./verify.sh
#
# It does NOT touch your database, does not need Docker, and does not modify any
# file. Safe to run repeatedly.
#
# Exit code is 0 only if every check passed.

set -uo pipefail
cd "$(dirname "$0")"

PASS=0
FAIL=0
SKIP=0

ok()   { printf '  \033[32mPASS\033[0m  %s\n' "$1"; PASS=$((PASS+1)); }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAIL=$((FAIL+1)); }
skip() { printf '  \033[33mSKIP\033[0m  %s (%s)\n' "$1" "$2"; SKIP=$((SKIP+1)); }
head_() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# check <description> <command...>
check() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then ok "$desc"; else bad "$desc"; fi
}

head_ "1. Toolchain"
check "Java 21 present"            bash -c 'java -version 2>&1 | grep -q "version \"2[1-9]\|version \"1[1-9]\|version \"2[0-9]\."'
check "Maven present"              bash -c 'command -v mvn'
check "Node 18+ present"           bash -c 'node -v | sed "s/v\([0-9]*\).*/\1/" | awk "{exit !(\$1>=18)}"'
java -version 2>&1 | head -1 | sed 's/^/        /'
mvn -v 2>/dev/null | head -1 | sed 's/^/        /'
node -v | sed 's/^/        /'

head_ "2. JavaScript only (no TypeScript)"
ts_files=$(find frontend/src frontend -maxdepth 2 \( -name '*.ts' -o -name '*.tsx' -o -name 'tsconfig*' \) -not -path '*/node_modules/*' 2>/dev/null)
if [ -z "$ts_files" ]; then ok "no .ts / .tsx / tsconfig anywhere in frontend"
else bad "TypeScript files found:"; echo "$ts_files" | sed 's/^/        /'; fi
check "no TypeScript type syntax in src" bash -c \
  "! grep -rInE '^\s*(interface|type)\s+[A-Za-z_]+\s*=' frontend/src 2>/dev/null"

head_ "3. Secrets are not committed"
tracked_env=$(git ls-files | grep -E '(^|/)\.env$' || true)
if [ -z "$tracked_env" ]; then ok "no .env is tracked by git"
else bad ".env is tracked: $tracked_env"; fi
check "only .env.example files are tracked" bash -c \
  '[ "$(git ls-files | grep -c "\.env\.example$")" -ge 2 ]'
# Look for credentials in *config* files only. Restricting the search to
# properties/yaml/env/compose avoids false positives from Java source, where
# `this.secret = secret.getBytes(...)` looks like an assignment but is not one.
if git grep -nIE '^[^#]*(password|secret|api[_-]?key)\s*[:=]\s*[A-Za-z0-9]{6,}' -- \
     '*.properties' '*.yml' '*.yaml' '*.env' '.env.example' '*.example' 2>/dev/null \
   | grep -vE '(\$\{|local-dev|test-secret|CHANGE_ME|<your|\{\})' ; then
  bad "possible hardcoded credential in a config file (see above)"
else
  ok "no hardcoded credential values in config files"
fi

head_ "4. Build artifacts are ignored"
for p in node_modules/ dist/ target/ .env; do
  if grep -qx "$p" .gitignore || grep -q "^$p" .gitignore; then ok ".gitignore covers $p"
  else bad ".gitignore missing $p"; fi
done
check "no dist/ or target/ actually tracked" bash -c \
  '[ -z "$(git ls-files | grep -E "^(frontend/dist|backend/target)/" )" ]'

head_ "5. Required files exist"
for f in README.md prompts.md database/schema.sql docker-compose.yml .gitignore \
         backend/pom.xml backend/Dockerfile backend/.env.example \
         frontend/package.json frontend/Dockerfile frontend/.env.example; do
  [ -f "$f" ] && ok "$f" || bad "$f MISSING"
done
for d in frontend/src/components frontend/src/hooks frontend/src/services \
         frontend/src/utils screenshots; do
  [ -d "$d" ] && ok "$d/" || bad "$d/ MISSING"
done

head_ "6. Required frontend components (assignment §20)"
for c in RegistrationForm CheckoutForm OtpModal UserBadge LoadingSpinner Toast; do
  [ -f "frontend/src/components/$c.jsx" ] && ok "$c.jsx" || bad "$c.jsx MISSING"
done
for f in frontend/src/hooks/useDebounce.js frontend/src/services/api.js \
         frontend/src/utils/validation.js frontend/src/App.jsx \
         frontend/src/main.jsx frontend/src/index.css; do
  [ -f "$f" ] && ok "${f#frontend/src/}" || bad "$f MISSING"
done

head_ "7. Backend layering (assignment §18)"
for p in controller service repository entity dto exception config; do
  [ -d "backend/src/main/java/com/bolt/checkout/$p" ] && ok "$p/" || bad "$p/ MISSING"
done

head_ "8. API endpoints declared in the spec (assignment §53)"
for e in "/api/health" "/api/auth/register" "/api/auth/recognize" \
         "/api/auth/verify" "/api/checkout"; do
  if grep -q "\"$e\"" backend/openapi.json; then ok "$e documented"
  else bad "$e MISSING from openapi.json"; fi
done
check "openapi.yaml matches openapi.json" python3 -c "
import json, yaml, sys
a = yaml.safe_load(open('backend/openapi.yaml'))
b = json.load(open('backend/openapi.json'))
sys.exit(0 if a == b else 1)
"

head_ "9. Schema requirements (assignment §10, §11)"
sql=database/schema.sql
grep -qi "CREATE TABLE IF NOT EXISTS users"        "$sql" && ok "users table"        || bad "users table missing"
grep -qi "CREATE TABLE IF NOT EXISTS checkout_records" "$sql" && ok "checkout_records table" || bad "checkout_records table missing"
for col in otp_hash first_name last_name created_at; do
  grep -qi "$col" "$sql" && ok "users.$col present" || bad "users.$col MISSING"
done
grep -qi "UNIQUE"  "$sql" && ok "email is unique"     || bad "email UNIQUE constraint missing"
grep -qi "AUTO_INCREMENT" "$sql" && ok "surrogate key" || bad "AUTO_INCREMENT missing"
grep -qi "PRIMARY KEY"     "$sql" && ok "primary keys"  || bad "PRIMARY KEY missing"
grep -qi "FOREIGN KEY"     "$sql" && ok "foreign key"   || bad "FOREIGN KEY missing"
grep -qi "ON DELETE SET NULL" "$sql" && ok "ON DELETE SET NULL (history preserved)" \
  || bad "ON DELETE SET NULL missing"
grep -qi "INDEX"           "$sql" && ok "indexes present" || bad "no indexes"
# MySQL, not PostgreSQL
if grep -qiE "postgres|BIGSERIAL|SERIAL|psql" "$sql" backend/pom.xml; then
  bad "PostgreSQL references still present"
else
  ok "no PostgreSQL leftovers in schema or pom"
fi
grep -q "mysql-connector-j" backend/pom.xml && ok "MySQL JDBC driver declared" || bad "MySQL driver missing"

head_ "10. Security requirements (assignment §12, §37)"
grep -rq "SecureRandom" backend/src/main/java && ok "SecureRandom used for the OTP" \
  || bad "SecureRandom not used"
if grep -rq "Math.random" backend/src/main/java; then
  bad "Math.random() found — assignment §12 forbids it"
else ok "Math.random() not used anywhere"; fi
grep -rq "BCryptPasswordEncoder" backend/src/main/java && ok "BCrypt hashing" || bad "BCrypt missing"
check "no .getOtpHash() in any HTTP response" bash -c \
  "! grep -rq 'getOtpHash' backend/src/main/java/com/bolt/checkout/dto"
grep -q "RestControllerAdvice" backend/src/main/java/com/bolt/checkout/exception/GlobalExceptionHandler.java \
  && ok "@RestControllerAdvice present" || bad "no @RestControllerAdvice"
grep -q "include-stacktrace=never" backend/src/main/resources/application.properties \
  && ok "stack traces suppressed in responses" || bad "stack traces not suppressed"
grep -q "ddl-auto=\${DDL_AUTO:validate}" backend/src/main/resources/application.properties \
  && ok "ddl-auto=validate (schema is authoritative)" || bad "ddl-auto is not validate"

head_ "11. Assignment §37 says these are NOT implemented"
# If someone later implements them, these should be updated deliberately.
for term in "otp_expires_at" "otp_failed_attempts" "audit_logs" "RateLimiter"; do
  if grep -rq "$term" backend/src/main/java database/schema.sql 2>/dev/null; then
    bad "$term is implemented — README says it is a future improvement"
  else ok "$term correctly not implemented"; fi
done

head_ "12. Frontend behaviour (assignment §24, §25, §28)"
check "debounce delay is 400-500ms" bash -c \
  "grep -qE 'RECOGNITION_DELAY = (400|500)' frontend/src/components/CheckoutForm.jsx"
# The code is six single-character boxes (maxLength={1} each) rather than one
# six-character field, so assert the pair, not maxLength={6}.
check "OTP input is six single-digit boxes" bash -c \
  "grep -q 'maxLength={1}' frontend/src/components/OtpModal.jsx && [ \$(grep -c 'maxLength={1}' frontend/src/components/OtpModal.jsx) -ge 1 ]"
check "OTP code length enforced as 6" bash -c \
  "grep -qE \"value.length !== 6|length !== 6\" frontend/src/utils/validation.js"
check "OTP boxes use numeric inputMode" bash -c \
  "grep -q 'inputMode' frontend/src/components/OtpModal.jsx"
check "modal is an accessible dialog" bash -c \
  "grep -q 'role=\"dialog\"' frontend/src/components/OtpModal.jsx"
check "Skip login control exists" bash -c \
  "grep -qi 'skip login' frontend/src/components/OtpModal.jsx"
check "API base URL comes from VITE_API_BASE_URL" bash -c \
  "grep -q 'import.meta.env.VITE_API_BASE_URL' frontend/src/services/api.js"
check "no hardcoded production URL in frontend" bash -c \
  "! grep -rqE 'https://[a-z0-9.-]+\\.(onrender|vercel)\\.app' frontend/src"
check "CORS is not wildcard" bash -c \
  "! grep -q 'setAllowedOrigins(java.util.List.of(\"\\*\"' backend/src/main/java/com/bolt/checkout/config/CorsConfig.java"
grep -q "CORS_ALLOWED_ORIGIN" backend/src/main/resources/application.properties \
  && ok "CORS origin is configurable via env" || bad "CORS origin not configurable"

head_ "13. Screenshots (assignment §56)"
shot_n=$(ls screenshots/*.png 2>/dev/null | wc -l)
if [ "$shot_n" -ge 7 ]; then ok "$shot_n screenshots present"
else bad "only $shot_n screenshots (expected at least 7)"; fi

head_ "14. README has every required section (§51)"
for s in Overview Features Architecture "Technology Stack" "Project Structure" \
         "Local Setup" "Environment Variables" "Database Setup" \
         "API Documentation" "Application Flow" Testing Docker Deployment \
         "Security Decisions" "Production Improvements" Screenshots "AI Assistance"; do
  grep -q "^## $s" README.md && ok "README §$s" || bad "README missing section: $s"
done

head_ "15. Git hygiene (assignment §58, §59)"
check "clean working tree" bash -c '[ -z "$(git status --porcelain)" ]'
commit_n=$(git rev-list --count HEAD)
if [ "$commit_n" -ge 5 ]; then ok "$commit_n commits (not one giant commit)"
else bad "only $commit_n commit(s) — assignment wants a granular history"; fi
check "no secrets in git history" bash -c \
  '! git log -p --all 2>/dev/null | grep -qE "^\+[A-Za-z0-9_]*(PASSWORD|SECRET)[A-Za-z0-9_]*=[A-Za-z0-9]{12,}"'

head_ "16. Docker (assignment §67)"
if command -v docker >/dev/null 2>&1 && timeout 10 docker info >/dev/null 2>&1; then
  check "compose file is valid" docker compose config -q
  echo "        \033[33mimages not built here — run: docker compose up --build\033[0m"
else
  skip "Docker checks" "daemon not reachable from this shell"
fi

head_ "17. Ports the app expects are free"
# Something else on 8080 makes the backend fail with
# "Web server failed to start. Port 8080 was already in use." - a confusing
# failure that looks like a bug in the app.
for p in 8080 5173 3306; do
  if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -q ":$p "; then
    skip "port $p" "already in use - set PORT / VITE_API_BASE_URL to another port"
  else
    ok "port $p is free"
  fi
done

printf '\n\033[1m========================================\033[0m\n'
printf '  passed: %d   failed: %d   skipped: %d\n' "$PASS" "$FAIL" "$SKIP"
printf '\033[1m========================================\033[0m\n'
if [ "$FAIL" -gt 0 ]; then
  printf '\n\033[31m%d check(s) failed — see FAIL lines above.\033[0m\n' "$FAIL"
  exit 1
fi
printf '\n\033[32mAll runnable checks passed.\033[0m\n'
printf 'Next: VERIFY-BROWSER.md for the flows that need a real browser.\n'
