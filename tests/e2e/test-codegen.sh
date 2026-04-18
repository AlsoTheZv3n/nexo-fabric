#!/usr/bin/env bash
# Tests the TypeScript SDK codegen against the seeded sandbox schema.
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f .token ]; then
  echo "No .token found — run ./seed.sh first"
  exit 1
fi
TOKEN=$(cat .token)

SDK="$SCRIPT_DIR/../fabric-sdk/typescript"
if [ ! -f "$SDK/dist/cli/generate.js" ]; then
  echo "SDK not built — building..."
  (cd "$SDK" && npm install --silent && npx tsc)
fi

echo "Running codegen with seeded schema..."
node "$SDK/dist/cli/generate.js" \
  --url "${NEXO_API_URL:-http://localhost:8082}" \
  --token "$TOKEN" \
  --out "$SCRIPT_DIR/generated/nexo-fabric.d.ts"

echo ""
echo "Preview of generated types:"
head -60 "$SCRIPT_DIR/generated/nexo-fabric.d.ts"
echo ""
echo "..."
echo ""
echo "✅  Generated at: $SCRIPT_DIR/generated/nexo-fabric.d.ts"
echo "   Total lines:  $(wc -l < "$SCRIPT_DIR/generated/nexo-fabric.d.ts")"
