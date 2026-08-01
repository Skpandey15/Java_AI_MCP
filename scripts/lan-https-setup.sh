#!/usr/bin/env bash
# Reproduce the LAN HTTPS setup for the local k3d cluster so it survives
# `kubectl apply -k` and cluster rebuilds. Idempotent — safe to re-run.
#
# Usage:   OPENAI_API_KEY=sk-... ./scripts/lan-https-setup.sh [LAN_IP]
# Default LAN_IP is 192.168.1.8. Pass a different IP if your machine's LAN
# address changes. Requires: kubectl (context k3d-dev), k3d, docker, openssl.
#
# What it does: k3d port maps (8081->80, 8443->443), a self-signed cert +
# TLS secret, applies the local overlay, then re-applies the HTTPS overrides
# (issuer/CORS/web-ui config, Keycloak proxy headers + client redirect URIs,
# the TLS ingress) and the OpenAI key, and restarts the affected workloads.
set -euo pipefail

IP="${1:-192.168.1.8}"
D="$IP.nip.io"
NS="online-interview"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CERTS="$ROOT/scripts/.lan-certs"
mkdir -p "$CERTS"

echo "== 1. k3d port mappings (8081->80, 8443->443) =="
ports="$(docker ps --filter name=k3d-dev-serverlb --format '{{.Ports}}' 2>/dev/null || true)"
echo "$ports" | grep -q ':8081->80'  || k3d cluster edit dev --port-add "8081:80@loadbalancer"
echo "$ports" | grep -q ':8443->443' || k3d cluster edit dev --port-add "8443:443@loadbalancer"

echo "== 2. self-signed cert + TLS secret =="
if [ ! -f "$CERTS/tls.crt" ]; then
  ( cd "$CERTS" && MSYS_NO_PATHCONV=1 openssl req -x509 -newkey rsa:2048 -sha256 -nodes \
      -keyout tls.key -out tls.crt -days 365 \
      -subj "/CN=$D" -addext "subjectAltName=DNS:$D,DNS:*.$D" )
fi
kubectl -n "$NS" create secret tls online-interview-tls \
  --cert="$CERTS/tls.crt" --key="$CERTS/tls.key" --dry-run=client -o yaml | kubectl apply -f -

echo "== 3. apply base local overlay =="
kubectl apply -k "$ROOT/platform/kubernetes/overlays/local" >/dev/null

echo "== 4. HTTPS config overrides (issuer, CORS, web-ui) =="
kubectl -n "$NS" patch configmap platform-config --type merge -p \
  "{\"data\":{\"KEYCLOAK_ISSUER_URI\":\"https://auth.$D:8443/realms/online-interview\",\"CORS_ALLOWED_ORIGINS\":\"https://interview.$D:8443\"}}"
cat > "$CERTS/config.js" <<CFG
window.__ONLINE_INTERVIEW_CONFIG__ = {
  apiBaseUrl: 'https://api.$D:8443',
  keycloakUrl: 'https://auth.$D:8443',
  keycloakRealm: 'online-interview',
  keycloakClientId: 'online-interview-web',
}
CFG
kubectl -n "$NS" create configmap web-ui-runtime-config --from-file=config.js="$CERTS/config.js" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "== 5. Keycloak trusts forwarded HTTPS =="
kubectl -n "$NS" patch deploy keycloak --type=json -p \
  '[{"op":"replace","path":"/spec/template/spec/containers/0/args","value":["start-dev","--import-realm","--health-enabled=true","--proxy-headers=xforwarded"]}]'

echo "== 6. HTTPS ingress =="
kubectl apply -f - <<ING
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: online-interview-tls
  namespace: $NS
  annotations:
    traefik.ingress.kubernetes.io/router.entrypoints: websecure
    traefik.ingress.kubernetes.io/router.tls: "true"
spec:
  ingressClassName: traefik
  tls:
    - hosts: [interview.$D, api.$D, auth.$D]
      secretName: online-interview-tls
  rules:
    - host: interview.$D
      http: { paths: [{ path: /, pathType: Prefix, backend: { service: { name: web-ui, port: { number: 80 } } } }] }
    - host: api.$D
      http: { paths: [{ path: /, pathType: Prefix, backend: { service: { name: interview-orchestrator, port: { number: 8080 } } } }] }
    - host: auth.$D
      http: { paths: [{ path: /, pathType: Prefix, backend: { service: { name: keycloak, port: { number: 8080 } } } }] }
ING

echo "== 7. OpenAI key =="
if [ -n "${OPENAI_API_KEY:-}" ]; then
  B64=$(printf '%s' "$OPENAI_API_KEY" | base64 -w0)
  kubectl -n "$NS" patch secret platform-secrets --type merge -p "{\"data\":{\"OPENAI_API_KEY\":\"$B64\"}}"
else
  echo "  (OPENAI_API_KEY not set — AI generation stays disabled until you set it)"
fi

echo "== 8. restart workloads =="
kubectl -n "$NS" rollout restart deploy/web-ui deploy/interview-orchestrator deploy/litellm deploy/keycloak >/dev/null

echo "== 9. register HTTPS redirect URIs on the Keycloak web client (best effort) =="
kubectl -n "$NS" rollout status deploy/keycloak --timeout=180s >/dev/null 2>&1 || true
ORIGIN="https://interview.$D:8443"
AT=$(curl -sk "https://auth.$D:8443/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d username=admin -d password=admin -d grant_type=password 2>/dev/null \
  | grep -oE '"access_token":"[^"]*"' | cut -d'"' -f4 || true)
if [ -n "$AT" ]; then
  CID=$(curl -sk -H "Authorization: Bearer $AT" \
    "https://auth.$D:8443/admin/realms/online-interview/clients?clientId=online-interview-web" \
    | grep -oE '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  C=$(curl -sk -H "Authorization: Bearer $AT" "https://auth.$D:8443/admin/realms/online-interview/clients/$CID")
  echo "$C" | grep -q "$ORIGIN" || curl -sk -X PUT -H "Authorization: Bearer $AT" -H "Content-Type: application/json" \
    "https://auth.$D:8443/admin/realms/online-interview/clients/$CID" \
    -d "$(printf '%s' "$C" | python -c "import sys,json;c=json.load(sys.stdin);o='$ORIGIN';c['redirectUris']=list({*c.get('redirectUris',[]),o+'/*'});c['webOrigins']=list({*c.get('webOrigins',[]),o});c['rootUrl']=o;c['baseUrl']=o;print(json.dumps(c))")" >/dev/null
fi

echo "== 10. limit candidates to one active session (deny second login) =="
python "$ROOT/scripts/keycloak-single-session.py" "https://auth.$D:8443" \
  || echo "  (skipped — run scripts/keycloak-single-session.py manually once Keycloak is up)"

echo ""
echo "Done. Open:  https://interview.$D:8443"
echo "First time on each machine: trust scripts/.lan-certs/tls.crt (Trusted Root) and allow inbound TCP 8443/8081 in the firewall."
