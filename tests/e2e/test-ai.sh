#!/usr/bin/env bash
# Focused test of ALL AI agent features against the seeded Acme data.
# Exercises each tool, multi-turn sessions, audit log, HITL approvals.

API="${NEXO_API_URL:-http://localhost:8082}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f .token ]; then echo "Run ./seed.sh first"; exit 1; fi
TOKEN=$(cat .token)

c_green="\033[0;32m"; c_red="\033[0;31m"; c_blue="\033[0;34m"
c_yellow="\033[0;33m"; c_dim="\033[0;90m"; c_bold="\033[1m"; c_reset="\033[0m"

# ── Agent call helper: sends message, returns JSON response ────────────
agent() {
  local msg="$1"
  local sid="$2"
  python - "$API" "$TOKEN" "$msg" "$sid" <<'PYEOF'
import sys, json, urllib.request, urllib.error, time
api, token, msg, sid = sys.argv[1:5]
variables = {"message": msg}
if sid: variables["sessionId"] = sid
q = 'mutation($message:String!,$sessionId:ID){agentChat(sessionId:$sessionId,message:$message){message sessionId toolCalls{tool duration}}}'
body = json.dumps({"query": q, "variables": variables}).encode('utf-8')
req = urllib.request.Request(f"{api}/graphql", data=body, method="POST", headers={
    'Authorization': f'Bearer {token}', 'Content-Type': 'application/json; charset=utf-8',
})
start = time.time()
try:
    resp = urllib.request.urlopen(req, timeout=180)
    raw = resp.read().decode('utf-8')
    data = json.loads(raw).get('data', {}).get('agentChat', {}) or {}
    elapsed = int((time.time() - start) * 1000)
    print(json.dumps({
        "elapsed_ms": elapsed,
        "session": data.get('sessionId', ''),
        "message": data.get('message', ''),
        "tools": [t.get('tool') for t in (data.get('toolCalls') or [])]
    }, ensure_ascii=False))
except Exception as e:
    print(json.dumps({"error": str(e)}))
PYEOF
}

# Parse helper
jq_get() { python -c "import json,sys; print(json.load(sys.stdin).get('$1',''))"; }

pass=0; fail=0; total=0

# ── Scenario runner ────────────────────────────────────────────────────
# Args: label, question, expected_tool (or empty for any), expected_substring (or empty), session_var
run() {
  total=$((total + 1))
  local label="$1"; local question="$2"; local expected_tool="$3"
  local expected_sub="$4"; local session_in="$5"

  printf "\n${c_blue}${c_bold}[Test %d]${c_reset} ${c_bold}%s${c_reset}\n" "$total" "$label"
  printf "${c_dim}  Frage: %s${c_reset}\n" "$question"

  local raw
  raw=$(agent "$question" "$session_in")

  local elapsed msg tools sid
  elapsed=$(echo "$raw" | python -c "import json,sys; print(json.load(sys.stdin).get('elapsed_ms',0))")
  msg=$(echo "$raw" | python -c "import json,sys; print(json.load(sys.stdin).get('message',''))")
  tools=$(echo "$raw" | python -c "import json,sys; print(','.join(json.load(sys.stdin).get('tools',[])))")
  sid=$(echo "$raw" | python -c "import json,sys; print(json.load(sys.stdin).get('session',''))")

  # Show response
  printf "${c_green}  Antwort (%sms):${c_reset} %s\n" "$elapsed" "$(echo "$msg" | head -c 250)"
  [ -n "$tools" ] && printf "${c_yellow}  Tools:${c_reset} %s\n" "$tools"

  # Check expected tool
  local ok=1
  if [ -n "$expected_tool" ]; then
    if [[ "$tools" == *"$expected_tool"* ]]; then
      printf "${c_green}  ✓ Tool '${expected_tool}' wurde aufgerufen${c_reset}\n"
    else
      printf "${c_red}  ✗ Tool '${expected_tool}' wurde NICHT aufgerufen${c_reset}\n"
      ok=0
    fi
  fi
  if [ -n "$expected_sub" ]; then
    if [[ "$msg" == *"$expected_sub"* ]]; then
      printf "${c_green}  ✓ Antwort enthält '%s'${c_reset}\n" "$expected_sub"
    else
      printf "${c_red}  ✗ Antwort enthält NICHT '%s'${c_reset}\n" "$expected_sub"
      ok=0
    fi
  fi
  [ "$ok" -eq 1 ] && pass=$((pass + 1)) || fail=$((fail + 1))

  # Export session for chaining
  LAST_SESSION="$sid"
}

