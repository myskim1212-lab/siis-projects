/* 배포 패널 */
const Deploy = (() => {
  let selectedFiles = []; // [{path, type}] — type은 detect_artifact_type 결과가 오기 전엔 null
  let checkedIds = new Set();
  let selectedTarget = null; // {type: 'CAR'|'JAR', name: file_name}
  let lastHistoryItems = [];
  let highlightedId = null; // 지금 "보고 있는" 인스턴스 — 체크 여부와 무관, 목록에서 강조만 함

  function fileName(path) {
    return path.split(/[\\/]/).pop();
  }

  // 서버그룹을 고르기 전에는 아무것도 보여주지 않는다 — 배포는 실행하면 실제로
  // 영향이 생기는 작업이라, 처음부터 전체 인스턴스가 와르르 나오는 것보다 범위를
  // 직접 좁혀서 시작하는 게 안전하다.
  function visibleInstances() {
    const serverGroupId = document.getElementById('deploy-server-group-filter').value;
    if (!serverGroupId) return [];
    const typeId = document.getElementById('deploy-server-type-filter').value;
    const serverId = document.getElementById('deploy-server-filter').value;
    const groupId = document.getElementById('deploy-group-filter').value;
    return Instances.getFlatInstances().filter((i) =>
      DEPLOYABLE_TYPES.has(i.type) &&
      String(i.server_group_id) === serverGroupId &&
      (!typeId || i.server_environment === typeId) &&
      (!serverId || String(i.server_id) === serverId) &&
      (!groupId || String(i.group_id) === groupId));
  }

  // 서버 필터는 서버그룹을, 그룹 필터는 서버를 먼저 골라야 의미가 있다 —
  // 상위가 "전체"면 하위 선택지를 비우고 비활성화한다. 서버타입은 서버의 운영환경으로
  // 서버 단을 한 번 더 좁히는 보조 필터다.
  function renderServerFilterOptions() {
    const serverGroupId = document.getElementById('deploy-server-group-filter').value;
    const typeId = document.getElementById('deploy-server-type-filter').value;
    const sel = document.getElementById('deploy-server-filter');
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
    const serverGroupId = document.getElementById('deploy-server-group-filter').value;
    const serverId = document.getElementById('deploy-server-filter').value;
    const sel = document.getElementById('deploy-group-filter');
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
    const sel = document.getElementById('deploy-server-group-filter');
    const prev = sel.value;
    sel.innerHTML = '<option value="">서버그룹 선택...</option>' +
      Instances.getServerGroups().map((sg) => `<option value="${sg.id}">${UI.esc(sg.name)}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
    renderServerFilterOptions();
  }

  function renderList() {
    const container = document.getElementById('deploy-instance-list');
    container.innerHTML = '';
    if (!document.getElementById('deploy-server-group-filter').value) {
      container.innerHTML = '<div class="hint" style="padding:8px">서버그룹을 선택하면 인스턴스 목록이 표시됩니다.</div>';
      renderCheckedSummary();
      refreshRefInstanceOptions();
      return;
    }
    for (const inst of visibleInstances()) {
      const row = document.createElement('label');
      row.className = 'checklist-item';
      if (inst.id === highlightedId) row.classList.add('checklist-item-highlighted');
      row.dataset.instanceId = inst.id;
      const envClass = inst.environment === 'PROD' ? 'env-prod' : 'env-dev';
      row.innerHTML = `<input type="checkbox"> <span class="env-badge ${envClass}">${UI.esc(environmentLabel(inst.environment))}</span> [${UI.esc(inst.server_name)} / ${UI.esc(inst.group_name)}] ${UI.esc(inst.name)} (<span class="${instanceTypeColorClass(inst.type)}">${UI.esc(instanceTypeShortLabel(inst.type))}</span> / ${UI.esc(inst.host)}:${inst.port})`;
      const cb = row.querySelector('input');
      cb.checked = checkedIds.has(inst.id);
      cb.addEventListener('change', () => {
        if (cb.checked) checkedIds.add(inst.id); else checkedIds.delete(inst.id);
        renderCheckedSummary();
        refreshRefInstanceOptions();
      });
      container.appendChild(row);
    }
    renderCheckedSummary();
    refreshRefInstanceOptions();
    const hlRow = highlightedId != null && container.querySelector(`[data-instance-id="${highlightedId}"]`);
    if (hlRow) hlRow.scrollIntoView({ block: 'nearest' });
  }

  /* 필터가 바뀌면 checkedIds를 비우므로(applyTreeChain, init()의 필터 change 리스너
   * 참고) 이 요약줄은 평상시엔 항상 현재 체크리스트와 일치해야 정상이다 — 그래도
   * 방어적으로, 삭제된 id처럼 더 이상 존재하지 않는 항목은 여기서 걸러내면서
   * checkedIds 자체에서도 정리하고, 지금 실제로 선택된 전체 목록을 눈에 보이게 표시한다. */
  function renderCheckedSummary() {
    const el = document.getElementById('deploy-checked-summary');
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
    el.textContent = `현재 선택된 배포 대상 (전체 ${targets.length}개): ${names.join(', ')}`;
  }

  /* 좌측 트리 클릭이 바뀌면 서버그룹/서버/그룹 필터를 그 위치까지만 맞춘다 — 서버를
   * 클릭하면 서버그룹+서버만 채우고 그룹은 "전체"로 남겨서 그 서버의 모든 인스턴스가
   * 보이게 하고, 인스턴스를 클릭하면 4단 전부 맞춰서 그 한 줄만 강조한다.
   * chain: {serverGroupId, serverId, groupId, instanceId} — ui.js의 UI.treeNodeToChain 참고.
   * 필터가 바뀌면 체크 상태를 비운다 — 예전엔 범위를 넘나들며 체크한 걸 그대로
   * 남겨뒀는데, 화면엔 안 보이는 이전 선택이 조용히 배포 대상에 남아있다가 운영
   * 인스턴스까지 의도치 않게 포함되는 사고가 실제로 있어서(선택 2개인 줄 알았는데
   * 실제로는 4개) "화면에 보이는 것 = 실제 대상"이 되도록 안전한 기본값으로 바꿨다. */
  function applyTreeChain(chain) {
    highlightedId = chain.instanceId ?? null;
    checkedIds.clear();
    const sgSel = document.getElementById('deploy-server-group-filter');
    const typeSel = document.getElementById('deploy-server-type-filter');
    const serverSel = document.getElementById('deploy-server-filter');
    const groupSel = document.getElementById('deploy-group-filter');
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

  function toggleUndeploySection() {
    const sec = document.getElementById('undeploy-section');
    const btn = document.getElementById('btn-undeploy-toggle');
    const show = sec.style.display === 'none';
    sec.style.display = show ? '' : 'none';
    btn.textContent = show ? '삭제 기능 비활성화' : '삭제 기능 활성화';
  }

  function reload() { renderServerGroupFilterOptions(); renderList(); }
  function selectAll() {
    checkedIds = new Set(visibleInstances().map((i) => i.id));
    renderList();
  }
  function selectNone() { checkedIds.clear(); renderList(); }

  // ── 삭제 대상 조회 (배포된 CAR / lib JAR 목록) ─────────────────────────

  function clearTarget() {
    selectedTarget = null;
    document.getElementById('undeploy-target-label').textContent = '선택된 삭제 대상: 없음';
  }

  function setTarget(type, name, rowEl) {
    selectedTarget = { type, name };
    document.getElementById('undeploy-target-label').textContent = `선택된 삭제 대상: [${type}] ${name}`;
    document.querySelectorAll('#undeploy-car-table tbody tr, #undeploy-jar-table tbody tr, #undeploy-sequence-table tbody tr')
      .forEach((tr) => tr.classList.remove('row-selected'));
    if (rowEl) rowEl.classList.add('row-selected');
  }

  function refreshRefInstanceOptions() {
    const sel = document.getElementById('undeploy-ref-instance');
    const prev = sel.value;
    const deployable = Instances.getFlatInstances().filter((i) => DEPLOYABLE_TYPES.has(i.type));
    const candidates = deployable.filter((i) => checkedIds.has(i.id));
    sel.innerHTML = '';
    if (!candidates.length) {
      sel.innerHTML = '<option value="">대상 인스턴스를 먼저 선택하세요</option>';
      renderUndeployTables([], [], []);
      clearTarget();
      return;
    }
    for (const inst of candidates) {
      const opt = document.createElement('option');
      opt.value = String(inst.id);
      opt.textContent = `[${inst.group_name}] ${inst.name}`;
      sel.appendChild(opt);
    }
    if (candidates.some((i) => String(i.id) === prev)) sel.value = prev;
    loadUndeployLists();
  }

  function formatSize(n) {
    if (n == null) return '';
    if (n < 1024) return `${n} B`;
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
    return `${(n / 1024 / 1024).toFixed(1)} MB`;
  }

  function formatMtime(ts) {
    if (!ts) return '';
    return new Date(ts * 1000).toLocaleString();
  }

  function renderUndeployTables(cars, jars, sequences) {
    const carBody = document.querySelector('#undeploy-car-table tbody');
    const jarBody = document.querySelector('#undeploy-jar-table tbody');
    const seqBody = document.querySelector('#undeploy-sequence-table tbody');
    carBody.innerHTML = '';
    jarBody.innerHTML = '';
    seqBody.innerHTML = '';

    if (!cars.length) {
      carBody.innerHTML = '<tr><td colspan="3" class="hint">배포된 CAR 앱이 없습니다.</td></tr>';
    } else {
      for (const app of cars) {
        const tr = document.createElement('tr');
        tr.style.cursor = 'pointer';
        tr.innerHTML = `<td>${UI.esc(app.name)}</td><td>${UI.esc(app.version || '')}</td><td>${app.faulty ? '<span class="result-failed">오류</span>' : '정상'}</td>`;
        tr.addEventListener('click', () => setTarget('CAR', app.file_name, tr));
        carBody.appendChild(tr);
      }
    }

    if (!jars.length) {
      jarBody.innerHTML = '<tr><td colspan="3" class="hint">JAR 파일이 없습니다.</td></tr>';
    } else {
      for (const jar of jars) {
        const tr = document.createElement('tr');
        tr.style.cursor = 'pointer';
        tr.innerHTML = `<td>${UI.esc(jar.name)}</td><td>${formatSize(jar.size)}</td><td>${formatMtime(jar.mtime)}</td>`;
        tr.addEventListener('click', () => setTarget('JAR', jar.name, tr));
        jarBody.appendChild(tr);
      }
    }

    if (!sequences.length) {
      seqBody.innerHTML = '<tr><td colspan="3" class="hint">시퀀스 파일이 없습니다.</td></tr>';
    } else {
      for (const seq of sequences) {
        const tr = document.createElement('tr');
        tr.style.cursor = 'pointer';
        tr.innerHTML = `<td>${UI.esc(seq.name)}</td><td>${formatSize(seq.size)}</td><td>${formatMtime(seq.mtime)}</td>`;
        tr.addEventListener('click', () => setTarget('SEQUENCE', seq.name, tr));
        seqBody.appendChild(tr);
      }
    }
  }

  async function loadUndeployLists() {
    const sel = document.getElementById('undeploy-ref-instance');
    const instId = sel.value ? Number(sel.value) : null;
    clearTarget();
    if (!instId) { renderUndeployTables([], [], []); return; }

    const carBody = document.querySelector('#undeploy-car-table tbody');
    const jarBody = document.querySelector('#undeploy-jar-table tbody');
    const seqBody = document.querySelector('#undeploy-sequence-table tbody');
    carBody.innerHTML = '<tr><td colspan="3" class="hint">조회 중...</td></tr>';
    jarBody.innerHTML = '<tr><td colspan="3" class="hint">조회 중...</td></tr>';
    seqBody.innerHTML = '<tr><td colspan="3" class="hint">조회 중...</td></tr>';

    const [carRes, jarRes, seqRes] = await Promise.all([
      Api.call('list_deployed_apps', instId),
      Api.call('list_lib_jars', instId),
      Api.call('list_sequence_files', instId),
    ]);
    const cars = carRes.success ? carRes.items : [];
    const jars = jarRes.success ? jarRes.items : [];
    const sequences = seqRes.success ? seqRes.items : [];
    renderUndeployTables(cars, jars, sequences);
    if (!carRes.success) {
      carBody.innerHTML = `<tr><td colspan="3" class="hint">CAR 조회 실패: ${UI.esc(carRes.error || '')}</td></tr>`;
    }
    if (!jarRes.success) {
      jarBody.innerHTML = `<tr><td colspan="3" class="hint">JAR 조회 실패: ${UI.esc(jarRes.error || '')}</td></tr>`;
    }
    if (!seqRes.success) {
      seqBody.innerHTML = `<tr><td colspan="3" class="hint">시퀀스 조회 실패: ${UI.esc(seqRes.error || '')}</td></tr>`;
    }
  }

  function appendLog(text) {
    const area = document.getElementById('deploy-log');
    area.value += (area.value ? '\n' : '') + text;
    area.scrollTop = area.scrollHeight;
  }

  const TYPE_LABELS = { CAR: 'CAR', SEQUENCE: '시퀀스', JAR: 'JAR', UNKNOWN: '알 수 없음' };
  const TYPE_CLASSES = { CAR: 'type-car', SEQUENCE: 'type-sequence', JAR: 'type-jar', UNKNOWN: 'type-unknown' };

  /* 파일 선택 대화상자에서 여러 개를 한 번에 고를 수 있고, 여러 번 눌러서 계속
   * 추가할 수도 있다(이미 추가된 경로는 중복으로 다시 넣지 않음). */
  async function browseFile() {
    const paths = await Api.call('pick_files');
    if (!paths || !paths.length) return;
    const existing = new Set(selectedFiles.map((f) => f.path));
    for (const path of paths) {
      if (existing.has(path)) continue;
      existing.add(path);
      selectedFiles.push({ path, type: null });
    }
    renderFileList();
  }

  function removeFile(path) {
    selectedFiles = selectedFiles.filter((f) => f.path !== path);
    renderFileList();
  }

  function clearFiles() {
    selectedFiles = [];
    renderFileList();
  }

  function renderFileList() {
    const container = document.getElementById('deploy-file-list');
    document.getElementById('btn-clear-files').classList.toggle('hidden', !selectedFiles.length);
    if (!selectedFiles.length) {
      container.innerHTML = '<span class="hint">선택된 파일 없음</span>';
      return;
    }
    container.innerHTML = '';
    for (const f of selectedFiles) {
      const chip = document.createElement('span');
      chip.className = 'file-chip';
      const badgeClass = f.type ? (TYPE_CLASSES[f.type] || 'type-unknown') : 'type-unknown';
      const badgeText = f.type ? (TYPE_LABELS[f.type] || f.type) : '확인 중…';
      chip.innerHTML = `
        <span class="file-chip-name" title="${UI.esc(f.path)}">${UI.esc(fileName(f.path))}</span>
        <span class="type-badge ${badgeClass}" data-role="type-badge">${UI.esc(badgeText)}</span>
        <span class="file-chip-remove" title="제거">&times;</span>`;
      chip.querySelector('.file-chip-remove').addEventListener('click', () => removeFile(f.path));
      container.appendChild(chip);
      if (!f.type) {
        Api.call('detect_artifact_type', f.path).then((res) => {
          f.type = res && res.success ? res.type : 'UNKNOWN';
          const badge = chip.querySelector('[data-role=type-badge]');
          if (badge) {
            badge.textContent = TYPE_LABELS[f.type] || f.type;
            badge.className = `type-badge ${TYPE_CLASSES[f.type] || 'type-unknown'}`;
          }
        });
      }
    }
  }

  /* 체크된 인스턴스를 대상으로, 무슨 작업인지(actionLabel)와 대상 목록을
   * 그룹별로 분리해서 보여주는 확인창. PROD가 섞여 있으면 자동으로 경고 스타일. */
  async function confirmTargets(actionLabel, extraHtml = '') {
    const targets = Instances.getFlatInstances().filter((i) => checkedIds.has(i.id));
    return UI.confirmAction(actionLabel, targets, extraHtml);
  }

  async function doDeploy() {
    if (!selectedFiles.length) { UI.toast('배포 파일을 선택하세요.', 'error'); return; }
    if (!checkedIds.size) { UI.toast('대상 인스턴스를 선택하세요.', 'error'); return; }
    const reason = document.getElementById('deploy-reason').value.trim();
    const fileNames = selectedFiles.map((f) => UI.esc(fileName(f.path))).join(', ');
    const extra = `<div class="confirm-detail">파일 ${selectedFiles.length}개: <b>${fileNames}</b>${reason ? ` · 사유: ${UI.esc(reason)}` : ''}</div>`;
    const ok = await confirmTargets('배포', extra);
    if (!ok) return;
    appendLog(`배포 시작: 파일 ${selectedFiles.length}개 -> ${checkedIds.size}개 인스턴스`);
    await Api.call('deploy', selectedFiles.map((f) => f.path), Array.from(checkedIds), reason);
  }

  function viewLogs() {
    if (!checkedIds.size) { UI.toast('로그를 볼 인스턴스를 선택하세요.', 'error'); return; }
    Logs.openForInstances(Instances.getFlatInstances().filter((i) => checkedIds.has(i.id)));
  }

  async function doUndeploy() {
    if (!checkedIds.size) { UI.toast('대상 인스턴스를 선택하세요.', 'error'); return; }
    if (!selectedTarget) { UI.toast('삭제 대상 조회 목록에서 삭제할 항목을 선택하세요.', 'error'); return; }
    const { type, name } = selectedTarget;
    const extra = `<div class="confirm-detail">삭제 대상: <b>[${UI.esc(type)}] ${UI.esc(name)}</b> · 가능하면 삭제 전 원본을 백업합니다.</div>`;
    const ok = await confirmTargets('삭제', extra);
    if (!ok) return;
    await Api.call('undeploy', name, type, Array.from(checkedIds));
  }

  function resultLabel(r) {
    const inst = Instances.getFlatInstances().find((i) => i.id === r.instance_id);
    return inst ? `[${inst.group_name}] ${inst.name}` : r.instance;
  }

  function onDeployDone(e) {
    for (const r of e.detail.results) {
      const fileTag = r.file_name ? `[${r.file_name}] ` : '';
      appendLog(`  ${fileTag}[${r.success ? 'OK' : 'FAIL'}] ${resultLabel(r)}  ${r.error || ''}`);
    }
    appendLog('배포 완료');
    searchDeploymentHistory();
  }

  function onUndeployDone(e) {
    for (const r of e.detail.results) {
      const backup = r.backup_path ? ` (백업: ${r.backup_path})` : '';
      appendLog(`  [${r.success ? 'OK' : 'FAIL'}] ${resultLabel(r)}  ${r.error || ''}${backup}`);
    }
    appendLog('삭제 완료');
    loadUndeployLists();
  }

  // ── 배포 이력 검색 ────────────────────────────────────────────────────

  function instanceNameById(id) {
    const inst = Instances.getFlatInstances().find((i) => i.id === id);
    return inst ? `[${inst.group_name}] ${inst.name}` : `#${id}`;
  }

  function renderDeploymentHistory(items) {
    const tbody = document.querySelector('#dh-table tbody');
    tbody.innerHTML = '';
    if (!items.length) { tbody.innerHTML = '<tr><td colspan="6" class="hint">결과가 없습니다.</td></tr>'; return; }
    for (const d of items) {
      const tr = document.createElement('tr');
      tr.style.cursor = 'pointer';
      const resultClass = d.status === 'SUCCESS' ? 'result-success' : 'result-failed';
      tr.innerHTML = `
        <td>${UI.esc(d.deployed_at || '')}</td>
        <td>${UI.esc(instanceNameById(d.instance_id))}</td>
        <td>${UI.esc(d.artifact_name)}</td>
        <td>${UI.esc(d.artifact_type)}</td>
        <td class="${resultClass}">${UI.esc(d.status)}</td>
        <td>${UI.esc(d.reason || '')}</td>`;
      tr.addEventListener('click', () => {
        UI.detailModal(`배포 이력: ${d.artifact_name}`, [
          ['배포일시', d.deployed_at], ['인스턴스', instanceNameById(d.instance_id)],
          ['파일', d.artifact_name], ['타입', d.artifact_type], ['결과', d.status],
          ['사유', d.reason], ['배포자', d.deployed_by],
          ['원본 경로', d.source_path], ['백업 경로', d.backup_path],
        ], 560);
      });
      tbody.appendChild(tr);
    }
  }

  async function searchDeploymentHistory() {
    const artifactName = document.getElementById('dh-search-file').value.trim();
    const reasonKeyword = document.getElementById('dh-search-reason').value.trim();
    const fromVal = document.getElementById('dh-search-from').value;
    const toVal = document.getElementById('dh-search-to').value;
    const dateFrom = fromVal ? `${fromVal} 00:00:00` : '';
    const dateTo = toVal ? `${toVal} 23:59:59` : '';
    const tbody = document.querySelector('#dh-table tbody');
    tbody.innerHTML = '<tr><td colspan="6" class="hint">검색 중...</td></tr>';
    const res = await Api.call('search_deployments', null, artifactName, reasonKeyword, dateFrom, dateTo, 200);
    if (!res.success) {
      tbody.innerHTML = `<tr><td colspan="6" class="hint">검색 실패: ${UI.esc(res.error || '')}</td></tr>`;
      return;
    }
    lastHistoryItems = res.items;
    renderDeploymentHistory(res.items);
  }

  function init() {
    document.getElementById('btn-browse-file').addEventListener('click', browseFile);
    document.getElementById('btn-clear-files').addEventListener('click', clearFiles);
    renderFileList();
    document.getElementById('deploy-select-all').addEventListener('click', selectAll);
    document.getElementById('deploy-select-none').addEventListener('click', selectNone);
    document.getElementById('deploy-server-group-filter').addEventListener('change', () => { checkedIds.clear(); renderServerFilterOptions(); renderList(); });
    document.getElementById('deploy-server-type-filter').addEventListener('change', () => { checkedIds.clear(); renderServerFilterOptions(); renderList(); });
    document.getElementById('deploy-server-filter').addEventListener('change', () => { checkedIds.clear(); renderGroupFilterOptions(); renderList(); });
    document.getElementById('deploy-group-filter').addEventListener('change', () => { checkedIds.clear(); renderList(); });
    document.getElementById('btn-deploy').addEventListener('click', doDeploy);
    document.getElementById('btn-deploy-view-logs').addEventListener('click', viewLogs);
    document.getElementById('btn-undeploy').addEventListener('click', doUndeploy);
    document.getElementById('btn-undeploy-toggle').addEventListener('click', toggleUndeploySection);
    document.getElementById('btn-dh-search').addEventListener('click', searchDeploymentHistory);
    document.getElementById('btn-undeploy-refresh').addEventListener('click', loadUndeployLists);
    document.getElementById('undeploy-ref-instance').addEventListener('change', loadUndeployLists);
    window.addEventListener('instances-changed', reload);
    window.addEventListener('instances-changed', () => renderDeploymentHistory(lastHistoryItems));
    window.addEventListener('deploy-done', onDeployDone);
    window.addEventListener('undeploy-done', onUndeployDone);

    window.addEventListener('tree-node-selected', (e) => applyTreeChain(UI.treeNodeToChain(e.detail.kind, e.detail.data)));

    reload();
    searchDeploymentHistory();
  }

  return { init, reload };
})();
