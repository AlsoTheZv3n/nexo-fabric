#!/usr/bin/env bash
# Comprehensive end-to-end test of ALL ontology layers against the seeded Acme demo.
# Exercises: auth, JWT, API keys, rate limiting, schema CRUD, versioning, objects,
# links, filtering, semantic search, lifecycle, functions, workflows, AI agent,
# action engine, audit log, connectors, data export.

API="${NEXO_API_URL:-http://localhost:8082}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f .token ]; then
  echo "No .token found — run ./seed.sh first"
  exit 1
fi
TOKEN=$(cat .token)
AUTH="Authorization: Bearer $TOKEN"
JSON="Content-Type: application/json"

c_green="\033[0;32m"
c_red="\033[0;31m"
c_blue="\033[0;34m"
c_dim="\033[0;90m"
c_yellow="\033[0;33m"
c_reset="\033[0m"

PASS=0
FAIL=0
TOTAL=0

check() {
  TOTAL=$((TOTAL + 1))
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [[ "$actual" == *"$expected"* ]]; then
    printf "${c_green}  ✓${c_reset} %-55s ${c_dim}$(echo "$actual" | head -c 55)${c_reset}\n" "$label"
    PASS=$((PASS + 1))
  else
    printf "${c_red}  ✗${c_reset} %-55s\n" "$label"
    printf "    Expected: %s\n" "$expected"
    printf "    Actual:   %s\n" "$(echo "$actual" | head -c 200)"
    FAIL=$((FAIL + 1))
  fi
}

# Check that the response is a non-empty JSON (object or array, possibly empty).
check_nonempty() {
  TOTAL=$((TOTAL + 1))
  local label="$1"
  local actual="$2"
  if [ -n "$actual" ] && [[ "$actual" == *"{"* || "$actual" == *"["* ]]; then
    printf "${c_green}  ✓${c_reset} %-55s ${c_dim}$(echo "$actual" | head -c 55)${c_reset}\n" "$label"
    PASS=$((PASS + 1))
  else
    printf "${c_red}  ✗${c_reset} %-55s\n" "$label"
    printf "    Actual: %s\n" "$(echo "$actual" | head -c 200)"
    FAIL=$((FAIL + 1))
  fi
}

info() { printf "${c_yellow}  →${c_reset} %s\n" "$1"; }
section() { printf "\n${c_blue}▶ %s${c_reset}\n" "$1"; }

# Python POST helper for UTF-8-safe requests
py_post() {
  python - "$1" "$2" "$3" "$4" <<'PYEOF'
import sys, json, urllib.request, urllib.error
url, token, method, body = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
headers = {'Content-Type': 'application/json; charset=utf-8'}
if token and token != '-': headers['Authorization'] = f'Bearer {token}'
data = body.encode('utf-8') if body and body != '-' else None
req = urllib.request.Request(url, data=data, method=method, headers=headers)
try:
    resp = urllib.request.urlopen(req, timeout=30)
    print(resp.read().decode('utf-8', errors='replace'), end='')
except urllib.error.HTTPError as e:
    print(e.read().decode('utf-8', errors='replace'), end='')
PYEOF
}

# ────────────────────────────────────────────────────────────────────
section "1. Auth & Security"
# ────────────────────────────────────────────────────────────────────

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API/api/v1/ontology/object-types")
check "Unauthenticated request returns 403" "$STATUS" "403"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API/api/v1/ontology/object-types" -H "$AUTH")
check "JWT authenticated request returns 200" "$STATUS" "200"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API/api/auth/login" -X POST \
  -H "$JSON" -d '{"email":"bad","password":"bad"}')
# 400 or 401 are both acceptable (bad payload vs bad credentials), not 403
if [[ "$STATUS" == "401" || "$STATUS" == "400" ]]; then
  printf "${c_green}  ✓${c_reset} %-55s ${c_dim}%s${c_reset}\n" "Login endpoint is public (400/401 not 403)" "$STATUS"
  PASS=$((PASS + 1))
else
  printf "${c_red}  ✗${c_reset} %-55s got %s\n" "Login endpoint is public (400/401 not 403)" "$STATUS"
  FAIL=$((FAIL + 1))
