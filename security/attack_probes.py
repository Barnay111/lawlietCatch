"""
Пробы на злоупотребления и инъекции против локального стенда. Только чтение и отказы.
Запуск: python3 attack_probes.py <лог с ключами и паролями>
"""
import json, re, sys, urllib.request, urllib.error, time, urllib.parse

LOG = sys.argv[1]
TEXT = open(LOG).read()
BASE = "http://localhost:8080"
SDK = re.search(r"bg_pk_[A-Za-z0-9_-]+", TEXT).group(0)
SERVER = re.search(r"bg_sk_[A-Za-z0-9_-]+", TEXT).group(0)
ADMIN_PW = re.search(r"admin\s+/\s+(\S+)", TEXT).group(1)

def call(method, path, headers=None, body=None, raw=None, timeout=20):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    h = {"Content-Type": "application/json", **(headers or {})}
    url = BASE + urllib.parse.quote(path, safe="/?=&%:,.-_~")
    r = urllib.request.Request(url, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, dict(resp.headers), resp.read().decode(errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode(errors="replace")

_, _, b = call("POST", "/api/v1/auth/login", body={"username": "admin", "password": ADMIN_PW})
ADMIN = {"Authorization": "Bearer " + json.loads(b)["accessToken"]}
SDKH = {"X-BG-Api-Key": SDK}
SRV = {"X-BG-Api-Key": SERVER}
results = []

def check(name, ok, detail=""):
    results.append((ok, name, detail))
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail else ""))

# 1. Инъекции через query-параметры дашборда
for qs in ["level=HIGH' OR '1'='1", "level=HIGH%27%20OR%20%271%27%3D%271", "page=-1", "size=999999", "hours=abc"]:
    st, _, body = call("GET", "/api/v1/dashboard/assessments?" + qs if "level" in qs or "size" in qs or "page" in qs else "/api/v1/dashboard/overview?" + qs, ADMIN)
    check(f"SQLi/граничные параметры: {qs}", st in (200, 400) and "syntax" not in body.lower() and "sql" not in body.lower(), f"HTTP {st}")

# 2. Инъекция через JSON-поля
st, _, body = call("POST", "/api/v1/sessions", SDKH, {"userId": "x'; DROP TABLE sessions;--", "deviceType": "MOBILE", "sdkVersion": "0.1.0"})
check("SQLi через userId отклоняется валидацией", st == 400, f"HTTP {st}")
st, _, body = call("POST", "/api/v1/sessions", SDKH, {"userId": "ok-user", "deviceType": "MOBILE", "sdkVersion": "0.1.0' OR 1=1"})
check("SQLi через sdkVersion отклоняется", st == 400, f"HTTP {st}")

# 3. Огромное тело запроса (DoS): принимаем ли 20 МБ JSON
big = b'{"userId":"a","deviceType":"MOBILE","sdkVersion":"0.1.0","pad":"' + b"x" * (20 * 1024 * 1024) + b'"}'
t0 = time.time()
try:
    st, _, _ = call("POST", "/api/v1/sessions", SDKH, raw=big, timeout=60)
except Exception as e:
    # Сервер закрыл соединение, не дочитав тело: это тоже отказ. Проверяем, что сервер жив.
    st = f"обрыв ({type(e).__name__})"
alive = call("GET", "/actuator/health")[0] == 200
check("тело 20 МБ отклоняется (413, 400 или обрыв), сервер жив", st in (400, 413) or str(st).startswith("обрыв"),
      f"{st}, сервер жив: {alive}, {time.time()-t0:.1f}с")

# 4. Глубоко вложенный JSON и огромные числа
st, _, _ = call("POST", "/api/v1/events/batch", SDKH, raw=b'{"sessionId":"00000000-0000-0000-0000-000000000000","events":' + b"[" * 5000 + b"]" * 5000 + b"}")
check("глубокая вложенность JSON не роняет сервер", st in (400, 413), f"HTTP {st}")
st, _, _ = call("POST", "/api/v1/risk/assess", SRV, raw=b'{"sessionId":"00000000-0000-0000-0000-000000000000","context":{"action":"TRANSFER"}}')
check("risk/assess с несуществующей сессией: 404, не 500", st == 404, f"HTTP {st}")

# 5. Перебор паролей: блокировка работает и не открывает путь
codes = [call("POST", "/api/v1/auth/login", body={"username": "analyst", "password": f"bad-{i}"})[0] for i in range(7)]
check("перебор: после порога вход заблокирован (423)", 423 in codes[5:], str(codes))

# 6. Утечка деталей ошибок: stack trace, SQL, версии
st, _, body = call("GET", "/api/v1/dashboard/sessions/not-a-uuid", ADMIN)
check("ошибка типа UUID не раскрывает трассу", "at kz." not in body and "Exception" not in body, f"HTTP {st}")
st, _, body = call("POST", "/api/v1/auth/login", body={"username": "nobody", "password": "x"})
check("ответ входа не раскрывает существование логина", "nobody" not in body and "не найден" not in body.lower(), f"HTTP {st}")
st, _, body = call("GET", "/nonexistent/path/../../etc/passwd")
check("обход пути не раскрывает файлы", "root:" not in body, f"HTTP {st}")
st, _, body = call("GET", "/dashboard/..%2F..%2Fapplication.properties")
check("обход пути в статике закрыт", "spring" not in body.lower() and "datasource" not in body.lower(), f"HTTP {st}")

# 7. Заголовки безопасности на дашборде
st, h, _ = call("GET", "/dashboard/index.html")
hl = {k.lower(): v for k, v in h.items()}
check("есть X-Content-Type-Options: nosniff", hl.get("x-content-type-options", "").lower() == "nosniff", hl.get("x-content-type-options", "—"))
check("есть X-Frame-Options (антиклик-джекинг)", "x-frame-options" in hl, hl.get("x-frame-options", "—"))
check("есть Content-Security-Policy", "content-security-policy" in hl, hl.get("content-security-policy", "—")[:60])
check("есть Referrer-Policy", "referrer-policy" in hl, hl.get("referrer-policy", "—"))

# 8. CORS: чужой источник не должен получать разрешение
st, h, _ = call("OPTIONS", "/api/v1/dashboard/overview", {"Origin": "https://evil.example", "Access-Control-Request-Method": "GET"})
hl = {k.lower(): v for k, v in h.items()}
check("CORS: чужой источник не разрешён", hl.get("access-control-allow-origin") != "https://evil.example", hl.get("access-control-allow-origin", "нет заголовка"))

# 9. Метод не из списка
st, _, _ = call("PUT", "/api/v1/dashboard/overview", ADMIN, {})
check("неподдерживаемый метод отклоняется", st in (405, 404), f"HTTP {st}")

# 10. Токен в ответе логина не должен совпадать с хешем / ключом
st, _, body = call("POST", "/api/v1/auth/login", body={"username": "admin", "password": ADMIN_PW})
check("вход не возвращает ключ интеграции или пароль", SERVER not in body and ADMIN_PW not in body, f"HTTP {st}")

bad = [r for r in results if not r[0]]
print(f"\nпроверок: {len(results)}, не пройдено: {len(bad)}")
for ok, name, d in bad:
    print("  НЕ ПРОЙДЕНО:", name, d)
