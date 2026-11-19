#!/usr/bin/env bash
set -euo pipefail

ORDER=${ORDER_URL:-http://localhost:8081}
ORCH=${ORCHESTRATOR_URL:-http://localhost:8084}

json_field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" | head -1; }

run() {
  local name=$1 product=$2 qty=$3 amount=$4 faults=$5 expected=$6
  local saga status=""
  saga=$(curl -sf -X POST "$ORDER/orders" -H 'Content-Type: application/json' \
    -d "{\"customerId\":\"smoke\",\"productId\":\"$product\",\"quantity\":$qty,\"amount\":$amount,\"faults\":$faults}" | json_field sagaId)
  for _ in $(seq 1 30); do
    status=$(curl -sf "$ORCH/sagas/$saga" 2>/dev/null | json_field status || true)
    [[ "$status" == "COMPLETED" || "$status" == "COMPENSATED" || "$status" == "FAILED" ]] && break
    sleep 1
  done
  if [[ "$status" == "$expected" ]]; then
    echo "✅ $name → $status (saga ${saga:0:8})"
  else
    echo "❌ $name → expected $expected, got '${status}' (saga $saga)"; exit 1
  fi
}

run "Happy path"          p-1 1 120  '{}'                          COMPLETED
run "Payment rejected"    p-2 3 1350 '{}'                          COMPENSATED
run "Out of stock"        p-3 1 300  '{}'                          COMPENSATED
run "Duplicate messages"  p-4 2 80   '{"duplicateCommands":true}'  COMPLETED
run "Card declined"       p-1 1 120  '{"paymentRejected":true}'    COMPENSATED

echo "All scenarios OK"
