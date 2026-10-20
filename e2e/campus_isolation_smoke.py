#!/usr/bin/env python3
"""Real campus tenant isolation across public catalog, student approval and task APIs."""
import json
import urllib.parse
import urllib.request
from student_dual_role_smoke import api, ok, sql

BASE="http://localhost:8080/api"

def admin_call(method, endpoint, token, payload=None):
    raw = json.dumps(payload,ensure_ascii=False).encode() if payload is not None else None
    req=urllib.request.Request(BASE+endpoint,data=raw,method=method,
             headers={"Content-Type":"application/json","token":token})
    with urllib.request.urlopen(req,timeout=15) as resp:
        return json.load(resp)

def login(username):
    return ok(api("POST","/user/login",
        {"loginType":1,"username":username,"password":"123456"}),username+" login")["token"]

def task(token, tag, lng="116.391", lat="39.907"):
    return ok(api("POST","/task/publish",{
       "type":"校内代办","subType":"图书馆还书",
       "publicDesc":"CI-CAMPUS-"+tag,
       "tip":"2.00","deliveryFee":"0.00","productCost":"0.00",
       "pickupAddress":"教学楼","pickupLng":lng,"pickupLat":lat,
       "expireMinutes":30,"payPassword":"123456"
    },token),"publish "+tag)["taskId"]

def contains(result, task_id):
    return any(int(q["taskId"])==int(task_id) for q in result.get("records",[]))

def main():
    schools=ok(api("GET","/campus/list"),"public campus listing")
    assert {10001,10002}.issubset({int(x["campusId"]) for x in schools}),schools
    admin=ok(api("POST","/admin/login",
          {"username":"admin","password":"ci-admin-password-not-for-production"}),
          "admin login")["token"]
    ok(admin_call("POST","/admin/campuses/schools",admin,{"name":"CI-University-For-E2E"}),
       "create school")
    school_id=int(sql("SELECT id FROM school WHERE name='CI-University-For-E2E'"))
    ok(admin_call("POST","/admin/campuses",admin,{"schoolId":school_id,"name":"CI-West-Campus-E2E"}),
       "create campus")
    assert len(ok(api("GET","/campus/list"),"new campus listed")) >= 3
    print("PASS: admin can create reference school/campus; public catalog sees them")

    east=login("testuser")
    west=login("testuser3")
    east_id=task(east,"EAST")
    west_id=task(west,"WEST")

    elist=ok(api("GET","/task/list?page=1&size=100",token=east),"east task hall")
    wlist=ok(api("GET","/task/list?page=1&size=100",token=west),"west task hall")
    assert contains(elist,east_id) and not contains(elist,west_id),elist
    assert contains(wlist,west_id) and not contains(wlist,east_id),wlist

    for token, own, other, tag in ((east,east_id,west_id,"EAST"),(west,west_id,east_id,"WEST")):
        q=urllib.parse.urlencode({"keyword":"CI-CAMPUS-"+tag})
        result=ok(api("GET","/task/search?"+q,token=token),"search campus")
        assert contains(result,own) and not contains(result,other)
        filtered=ok(api("GET","/task/filter?type="+urllib.parse.quote("校内代办")+
                        "&page=1&size=100",token=token),"filter campus")
        assert contains(filtered,own) and not contains(filtered,other)
        nearby=ok(api("GET","/task/nearby?lng=116.391&lat=39.907&radiusKm=2",
                      token=token),"nearby campus")
        assert contains(nearby,own) and not contains(nearby,other)
        assert api("GET",f"/task/{other}",token=token).get("code") != 1
        assert api("POST",f"/order/accept/{other}",token=token).get("code") != 1
    print("PASS: hall/search/filter/nearby/details/accept all isolate campuses")

    # Pending student can SELECT a campus, but cannot grant themselves membership.
    sql("INSERT INTO user (username,password,pay_password,nickname,status,is_certify,balance) "
        "SELECT 'ci_campus_pending',password,pay_password,'Onboarding Student',1,0,100 "
        "FROM user WHERE username='testuser2'")
    new_id=int(sql("SELECT id FROM user WHERE username='ci_campus_pending'"))
    newcomer=login("ci_campus_pending")
    ok(api("POST","/user/certify",{
       "realName":"CI学生","studentId":"CI2026","certImageUrl":"https://example.invalid/student.jpg",
       "campusId":10002
    },newcomer),"submit campus certification")
    values=sql(f"SELECT CONCAT(IFNULL(campus_id,'NULL'),':',pending_campus_id,':',is_certify) "
               f"FROM user WHERE id={new_id}")
    assert values=="NULL:10002:1",values
    assert api("GET","/task/list",token=newcomer).get("code") != 1
    ok(admin_call("PUT",f"/admin/users/{new_id}/certify?isCertify=2",admin),
       "admin approves student campus")
    assert sql(f"SELECT campus_id FROM user WHERE id={new_id}")=="10002"
    ok(api("PUT","/user/profile",{"campus":"计算机学院","campusId":10001},newcomer),
       "update college, not reviewed campus")
    assert sql(f"SELECT campus_id FROM user WHERE id={new_id}")=="10002"
    new_view=ok(api("GET","/task/list?page=1&size=100",token=newcomer),
                "newly verified west campus")
    assert contains(new_view,west_id) and not contains(new_view,east_id)
    print("PASS: pending campus not trusted before approval; profile update cannot spoof membership")

    # An existing approved user without campus binding also fails closed.
    sql("INSERT INTO user (username,password,pay_password,nickname,status,is_certify,balance) "
        "SELECT 'ci_legacy_unbound',password,pay_password,'Legacy Unbound',1,2,100 "
        "FROM user WHERE username='testuser2'")
    legacy=login("ci_legacy_unbound")
    assert api("GET","/task/list",token=legacy).get("code") != 1
    assert api("POST",f"/order/accept/{east_id}",token=legacy).get("code") != 1
    print("PASS: legacy unbound accounts denied until reviewed migration")

if __name__=="__main__":
    main()
