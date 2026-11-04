"""
Матрица доступа: эндпоинт x учётные данные -> ожидаемый код.
Запуск: python3 authz_matrix.py <лог dev-запуска с ключами и паролями>
Ничего не пишет в данные, кроме попыток входа (которые закончатся 401).
"""
import json, re, sys, urllib.request, urllib.error, uuid

LOG = sys.argv[1]
TEXT = open(LOG).read()
BASE = "http://localhost:8080"
SDK = re.search(r"bg_pk_[A-Za-z0-9_-]+", TEXT).group(0)
SERVER = re.search(r"bg_sk_[A-Za-z0-9_-]+", TEXT).group(0)
ADMIN_PW = re.search(r"admin\s+/\s+(\S+)", TEXT).group(1)
ANALYST_PW = re.search(r"analyst\s+/\s+(\S+)", TEXT).group(1)

def req(method, path, headers=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json", **(headers or {})}
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()

def token(user, pw):
    st, b = req("POST", "/api/v1/auth/login", body={"username": user, "password": pw})
    return json.loads(b)["accessToken"] if st == 200 else None

ADMIN = token("admin", ADMIN_PW)
ANALYST = token("analyst", ANALYST_PW)
assert ADMIN and ANALYST, "не удалось получить токены"

CREDS = {
    "anon": {},
    "sdk_key": {"X-BG-Api-Key": SDK},
    "server_key": {"X-BG-Api-Key": SERVER},
    "analyst": {"Authorization": f"Bearer {ANALYST}"},
    "admin": {"Authorization": f"Bearer {ADMIN}"},
    "bogus_bearer": {"Authorization": "Bearer not-a-real-token"},
    "bogus_key": {"X-BG-Api-Key": "bg_pk_fake"},
}
FAKE_ID = str(uuid.uuid4())
# (метод, путь, тело, {учётные данные: ожидаемый код})
Y, N = None, None
ENDPOINTS = [
    ("GET", "/actuator/health", None, {"anon": 200}),
    ("GET", "/actuator/env", None, {"anon": 401, "admin": 403}),
    ("POST", "/api/v1/sessions", {"userId": "sec-probe", "deviceType": "MOBILE", "sdkVersion": "0.1.0"},
        {"anon": 401, "bogus_key": 401, "sdk_key": 201, "server_key": 403, "analyst": 403, "admin": 403}),
    ("POST", "/api/v1/events/batch", {"sessionId": FAKE_ID, "events": []},
        {"anon": 401, "server_key": 403, "analyst": 403, "sdk_key": 400}),
    ("POST", f"/api/v1/sessions/{FAKE_ID}/finish", None,
        {"anon": 401, "server_key": 403, "sdk_key": 404}),
    ("POST", "/api/v1/risk/assess", {"sessionId": FAKE_ID},
        {"anon": 401, "sdk_key": 403, "analyst": 403, "admin": 403, "server_key": 404}),
    ("GET", f"/api/v1/risk/{FAKE_ID}", None,
        {"anon": 401, "sdk_key": 403, "analyst": 403, "server_key": 404}),
    ("GET", f"/api/v1/users/{FAKE_ID}/profile", None,
        {"anon": 401, "sdk_key": 403, "analyst": 403, "server_key": 404}),
    ("GET", "/api/v1/dashboard/overview?hours=24", None,
        {"anon": 401, "sdk_key": 403, "server_key": 403, "bogus_bearer": 401, "analyst": 200, "admin": 200}),
    ("GET", "/api/v1/dashboard/assessments", None,
        {"anon": 401, "sdk_key": 403, "analyst": 200, "admin": 200}),
    ("GET", f"/api/v1/dashboard/sessions/{FAKE_ID}", None,
        {"anon": 401, "analyst": 404, "admin": 404}),
    ("GET", f"/api/v1/dashboard/users/{FAKE_ID}/profile", None,
        {"anon": 401, "analyst": 404, "admin": 404}),
    ("POST", "/api/v1/admin/analysts", {"username": "probe-x", "password": "short"},
        {"anon": 401, "analyst": 403, "sdk_key": 403, "admin": 400}),
    ("GET", "/api/v1/admin/audit", None,
        {"anon": 401, "analyst": 403, "sdk_key": 403, "admin": 200}),
    ("POST", "/api/v1/auth/logout", None, {"anon": 204}),
    ("GET", "/dashboard/index.html", None, {"anon": 200}),
]

failures = []
rows = []
for method, path, body, expect in ENDPOINTS:
    for cred, want in expect.items():
        headers = CREDS[cred]
        st, _ = req(method, path, headers, body)
        ok = st == want
        rows.append((ok, method, path, cred, want, st))
        if not ok:
            failures.append((method, path, cred, want, st))

print(f"{'ok':3} {'method':6} {'cred':12} {'want':>4} {'got':>4}  path")
for ok, m, p, c, w, s in rows:
    print(f"{'ok' if ok else 'XX':3} {m:6} {c:12} {w:>4} {s:>4}  {p}")
print(f"\nпроверок: {len(rows)}, расхождений: {len(failures)}")
for f in failures:
    print("  РАСХОЖДЕНИЕ:", f)
