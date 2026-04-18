#!/usr/bin/env bash
# Seeds the sandbox Acme Swiss Consulting demo project.
# Uses Python for JSON handling to avoid Windows UTF-8 encoding issues.
set -e
API="${NEXO_API_URL:-http://localhost:8082}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

c_green="\033[0;32m"
c_yellow="\033[0;33m"
c_red="\033[0;31m"
c_blue="\033[0;34m"
c_reset="\033[0m"

say()  { printf "${c_blue}[seed]${c_reset} %s\n" "$1"; }
ok()   { printf "${c_green}  ✓${c_reset} %s\n" "$1"; }
warn() { printf "${c_yellow}  !${c_reset} %s\n" "$1"; }
fail() { printf "${c_red}  ✗${c_reset} %s\n" "$1"; }

# ─── Python JSON POST helper ─────────────────────────────────────────
# Avoids Windows bash UTF-8 mangling by using Python's json + requests.
py_post() {
  python - "$1" "$2" "$3" "$4" <<'PYEOF'
import sys, json, urllib.request, urllib.error
url, token, method, body_file = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
with open(body_file, 'rb') as f:
    data = f.read()
req = urllib.request.Request(url, data=data, method=method, headers={
    'Authorization': f'Bearer {token}',
    'Content-Type': 'application/json; charset=utf-8',
})
try:
    resp = urllib.request.urlopen(req, timeout=30)
    print(f"STATUS {resp.status}")
    print(resp.read().decode('utf-8', errors='replace'))
except urllib.error.HTTPError as e:
    print(f"STATUS {e.code}")
    print(e.read().decode('utf-8', errors='replace'))
PYEOF
}

# Write JSON to temp file and POST it; return exit 0 if status 2xx.
post_json() {
  local url="$1"
  local token="$2"
  local method="$3"
  local json_body="$4"

  local tmp
  tmp=$(mktemp)
  python -c "
import json, sys
body = json.loads(sys.argv[1])
sys.stdout.buffer.write(json.dumps(body, ensure_ascii=False).encode('utf-8'))
" "$json_body" > "$tmp"

  local out
  out=$(py_post "$url" "$token" "$method" "$tmp")
  rm -f "$tmp"
  local status
  status=$(echo "$out" | head -1 | awk '{print $2}')
  local body
  body=$(echo "$out" | tail -n +2)

  echo "$body"
  if [[ "$status" =~ ^2[0-9][0-9]$ ]]; then
    return 0
  else
    return 1
  fi
}

# ─── Backend check ───────────────────────────────────────────────────
say "Checking backend at $API ..."
if ! curl -sf "$API/actuator/health" > /dev/null; then
  fail "Backend not reachable at $API"
  exit 1
fi
ok "Backend healthy"

# ─── Register / Login ────────────────────────────────────────────────
say "Registering tenant 'acme' ..."
REG=$(post_json "$API/api/auth/register" "" POST \
  '{"tenantApiName":"acme","tenantDisplayName":"Acme Swiss Consulting","email":"admin@acme.ch","password":"acme1234"}' 2>/dev/null || true)

if echo "$REG" | grep -q '"token"'; then
  TOKEN=$(python -c "import json, sys; print(json.loads(sys.argv[1])['token'])" "$REG")
  ok "Tenant registered"
else
  warn "Already exists — logging in"
  LOGIN=$(post_json "$API/api/auth/login" "" POST \
    '{"email":"admin@acme.ch","password":"acme1234"}')
  TOKEN=$(python -c "import json, sys; print(json.loads(sys.argv[1])['token'])" "$LOGIN")
fi

if [ -z "$TOKEN" ] || [ "${#TOKEN}" -lt 50 ]; then
  fail "Failed to get auth token"
  exit 1
fi
echo "$TOKEN" > .token
ok "Token saved (${#TOKEN} chars)"

# ─── ObjectTypes ──────────────────────────────────────────────────────
say "Creating ObjectTypes ..."

create_type() {
  local name="$1"
  local payload="$2"
  if post_json "$API/api/v1/ontology/object-types" "$TOKEN" POST "$payload" > /tmp/resp.json 2>&1; then
    ok "ObjectType: $name"
  else
    local msg=$(cat /tmp/resp.json | python -c "import json,sys; d=json.loads(sys.stdin.read()); print(d.get('error',''))" 2>/dev/null || echo "error")
    if [[ "$msg" == *"already exists"* ]]; then
      warn "ObjectType $name already exists"
    else
      fail "Failed to create $name: $msg"
    fi
  fi
}

create_type "Customer" '{
  "apiName":"Customer","displayName":"Kunde",
  "description":"Kunden-Stammdaten",
  "properties":[
    {"apiName":"name","displayName":"Name","dataType":"STRING","isRequired":true},
    {"apiName":"email","displayName":"Email","dataType":"STRING"},
    {"apiName":"revenue","displayName":"Jahresumsatz","dataType":"FLOAT"},
    {"apiName":"region","displayName":"Region","dataType":"STRING"},
    {"apiName":"industry","displayName":"Branche","dataType":"STRING"},
    {"apiName":"employeeCount","displayName":"Mitarbeiter","dataType":"INTEGER"},
    {"apiName":"status","displayName":"Status","dataType":"STRING"},
    {"apiName":"overdueInvoiceCount","displayName":"Ueberfaellige Rechnungen","dataType":"INTEGER"},
    {"apiName":"notes","displayName":"Notizen","dataType":"STRING"}
  ]
}'

