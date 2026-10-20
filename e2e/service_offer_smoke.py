#!/usr/bin/env python3
"""End-to-end service offers and directed prepaid bookings against real Docker stack."""
import concurrent.futures
from datetime import datetime, timedelta
from decimal import Decimal
from student_dual_role_smoke import api, ok, sql

def login(name):
    return ok(api("POST","/user/login",{"loginType":1,"username":name,"password":"123456"}),name+" login")["token"]

def insert(name, seed="testuser2", campus_override=None):
    campus_select = "campus_id" if campus_override is None else str(campus_override)
    sql("INSERT INTO user (username,password,pay_password,nickname,status,is_certify,balance,campus_id) "
        f"SELECT '{name}',password,pay_password,'CI Offer',1,2,100,{campus_select} "
        f"FROM user WHERE username='{seed}'")
    return int(sql(f"SELECT id FROM user WHERE username='{name}'"))

def cash(user_id):
    return Decimal(sql(f"SELECT balance FROM user WHERE id={user_id}"))

def offer(token,title,price="4.25"):
    # Wide enough for CI host vs container timezone differences; actual
    # stored DATETIME is read as LocalDateTime without Timestamp conversion.
    start=(datetime.now()-timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%S")
    end=(datetime.now()+timedelta(days=2)).strftime("%Y-%m-%dT%H:%M:%S")
    return ok(api("POST","/service-offers",{
        "category":"校内代办","subType":"图书馆还书",
        "title":title,"description":"校园服务橱窗真实交易回归测试",
        "servicePrice":price, "availableFrom":start,"availableUntil":end
    },token),"offer published")

def main():
    provider_id=insert("ci_offer_provider")
    buyer_id=insert("ci_offer_buyer")
    other_id=insert("ci_offer_other")
    outsider_id=insert("ci_offer_other_campus","testuser3")
    provider=login("ci_offer_provider")
    buyer=login("ci_offer_buyer")
    other=login("ci_offer_other")
    outsider=login("ci_offer_other_campus")

    old_order_count=int(sql("SELECT COUNT(*) FROM task_order"))
    old_task_count=int(sql("SELECT COUNT(*) FROM task"))
    offer_id=int(offer(provider,"CI可预约送还服务"))
    all_offers=ok(api("GET","/service-offers?page=1&size=20",token=buyer),"offer hall")
    assert any(int(o["id"])==offer_id for o in all_offers["records"]),all_offers
    assert not any(int(o["id"])==offer_id for o in
                   ok(api("GET","/service-offers",token=outsider),"outsider offers")["records"])
    assert api("GET",f"/service-offers/{offer_id}",token=outsider).get("code")!=1
    assert api("POST",f"/service-offers/{offer_id}/book",{
        "requestKey":"invalid-tenant-1","deliveryAddressId":1,"pickupAddress":"图书馆","payPassword":"123456"
    },outsider).get("code")!=1
    assert api("POST",f"/service-offers/{offer_id}/book",{
        "requestKey":"cannot-book-own-1","deliveryAddressId":1,"pickupAddress":"图书馆","payPassword":"123456"
    },provider).get("code")!=1
    print("PASS: offer owner vs visitor vs other campus isolation")

    ok(api("POST","/user/address/save",{
        "contactName":"买家同学","contactPhone":"13800000001",
        "detail":"CI校园宿舍A-01","sex":"男","isDefault":1
    },buyer),"create buyer delivery address")
    addresses=ok(api("GET","/user/address/list",token=buyer),"list buyer addresses")
    address_id=int(addresses[0]["id"])
    details={"requestKey":"ci_service_double_request_0001",
        "deliveryAddressId":address_id,
        "pickupAddress":"图书馆大厅",
        "shoppingBudget":"5.00","privateNote":"书本放在柜台",
        "payPassword":"123456"}

    # Provider is created as a certified student without a separate runner profile.
    assert sql(f"SELECT COUNT(*) FROM runner_profile WHERE user_id={provider_id}")=="0"
    first=ok(api("POST",f"/service-offers/{offer_id}/book",details,buyer),"directed first booking")
    task_id=int(first["taskId"]);order_id=int(first["orderId"])
    assert first["totalAmount"]==9.25 or Decimal(str(first["totalAmount"]))==Decimal("9.25")
    assert cash(buyer_id)==Decimal("90.75"),cash(buyer_id)
    assert int(sql(f"SELECT COUNT(*) FROM task_order WHERE id={order_id} AND runner_id={provider_id}"))==1
    assert sql(f"SELECT CONCAT(service_offer_id,':',reserved_runner_id,':',status) "
               f"FROM task WHERE id={task_id}")==f"{offer_id}:{provider_id}:2"
    stored_window=sql(f"SELECT CONCAT(available_from, ' -> ', available_until) FROM service_offer WHERE id={offer_id}")
    print("CI offer booking accepted with stored window:", stored_window)
    assert sql(f"SELECT COUNT(*) FROM payment_idempotent WHERE idempotent_key='PAY:task:{task_id}'")=="1"
    print("PASS: service offer creates one prepaid task assigned to provider, not public runner")

    responses=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        responses=list(pool.map(lambda _:api("POST",f"/service-offers/{offer_id}/book",details,buyer),range(6)))
    assert all(x.get("code")==1 and int(x["data"]["orderId"])==order_id for x in responses),responses
    assert cash(buyer_id)==Decimal("90.75")
    assert sql(f"SELECT COUNT(*) FROM service_booking WHERE task_id={task_id}")=="1"
    assert api("POST",f"/service-offers/{offer_id}/book",
        {**details,"shoppingBudget":"3.00"},buyer).get("code")!=1
    assert api("POST",f"/order/accept/{task_id}",token=other).get("code")!=1
    assert not any(int(t["taskId"])==task_id for t in
                   ok(api("GET","/task/list?page=1&size=100",token=other),"task hall")["records"])
    print("PASS: 6 duplicate booking requests replay same order, no extra charge")

    # Existing fulfillment and fee-split settlement must work unchanged.
    proof={"imageUrls":["https://example.invalid/ci-service-proof.jpg"]}
    ok(api("POST",f"/order/{order_id}/pickup",proof,provider),"service pickup proof")
    ok(api("POST",f"/order/{order_id}/deliver",proof,provider),"service completed proof")
    ok(api("POST",f"/order/{order_id}/confirm",token=buyer),"buyer confirms service")
    assert cash(provider_id)==Decimal("109.25")
    assert sql("SELECT CONCAT(type,':',CAST(amount AS CHAR)) FROM transaction_record "
               f"WHERE user_id={provider_id} AND task_id={task_id} ORDER BY type").splitlines()==[
                    "2:4.25","6:5.00"]
    print("PASS: existing fulfillment and settlement split 4.25 earnings / 5.00 budget")

    # Save a second offer, make provider unavailable, ensure ZERO partial debits.
    offline_offer=int(offer(provider,"CI离线服务不可扣款"))
    sql(f"UPDATE runner_profile SET is_online=0 WHERE user_id={provider_id}")
    before=cash(buyer_id)
    n_tasks=int(sql("SELECT COUNT(*) FROM task"))
    n_orders=int(sql("SELECT COUNT(*) FROM task_order"))
    invalid=api("POST",f"/service-offers/{offline_offer}/book",
        {**details,"requestKey":"ci_offline_rollback_0001"},buyer)
    assert invalid.get("code")!=1,invalid
    assert cash(buyer_id)==before
    assert int(sql("SELECT COUNT(*) FROM task"))==n_tasks
    assert int(sql("SELECT COUNT(*) FROM task_order"))==n_orders
    assert sql("SELECT COUNT(*) FROM service_booking WHERE request_key='ci_offline_rollback_0001'")=="0"
    print("PASS: runner offline rolls back task, ledger, booking, debit and order")

    # Offer editing and taking offline protect existing settlement while
    # removing offer from new public reservations.
    ok(api("PUT",f"/service-offers/{offer_id}/status?status=0",{},provider),"offline offer")
    assert api("GET",f"/service-offers/{offer_id}",token=buyer).get("code")!=1
    assert api("POST",f"/service-offers/{offer_id}/book",
        {**details,"requestKey":"ci_new_offline_0001"},buyer).get("code")!=1
    existing=ok(api("POST",f"/service-offers/{offer_id}/book",details,buyer),"idempotent replay offline")
    assert int(existing["orderId"])==order_id
    assert api("PUT",f"/service-offers/{offer_id}/status?status=1",{},buyer).get("code")!=1
    print("PASS: provider-only management, offline blocks new bookings but allows idempotent replay")
    print("PASS: ALL storefront real-DB checks completed")

if __name__=="__main__":
    main()