# ═══════════════════════════════════════════════════════════════════════
printf "${c_bold}${c_blue}"
echo "════════════════════════════════════════════════════════════════"
echo "  NEXO Fabric AI Agent — Funktionstest"
echo "  Provider: Ollama (llama3.2:3b on GPU)"
echo "  Daten: Acme Swiss Consulting (5+ Customers, 12 Invoices, 8 Tickets)"
echo "════════════════════════════════════════════════════════════════"
printf "${c_reset}"

# ── Tool 1: getOntologySchema ──────────────────────────────────────────
run "Tool: getOntologySchema" \
    "Welche Object Types gibt es im System?" \
    "getOntologySchema" \
    "Customer"

# ── Tool 2: aggregateObjects (COUNT) ───────────────────────────────────
run "Tool: aggregateObjects COUNT" \
    "Wie viele Customer gibt es insgesamt?" \
    "aggregateObjects" \
    "8"

run "Tool: aggregateObjects auf Tickets" \
    "Wie viele Tickets haben wir?" \
    "aggregateObjects" \
    ""

# ── Tool 3: searchObjects ──────────────────────────────────────────────
run "Tool: searchObjects einfach" \
    "Such mir alle Customer" \
    "searchObjects" \
    ""

run "Tool: searchObjects semantisch" \
    "Zeig mir IT-Firmen aus unseren Kunden" \
    "" \
    ""

# ── Tool 4: callFunction mit konkreten Zahlen ──────────────────────────
run "Tool: callFunction (direkte Argumente)" \
    "Ruf die calculateRisk Function auf mit revenue=30000 und overdueInvoiceCount=3" \
    "callFunction" \
    ""

# ── Multi-turn Session ──────────────────────────────────────────────────
SESSION_ID=""
run "Multi-Turn: Session starten" \
    "Starte eine neue Analyse der Kundenbasis" \
    "" \
    "" \
    ""
SESSION_ID="$LAST_SESSION"

run "Multi-Turn: Follow-up (gleiche Session)" \
    "Und wie viele davon sind in Region 'Zuerich'?" \
    "" \
    "" \
    "$SESSION_ID"

# ── Komplex: Agent muss zwei Tools kombinieren ─────────────────────────
run "Komplex: Schema + Count" \
    "Welche Object Types hast du und wie viele Objects pro Typ?" \
    "" \
    ""

# ── Schweizerdeutsch / Dialekt ─────────────────────────────────────────
run "Schweizerdeutsch" \
    "Wieviel Kunde hämmer?" \
    "" \
    "8"

# ── Function mit Custom Logic ──────────────────────────────────────────
run "Function: low risk" \
    "Was ist der Risk-Score bei revenue 500000 und 0 offenen Rechnungen?" \
    "callFunction" \
    ""

# ── Summary ────────────────────────────────────────────────────────────
printf "\n${c_bold}${c_blue}════════════════════════════════════════════════════════════════${c_reset}\n"
printf "${c_bold}  Ergebnis:${c_reset}  ${c_green}${pass} pass${c_reset}  /  "
[ "$fail" -gt 0 ] && printf "${c_red}%d fail${c_reset}  /  " "$fail"
printf "${total} total\n"
printf "${c_bold}${c_blue}════════════════════════════════════════════════════════════════${c_reset}\n\n"

# ── Post-test: Audit Log einsehen ──────────────────────────────────────
printf "${c_bold}Agent Audit Log (letzte 5):${c_reset}\n"
curl -s -X POST "$API/graphql" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query":"{getAgentAuditLog(limit:5){userMessage performedAt}}"}' \
  | python -c "
import json, sys
try:
  d = json.load(sys.stdin)
  for e in (d.get('data', {}).get('getAgentAuditLog') or [])[:5]:
    msg = e.get('userMessage','')[:70]
    ts = e.get('performedAt','')
    print(f'  · {ts} | {msg}')
except Exception as ex:
  print('  (audit log parse error:', ex, ')')
"

printf "\n${c_bold}GPU Status:${c_reset}\n"
# Show GPU status if ollama CLI is on PATH
if command -v ollama >/dev/null 2>&1; then
  ollama ps 2>&1 | tail -3
fi
