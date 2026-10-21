"""Genuine disposable Compose E2E via Java JWT -> Python LangGraph -> Java business.

SAFETY: Only runs with SHUNSHOU_E2E_DISPOSABLE=1, CI-seeded fake users and
isolated ephemeral MySQL volume. No production credentials or real payments.
"""
import os
from decimal import Decimal
import httpx

BASE = "http://backend:8080/api"
assert os.environ.get("SHUNSHOU_E2E_DISPOSABLE") == "1", "Refusing non-disposable environment"


def call(client, method, path, *, payload=None, token=None, ok=True):
    headers = {"authentication": token} if token else {}
    response = client.request(method, BASE + path, json=payload, headers=headers)
    try:
        parsed = response.json()
    except ValueError:
        raise AssertionError(f"{path}: invalid JSON status={response.status_code}") from None
    if ok:
        assert response.status_code == 200 and parsed.get("code") == 1, (
            path, response.status_code, parsed.get("code"), parsed.get("msg"))
    else:
        assert response.status_code >= 400 or parsed.get("code") != 1, (
            "unauthorized operation unexpectedly succeeded", path)
    return parsed.get("data") if ok else parsed


def amount(value):
    return Decimal(str(value)).quantize(Decimal("0.01"))


with httpx.Client(timeout=40, follow_redirects=False) as client:
    login = call(client, "POST", "/user/login", payload={
        "loginType": 1, "username": "testuser", "password": "123456"})
    token, owner = login["token"], login["userId"]
    assert isinstance(token, str) and len(token) > 40 and owner > 0
    other = call(client, "POST", "/user/login", payload={
        "loginType": 1, "username": "testuser2", "password": "123456"})
    cross = call(client, "POST", "/user/login", payload={
        "loginType": 1, "username": "testuser3", "password": "123456"})

    initial = call(client, "GET", "/user/info", token=token)
    assert initial["campusId"] == 10001 and initial["isCertify"] == 2
    assert amount(initial["balance"]) == Decimal("100.00"), "Disposable fresh DB required"

    # Read-only user context traverses Java JWT -> HMAC -> Python -> signed Java GET.
    response = call(client, "POST", "/agent/chat", token=token,
                    payload={"message": "帮我打印30页资料送到教学楼，预算10元"})
    assert response["status"] == "needs_clarification", response.get("status")
    assert response["session_id"]
    assert "payPassword" not in str(response)

    call(client, "POST", "/user/address/save", token=token, payload={
        "contactName": "测试收件人", "contactPhone": "13800000011",
        "detail": "三号宿舍楼301室", "isDefault": 1})
    addresses = call(client, "GET", "/user/address/list", token=token)
    assert addresses and addresses[0]["id"] > 0
    address = addresses[0]["id"]
    draft = {"mode": "PUBLIC_TASK", "task": {
        "type": "校内代办", "subType": "资料打印",
        "publicDesc": "CI隔离测试：打印30页资料",
        "pickupAddress": "图书馆", "deliveryAddressId": address,
        "tip": 5, "deliveryFee": 0, "productCost": 0, "expireMinutes": 60
    }}
    prepared = call(client, "POST", "/agent/drafts", token=token, payload=draft)
    operation_id, confirmation = prepared["operationId"], prepared["confirmToken"]
    assert amount(prepared["total"]) == Decimal("5.00")
    assert len(confirmation) == 64

    before = call(client, "GET", f"/agent/operations/{operation_id}", token=token)
    assert before["status"] == "PREPARED" and before["taskId"] is None
    assert amount(call(client, "GET", "/user/info", token=token)["balance"]) == Decimal("100.00")
    # Other account and other campus are not allowed to inspect this operation.
    call(client, "GET", f"/agent/operations/{operation_id}", token=other["token"], ok=False)
    call(client, "GET", f"/agent/operations/{operation_id}", token=cross["token"], ok=False)

    # Wrong password must neither publish nor debit the wallet.
    call(client, "POST", f"/agent/operations/{operation_id}/confirm", token=token,
         payload={"confirmToken": confirmation, "payPassword": "incorrect"}, ok=False)
    assert amount(call(client, "GET", "/user/info", token=token)["balance"]) == Decimal("100.00")
    assert call(client, "GET", f"/agent/operations/{operation_id}",
                token=token)["status"] == "PREPARED"

    confirm = {"confirmToken": confirmation, "payPassword": "123456"}
    completed = call(client, "POST", f"/agent/operations/{operation_id}/confirm",
                     token=token, payload=confirm)
    assert completed["status"] == "SUCCEEDED"
    task_id = completed["taskId"]
    assert isinstance(task_id, int) and task_id > 0
    assert amount(call(client, "GET", "/user/info", token=token)["balance"]) == Decimal("95.00")

    repeat = call(client, "POST", f"/agent/operations/{operation_id}/confirm",
                  token=token, payload=confirm)
    assert repeat["taskId"] == task_id and repeat["status"] == "SUCCEEDED"
    assert amount(call(client, "GET", "/user/info", token=token)["balance"]) == Decimal("95.00")

    confirmed = call(client, "GET", f"/agent/operations/{operation_id}", token=token)
    assert confirmed["taskId"] == task_id and confirmed["status"] == "SUCCEEDED"

    # Java's task detail proves the actual record exists, scoped to campus.
    details = call(client, "GET", f"/task/{task_id}", token=token)
    assert details is not None
    print("PASS: JWT->Python->Java read-only signed flow, verified campus, "
          "draft without debit, denied foreign access, wrong password rollback, "
          "one real TaskService publish/payment, repeat confirmation same task; "
          "all on disposable demo data")