create_type "Invoice" '{
  "apiName":"Invoice","displayName":"Rechnung",
  "properties":[
    {"apiName":"number","displayName":"Nummer","dataType":"STRING","isRequired":true},
    {"apiName":"customer","displayName":"Kunde","dataType":"STRING"},
    {"apiName":"amount","displayName":"Betrag","dataType":"FLOAT"},
    {"apiName":"status","displayName":"Status","dataType":"STRING"},
    {"apiName":"dueInDays","displayName":"Faellig in Tagen","dataType":"INTEGER"}
  ]
}'

create_type "Ticket" '{
  "apiName":"Ticket","displayName":"Support-Ticket",
  "properties":[
    {"apiName":"title","displayName":"Titel","dataType":"STRING","isRequired":true},
    {"apiName":"customer","displayName":"Kunde","dataType":"STRING"},
    {"apiName":"priority","displayName":"Prioritaet","dataType":"STRING"},
    {"apiName":"status","displayName":"Status","dataType":"STRING"},
    {"apiName":"ageDays","displayName":"Alter in Tagen","dataType":"INTEGER"}
  ]
}'

# ─── Lifecycle ────────────────────────────────────────────────────────
say "Defining Customer Lifecycle ..."
LIFECYCLE_JSON='{
  "stateProperty":"status",
  "initialState":"prospect",
  "states":[
    {"name":"prospect","displayName":"Prospect","color":"gray"},
    {"name":"qualified","displayName":"Qualified","color":"blue"},
    {"name":"active","displayName":"Active","color":"green"},
    {"name":"at_risk","displayName":"At Risk","color":"orange"},
    {"name":"churned","displayName":"Churned","color":"red","final":true}
  ],
  "transitions":[
    {"from":"prospect","to":"qualified","displayName":"Qualifizieren"},
    {"from":"qualified","to":"active","displayName":"Aktivieren"},
    {"from":"active","to":"at_risk","displayName":"Risiko markieren"},
    {"from":"at_risk","to":"active","displayName":"Rehabilitieren"},
    {"from":"at_risk","to":"churned","displayName":"Verloren"},
    {"from":"active","to":"churned","displayName":"Direkt verloren"}
  ]
}'
post_json "$API/api/v1/lifecycle/Customer" "$TOKEN" PUT "$LIFECYCLE_JSON" > /dev/null \
  && ok "Lifecycle: 5 states, 6 transitions"

# ─── Function ─────────────────────────────────────────────────────────
say "Creating calculateRisk Function ..."
FN_JSON=$(python - <<'PY'
import json
with open('functions/calculateRisk.js', 'rb') as f:
    code = f.read().decode('utf-8')
payload = {
    "apiName": "calculateRisk",
    "displayName": "Calculate Customer Risk",
    "description": "Returns low/medium/high based on revenue and overdue invoices",
    "sourceCode": code,
    "inputType": "Customer",
    "outputType": "JSON"
}
print(json.dumps(payload, ensure_ascii=False))
PY
)
post_json "$API/api/v1/functions" "$TOKEN" POST "$FN_JSON" > /dev/null \
  && ok "Function: calculateRisk" || warn "Function creation skipped (already exists?)"

# ─── Mock Data ────────────────────────────────────────────────────────
say "Loading mock data via GraphQL ..."

seed_objects() {
  local object_type="$1"
  local file="$2"
  local count=0
  local records=$(python -c "import json; data=json.load(open('$file')); [print(json.dumps(r, ensure_ascii=False)) for r in data]")

  while IFS= read -r props; do
    local body=$(python -c "
import json, sys
props = json.loads(sys.argv[1])
body = {
    'query': 'mutation(\$p: JSON!) { createObject(objectType: \"$object_type\", properties: \$p) { id } }',
    'variables': {'p': props}
}
print(json.dumps(body, ensure_ascii=False))
" "$props")
    post_json "$API/graphql" "$TOKEN" POST "$body" > /dev/null && count=$((count + 1)) || true
  done <<< "$records"
  ok "Loaded $count $object_type objects"
}

seed_objects "Customer" "data/customers.json"
seed_objects "Invoice" "data/invoices.json"
seed_objects "Ticket" "data/tickets.json"

# ─── Workflow ─────────────────────────────────────────────────────────
say "Creating Risk-Alert Workflow ..."
WF_JSON='{
  "name":"Risk Alert",
  "description":"Notify team when a customer moves to at_risk state",
  "triggerType":"MANUAL",
  "triggerConfig":"{}",
  "steps":"[{\"id\":\"s1\",\"type\":\"NOTIFY\",\"name\":\"Alert Account Manager\",\"config\":{\"title\":\"Customer at risk\"},\"next\":\"s2\"},{\"id\":\"s2\",\"type\":\"WAIT\",\"name\":\"Cool down\",\"config\":{\"amount\":3,\"unit\":\"SECONDS\"},\"next\":\"s3\"},{\"id\":\"s3\",\"type\":\"END\",\"name\":\"End\"}]"
}'
post_json "$API/api/v1/workflows" "$TOKEN" POST "$WF_JSON" > /dev/null \
  && ok "Workflow: Risk Alert"

echo ""
printf "${c_green}✅  Sandbox ready!${c_reset}\n\n"
echo "Next steps:"
echo "  ./test-features.sh    — run end-to-end test suite"
echo "  ./test-codegen.sh     — generate typed SDK"
echo "  http://localhost:3005 — open frontend (login: admin@acme.ch / acme1234)"
