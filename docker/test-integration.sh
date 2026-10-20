#!/bin/bash
# Integration tests for runningerrands API
# Usage: bash test-integration.sh [base_url]
set -u
BASE="${1:-http://localhost:8080}"
PASS=0 FAIL=0

pass() { echo "  [PASS] $1"; ((PASS++)); }
fail() { echo "  [FAIL] $1"; ((FAIL++)); }

echo "================================================"
echo " Integration Tests - runningerrands API"
echo " Base: $BASE     $(date '+%H:%M:%S')"
echo "================================================"

# ----- 1. Upload endpoint (auth enforcement) -----
echo ""
echo "--- 1. Upload Auth Enforcement ---"
R1=$(curl -s --max-time 10 -X POST "$BASE/api/common/upload" \
  -F "file=@/dev/null" 2>/dev/null)
if echo "$R1" | grep -q '"code":0'; then
  pass "Upload without auth returns code=0 (auth enforced)"
else
  fail "Upload: expected code=0, got: $R1"
fi

# ----- 2. Admin Login (default credentials) -----
echo ""
echo "--- 2. Admin Login ---"
R2=$(curl -s --max-time 10 -X POST "$BASE/api/admin/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}')
CODE2=$(echo "$R2" | grep -o '"code":[0-9]*' | head -1 | cut -d: -f2)
if [ "$CODE2" = "1" ]; then
  pass "Login succeeds (code=1)"
else
  fail "Login failed: $(echo "$R2" | head -c 120)"
fi

# ----- 3. Public Announcement -----
echo ""
echo "--- 3. Public Announcement ---"
HTTP3=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$BASE/api/common/announcement")
[ "$HTTP3" = "200" ] && pass "Returns HTTP 200" || fail "Got HTTP $HTTP3"

# ----- 4. SMS Send (no Aliyun configured) -----
echo ""
echo "--- 4. SMS Send ---"
R4=$(curl -s --max-time 10 -X POST "$BASE/api/user/send" \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800000001","operation":"login"}')
if echo "$R4" | grep -qE '"code":0|"msg":"(.*失败|.*错误|.*繁忙)"'; then
  pass "SMS fails gracefully (no SMS provider in Docker)"
else
  fail "SMS unexpected: $(echo "$R4" | head -c 120)"
fi

# ----- 5. Wrong password lockout (5 attempts = 300s lock) -----
echo ""
echo "--- 5. Account Lockout ---"
echo -n "  7 attempts:"
LOCK_SEEN=0
for i in $(seq 1 7); do
  R5=$(curl -s --max-time 5 -X POST "$BASE/api/admin/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrongpassword"}')
  # "登录失败次数过多" = locked; "用户名或密码错误" = just wrong
  if echo "$R5" | grep -q "登录失败次数过多"; then
    echo -n "L"; ((LOCK_SEEN++))
  else
    echo -n "."
  fi
  sleep 0.2
done
echo ""
[ "$LOCK_SEEN" -ge 1 ] \
  && pass "Account locked after repeated wrong logins ($LOCK_SEEN lock responses)" \
  || fail "Lockout NOT detected"

# ----- 6. Admin Refresh & Token Rotation -----
echo ""
echo "--- 6. Admin Refresh & Token Rotation ---"
# Clear previous state
docker exec rr-redis redis-cli -n 1 KEYS "admin:refresh:token:1:*" 2>/dev/null | while read k; do docker exec rr-redis redis-cli -n 1 DEL "$k" 2>/dev/null; done 2>/dev/null || true
docker exec rr-redis redis-cli -n 1 DEL "admin:login:fail:admin" 2>/dev/null || true

# Login and get refresh token
R6_LOGIN=$(curl -s --max-time 10 -X POST "$BASE/api/admin/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}')
R6_REFRESH=$(echo "$R6_LOGIN" | grep -o '"refreshToken":"[^"]*"' | cut -d'"' -f4)

if [ -z "$R6_REFRESH" ]; then
  fail "Could not extract refresh token"
else
  echo "  Token extracted (${#R6_REFRESH} chars)"

  # First refresh should succeed (200)
  R6A=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 -X POST "$BASE/api/admin/refresh" \
    -H "Content-Type: application/json" \
    -H "X-Refresh-Token: $R6_REFRESH" \
    -d '{}')

  # Second refresh with same token should fail (401 - token rotation)
  R6B=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 -X POST "$BASE/api/admin/refresh" \
    -H "Content-Type: application/json" \
    -H "X-Refresh-Token: $R6_REFRESH" \
    -d '{}')

  [ "$R6A" = "200" ] && pass "First refresh succeeds (200)" || fail "First refresh: got $R6A"
  [ "$R6B" = "401" ] && pass "Second refresh with same token fails (401, rotation works)" || fail "Second refresh: got $R6B"
fi

# ----- Summary -----
echo ""
echo "================================================"
echo " Results: $PASS passed, $FAIL failed"
echo "================================================"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
