#!/usr/bin/env bash
#
# One-command runner for Bolt OTP Checkout.
#
#   ./run.sh            start database check, backend and frontend
#   ./run.sh stop       stop everything this script started
#   ./run.sh status     show what is running
#   ./run.sh logs       follow backend and frontend logs
#   ./run.sh restart    stop then start
#
# No second terminal needed. Output goes to .run/ and the process keeps running
# after you close this window.
#
# Database credentials come from the environment, so nothing is stored:
#   DB_URL       default jdbc:mysql://127.0.0.1:3306/bolt_checkout
#   DB_USER      default bolt
#   DB_PASSWORD  default boltpw
#   MYSQL_ROOT_PASSWORD  only needed the first time, to create the db/user

set -uo pipefail
cd "$(dirname "$0")"

ROOT="$(pwd)"
RUN_DIR="$ROOT/.run"
BACKEND_LOG="$RUN_DIR/backend.log"
FRONTEND_LOG="$RUN_DIR/frontend.log"
# Absolute, because each service is launched after a `cd` into its own directory.
JAR="$ROOT/backend/target/bolt-checkout-1.0.0.jar"

DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:3306/bolt_checkout?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8}"
DB_USER="${DB_USER:-bolt}"
DB_PASSWORD="${DB_PASSWORD:-boltpw}"
DB_NAME="${DB_NAME:-bolt_checkout}"

# The mysql client needs a host and port, but overriding DB_URL alone should be
# enough - so derive them from it rather than making the caller keep two
# variables in sync with a third.
_parsed=$(printf '%s' "$DB_URL" | sed -n 's#jdbc:mysql://\([^/?]*\).*#\1#p')
DB_HOST="${_parsed%%:*}"
DB_PORT="${_parsed##*:}"
[ "$DB_PORT" = "$_parsed" ] && DB_PORT=3306   # no port in the URL
DB_HOST="${DB_HOST:-127.0.0.1}"

bold() { printf '\033[1m%s\033[0m\n' "$1"; }
ok()   { printf '  \033[32mok\033[0m    %s\n' "$1"; }
warn() { printf '  \033[33mwarn\033[0m  %s\n' "$1"; }
die()  { printf '  \033[31mfail\033[0m  %s\n' "$1"; exit 1; }

# First free port at or after $1.
free_port() {
  local p=$1
  while ss -ltn 2>/dev/null | grep -q ":$p "; do p=$((p + 1)); done
  echo "$p"
}

stop_all() {
  local stopped=0
  for f in "$RUN_DIR/frontend.pid" "$RUN_DIR/backend.pid"; do
    [ -f "$f" ] || continue
    local pid; pid=$(cat "$f" 2>/dev/null)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      # Negative pid kills the whole process group, so Maven/npm children go too.
      kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null
      stopped=1
    fi
    rm -f "$f"
  done
  [ "$stopped" = 1 ] && echo "stopped" || echo "nothing running"
}

case "${1:-start}" in
  stop)    stop_all; exit 0 ;;
  status)
    for name in backend frontend; do
      f="$RUN_DIR/$name.pid"
      if [ -f "$f" ] && kill -0 "$(cat "$f")" 2>/dev/null; then
        echo "$name: running (pid $(cat "$f"))"
      else
        echo "$name: not running"
      fi
    done
    exit 0 ;;
  logs)
    tail -n 40 -f "$RUN_DIR/backend.log" "$RUN_DIR/frontend.log" ;;
  restart) stop_all; sleep 2 ;;
  start)   ;;
  *) die "unknown command '$1' — use start | stop | status | logs | restart" ;;
esac

bold "Bolt OTP Checkout"
mkdir -p "$RUN_DIR"

# ---------------------------------------------------------------- toolchain
bold "1/4  Checking prerequisites"
command -v java >/dev/null 2>&1 || die "java not found — install Java 21"
command -v node >/dev/null 2>/dev/null || die "node not found — install Node 18+"
[ -d frontend/node_modules ] || warn "installing frontend dependencies (first run only)"
[ -d frontend/node_modules ] || (cd frontend && npm install >/dev/null 2>&1)
ok "java $(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')"
ok "node $(node -v)"

# ------------------------------------------------------------------ database
bold "2/4  Database"
if ! command -v mysql >/dev/null 2>&1; then
  warn "mysql client not found — assuming the database already exists"
