/* 재시작 / 정지 패널 */
const Restart = (() => {
  let checkedIds = new Set();
  let highlightedId = null; // 지금 "보고 있는" 인스턴스 — 체크 여부와 무관, 목록에서 강조만 함

  // 서버그룹을 고르기 전에는 아무것도 보여주지 않는다 — 재시작/정지는 실행하면
  // 실제 서비스에 영향이 생기는 작업이라, 처음부터 전체 인스턴스가 와르르 나오는
  // 것보다 범위를 직접 좁혀서 시작하는 게 안전하다.
  function visibleInstances() {
    const serverGroupId = document.getElementById('restart-server-group-filter').value;
    if (!serverGroupId) return [];
    const typeId = document.getElementById('restart-server-type-filter').value;
    const serverId = document.getElementById('restart-server-filter').value;
    const groupId = document.getElementById('restart-group-filter').value;
    return Instances.getFlatInstances().filter((i) =>
      String(i.server_group_id) === serverGroupId &&
      (!typeId || i.server_environment === typeId) &&
      (!serverId || String(i.server_id) === serverId) &&
      (!groupId || String(i.group_id) === groupId));
  }

  // 서버 필터는 서버그룹을, 그룹 필터는 서버를 먼저 골라야 의미가 있다 —
  // 상위가 "전체"면 하위 선택지를 비우고 비활성화한다. 서버타입은 서버의 운영환경으로
  // 서버 단을 한 번 더 좁히는 보조 필터다.
  function renderServerFilterOptions() {
    const serverGroupId = document.getElementById('restart-server-group-filter').value;
    const typeId = document.getElementById('restart-server-type-filter').value;
    const sel = document.getElementById('restart-server-filter');
    const prev = sel.value;
    if (!serverGroupId) {
      sel.innerHTML = '<option value="">전체 서버</option>';
      sel.disabled = true;
      renderGroupFilterOptions();
      return;
    }
    sel.disabled = false;
    const sg = Instances.getServerGroups().find((x) => String(x.id) === serverGroupId);
    const servers = (sg ? sg.servers : []).filter((s) => !typeId || s.environment === typeId);
    sel.innerHTML = '<option value="">전체 서버</option>' +
      servers.map((s) => `<option value="${s.id}">${UI.esc(serverOptionLabel(s))}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
    renderGroupFilterOptions();
  }

  function renderGroupFilterOptions() {
    const serverGroupId = document.getElementById('restart-server-group-filter').value;
    const serverId = document.getElementById('restart-server-filter').value;
    const sel = document.getElementById('restart-group-filter');
    const prev = sel.value;
    if (!serverGroupId || !serverId) {
      sel.innerHTML = '<option value="">전체 그룹</option>';
      sel.disabled = true;
      return;
    }
    sel.disabled = false;
    const sg = Instances.getServerGroups().find((x) => String(x.id) === serverGroupId);
    const server = sg ? sg.servers.find((s) => String(s.id) === serverId) : null;
    const groups = server ? server.groups : [];
    sel.innerHTML = '<option value="">전체 그룹</option>' +
      groups.map((g) => `<option value="${g.id}">${UI.esc(g.name)}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
  }

  function renderServerGroupFilterOptions() {
    const sel = document.getElementById('restart-server-group-filter');
    const prev = sel.value;
    sel.innerHTML = '<option value="">서버그룹 선택...</option>' +
      Instances.getServerGroups().map((sg) => `<option value="${sg.id}">${UI.esc(sg.name)}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
    renderServerFilterOptions();
  }

  function renderList() {
    const container = document.getElementById('restart-instance-list');
    container.innerHTML = '';
    if (!document.getElementById('restart-server-group-filter').value) {
      container.innerHTML = '<div class="hint" style="padding:8px">서버그룹을 선택하면 인스턴스 목록이 표시됩니다.</div>';
      renderCheckedSummary();
      return;
    }
    for (const inst of visibleInstances()) {
      const row = document.createElement('label');
      row.className = 'checklist-item';
      if (inst.id === highlightedId) row.classList.add('checklist-item-highlighted');
      row.dataset.instanceId = inst.id;
      const envClass = inst.environment === 'PROD' ? 'env-prod' : 'env-dev';
      row.innerHTML = `<input type="checkbox"> <span class="env-badge ${envClass}">${UI.esc(environmentLabel(inst.environment))}</span> [${UI.esc(inst.server_name)} / ${UI.esc(inst.group_name)}] ${UI.esc(inst.name)}  (<span class="${instanceTypeColorClass(inst.type)}">${UI.esc(instanceTypeShortLabel(inst.type))}</span> / ${UI.esc(inst.host)}:${inst.port})`;
      const cb = row.querySelector('input');
      cb.checked = checkedIds.has(inst.id);
      cb.addEventListener('change', () => {
        if (cb.checked) checkedIds.add(inst.id); else checkedIds.delete(inst.id);
        renderCheckedSummary();
      });
      container.appendChild(row);
    }
    renderCheckedSummary();
    const hlRow = highlightedId != null && container.querySelector(`[data-instance-id="${highlightedId}"]`);
    if (hlRow) hlRow.scrollIntoView({ block: 'nearest' });
  }

  /* 필터가 바뀌면 checkedIds를 비우므로(applyTreeChain, init()의 필터 change 리스너
   * 참고 — 재시작/정지는 운영 서비스에 직접 영향을 주는 작업이라 "화면에 보이는 것 =
   * 실제 대상"이 되도록 안전한 기본값으로 바꿨다) 이 요약줄은 평상시엔 항상 현재
   * 체크리스트와 일치해야 정상이다. 그래도 방어적으로, 삭제된 id처럼 더 이상
   * 존재하지 않는 항목은 여기서 걸러내면서 checkedIds 자체에서도 정리한다. */
  function renderCheckedSummary() {
    const el = document.getElementById('restart-checked-summary');
    const all = Instances.getFlatInstances();
    const targets = [];
    for (const id of Array.from(checkedIds)) {
      const inst = all.find((i) => i.id === id);
      if (inst) targets.push(inst); else checkedIds.delete(id);
    }
    if (!targets.length) { el.textContent = ''; return; }
    const visibleIds = new Set(visibleInstances().map((i) => i.id));
    const names = targets.map((i) => {
      const label = `[${i.server_group_name} / ${i.server_name} / ${i.group_name}] ${i.name}`;
      return visibleIds.has(i.id) ? label : `${label} (현재 필터에 안 보임)`;
    });
    el.textContent = `현재 선택된 대상 (전체 ${targets.length}개): ${names.join(', ')}`;
  }

  /* 좌측 트리 클릭이 바뀌면 서버그룹/서버/그룹 필터를 그 위치까지만 맞춘다 — 서버를
   * 클릭하면 서버그룹+서버만 채우고 그룹은 "전체"로 남겨서 그 서버의 모든 인스턴스가
   * 보이게 하고, 인스턴스를 클릭하면 4단 전부 맞춰서 그 한 줄만 강조한다.
   * chain: {serverGroupId, serverId, groupId, instanceId} — ui.js의 UI.treeNodeToChain 참고.
   * 필터가 바뀌면 체크 상태를 비운다(재시작/정지 사고 방지 — deploy.js 참고). */
  function applyTreeChain(chain) {
    highlightedId = chain.instanceId ?? null;
    checkedIds.clear();
    const sgSel = document.getElementById('restart-server-group-filter');
    const typeSel = document.getElementById('restart-server-type-filter');
    const serverSel = document.getElementById('restart-server-filter');
    const groupSel = document.getElementById('restart-group-filter');
    // renderXFilterOptions()는 옵션 목록만 다시 채우면서 "이전 값이 새 목록에도 있으면
    // 유지"하려 드는데, 트리 클릭으로 강제 동기화할 때는 그 값을 명시적으로 원하는
    // 대로 덮어써야 하므로 각 단계 재구성 직후 항상 값을 다시 지정한다.
    sgSel.value = chain.serverGroupId != null ? String(chain.serverGroupId) : '';
    typeSel.value = ''; // 트리 클릭은 서버를 직접 지정하므로 서버타입 필터는 초기화
    renderServerFilterOptions();
    serverSel.value = chain.serverId != null ? String(chain.serverId) : '';
    renderGroupFilterOptions();
    groupSel.value = chain.groupId != null ? String(chain.groupId) : '';
    renderList();
  }

  function reload() { renderServerGroupFilterOptions(); renderList(); }
  function selectAll() { checkedIds = new Set(visibleInstances().map((i) => i.id)); renderList(); }
  function selectNone() { checkedIds.clear(); renderList(); }

  function appendLog(text) {
    const area = document.getElementById('restart-log');
    area.value += (area.value ? '\n' : '') + text;
    area.scrollTop = area.scrollHeight;
  }

  async function confirmAndRun(label, fn) {
    if (!checkedIds.size) { UI.toast('인스턴스를 선택하세요.', 'error'); return; }
    const targets = Instances.getFlatInstances().filter((i) => checkedIds.has(i.id));
    const ok = await UI.confirmAction(label, targets);
    if (!ok) return;
    await fn();
  }

  function viewLogs() {
    if (!checkedIds.size) { UI.toast('로그를 볼 인스턴스를 선택하세요.', 'error'); return; }
    Logs.openForInstances(Instances.getFlatInstances().filter((i) => checkedIds.has(i.id)));
  }

  function selectedMethod() {
    return document.getElementById('restart-method').value; // '' | 'API' | 'SSH'
  }

  const restartGraceful = () => confirmAndRun('Graceful 재시작', () => Api.call('restart_all', Array.from(checkedIds), true, selectedMethod()));
  const restartForce = () => confirmAndRun('강제 재시작', () => Api.call('restart_all', Array.from(checkedIds), false, selectedMethod()));
  const shutdownGraceful = () => confirmAndRun('Graceful 정지', () => Api.call('shutdown_instances', Array.from(checkedIds), true, selectedMethod()));
  const shutdownForce = () => confirmAndRun('강제 정지', () => Api.call('shutdown_instances', Array.from(checkedIds), false, selectedMethod()));

  function onProgress(e) { appendLog(e.detail.message); }

  // ── 재시작/정지 이력 (감사로그의 RESTART_ALL/SHUTDOWN 항목을 걸러서 보여줌) ──

  function actionLabel(action) {
    if (action === 'RESTART_ALL') return '재시작';
    if (action === 'SHUTDOWN') return '정지';
    return action;
  }

  function resultClass(result) {
    if (result === 'SUCCESS') return 'result-success';
    if (result === 'FAILED') return 'result-failed';
    if (result === 'PARTIAL') return 'result-partial';
    return '';
  }

  function refreshHistoryInstanceOptions() {
    const sel = document.getElementById('rh-search-instance');
    const prev = sel.value;
    sel.innerHTML = '<option value="">전체</option>';
    for (const inst of Instances.getFlatInstances()) {
      const opt = document.createElement('option');
      opt.value = String(inst.id);
      opt.textContent = `[${inst.group_name}] ${inst.name}`;
      sel.appendChild(opt);
    }
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
  }

  function renderRestartHistory(items) {
    const tbody = document.querySelector('#rh-table tbody');
    tbody.innerHTML = '';
    if (!items.length) { tbody.innerHTML = '<tr><td colspan="5" class="hint">결과가 없습니다.</td></tr>'; return; }
    for (const h of items) {
      const tr = document.createElement('tr');
      tr.style.cursor = 'pointer';
      const detail = h.detail || '';
      const shortDetail = detail.length > 60 ? `${detail.slice(0, 60)}…` : detail;
      tr.innerHTML = `
        <td>${UI.esc(h.timestamp || '')}</td>
        <td>${UI.esc(h.target_name)}</td>
        <td>${UI.esc(actionLabel(h.action))}</td>
        <td class="${resultClass(h.result)}">${UI.esc(h.result)}</td>
        <td>${UI.esc(shortDetail)}</td>`;
      tr.addEventListener('click', () => {
        UI.detailModal(`${actionLabel(h.action)} 이력`, [
          ['시간', h.timestamp], ['인스턴스', h.target_name],
          ['구분', actionLabel(h.action)], ['결과', h.result],
          ['운영자', h.operator], ['상세', h.detail],
        ], 560);
      });
      tbody.appendChild(tr);
    }
  }

  async function searchRestartHistory() {
    const instanceVal = document.getElementById('rh-search-instance').value;
    const fromVal = document.getElementById('rh-search-from').value;
    const toVal = document.getElementById('rh-search-to').value;
    const dateFrom = fromVal ? `${fromVal} 00:00:00` : '';
    const dateTo = toVal ? `${toVal} 23:59:59` : '';
    const tbody = document.querySelector('#rh-table tbody');
    tbody.innerHTML = '<tr><td colspan="5" class="hint">검색 중...</td></tr>';
    const res = await Api.call('search_restart_history', instanceVal ? Number(instanceVal) : null, dateFrom, dateTo, 200);
    if (!res.success) {
      tbody.innerHTML = `<tr><td colspan="5" class="hint">검색 실패: ${UI.esc(res.error || '')}</td></tr>`;
      return;
    }
    renderRestartHistory(res.items);
  }

  async function clearRestartHistory() {
    const ok = await UI.confirm(
      '재시작/정지 이력을 모두 삭제합니다. 이 이력은 감사 로그와 같은 저장소를 사용하므로 ' +
      '감사 로그 탭의 RESTART_ALL / SHUTDOWN 기록도 함께 사라집니다 (되돌릴 수 없음).\n\n' +
      '계속하시겠습니까?',
      '⚠ 재시작/정지 이력 초기화');
    if (!ok) return;
    const res = await Api.call('clear_restart_history');
    UI.toast(`이력 ${res.count}건을 삭제했습니다.`);
    searchRestartHistory();
  }

  function onRestartDone() { searchRestartHistory(); }

  function init() {
    document.getElementById('restart-select-all').addEventListener('click', selectAll);
    document.getElementById('restart-select-none').addEventListener('click', selectNone);
    document.getElementById('restart-server-group-filter').addEventListener('change', () => { checkedIds.clear(); renderServerFilterOptions(); renderList(); });
    document.getElementById('restart-server-type-filter').addEventListener('change', () => { checkedIds.clear(); renderServerFilterOptions(); renderList(); });
    document.getElementById('restart-server-filter').addEventListener('change', () => { checkedIds.clear(); renderGroupFilterOptions(); renderList(); });
    document.getElementById('restart-group-filter').addEventListener('change', () => { checkedIds.clear(); renderList(); });
    document.getElementById('btn-shutdown-graceful').addEventListener('click', shutdownGraceful);
    document.getElementById('btn-restart-graceful').addEventListener('click', restartGraceful);
    document.getElementById('btn-shutdown-force').addEventListener('click', shutdownForce);
    document.getElementById('btn-restart-force').addEventListener('click', restartForce);
    document.getElementById('btn-restart-view-logs').addEventListener('click', viewLogs);
    document.getElementById('btn-rh-search').addEventListener('click', searchRestartHistory);
    document.getElementById('btn-rh-clear').addEventListener('click', clearRestartHistory);
    window.addEventListener('instances-changed', reload);
    window.addEventListener('instances-changed', refreshHistoryInstanceOptions);
    window.addEventListener('restart-progress', onProgress);
    window.addEventListener('restart-done', onRestartDone);

    window.addEventListener('tree-node-selected', (e) => applyTreeChain(UI.treeNodeToChain(e.detail.kind, e.detail.data)));

    reload();
    refreshHistoryInstanceOptions();
    searchRestartHistory();
  }

  return { init, reload };
})();
