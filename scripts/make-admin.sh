#!/usr/bin/env bash
# Gives an existing account admin rights (or takes them away). Run on the machine that hosts the database:
#
#   scripts/make-admin.sh someone@example.com            # make admin
#   scripts/make-admin.sh someone@example.com --revoke   # back to a normal user
#
# There is deliberately no way to become admin through the website itself: only someone with access
# to the server can run this. The change applies the next time that person logs in or their login
# refreshes (at most 15 minutes).
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 || ( $# -eq 2 && "$2" != "--revoke" ) ]]; then
  echo "Usage: $0 <email> [--revoke]" >&2
  exit 1
fi

EMAIL="$1"
ROLE="ADMIN"
[[ "${2:-}" == "--revoke" ]] && ROLE="USER"

cd "$(dirname "$0")/../infra"
# Read the database name and user from infra/.env
set -a; source .env; set +a
CONTAINER="${POSTGRES_CONTAINER:-infra-postgres-1}"

# :'email' and :'role' are psql variables: the values are quoted safely, never pasted into the SQL.
# -q hides psql's own status line ("UPDATE 0"), so RESULT is empty when no account matched.
RESULT=$(docker exec -i "$CONTAINER" psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA \
  -v email="$EMAIL" -v role="$ROLE" <<'SQL'
UPDATE users SET role = :'role' WHERE email = lower(trim(:'email')) RETURNING email || ' is now ' || role;
SQL
)

if [[ -z "$RESULT" ]]; then
  echo "No account with the email $EMAIL. Register it on the website first." >&2
  exit 1
fi
echo "$RESULT"
echo "It takes effect at their next login (or within 15 minutes)."
