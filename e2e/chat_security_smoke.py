#!/usr/bin/env python3
"""Chat privacy against actual HTTP controllers, MySQL and verified campus identities.

Uses only the disposable Docker CI stack created in verify.yml; never run against
production. The STOMP inbound channel is covered separately by JUnit tests.
"""
from student_dual_role_smoke import api, ok, sql


def identity(username):
    user_id = int(sql(f"SELECT id FROM user WHERE username='{username}'"))
    token = ok(api("POST", "/user/login", {
        "loginType": 1, "username": username, "password": "123456"
    }), "login " + username)["token"]
    return user_id, token


def reject(result, action):
    assert result.get("code") != 1, f"Unexpected access to {action}: {result!r}"


def main():
    # These fixture users are created by service_offer_smoke before this suite.
    alice_id, alice = identity("ci_offer_provider")
    bob_id, bob = identity("ci_offer_buyer")
    outsider_id, outsider = identity("ci_offer_other_campus")

    campuses = sql("SELECT COUNT(DISTINCT campus_id) FROM user "
                   f"WHERE id IN ({alice_id},{bob_id},{outsider_id})")
    assert campuses == "2", "Expected a real cross-campus fixture"
    assert sql(f"SELECT campus_id FROM user WHERE id={alice_id}") == sql(
        f"SELECT campus_id FROM user WHERE id={bob_id}")

    # The service is allowed to read same-campus, even with no prior conversation.
    ok(api("GET", f"/chat/history/{bob_id}?page=1&size=20", token=alice),
       "same-campus history before messages")

    tag = "CI_CHAT_SAME_CAMPUS_PRIVATE_20261020"
    sql("INSERT INTO chat_message "
        "(sender_id,receiver_id,content,message_type,is_read,is_deleted,is_recalled,created_at) "
        f"VALUES ({alice_id},{bob_id},'{tag}',1,0,0,0,NOW())")
    msg_id = int(sql(f"SELECT MAX(id) FROM chat_message WHERE content='{tag}'"))
    for token, other, action in (
            (alice, bob_id, "sender history"), (bob, alice_id, "receiver history")):
        messages = ok(api("GET", f"/chat/history/{other}?page=1&size=100",
                          token=token), action)
        assert any(int(row["messageId"]) == msg_id and row["content"] == tag
                   for row in messages), f"Missing own conversation: {messages}"
    contacts = ok(api("GET", "/chat/contacts", token=bob), "same-campus contacts")
    assert any(int(row["userId"]) == alice_id for row in contacts), contacts
    ok(api("PUT", f"/chat/read/{alice_id}", token=bob), "read own message")
    assert sql(f"SELECT is_read FROM chat_message WHERE id={msg_id}") == "1"
    print("PASS: real MySQL chat history, contacts and read receipt for verified peers")

    # Historical rows inserted by the test deliberately simulate legacy records.
    # Backend may not expose cross-campus messages even if such a row exists.
    legacy = "CI_CHAT_CROSS_CAMPUS_LEGACY_20261020"
    sql("INSERT INTO chat_message "
        "(sender_id,receiver_id,content,message_type,is_read,is_deleted,is_recalled,created_at) "
        f"VALUES ({outsider_id},{alice_id},'{legacy}',1,0,0,0,NOW())")
    reject(api("GET", f"/chat/history/{outsider_id}", token=alice),
           "other-campus history")
    reject(api("GET", f"/chat/history/{alice_id}", token=outsider),
           "reverse other-campus history")
    reject(api("PUT", f"/chat/read/{outsider_id}", token=alice),
           "other-campus read receipt")
    reject(api("PUT", f"/chat/read/{alice_id}", token=outsider),
           "reverse other-campus read receipt")
    assert sql(f"SELECT is_read FROM chat_message WHERE content='{legacy}'") == "0"
    assert not any(int(row["userId"]) == outsider_id for row in
                   ok(api("GET", "/chat/contacts", token=alice), "private contacts"))
    reject(api("GET", f"/chat/history/{alice_id}", token=alice), "self history")
    reject(api("GET", f"/chat/history/{bob_id}"), "anonymous history")
    print("PASS: cross-campus, self and anonymous history denied; no read mutation or contact leak")


if __name__ == "__main__":
    main()
