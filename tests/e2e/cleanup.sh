#!/usr/bin/env bash
# Removes all seeded data for the Acme tenant (keeps the tenant itself).
set -e
API="${NEXO_API_URL:-http://localhost:8082}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f .token ]; then
  echo "No .token found — nothing to clean"
  exit 0
fi
TOKEN=$(cat .token)
AUTH="Authorization: Bearer $TOKEN"
JSON="Content-Type: application/json"

echo "Deleting lifecycle ..."
curl -s -X DELETE "$API/api/v1/lifecycle/Customer" -H "$AUTH" > /dev/null

echo "Deleting function ..."
curl -s -X DELETE "$API/api/v1/functions/calculateRisk" -H "$AUTH" > /dev/null

echo "Note: ObjectTypes and Objects are not auto-deleted. Use the backend admin API."
echo "✅  Cleanup complete."