elif ! mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
        -e "USE \`$DB_NAME\`;" >/dev/null 2>&1; then
  warn "cannot reach '$DB_NAME' as user '$DB_USER'"
  echo
  echo "  If you have not set the database up yet, run this once:"
  echo
  echo "    mysql -u root -p <<'SQL'"
  echo "    CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  echo "    CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';"
  echo "    GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'localhost';"
  echo "    FLUSH PRIVILEGES;"
  echo "    SQL"
  echo
  echo "    mysql -u root -p $DB_NAME < database/schema.sql"
  echo
  die "start the database, then run ./run.sh again"
else
  # Apply the schema; it is safe to re-run.
  if mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
       "$DB_NAME" < database/schema.sql 2>/dev/null; then
    ok "connected to $DB_NAME, schema applied"
  else
    warn "schema.sql reported an error — check it manually"
  fi
  ok "tables: $(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" -N -B \
        -e "SELECT GROUP_CONCAT(table_name) FROM information_schema.tables WHERE table_schema='$DB_NAME';" 2>/dev/null)"
fi

# ------------------------------------------------------------------- backend
bold "3/4  Backend"
if [ ! -f "$JAR" ]; then
  warn "jar not found, building it (takes a minute)"
  (cd backend && mvn -B -q -DskipTests package >"$RUN_DIR/build.log" 2>&1) \
    || die "build failed — see $RUN_DIR/build.log"
fi
[ -f "$JAR" ] || die "jar still missing after build"

# Both ports are chosen before either service starts, because the backend's
# CORS_ALLOWED_ORIGIN has to name the port the frontend will end up on.
# 8080 is often taken by something else on a dev machine.
BACKEND_PORT=$(free_port 8080)
FRONTEND_PORT=$(free_port 5173)
[ "$BACKEND_PORT" = 8080 ]  || warn "port 8080 is busy — using $BACKEND_PORT"
[ "$FRONTEND_PORT" = 5173 ] || warn "port 5173 is busy — using $FRONTEND_PORT"

: > "$BACKEND_LOG"
(
  cd backend
  DATABASE_URL="$DB_URL" \
  DATABASE_USERNAME="$DB_USER" \
  DATABASE_PASSWORD="$DB_PASSWORD" \
  SESSION_TOKEN_SECRET="${SESSION_TOKEN_SECRET:-$(openssl rand -base64 48)}" \
  CORS_ALLOWED_ORIGIN="http://localhost:$FRONTEND_PORT" \
  PORT="$BACKEND_PORT" \
  setsid java -jar "$JAR" >"$BACKEND_LOG" 2>&1 &
  echo $! > "$RUN_DIR/backend.pid"
)

printf '  waiting'
for _ in $(seq 1 60); do
  if curl -fs -m 2 "http://localhost:$BACKEND_PORT/api/health" >/dev/null 2>&1; then
    printf '\r'
    ok "up on http://localhost:$BACKEND_PORT  (health: OK)"
    break
  fi
  printf '.'
  sleep 1
done
if ! curl -fs -m 2 "http://localhost:$BACKEND_PORT/api/health" >/dev/null 2>&1; then
  echo
  tail -n 25 "$BACKEND_LOG" | sed 's/^/    /'
  die "backend did not become healthy — full log in $BACKEND_LOG"
fi

# ------------------------------------------------------------------ frontend
bold "4/4  Frontend"

: > "$FRONTEND_LOG"
(
  cd frontend
  VITE_API_BASE_URL="http://localhost:$BACKEND_PORT" \
  setsid npx vite --port "$FRONTEND_PORT" --strictPort >"$FRONTEND_LOG" 2>&1 &
  echo $! > "$RUN_DIR/frontend.pid"
)

printf '  waiting'
for _ in $(seq 1 45); do
  if curl -fs -m 2 "http://localhost:$FRONTEND_PORT/" >/dev/null 2>&1; then
    printf '\r'
    ok "up on http://localhost:$FRONTEND_PORT"
    break
  fi
  printf '.'
  sleep 1
done
curl -fs -m 2 "http://localhost:$FRONTEND_PORT/" >/dev/null 2>&1 \
  || { echo; tail -n 20 "$FRONTEND_LOG" | sed 's/^/    /'; die "frontend did not start — see $FRONTEND_LOG"; }

cat <<BANNER

$(bold "Ready — open the app")
  $(printf '\033[4mhttp://localhost:%s\033[0m\n' "$FRONTEND_PORT")

  API      http://localhost:$BACKEND_PORT
  logs     ./run.sh logs
  stop     ./run.sh stop

  Register an account, note the 6-digit code, then go to checkout with the
  same email — the OTP modal appears after a short pause.
BANNER
