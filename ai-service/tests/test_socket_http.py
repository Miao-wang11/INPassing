"""Actual TCP/uvicorn authenticated HTTP smoke test."""
import os
import socket
import subprocess
import sys
import time

import httpx

from tests.test_http import principal, signed


def test_uvicorn_socket_http(tmp_path):
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        port = s.getsockname()[1]
    secret = "socket-http-test-abcdef1234567890-abcde"
    env = {
        **os.environ,
        "SHUNSHOU_AI_INTERNAL_TOKEN": secret,
        "SHUNSHOU_AI_MODEL_PROVIDER": "mock",
        "SHUNSHOU_AI_ENVIRONMENT": "test",
        "SHUNSHOU_AI_CHECKPOINT_PATH": str(tmp_path / "socket.sqlite3"),
    }
    proc = subprocess.Popen(
        [sys.executable, "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", str(port)],
        env=env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True,
    )
    base = f"http://127.0.0.1:{port}"
    try:
        deadline = time.monotonic() + 15
        with httpx.Client(timeout=1) as client:
            while time.monotonic() < deadline:
                if proc.poll() is not None:
                    raise AssertionError(f"uvicorn exited: {proc.stderr.read()[-2000:]}")
                try:
                    if client.get(f"{base}/readyz").status_code == 200:
                        break
                except httpx.RequestError:
                    pass
                time.sleep(0.2)
            else:
                raise AssertionError("uvicorn readiness timed out")
            req = signed(client, "POST", "/internal/v1/chat", secret, principal(secret, 19, 1),
                         payload={"message": "附近任务"})
            reply = client.send(req)
            assert reply.status_code == 200, reply.text
            assert reply.json()["turns"] == 1
    finally:
        proc.terminate()
        try:
            proc.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.communicate()
