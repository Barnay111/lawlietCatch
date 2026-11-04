// Дашборд BehaviorGuard. Токен лежит в httpOnly-cookie: из JavaScript он недоступен.
// Все запросы идут с заголовком X-BG-Requested (защита от CSRF).
// Все данные из API выводятся через esc(): в них могут быть пользовательские строки.
(() => {
  const state = { page: 0, size: 20, level: '', role: null };

  const $ = (sel) => document.querySelector(sel);
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
  const fmtTime = (iso) => iso ? new Date(iso).toLocaleString('ru-RU') : '—';
  const levelBadge = (lvl) => `<span class="badge ${esc(lvl)}">${esc(lvl)}</span>`;

  async function api(path, options = {}) {
    const res = await fetch(path, {
      ...options,
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-BG-Requested': 'dashboard', ...(options.headers || {}) },
    });
    if (res.status === 401) { $('#top').hidden = true; show('login'); throw new Error('Сессия истекла, войдите снова'); }
    if (res.status === 403) throw new Error('Недостаточно прав для этого действия');
    if (!res.ok) throw new Error('Ошибка сервера: ' + res.status);
    return res.status === 204 ? null : res.json();
  }

  function show(id) {
    ['login', 'view-overview', 'view-assessments', 'view-audit'].forEach((v) => {
      const el = document.getElementById(v);
      if (el) el.hidden = v !== id;
    });
  }

  function signIn(data) {
    state.role = data.role;
    $('#top').hidden = false;
    $('#who').textContent = `${data.username} · ${data.role === 'ADMIN' ? 'администратор' : 'аналитик'}`;
    $('#auditTab').hidden = data.role !== 'ADMIN';
    switchTab('overview');
  }

  async function signOut() {
    try {
      await fetch('/api/v1/auth/logout', { method: 'POST', credentials: 'same-origin', headers: { 'X-BG-Requested': 'dashboard' } });
    } catch (_) { /* сеть недоступна: cookie всё равно истечёт */ }
    $('#top').hidden = true;
    show('login');
  }

  function switchTab(name) {
    document.querySelectorAll('.tab').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
    show('view-' + name);
    if (name === 'overview') loadOverview();
    if (name === 'assessments') { state.page = 0; loadFeed(); }
    if (name === 'audit') loadAudit();
  }

  async function loadOverview() {
    const hours = $('#hours').value;
    const o = await api(`/api/v1/dashboard/overview?hours=${hours}`);
    $('#cards').innerHTML = [
      ['Сессии', o.sessionsStarted, ''],
      ['Оценено сессий', o.assessedSessions, ''],
      ['Аномалии (MEDIUM+HIGH)', o.anomalies, 'MEDIUM'],
      ['Высокий риск', o.highRisk, 'HIGH'],
      ['Недостаточно данных', o.unknown, ''],
    ].map(([label, value, cls]) =>
      `<div class="metric ${cls}"><div class="label">${esc(label)}</div><div class="value">${esc(value)}</div></div>`
    ).join('');

    const max = Math.max(1, ...o.timeline.map((b) => Object.values(b.counts).reduce((a, c) => a + c, 0)));
    // Подписи через равный шаг: при 168 часах все не поместятся. Полная метка — в title.
    // Высоты задаются через DOM (element.style), а не атрибутом style в HTML: так CSP без 'unsafe-inline'.
    const step = Math.max(1, Math.ceil(o.timeline.length / 8));
    const host = $('#timeline');
    host.replaceChildren();
    if (o.timeline.length === 0) {
      const p = document.createElement('p');
      p.className = 'muted';
      p.textContent = 'За выбранный период оценок нет.';
      host.append(p);
    }
    o.timeline.forEach((b, i) => {
      const total = Object.values(b.counts).reduce((a, c) => a + c, 0);
      const col = document.createElement('div');
      col.className = 'col';
      col.title = `${b.hour}: оценок ${total}`;
      const stack = document.createElement('div');
      stack.className = 'stack';
      ['LOW', 'MEDIUM', 'HIGH', 'UNKNOWN'].forEach((lvl) => {
        const c = b.counts[lvl] || 0;
        if (!c) return;
        const seg = document.createElement('div');
        seg.className = 'seg ' + lvl;
        seg.style.height = (c / max * 100).toFixed(1) + '%';
        stack.append(seg);
      });
      const label = document.createElement('div');
      label.className = 'bar-label';
      label.textContent = i % step === 0 ? b.hour : '';
      col.append(stack, label);
      host.append(col);
    });

    renderRows($('#recent'), o.recent, true);
  }

  function renderRows(table, rows, compact) {
    const head = `<tr><th>Время</th><th>Сессия</th><th>Клиент</th><th>Балл</th><th>Уровень</th>${compact ? '' : '<th>Рекомендация</th>'}<th>Причины</th></tr>`;
    const body = rows.length === 0
      ? `<tr><td colspan="7" class="muted">Нет данных</td></tr>`
      : rows.map((r) => `
          <tr class="clickable" data-session="${esc(r.sessionId)}">
            <td>${esc(fmtTime(r.createdAt))}</td>
            <td><code>${esc(r.sessionId.slice(0, 8))}</code></td>
            <td>${esc(r.userId)}</td>
            <td>${r.riskScore == null ? '—' : esc(r.riskScore)}</td>
            <td>${levelBadge(r.riskLevel)}</td>
            ${compact ? '' : `<td>${esc(r.recommendation)}</td>`}
            <td>${esc((r.reasons || []).join(', ') || '—')}</td>
          </tr>`).join('');
    table.innerHTML = head + body;
    table.querySelectorAll('tr.clickable').forEach((tr) =>
      tr.addEventListener('click', () => openSession(tr.dataset.session)));
  }

  async function loadFeed() {
    const qs = new URLSearchParams({ page: state.page, size: state.size });
    if (state.level) qs.set('level', state.level);
    const p = await api('/api/v1/dashboard/assessments?' + qs.toString());
    renderRows($('#feed'), p.content, false);
    const pages = Math.max(1, Math.ceil(p.totalElements / p.size));
    $('#pageInfo').textContent = `Страница ${p.page + 1} из ${pages} · всего ${p.totalElements}`;
    $('#prev').disabled = p.page === 0;
    $('#next').disabled = (p.page + 1) >= pages;
  }

  async function loadAudit() {
    const rows = await api('/api/v1/admin/audit?size=100');
    $('#auditTable').innerHTML = `<tr><th>Время</th><th>Кто</th><th>Действие</th><th>Объект</th></tr>` +
      (rows.length === 0 ? '<tr><td colspan="4" class="muted">Записей нет</td></tr>' : rows.map((r) => `
        <tr><td>${esc(fmtTime(r.createdAt))}</td><td>${esc(r.actorType)} <code>${esc(String(r.actorId).slice(0, 8))}</code></td>
        <td>${esc(r.action)}</td><td>${esc(r.target || '—')}</td></tr>`).join(''));
  }

  async function openSession(id) {
    const s = await api('/api/v1/dashboard/sessions/' + encodeURIComponent(id));
    const feats = Object.entries(s.features || {}).map(([k, v]) =>
      `<div>${esc(k)}</div><div>${v == null ? '<span class="muted">нет данных</span>' : esc(Number(v).toFixed(2))}</div>`).join('');
    const types = Object.entries(s.eventTypes || {}).map(([k, v]) => `${esc(k)}: ${esc(v)}`).join(' · ') || '—';
    const latest = s.latest;
    $('#detailBody').innerHTML = `
      <div class="kv">
        <div>Клиент</div><div>${esc(s.userId)}</div>
        <div>Начало</div><div>${esc(fmtTime(s.startedAt))}</div>
        <div>Статус</div><div>${esc(s.status)} · ${esc(s.deviceType)} · SDK ${esc(s.sdkVersion)}</div>
        <div>События</div><div>${esc(s.eventCount)} — ${types}</div>
        <div>Последний уровень</div><div>${latest ? levelBadge(latest.riskLevel) + ' ' + esc(latest.riskScore ?? '—') : '—'}</div>
        <div>Причины</div><div>${esc((latest?.reasons || []).join(', ') || '—')}</div>
      </div>
      <h3>Признаки сессии</h3>
      <div class="kv">${feats}</div>
      <h3>История оценок</h3>
      <table>${(s.history || []).map((h) => `<tr><td>${esc(fmtTime(h.createdAt))}</td><td>${levelBadge(h.riskLevel)}</td><td>${esc(h.riskScore ?? '—')}</td><td>${esc(h.recommendation)}</td></tr>`).join('') || '<tr><td class="muted">Оценок нет</td></tr>'}</table>`;
    $('#detail').showModal();
  }

  $('#loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    $('#loginError').textContent = '';
    const f = new FormData(e.target);
    const res = await fetch('/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: f.get('username'), password: f.get('password') }),
    });
    if (res.ok) {
      signIn(await res.json());
      e.target.reset();
    } else if (res.status === 429) {
      $('#loginError').textContent = 'Слишком много попыток входа. Подождите минуту.';
    } else if (res.status === 423) {
      $('#loginError').textContent = 'Слишком много неудачных попыток. Повторите позже.';
    } else {
      $('#loginError').textContent = 'Неверный логин или пароль';
    }
  });

  document.querySelectorAll('.tab').forEach((b) => b.addEventListener('click', () => switchTab(b.dataset.tab)));
  $('#hours').addEventListener('change', loadOverview);
  $('#levelFilter').addEventListener('change', (e) => { state.level = e.target.value; state.page = 0; loadFeed(); });
  $('#prev').addEventListener('click', () => { state.page = Math.max(0, state.page - 1); loadFeed(); });
  $('#next').addEventListener('click', () => { state.page += 1; loadFeed(); });
  $('#logout').addEventListener('click', signOut);
  $('#closeDetail').addEventListener('click', () => $('#detail').close());

  // Восстановление сессии: cookie живёт 8 часов, /me говорит, кто вошёл.
  fetch('/api/v1/auth/me', { credentials: 'same-origin', headers: { 'X-BG-Requested': 'dashboard' } })
    .then((res) => (res.ok ? res.json() : null))
    .then((me) => { if (me) signIn(me); else show('login'); })
    .catch(() => show('login'));
})();
