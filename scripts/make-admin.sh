#!/usr/bin/env bash
# Gives an existing account admin rights (or takes them away).
#
#   scripts/make-admin.sh someone@example.com            # make admin
#   scripts/make-admin.sh someone@example.com --revoke   # back to a normal user
#
# By default this changes the local Docker database (infra/docker-compose.yml).
# For a hosted database (e.g. Render), pass its connection URL:
#   DATABASE_URL='postgresql://user:password@host/db' scripts/make-admin.sh someone@example.com
#
# There is deliberately no way to become admin through the website itself: only someone with access
# to the database can run this. The change applies the next time that person logs in or their login
# refreshes (at most 15 minutes).
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 || ( $# -eq 2 && "$2" != "--revoke" ) ]]; then
  echo "Usage: $0 <email> [--revoke]" >&2
  exit 1
fi

EMAIL="$1"
ROLE="ADMIN"
[[ "${2:-}" == "--revoke" ]] && ROLE="USER"

if [[ -n "${DATABASE_URL:-}" ]]; then
  # Hosted database: run psql from the postgres image, so nothing needs installing.
  PSQL=(docker run --rm -i postgres:16 psql "$DATABASE_URL")
else
  # Local Docker database: read its name and user from infra/.env
  cd "$(dirname "$0")/../infra"
  set -a; source .env; set +a
  PSQL=(docker exec -i "${POSTGRES_CONTAINER:-infra-postgres-1}" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB")
fi

# :'email' and :'role' are psql variables: the values are quoted safely, never pasted into the SQL.
# -q hides psql's own status line ("UPDATE 0"), so RESULT is empty when no account matched.
RESULT=$("${PSQL[@]}" -q -tA -v email="$EMAIL" -v role="$ROLE" <<'SQL'
UPDATE users SET role = :'role' WHERE email = lower(trim(:'email')) RETURNING email || ' is now ' || role;
SQL
)

if [[ -z "$RESULT" ]]; then
  echo "No account with the email $EMAIL. Register it on the website first." >&2
  exit 1
fi
echo "$RESULT"
echo "It takes effect at their next login (or within 15 minutes)."
