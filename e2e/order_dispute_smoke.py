#!/usr/bin/env python3
"""Real authenticated HTTP, MySQL ledger and access-control disputes regression."""
from decimal import Decimal
from student_dual_role_smoke import api,ok,sql

def student(name):
    return ok(api('POST','/user/login',
        {'loginType':1,'username':name,'password':'123456'}),name+' login')['token']
def money(uid):
    return Decimal(sql(f"SELECT balance FROM user WHERE id={uid}"))
def prepare(name):
    sql(f"INSERT INTO user (username,password,pay_password,nickname,status,is_certify,balance,campus_id) "+
        f"SELECT '{name}',password,pay_password,'CI Aftersale',1,2,100,campus_id "+
        "FROM user WHERE username='testuser2'")
    return int(sql(f"SELECT id FROM user WHERE username='{name}'"))
def create_delivered(buyer,runner,idx):
    t=ok(api('POST','/task/publish',{
        'type':'校内代办','subType':'图书馆还书','publicDesc':'CI仲裁订单-'+idx,
        'pickupAddress':'校园图书馆','tip':10,'deliveryFee':0,'productCost':0,
        'expireMinutes':30,'payPassword':'123456'},buyer),'publish dispute task')['taskId']
    order=ok(api('POST',f'/order/accept/{t}',token=runner),'accept dispute task')['orderId']
    proof={'imageUrls':['https://example.invalid/proof.jpg']}
    ok(api('POST',f'/order/{order}/pickup',proof,runner),'pick up task')
    ok(api('POST',f'/order/{order}/deliver',proof,runner),'deliver task')
    return int(t),int(order)

def main():
    uid=prepare('ci_dispute_customer')
    rid=prepare('ci_dispute_provider')
    xid=prepare('ci_dispute_stranger')
    customer=student('ci_dispute_customer')
    provider=student('ci_dispute_provider')
    stranger=student('ci_dispute_stranger')
    admin=ok(api('POST','/admin/login',{'username':'admin',
        'password':'ci-admin-password-not-for-production'}),'admin login')['token']
    # api uses user auth header, admin must use the token header.
    from campus_isolation_smoke import admin_call
    import urllib.error
    import json
    def admin_expected_failure(method, path, payload=None):
        # Spring returns HTTP 400 for some business/validation failures; the
        # original campus helper assumes 2xx and is only for success cases.
        try:
            return admin_call(method,path,admin,payload).get('code')!=1
        except urllib.error.HTTPError as exc:
            if exc.code not in (400,403,409):
                raise
            try:
                parsed=json.loads(exc.read().decode('utf-8'))
                return parsed.get('code')!=1
            except ValueError:
                return True

    first_task,first_order=create_delivered(customer,provider,'REFUND')
    assert money(uid)==Decimal('90.00')
    assert money(rid)==Decimal('100.00')
    reason={'reason':'实际收到的物品存在损坏，申请核验与退款',
            'evidenceUrls':['https://example.invalid/evidence-1.jpg']}
    assert api('POST',f'/disputes/orders/{first_order}',reason,stranger).get('code')!=1
    opened=ok(api('POST',f'/disputes/orders/{first_order}',reason,customer),'open dispute')
    case_id=opened['id']
    assert opened['status']==1
    replay=ok(api('POST',f'/disputes/orders/{first_order}',reason,customer),'replay application')
    assert replay['id']==case_id
    assert sql(f"SELECT COUNT(*) FROM order_dispute WHERE order_id={first_order}")=='1'
    assert sql(f"SELECT COUNT(*) FROM dispute_event WHERE dispute_id={case_id}")=='1'
    assert api('GET',f'/disputes/{case_id}',token=stranger).get('code')!=1
    assert api('GET',f'/disputes/{case_id}/events',token=stranger).get('code')!=1
    assert api('POST',f'/order/{first_order}/confirm',token=customer).get('code')!=1
    assert admin_expected_failure('PUT',f'/admin/orders/{first_order}/status?status=4')
    assert money(rid)==Decimal('100.00')
    assert sql(f"SELECT status FROM task_order WHERE id={first_order}")=='3'
    assert api('POST',f'/admin/disputes/{case_id}/decision',
        {'decision':'REFUND','note':'无权限操作'},stranger).get('code')!=1
    print('PASS: only trade parties can apply, repeated apply idempotent; open dispute freezes manual/admin settlement')

    decided=ok(admin_call('POST',f'/admin/disputes/{case_id}/decision',admin,
        {'decision':'REFUND','note':'双方材料显示物品问题未解决，模拟全额退回预付款'}),'admin full refund')
    assert decided['status']==3
    assert sql(f"SELECT status FROM task_order WHERE id={first_order}")=='5'
    assert sql(f"SELECT status FROM task WHERE id={first_task}")=='6'
    assert money(uid)==Decimal('100.00')
    assert money(rid)==Decimal('100.00')
    assert sql(f"SELECT COUNT(*) FROM transaction_record WHERE task_id={first_task} AND type=5")=='1'
    assert sql(f"SELECT current_orders FROM runner_profile WHERE user_id={rid}")=='0'
    assert sql(f"SELECT COUNT(*) FROM dispute_event WHERE dispute_id={case_id}")=='2'
    assert admin_expected_failure('POST',f'/admin/disputes/{case_id}/decision',
        {'decision':'REFUND','note':'重复退款'})
    assert money(uid)==Decimal('100.00')
    assert sql(f"SELECT COUNT(*) FROM transaction_record WHERE task_id={first_task} AND type=5")=='1'
    print('PASS: admin full refund is atomic, terminal and non-repeatable with immutable history')

    next_task,next_order=create_delivered(customer,provider,'RELEASE')
    assert money(uid)==Decimal('90.00')
    another=ok(api('POST',f'/disputes/orders/{next_order}',
        {'reason':'希望管理员检查凭证','evidenceUrls':[]},provider),'provider opens dispute')
    assert another['applicantId']==rid
    assert api('POST',f'/order/{next_order}/confirm',token=customer).get('code')!=1
    decision=ok(admin_call('POST',f'/admin/disputes/{another["id"]}/decision',admin,
        {'decision':'REJECT','note':'凭证合法且已按约定完成服务'}),'reject dispute')
    assert decision['status']==2
    assert sql(f"SELECT status FROM task_order WHERE id={next_order}")=='3'
    ok(api('POST',f'/order/{next_order}/confirm',token=customer),'complete after reject')
    assert sql(f"SELECT status FROM task_order WHERE id={next_order}")=='4'
    assert money(rid)==Decimal('110.00')
    assert money(uid)==Decimal('90.00')
    assert sql(f"SELECT COUNT(*) FROM transaction_record WHERE task_id={next_task} AND type=2")=='1'
    assert sql(f"SELECT COUNT(*) FROM transaction_record WHERE task_id={next_task} AND type=5")=='0'
    print('PASS: rejecting a dispute resumes existing normal single settlement')
    rows=ok(admin_call('GET','/admin/disputes?status=3&page=1&size=20',admin),'admin disputed list')
    assert any(int(v['id'])==case_id for v in rows['records'])
    print('PASS: admin filtered queue is backed by real orders and ledger')

if __name__=='__main__':
    main()
