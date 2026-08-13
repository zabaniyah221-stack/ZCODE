#!/usr/bin/env python3
"""Inbox sementara via mail.tm — TERIMA saja, tanpa captcha.

  python3 tools/temp_inbox.py create
  python3 tools/temp_inbox.py inbox

Bukan pengirim Gmail. Bukan bypass bot-detection situs lain.
Session: /var/tmp/mailtm.json (jangan commit).
API: https://api.mail.tm
"""
from __future__ import annotations

import json
import random
import string
import sys
import urllib.request
from pathlib import Path

STATE = Path("/var/tmp/mailtm.json")
API = "https://api.mail.tm"


def _req(path: str, data=None, token=None):
    h = {
        "User-Agent": "zcode-temp-inbox/1.0",
        "Accept": "application/json",
        "Content-Type": "application/json",
    }
    if token:
        h["Authorization"] = "Bearer " + token
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(API + path, data=body, headers=h)
    with urllib.request.urlopen(req, timeout=20) as r:
        raw = r.read().decode() or "null"
        return r.status, json.loads(raw)


def create() -> dict:
    _st, domains = _req("/domains")
    if isinstance(domains, list):
        dom = domains[0]["domain"]
    else:
        mem = domains.get("hydra:member") or []
        dom = mem[0]["domain"]
    user = "z" + "".join(random.choices(string.ascii_lowercase + string.digits, k=10))
    addr = f"{user}@{dom}"
    pw = "Tmp-" + "".join(random.choices(string.ascii_letters + string.digits, k=14))
    _req("/accounts", {"address": addr, "password": pw})
    _st, tok = _req("/token", {"address": addr, "password": pw})
    state = {"address": addr, "password": pw, "token": tok["token"]}
    STATE.write_text(json.dumps(state))
    print(addr)
    return state


def inbox() -> None:
    state = json.loads(STATE.read_text())
    _st, msgs = _req("/messages", token=state["token"])
    print(json.dumps({"address": state["address"], "messages": msgs}, indent=2)[:4000])


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "create"
    if cmd == "create":
        create()
    elif cmd == "inbox":
        inbox()
    else:
        sys.exit("usage: temp_inbox.py create|inbox")
