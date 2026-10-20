#!/usr/bin/env python3
"""Real MySQL wallet ledger read-model and dispute dashboard reconciliation."""
import json
from campus_isolation_smoke import admin_call
from student_dual_role_smoke import sql,ok,api

def main():
    admin=ok(api("POST","/admin/login",{
      "username":"admin","password":"ci-admin-password-not-for-production"
    }),"admin operations login")["token"]
    summary=ok(admin_call("GET","/admin/operations/summary",admin),"operations summary")
    assert summary["pendingCases"] == 0,summary
    assert summary["refundedCases"] >= 1 and summary["rejectedCases"] >= 1,summary
    assert float(summary["totalDisputeRefunds"]) >= 10.0,summary
    print("PASS: real dispute status and simulated refund ledger metrics")

    for dispute_status,state in [(3,6),(2,5)]:
        # Use the canonical dispute->task FK; raw Chinese task descriptions
        # are sensitive to mysql CLI locale/character-set settings in CI.
        task=int(sql(f"SELECT task_id FROM order_dispute WHERE status={dispute_status} ORDER BY id DESC LIMIT 1"))
        res=ok(admin_call("GET",f"/admin/operations/ledger/{task}",admin),"inspect arbitrated task")
        assert res["consistent"] is True,res
        assert int(res["taskStatus"])==state,res
        assert float(res["charged"])==10.0,res
        assert float(res["refunded"] if state==6 else res["disbursed"])==10.0,res
    print("PASS: refunded and settled orders reconcile against actual debit/credit records")

    task=int(sql("SELECT task_id FROM order_dispute WHERE status=3 ORDER BY id DESC LIMIT 1"))
    # CI only: inject a 1-cent ledger corruption and restore it, verifying
    # that the read-only diagnostics do not silently repair wallet balances.
    sql(f"UPDATE transaction_record SET amount=amount-0.01 WHERE task_id={task} AND type=1 LIMIT 1")
    try:
        bad=ok(admin_call("GET",f"/admin/operations/ledger/{task}",admin),"detect bad amount")
        assert bad["consistent"] is False and bad["issue"],bad
        rows=ok(admin_call("GET","/admin/operations/anomalies?limit=200",admin),"anomaly table")
        assert any(int(row["taskId"])==task for row in rows),rows
    finally:
        sql(f"UPDATE transaction_record SET amount=amount+0.01 WHERE task_id={task} AND type=1 LIMIT 1")
    good=ok(admin_call("GET",f"/admin/operations/ledger/{task}",admin),"verify restoration")
    assert good["consistent"] is True,good
    print("PASS: a synthetic 0.01 ledger mismatch appears in anomaly list and disappears when fixed")

if __name__=="__main__":
    main()
