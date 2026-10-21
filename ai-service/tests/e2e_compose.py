"""Phase 6 isolated live Java/Python/MySQL/Redis HTTP business acceptance.

Never run on real user databases. Generates no personal data. Prints no auth,
payment credential, confirmation token, participant ID or private address.
"""
import os
import uuid
from decimal import Decimal

import httpx


def exercise():
    if os.environ.get("SHUNSHOU_E2E_ISOLATED") != "yes":
        raise RuntimeError("E2E requires explicit isolated disposable database flag")
    credential = os.environ["SHUNSHOU_E2E_DEMO_PASSWORD"]
    assert credential.isdigit() and len(credential) == 6
    root = "http://backend:8080/api"
    with httpx.Client(timeout=25.0, follow_redirects=False) as client:
        def request(method, path, token=None, data=None, ok=True):
            headers = {"authentication": token} if token else {}
            res = client.request(method, root + path, headers=headers, json=data)
            doc = res.json()
            assert isinstance(doc, dict)
            if ok:
                assert res.status_code == 200 and doc.get("code") == 1, (
                    path, res.status_code, doc.get("code"), str(doc.get("msg", ""))[:100])
                return doc.get("data")
            assert res.status_code >= 400 or doc.get("code") != 1, (
                "Unexpected successful forbidden operation", path)
            return None

        def login(name):
            result = request("POST", "/user/login", data={
                "loginType": 1, "username": name, "password": credential})
            assert isinstance(result["token"], str) and len(result["token"]) > 20
            return result["token"]

        owner, peer, outsider = (login(name) for name in (
            "testuser", "testuser2", "testuser3"))
        request("POST", "/agent/chat", data={"message": "帮我打印"}, ok=False)
        account = request("GET", "/user/info", owner)
        assert account["campusId"] == 10001
        before = Decimal(str(account["balance"]))
        location_label = "CI-ISOLATED-" + str(uuid.uuid4())[:8]
        request("POST", "/user/address/save", owner, {
            "contactName": "仅测试", "contactPhone": "13800000001",
            "detail": location_label, "isDefault": 0})
        addresses = request("GET", "/user/address/list", owner)
        owned = [a for a in addresses if a.get("detail") == location_label]
        assert len(owned) == 1
        address_id = int(owned[0]["id"])

        # The Python Graph cannot authorize use of another account's address.
        other_draft = {"mode": "PUBLIC_TASK", "task": {
            "type": "校内代办", "subType": "资料打印",
            "publicDesc": "隔离测试", "pickupAddress": "图书馆",
            "deliveryAddressId": address_id, "tip": 5,
            "deliveryFee": 0, "productCost": 0, "expireMinutes": 60}}
        request("POST", "/agent/drafts", peer, other_draft, ok=False)
        request("POST", "/agent/drafts", outsider, other_draft, ok=False)

        # Genuine Java JWT proxy -> signed Python LangGraph -> Java offer Tool.
        utterance = (f"帮我打印30页资料，取件地点：图书馆，地址ID {address_id}，"
                     "小费5元，配送费0元，商品费0元")
        initial = request("POST", "/agent/chat", owner, {"message": utterance})
        assert initial["status"] == "choose_mode", initial.get("status")
        selected = request("POST", "/agent/chat", owner, {
            "session_id": initial["session_id"], "choice": "public"})
        assert selected["status"] == "ready_for_java_preview"
        draft = selected["preview_input"]
        assert draft["mode"] == "PUBLIC_TASK"
        assert "payPassword" not in str(draft)
        prepared = request("POST", "/agent/drafts", owner, draft)
        assert Decimal(str(prepared["total"])) == Decimal("5")
        assert Decimal(str(prepared["serviceFee"])) == Decimal("5")
        opid = prepared["operationId"]
        assert len(opid) == 36 and len(prepared["confirmToken"]) == 64
        assert Decimal(str(request("GET", "/user/info", owner)["balance"])) == before
        pending = request("GET", f"/agent/operations/{opid}", owner)
        assert pending["status"] == "PREPARED"
        request("GET", f"/agent/operations/{opid}", peer, ok=False)
        request("GET", f"/agent/operations/{opid}", outsider, ok=False)

        path = f"/agent/operations/{opid}/confirm"
        request("POST", path, owner, {
            "confirmToken": "0" * 64, "payPassword": credential}, ok=False)
        request("POST", path, owner, {
            "confirmToken": prepared["confirmToken"],
            "payPassword": "000000" if credential != "000000" else "111111"}, ok=False)
        assert Decimal(str(request("GET", "/user/info", owner)["balance"])) == before

        body = {"confirmToken": prepared["confirmToken"], "payPassword": credential}
        good = request("POST", path, owner, body)
        assert good["status"] == "SUCCEEDED" and int(good["taskId"]) > 0
        after = Decimal(str(request("GET", "/user/info", owner)["balance"]))
        assert after == before - Decimal("5"), "Real wallet charged incorrectly"
        for _ in range(2):
            same = request("POST", path, owner, body)
            assert same["status"] == "SUCCEEDED" and same["taskId"] == good["taskId"]
            assert Decimal(str(request("GET", "/user/info", owner)["balance"])) == after
        result = request("GET", f"/agent/operations/{opid}", owner)
        assert result["taskId"] == good["taskId"]
        task = request("GET", f"/task/{good['taskId']}", owner)
        assert task["campusId"] == 10001
        request("GET", f"/task/{good['taskId']}", outsider, ok=False)

        route = request("POST", "/agent/route-match", owner, {
            "message": "明天下午五点从图书馆回三号宿舍，最多绕路十分钟"})
        assert route["status"] in ("no_candidates", "choose_task")
        assert all(not c["routeVerified"] and c["estimatedDetourMinutes"] is None
                   for c in route.get("unverified", []))
        print("LIVE_HTTP_PASS: JWT -> Java -> signed Python -> Java Tool ->"
              " MySQL draft -> real TaskService + wallet debit -> idempotent receipt")
        print("SECURITY_PASS: wrong token/password, other account, other campus,"
              " no JWT, no fabricated route time")


if __name__ == "__main__":
    exercise()
