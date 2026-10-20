#!/usr/bin/env python3
"""Real MySQL/Redis/Spring HTTP regression for one student, two trade roles.

Executed in an EPHEMERAL docker-compose CI with SHUNSHOU_DEMO_SEED=true only.
Every API operation uses the public HTTP interface and authenticated JWT.
"""
import json
import os
import subprocess
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"

def api(method, endpoint, payload=None, token=None):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["authentication"] = token
    request = urllib.request.Request(BASE + endpoint, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=12) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", "replace")
        try:
            return json.loads(raw)
        except ValueError:
            raise AssertionError(f"{method} {endpoint}: HTTP {exc.code}: {raw[:200]}") from exc

def ok(result, operation):
    assert result.get("code") == 1, f"{operation} failed: {result.get('msg')} (code={result.get('code')})"
    return result.get("data") or {}

def sql(statement):
    args = ["docker", "exec", "-e", "MYSQL_PWD=" + os.environ["MYSQL_ROOT_PASSWORD"],
            "rr-mysql", "mysql", "-N", "-B", "-uroot", "runningerrands", "-e", statement]
    return subprocess.check_output(args, text=True).strip()

def main():
    publisher = ok(api("POST", "/user/login",
             {"loginType":1,"username":"testuser","password":"123456"}), "publisher login")["token"]
    worker = ok(api("POST", "/user/login",
             {"loginType":1,"username":"testuser2","password":"123456"}), "student login")["token"]
    worker_id = sql("SELECT id FROM user WHERE username='testuser2'")
    assert worker_id and sql(f"SELECT COUNT(*) FROM runner_profile WHERE user_id={int(worker_id)}") == "0", (
        "Expected demo student with NO runner_profile before first accept")

    def publish(token=publisher):
        result = api("POST", "/task/publish", {
            "type":"校内代办", "subType":"图书馆还书",
            "publicDesc":"CI 校园学生双身份端到端验证",
            "tip":2.0,"deliveryFee":0,"productCost":0,
            "pickupAddress":"教学楼A座","contactName":"CI同学",
            "contactPhone":"13800000001","expireMinutes":30,
            "payPassword":"123456"
        }, token)
        return ok(result,"publish as student")["taskId"]

    task_id = publish()
    # A publisher cannot accept their own task; no second role can bypass this.
    own = api("POST", f"/order/accept/{task_id}", token=publisher)
    assert own.get("code") != 1, "A student must not accept their own task"

    result = ok(api("POST", f"/order/accept/{task_id}", token=worker),
                "first accept without runner application")
    assert result.get("orderId"), "No orderId returned from first accept"

    row = sql(f"SELECT verify_status,is_online,current_orders FROM runner_profile WHERE user_id={int(worker_id)}")
    assert row == "2\t1\t1", f"Unexpected automatically provisioned profile: {row!r}"
    assert sql(f"SELECT COUNT(*) FROM task_order WHERE task_id={int(task_id)} AND status<>5") == "1", (
        "Exactly one active order must exist for this task")

    # The losing repeated accept may not create an extra order.
    repeat = api("POST", f"/order/accept/{task_id}", token=worker)
    assert repeat.get("code") != 1, "Repeated accept must be rejected"
    assert sql(f"SELECT COUNT(*) FROM task_order WHERE task_id={int(task_id)} AND status<>5") == "1"

    # Reverse roles: user2 was the runner and now publishes. User1 published
    # earlier, becomes a runner with the SAME login account.
    reverse_task = publish(token=worker)
    ok(api("POST", "/runner/online", token=publisher), "existing runner goes online")
    reversed_order = ok(api("POST", f"/order/accept/{reverse_task}", token=publisher),
                        "original publisher becomes runner")
    assert reversed_order.get("orderId"), "Reverse-role acceptance returned no order"
    assert sql(f"SELECT COUNT(*) FROM task_order WHERE task_id={int(reverse_task)} AND status<>5") == "1"

    # Existing restrictions must NEVER be silently cleared by auto-bootstrap.
    sql(f"UPDATE runner_profile SET is_banned=1, credit_score=45 WHERE user_id={int(worker_id)}")
    second = publish()
    banned = api("POST", f"/order/accept/{second}", token=worker)
    assert banned.get("code") != 1, "Banned student must remain banned"
    row = sql(f"SELECT is_banned,credit_score FROM runner_profile WHERE user_id={int(worker_id)}")
    assert row == "1\t45", f"An existing restriction was overridden: {row!r}"
    print("PASS: certified student publishes; other certified student accepts")
    print("PASS: fresh profile created atomically without separate runner application")
    print("PASS: self-accept, duplicate accept and banned student rejected")
    print("PASS: both students swapped publisher and runner roles with one login each")
    print("PASS: existing credit and ban state preserved")

if __name__ == "__main__":
    main()
