#!/usr/bin/env python3
"""Limit CANDIDATES to one active Keycloak session (deny the second login).

Builds a copy of the browser flow with a conditional sub-flow:
  if the user has the realm role `candidate` -> User Session Count Limiter
  (deny new session, limit 1). Interviewers are unaffected. Idempotent.

Usage:  python scripts/keycloak-single-session.py [AUTH_BASE_URL]
        default AUTH_BASE_URL = https://auth.192.168.1.8.nip.io:8443
"""
import json
import ssl
import sys
import urllib.parse
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "https://auth.192.168.1.8.nip.io:8443").rstrip("/")
REALM = "online-interview"
FLOW = "browser-candidate-limit"
SUBFLOW = "candidate-session-guard"
ROLE = "candidate"
MSG = "This candidate is already logged in on another device."
CTX = ssl._create_unverified_context()


def call(method, path, data=None, token=None, form=False):
    h, body = {}, None
    if data is not None:
        if form:
            body = urllib.parse.urlencode(data).encode()
            h["Content-Type"] = "application/x-www-form-urlencoded"
        else:
            body = json.dumps(data).encode()
            h["Content-Type"] = "application/json"
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=body, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=25) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:200]


def admin_token():
    return call("POST", "/realms/master/protocol/openid-connect/token",
                {"client_id": "admin-cli", "username": "admin", "password": "admin",
                 "grant_type": "password"}, form=True)[1]["access_token"]


def set_required(flow, provider, tok, config=None, config_alias=""):
    _, execs = call("GET", f"/admin/realms/{REALM}/authentication/flows/{flow}/executions", token=tok)
    ex = next(e for e in execs if e.get("providerId") == provider)
    ex["requirement"] = "REQUIRED"
    call("PUT", f"/admin/realms/{REALM}/authentication/flows/{flow}/executions", ex, token=tok)
    if config and not ex.get("authenticationConfig"):
        call("POST", f"/admin/realms/{REALM}/authentication/executions/{ex['id']}/config",
             {"alias": config_alias, "config": config}, token=tok)


def main():
    tok = admin_token()
    _, flows = call("GET", f"/admin/realms/{REALM}/authentication/flows", token=tok)
    if not any(f.get("alias") == FLOW for f in flows):
        call("POST", f"/admin/realms/{REALM}/authentication/flows/browser/copy",
             {"newName": FLOW}, token=tok)
        call("POST", f"/admin/realms/{REALM}/authentication/flows/{FLOW}/executions/flow",
             {"alias": SUBFLOW, "type": "conditional", "description": "Candidate session guard"},
             token=tok)
        _, execs = call("GET", f"/admin/realms/{REALM}/authentication/flows/{FLOW}/executions", token=tok)
        sub = next(e for e in execs if e.get("displayName") == SUBFLOW)
        sub["requirement"] = "CONDITIONAL"
        call("PUT", f"/admin/realms/{REALM}/authentication/flows/{FLOW}/executions", sub, token=tok)
        call("POST", f"/admin/realms/{REALM}/authentication/flows/{SUBFLOW}/executions/execution",
             {"provider": "conditional-user-role"}, token=tok)
        call("POST", f"/admin/realms/{REALM}/authentication/flows/{SUBFLOW}/executions/execution",
             {"provider": "user-session-limits"}, token=tok)
        set_required(SUBFLOW, "conditional-user-role", tok,
                     {"condUserRole": ROLE, "negate": "false"}, "candidate-role-cond")
        set_required(SUBFLOW, "user-session-limits", tok,
                     {"behavior": "Deny new session", "userRealmLimit": "1",
                      "userClientLimit": "0", "errorMessage": MSG}, "candidate-session-limit")
        print("built", FLOW)
    else:
        print(FLOW, "already exists")

    _, realm = call("GET", f"/admin/realms/{REALM}", token=tok)
    if realm.get("browserFlow") != FLOW:
        realm["browserFlow"] = FLOW
        call("PUT", f"/admin/realms/{REALM}", realm, token=tok)

    # drop the earlier realm-wide flow if it is no longer bound
    _, flows = call("GET", f"/admin/realms/{REALM}/authentication/flows", token=tok)
    old = next((f for f in flows if f.get("alias") == "browser-single-session"), None)
    if old:
        call("DELETE", f"/admin/realms/{REALM}/authentication/flows/{old['id']}", token=tok)

    print("realm browserFlow ->", call("GET", f"/admin/realms/{REALM}", token=tok)[1]["browserFlow"])


if __name__ == "__main__":
    main()
