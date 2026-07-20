#!/usr/bin/env python3
"""Contract test for the reverse-engineered Fire TV Lightning endpoints.

This is a software mock, not physical Fire Stick certification.
"""
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading
import urllib.error
import urllib.request

API_KEY = "0987654321"
TOKEN = "mock-client-token"

@dataclass(frozen=True)
class Model:
    name: str
    lightning: bool
    key_up: bool

MODELS = [
    Model("Stick Gen 1", True, False),
    Model("Stick Gen 2", True, False),
    Model("Basic Edition", True, False),
    Model("Stick 4K Gen 1", True, True),
    Model("Stick Lite / Gen 3", True, True),
    Model("4K Max Gen 1", True, True),
    Model("4K / 4K Max Gen 2", True, True),
    Model("Stick HD Fire OS", True, True),
    Model("4K Select Vega experimental", False, True),
    Model("Stick HD Vega experimental", False, True),
]

class State:
    model = MODELS[0]
    events = []

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        return

    def _body(self):
        size = int(self.headers.get("content-length", "0"))
        return self.rfile.read(size).decode() if size else ""

    def _send(self, code, payload=""):
        raw = payload.encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if not State.model.lightning:
            return self._send(404, '{"description":"unsupported"}')
        if self.headers.get("x-api-key") != API_KEY:
            return self._send(401, "")
        if self.path == "/v1/FireTV/status":
            return self._send(200, json.dumps({"platformType": "android", "model": State.model.name}))
        if self.path == "/v1/FireTV/properties":
            return self._send(200, json.dumps({"pfm": State.model.name, "volumeSupport": "NotSupported"}))
        return self._send(404, "")

    def do_POST(self):
        body = self._body()
        State.events.append((self.path, body))
        if self.path == "/apps/FireTVRemote":
            return self._send(201, "")
        if not State.model.lightning:
            return self._send(404, '{"description":"unsupported"}')
        if self.headers.get("x-api-key") != API_KEY:
            return self._send(401, "")
        if self.path == "/v1/FireTV/pin/display":
            return self._send(200, '{"description":"OK"}')
        if self.path == "/v1/FireTV/pin/verify":
            return self._send(200, json.dumps({"description": TOKEN}))
        if self.headers.get("x-client-token") != TOKEN:
            return self._send(401, "")
        return self._send(200, '{"description":"OK"}')


def call(method, path, body=None, token=None):
    data = None if body is None else body.encode()
    request = urllib.request.Request(f"http://127.0.0.1:18080{path}", data=data, method=method)
    request.add_header("x-api-key", API_KEY)
    request.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        request.add_header("x-client-token", token)
    try:
        with urllib.request.urlopen(request, timeout=2) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode()


def run_model(model):
    State.model = model
    State.events = []
    pin_status, _ = call("POST", "/v1/FireTV/pin/display", '{"friendlyName":"test"}')
    if not model.lightning:
        assert pin_status == 404
        return "UNSUPPORTED DETECTED"
    assert pin_status == 200
    verify_status, verify_body = call("POST", "/v1/FireTV/pin/verify", '{"pin":"1234"}')
    assert verify_status == 200 and json.loads(verify_body)["description"] == TOKEN
    commands = [
        ("/v1/FireTV?action=dpad_up", '{"keyActionType":"keyDown"}'),
        ("/v1/FireTV?action=select", '{"keyActionType":"keyDown"}'),
        ("/v1/FireTV?action=home", ""),
        ("/v1/media?action=play", ""),
        ("/v1/FireTV/text", '{"text":"A"}'),
        ("/v1/FireTV/app/com.netflix.ninja", ""),
    ]
    for path, body in commands:
        status, _ = call("POST", path, body, TOKEN)
        assert status == 200, (model.name, path, status)
    status, _ = call("GET", "/v1/FireTV/status", None, TOKEN)
    assert status == 200
    return "PASS"


def main():
    server = ThreadingHTTPServer(("127.0.0.1", 18080), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        for model in MODELS:
            result = run_model(model)
            print(f"{result:20} {model.name}")
    finally:
        server.shutdown()
        server.server_close()
    print(f"PASS: protocol contract exercised against {len(MODELS)} software model profiles")

if __name__ == "__main__":
    main()
