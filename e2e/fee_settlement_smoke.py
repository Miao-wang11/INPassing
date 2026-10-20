#!/usr/bin/env python3
"""Real MySQL + Spring Boot + Redis trade settlement / refund smoke tests.
Only run against ephemeral CI containers with SHUNSHOU_DEMO_SEED=true.
"""
from decimal import Decimal
from student_dual_role_smoke import api, ok, sql

def cash(query):
    return Decimal(sql(query))

def main():
    # Use real BCrypt hashes + seeded pay password. These accounts are CI-only.
    for name in ("ci_fee_publisher", "ci_fee_runner"):
        sql("INSERT INTO user (username,password,pay_password,nickname,status,is_certify,balance) "
            f"SELECT '{name}',password,pay_password,'CI Accounting',1,2,100 "
            "FROM user WHERE username='testuser2'")

    publisher_id = int(sql("SELECT id FROM user WHERE username='ci_fee_publisher'"))
    runner_id = int(sql("SELECT id FROM user WHERE username='ci_fee_runner'"))
    publisher = ok(api("POST", "/user/login", {
        "loginType":1, "username":"ci_fee_publisher", "password":"123456"
    }), "accounting publisher login")["token"]
    runner = ok(api("POST", "/user/login", {
        "loginType":1, "username":"ci_fee_runner", "password":"123456"
    }), "accounting worker login")["token"]

    ok(api("POST","/user/address/save",{
        "contactName":"CI 同学","contactPhone":"13800000002",
        "detail":"CI 测试宿舍 1001","sex":"男","isDefault":1
    },publisher),"save publisher address")
    addresses=ok(api("GET","/user/address/list",token=publisher),"list addresses")
    assert isinstance(addresses,list) and addresses and addresses[0].get("id"), addresses
    address_id=addresses[0]["id"]

    def publish(tip="2.50",delivery="1.00",goods="6.40"):
        return ok(api("POST", "/task/publish", {
            "type":"代购物品", "subType":"校内代购",
            "publicDesc":"CI 模拟商品采购费用分账测试",
            "tip":tip, "deliveryFee":delivery, "productCost":goods,
            "pickupAddress":"校园超市","deliveryAddressId":address_id,
            "expireMinutes":30,
            "payPassword":"123456"
        }, publisher), "task publish")["taskId"]

    task_id = publish()
    assert cash(f"SELECT balance FROM user WHERE id={publisher_id}") == Decimal("90.10")
    order_id = ok(api("POST", f"/order/accept/{task_id}", token=runner),
                  "worker accepts task")["orderId"]

    # Proof is only a dummy URL; this stage does NOT audit purchased goods.
    proof = {"imageUrls":["https://example.invalid/ci-proof.jpg"]}
    ok(api("POST", f"/order/{order_id}/pickup", proof, runner), "pickup proof")
    ok(api("POST", f"/order/{order_id}/deliver", proof, runner), "delivery proof")
    ok(api("POST", f"/order/{order_id}/confirm", token=publisher), "buyer confirms")

    # Prepaid total moved to worker, but only the SERVICE part is earnings.
    assert cash(f"SELECT balance FROM user WHERE id={runner_id}") == Decimal("109.90")
    ledger = sql(f"SELECT CONCAT(type, ':', CAST(amount AS CHAR)) "
                 f"FROM transaction_record WHERE task_id={int(task_id)} "
                 f"AND user_id={runner_id} ORDER BY type").splitlines()
    assert ledger == ["2:3.50","6:6.40"], f"Incorrect accounting categories: {ledger}"
    levels = sql(f"SELECT CONCAT(balance_before, ':', balance_after) "
                 f"FROM transaction_record WHERE task_id={int(task_id)} "
                 f"AND user_id={runner_id} ORDER BY type").splitlines()
    assert levels == ["100.00:103.50","103.50:109.90"], levels

    summary = ok(api("GET","/user/transactions/summary",token=runner),
                 "worker wallet summary")
    assert Decimal(str(summary["total_income"])) == Decimal("3.50"), summary
    assert Decimal(str(summary["shopping_reimbursement"])) == Decimal("6.40"), summary
    repeat = api("POST", f"/order/{order_id}/confirm", token=publisher)
    assert repeat.get("code") != 1, f"Repeat confirmation unexpectedly succeeded: {repeat}"
    assert sql(f"SELECT COUNT(*) FROM transaction_record "
               f"WHERE task_id={task_id} AND user_id={runner_id}") == "2"
    assert cash(f"SELECT balance FROM user WHERE id={runner_id}") == Decimal("109.90")

    # Cancel an unaccepted task: refund ENTIRE prepaid amount once.
    cancelled_id = publish(tip="1.00",delivery="0.50",goods="3.00")
    assert cash(f"SELECT balance FROM user WHERE id={publisher_id}") == Decimal("85.60")
    ok(api("PUT", f"/task/{cancelled_id}/cancel",{"reason":"CI取消"},publisher),
       "publisher cancels unaccepted task")
    assert cash(f"SELECT balance FROM user WHERE id={publisher_id}") == Decimal("90.10")
    assert sql(f"SELECT COUNT(*) FROM transaction_record WHERE task_id={int(cancelled_id)} "
               "AND type=5") == "1"
    repeated_cancel = api("PUT",f"/task/{cancelled_id}/cancel",{"reason":"重复"},publisher)
    assert repeated_cancel.get("code") != 1
    assert sql(f"SELECT COUNT(*) FROM transaction_record WHERE task_id={int(cancelled_id)} "
               "AND type=5") == "1"

    # Invalid money must be rejected before any payment record is made.
    bad = api("POST","/task/publish",{
       "type":"校内代办","subType":"图书馆还书","tip":"0.001",
       "deliveryFee":"0.00","productCost":"0.00",
       "pickupAddress":"图书馆","payPassword":"123456"
    },publisher)
    assert bad.get("code") != 1, "Amount with more than two decimals should fail"
    print("PASS: prepaid 9.90 = service income 3.50 + shopping reimbursement 6.40")
    print("PASS: wallet income excludes non-income shopping budget")
    print("PASS: confirm idempotent, two ledger rows, stable balances")
    print("PASS: cancelling an unaccepted purchase task refunds full 4.50 once")
    print("PASS: fractional-cent amount rejected")

if __name__ == "__main__":
    main()