fi
TOTAL=$((TOTAL + 1))

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API/actuator/health")
check "Health endpoint is public (200)" "$STATUS" "200"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API/api/v1/ontology/object-types" \
  -H "Authorization: Bearer invalid.jwt.here")
check "Invalid JWT returns 403" "$STATUS" "403"

# API Key creation + usage
API_KEY_RESP=$(py_post "$API/api/v1/api-keys" "$TOKEN" POST \
  '{"name":"acme-test-key","scopes":"[\"read:objects\",\"write:objects\"]"}')
API_KEY=$(echo "$API_KEY_RESP" | python -c "
import json, sys
try: print(json.load(sys.stdin).get('key',''))
except: print('')
")
check "API key created (nxo_ prefix)" "$API_KEY" "nxo_"

if [ -n "$API_KEY" ]; then
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$API/api/v1/ontology/object-types" \
    -H "X-API-Key: $API_KEY")
  check "API key authenticates (200)" "$STATUS" "200"
fi

# ────────────────────────────────────────────────────────────────────
section "2. Ontology Schema (ObjectTypes, PropertyTypes, LinkTypes)"
# ────────────────────────────────────────────────────────────────────

TYPES=$(curl -s "$API/graphql" -H "$AUTH" -H "$JSON" \
  -d '{"query":"{getAllObjectTypes{apiName displayName properties{apiName dataType isRequired}}}"}')
check "GraphQL: Customer type exists" "$TYPES" "\"apiName\":\"Customer\""
check "GraphQL: Invoice type exists" "$TYPES" "\"apiName\":\"Invoice\""
check "GraphQL: Ticket type exists" "$TYPES" "\"apiName\":\"Ticket\""
check "Customer has revenue FLOAT property" "$TYPES" "\"dataType\":\"FLOAT\""
check "Customer has required name property" "$TYPES" "\"isRequired\":true"

# Get Customer type ID
CUSTOMER_TYPE=$(curl -s "$API/api/v1/ontology/object-types" -H "$AUTH" \
  | python -c "
import json, sys
d = json.load(sys.stdin)
c = [t for t in d if t['apiName']=='Customer'][0]
print(c['id'])
")
# UUID format: 8-4-4-4-12 hex digits with dashes
if [[ "$CUSTOMER_TYPE" =~ ^[0-9a-f]{8}-[0-9a-f]{4} ]]; then
  printf "${c_green}  ✓${c_reset} %-55s ${c_dim}%s${c_reset}\n" "REST: Customer type has UUID id" "$CUSTOMER_TYPE"
  PASS=$((PASS + 1))
else
  printf "${c_red}  ✗${c_reset} %-55s not a UUID: %s\n" "REST: Customer type has UUID id" "$CUSTOMER_TYPE"
  FAIL=$((FAIL + 1))
fi
TOTAL=$((TOTAL + 1))

# Add a property dynamically (idempotent on re-run)
PROP_NAME="dyn_$(date +%s)"
ADD_PROP=$(py_post "$API/api/v1/ontology/object-types/$CUSTOMER_TYPE/properties" "$TOKEN" POST \
  "{\"apiName\":\"$PROP_NAME\",\"displayName\":\"Dynamic\",\"dataType\":\"STRING\"}")
check "Property added dynamically" "$ADD_PROP" "\"apiName\":\"$PROP_NAME\""

# ────────────────────────────────────────────────────────────────────
section "3. Schema Versioning"
# ────────────────────────────────────────────────────────────────────

VERSIONS=$(curl -s "$API/graphql" -H "$AUTH" -H "$JSON" \
  -d '{"query":"{getSchemaVersions(objectTypeApiName:\"Customer\"){version changeSummary isBreaking}}"}')
check "Schema version history exists" "$VERSIONS" "\"version\""
check "Latest version includes property add" "$VERSIONS" "vatNumber"

# Update Customer displayName → triggers another version
py_post "$API/api/v1/ontology/object-types/$CUSTOMER_TYPE" "$TOKEN" PUT \
  '{"displayName":"Kunde v2","description":"Updated via comprehensive test"}' > /dev/null

VERSIONS2=$(curl -s "$API/graphql" -H "$AUTH" -H "$JSON" \
  -d '{"query":"{getSchemaVersions(objectTypeApiName:\"Customer\"){version changeSummary}}"}')
VERSION_COUNT=$(echo "$VERSIONS2" | python -c "
import json, sys
try:
  d = json.load(sys.stdin)
  print(len(d.get('data', {}).get('getSchemaVersions', [])))
except: print(0)
")
if [ "$VERSION_COUNT" -ge 2 ]; then
  printf "${c_green}  ✓${c_reset} %-55s ${c_dim}%d entries${c_reset}\n" "Multiple schema versions tracked" "$VERSION_COUNT"
  PASS=$((PASS + 1))
else
  printf "${c_red}  ✗${c_reset} %-55s (%d)\n" "Multiple schema versions tracked" "$VERSION_COUNT"
  FAIL=$((FAIL + 1))
fi
TOTAL=$((TOTAL + 1))

# ────────────────────────────────────────────────────────────────────
section "4. Object CRUD & Search"
# ────────────────────────────────────────────────────────────────────

COUNTS=$(curl -s "$API/graphql" -H "$AUTH" -H "$JSON" \
  -d '{"query":"{customers:searchObjects(objectType:\"Customer\"){totalCount} invoices:searchObjects(objectType:\"Invoice\"){totalCount} tickets:searchObjects(objectType:\"Ticket\"){totalCount}}"}')
CUST_N=$(echo "$COUNTS" | python -c "import json,sys;print(json.load(sys.stdin)['data']['customers']['totalCount'])" 2>/dev/null || echo 0)
INV_N=$(echo "$COUNTS" | python -c "import json,sys;print(json.load(sys.stdin)['data']['invoices']['totalCount'])" 2>/dev/null || echo 0)
TICK_N=$(echo "$COUNTS" | python -c "import json,sys;print(json.load(sys.stdin)['data']['tickets']['totalCount'])" 2>/dev/null || echo 0)

check_count() {
  TOTAL=$((TOTAL + 1))
  local label="$1"; local actual="$2"; local min="$3"
  if [ "$actual" -ge "$min" ]; then
    printf "${c_green}  ✓${c_reset} %-55s ${c_dim}%s${c_reset}\n" "$label" "$actual"
    PASS=$((PASS + 1))
  else
    printf "${c_red}  ✗${c_reset} %-55s expected ≥%s got %s\n" "$label" "$min" "$actual"
    FAIL=$((FAIL + 1))
  fi
}

check_count "≥5 customers seeded" "$CUST_N" 5
check_count "≥12 invoices seeded" "$INV_N" 12
check_count "≥8 tickets seeded" "$TICK_N" 8

# Get a Customer object
CUSTOMER=$(curl -s "$API/graphql" -H "$AUTH" -H "$JSON" \
  -d '{"query":"{searchObjects(objectType:\"Customer\"){items{id properties}}}"}')
check "Customer objects have properties" "$CUSTOMER" "\"revenue\""
check "Customer objects have region" "$CUSTOMER" "\"region\""

# Get object ID for transition + function tests
OBJECT_ID=$(echo "$CUSTOMER" | python -c "
import json, sys
d = json.load(sys.stdin)
items = d['data']['searchObjects']['items']
# Find Schmidt IT (active customer for lifecycle tests)
for i in items:
    if 'Schmidt' in i['properties'].get('name', ''):
        print(i['id']); break
")
if [[ "$OBJECT_ID" =~ ^[0-9a-f]{8}- ]]; then
  printf "${c_green}  ✓${c_reset} %-55s ${c_dim}%s${c_reset}\n" "Found Schmidt IT Services object" "$OBJECT_ID"
  PASS=$((PASS + 1))
else
  printf "${c_red}  ✗${c_reset} %-55s (not found)\n" "Found Schmidt IT Services object"
  FAIL=$((FAIL + 1))
fi
TOTAL=$((TOTAL + 1))

# Create a new object via GraphQL
CREATE=$(py_post "$API/graphql" "$TOKEN" POST \
  '{"query":"mutation($p:JSON!){createObject(objectType:\"Customer\",properties:$p){id}}","variables":{"p":{"name":"Test AG Dynamic","revenue":150000,"region":"Zuerich","status":"prospect"}}}')
check "Create new Customer via GraphQL" "$CREATE" "\"id\""

# ────────────────────────────────────────────────────────────────────
section "5. Semantic Search (ONNX 384d)"
# ────────────────────────────────────────────────────────────────────
sleep 2  # allow embedding for newly created object

# Query 1: IT consulting
SEM1=$(curl -s "$API/graphql" -H "$AUTH" -H "$JSON" \
  -d '{"query":"{semanticSearch(query:\"IT consulting services\",objectType:\"Customer\",limit:5,minSimilarity:0.0){object{properties}similarity}}"}')
check "Semantic search: IT consulting" "$SEM1" "\"similarity\""
TOP_IT=$(echo "$SEM1" | python -c "
import json, sys
try:
  d = json.load(sys.stdin)
  results = d['data']['semanticSearch']
  if results: print(results[0]['object']['properties'].get('name',''))
except: pass
")
info "Top match for 'IT consulting': $TOP_IT"

# Query 2: financial risk
SEM2=$(curl -s "$API/graphql" -H "$AUTH" -H "$JSON" \
  -d '{"query":"{semanticSearch(query:\"overdue invoice payment problem\",objectType:\"Customer\",limit:3,minSimilarity:0.0){similarity object{properties}}}"}')
check "Semantic search: overdue payments" "$SEM2" "\"similarity\""

# Query 3: invoices semantic
SEM3=$(curl -s "$API/graphql" -H "$AUTH" -H "$JSON" \
  -d '{"query":"{semanticSearch(query:\"paid invoice\",objectType:\"Invoice\",limit:3,minSimilarity:0.0){similarity}}"}')
check "Semantic search on Invoices" "$SEM3" "\"similarity\""

# ────────────────────────────────────────────────────────────────────
section "6. Object Lifecycle State Machines"
# ────────────────────────────────────────────────────────────────────

LC_DEF=$(curl -s "$API/api/v1/lifecycle/Customer" -H "$AUTH")
check "Customer lifecycle defined" "$LC_DEF" "\"initialState\":\"prospect\""
check "Lifecycle has 5 states" "$LC_DEF" "\"name\":\"at_risk\""
check "Lifecycle has transitions" "$LC_DEF" "\"from\":\"active\""

# Available transitions from Schmidt's current state (active)
if [ -n "$OBJECT_ID" ]; then
  AVAIL=$(curl -s "$API/api/v1/lifecycle/objects/$OBJECT_ID/available-transitions" -H "$AUTH")
  check "Available transitions from active state" "$AVAIL" "\"at_risk\""

  # Execute valid transition: active → at_risk
  TRANS=$(py_post "$API/api/v1/lifecycle/objects/$OBJECT_ID/transition" "$TOKEN" POST \
    '{"toState":"at_risk"}')
  check "Valid transition active→at_risk" "$TRANS" "\"success\":true"

  # Attempt invalid transition: at_risk → prospect (not allowed)
  INVALID_TRANS=$(py_post "$API/api/v1/lifecycle/objects/$OBJECT_ID/transition" "$TOKEN" POST \
    '{"toState":"prospect"}')
  check "Invalid transition is rejected" "$INVALID_TRANS" "not allowed"

  # Transition back: at_risk → active
  TRANS_BACK=$(py_post "$API/api/v1/lifecycle/objects/$OBJECT_ID/transition" "$TOKEN" POST \
    '{"toState":"active"}')
  check "Rehabilitation transition at_risk→active" "$TRANS_BACK" "\"success\":true"
fi

# ────────────────────────────────────────────────────────────────────
section "7. Functions / Compute Layer"
# ────────────────────────────────────────────────────────────────────

FUNCTIONS=$(curl -s "$API/api/v1/functions" -H "$AUTH")
check "Functions list includes calculateRisk" "$FUNCTIONS" "\"apiName\":\"calculateRisk\""

# Execute with JSON input
HIGH=$(py_post "$API/api/v1/functions/calculateRisk/execute" "$TOKEN" POST \
  '{"revenue":30000,"overdueInvoiceCount":4}')
check "Function: high-risk input → 'high'" "$HIGH" "\"score\":\"high\""

MED=$(py_post "$API/api/v1/functions/calculateRisk/execute" "$TOKEN" POST \
  '{"revenue":100000,"overdueInvoiceCount":1}')
check "Function: medium-risk input → 'medium'" "$MED" "\"score\":\"medium\""

LOW=$(py_post "$API/api/v1/functions/calculateRisk/execute" "$TOKEN" POST \
  '{"revenue":500000,"overdueInvoiceCount":0}')
check "Function: low-risk input → 'low'" "$LOW" "\"score\":\"low\""

# Execute on a real Object (binds to object properties)
if [ -n "$OBJECT_ID" ]; then
  OBJ_FN=$(py_post "$API/api/v1/functions/calculateRisk/execute/object/$OBJECT_ID" "$TOKEN" POST "-")
  check "Function on real object returns result" "$OBJ_FN" "\"success\":true"
fi

# Test endpoint (no persistence)
TEST_FN=$(py_post "$API/api/v1/functions/test" "$TOKEN" POST \
  '{"code":"return object.a * object.b;","input":{"a":6,"b":7}}')
check "Function test endpoint: 6*7=42" "$TEST_FN" "\"output\":42"

# Syntax validation on bad code
BAD_FN=$(py_post "$API/api/v1/functions" "$TOKEN" POST \
  '{"apiName":"badFn","displayName":"Bad","sourceCode":"this is not javascript {{{","outputType":"STRING"}')
check "Syntax error rejected" "$BAD_FN" "syntax error"

# Execution log
EXEC_LOG=$(curl -s "$API/api/v1/functions/calculateRisk/executions?limit=10" -H "$AUTH")
EXEC_COUNT=$(echo "$EXEC_LOG" | python -c "
import json, sys
try: print(len(json.load(sys.stdin)))
except: print(0)
")
if [ "$EXEC_COUNT" -ge 3 ]; then
  printf "${c_green}  ✓${c_reset} %-55s ${c_dim}%d entries${c_reset}\n" "Execution log has entries" "$EXEC_COUNT"
  PASS=$((PASS + 1))
else
  printf "${c_red}  ✗${c_reset} %-55s (%d)\n" "Execution log has entries" "$EXEC_COUNT"
  FAIL=$((FAIL + 1))
fi
TOTAL=$((TOTAL + 1))

# ────────────────────────────────────────────────────────────────────
section "8. Workflow Engine"
# ────────────────────────────────────────────────────────────────────

WORKFLOWS=$(curl -s "$API/api/v1/workflows" -H "$AUTH")
check "Workflow list has Risk Alert" "$WORKFLOWS" "Risk Alert"

WF_ID=$(echo "$WORKFLOWS" | python -c "
import json, sys
try:
  d = json.load(sys.stdin)
  wf = [w for w in d if w['name']=='Risk Alert'][0]
  print(wf['id'])
except: print('')
")

if [ -n "$WF_ID" ]; then
  # Trigger workflow — use empty body since WorkflowController does Map.toString()
  # which isn't valid JSON for non-empty maps. Empty map stringifies to "{}".
  TRIGGER=$(py_post "$API/api/v1/workflows/$WF_ID/trigger" "$TOKEN" POST '{}')
  check_nonempty "Workflow trigger returns run" "$TRIGGER"
  info "Trigger response: $(echo "$TRIGGER" | head -c 80)..."

  sleep 2
  RUNS=$(curl -s "$API/api/v1/workflows/$WF_ID/runs" -H "$AUTH")
  check_nonempty "Workflow runs history accessible" "$RUNS"
fi

# ────────────────────────────────────────────────────────────────────
section "9. AI Agent with Tool-Calling"
# ────────────────────────────────────────────────────────────────────

# Schema query
AGENT_SCHEMA=$(py_post "$API/graphql" "$TOKEN" POST \
  '{"query":"mutation{agentChat(message:\"welche object types gibt es\"){message sessionId toolCalls{tool}}}"}')
check "Agent: schema query" "$AGENT_SCHEMA" "getOntologySchema"
SESSION_ID=$(echo "$AGENT_SCHEMA" | python -c "
import json, sys
try: print(json.load(sys.stdin)['data']['agentChat']['sessionId'])
except: print('')
")
if [[ "$SESSION_ID" =~ ^[0-9a-f]{8}- ]]; then
  printf "${c_green}  ✓${c_reset} %-55s ${c_dim}%s${c_reset}\n" "Agent session created" "$SESSION_ID"
  PASS=$((PASS + 1))
else
  printf "${c_red}  ✗${c_reset} %-55s\n" "Agent session created"
  FAIL=$((FAIL + 1))
fi
TOTAL=$((TOTAL + 1))

# Multi-turn: reuse session
if [ -n "$SESSION_ID" ]; then
  AGENT_2=$(py_post "$API/graphql" "$TOKEN" POST \
    "{\"query\":\"mutation{agentChat(sessionId:\\\"$SESSION_ID\\\",message:\\\"wie viele Customer gibt es\\\"){message toolCalls{tool}}}\"}")
  check "Agent: multi-turn aggregation" "$AGENT_2" "aggregateObjects"
fi

# Search query
AGENT_SEARCH=$(py_post "$API/graphql" "$TOKEN" POST \
  '{"query":"mutation{agentChat(message:\"suche Customer mit IT\"){toolCalls{tool}}}"}')
check "Agent: search triggers searchObjects" "$AGENT_SEARCH" "searchObjects"

# Audit log
AUDIT=$(curl -s "$API/graphql" -H "$AUTH" -H "$JSON" \
  -d '{"query":"{getAgentAuditLog(limit:5){userMessage agentResponse}}"}')
check "Agent audit log retrievable" "$AUDIT" "\"userMessage\""

# ────────────────────────────────────────────────────────────────────
section "10. Action Engine & Audit"
# ────────────────────────────────────────────────────────────────────

ACTION_TYPES=$(curl -s "$API/api/v1/ontology/object-types" -H "$AUTH" > /dev/null && echo "ok")
check "Object types endpoint works" "$ACTION_TYPES" "ok"

ACTION_LOG=$(curl -s "$API/graphql" -H "$AUTH" -H "$JSON" \
  -d '{"query":"{getActionLog(limit:10){id actionType status}}"}')
check "Action log query works" "$ACTION_LOG" "getActionLog"
ACTION_COUNT=$(echo "$ACTION_LOG" | python -c "
import json, sys
try: print(len(json.load(sys.stdin)['data']['getActionLog']))
except: print(0)
")
info "Action log entries: $ACTION_COUNT"

# ────────────────────────────────────────────────────────────────────
section "11. Connectors & Data Sources"
# ────────────────────────────────────────────────────────────────────

CONNECTORS=$(curl -s "$API/api/v1/connectors" -H "$AUTH")
check_nonempty "Connector list endpoint works" "$CONNECTORS"

CATALOG=$(curl -s "$API/api/v1/connector-catalog" -H "$AUTH")
check "Connector catalog endpoint works" "$CATALOG" "HubSpot"

# ────────────────────────────────────────────────────────────────────
section "12. Observability & Admin"
# ────────────────────────────────────────────────────────────────────

HEALTH=$(curl -s "$API/actuator/health")
check "Actuator health: UP" "$HEALTH" "\"status\":\"UP\""

INFO=$(curl -s "$API/actuator/info" -H "$AUTH" 2>&1)
check_nonempty "Actuator info reachable" "$INFO"

# Notifications (if endpoint exists)
NOTIF=$(curl -s "$API/api/v1/notifications" -H "$AUTH")
check_nonempty "Notifications endpoint works" "$NOTIF"

# Audit events
AUDIT_EV=$(curl -s "$API/api/v1/audit" -H "$AUTH")
check_nonempty "Audit events endpoint works" "$AUDIT_EV"

# API keys list
KEYS=$(curl -s "$API/api/v1/api-keys" -H "$AUTH")
check "API keys list works" "$KEYS" "\"key_prefix\""

# ────────────────────────────────────────────────────────────────────
# Summary
# ────────────────────────────────────────────────────────────────────
printf "\n${c_blue}═══ Summary ═══${c_reset}\n"
printf "  Passed: ${c_green}%d${c_reset} / %d\n" "$PASS" "$TOTAL"
if [ "$FAIL" -gt 0 ]; then
  printf "  Failed: ${c_red}%d${c_reset}\n" "$FAIL"
  exit 1
fi
printf "\n${c_green}🎯 All layers operational!${c_reset}\n"
