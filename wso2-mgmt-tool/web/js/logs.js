/* 실시간 로그 뷰어 — 인스턴스별 탭, SSH/API 자동 분기 */
const Logs = (() => {
  const tabs = new Map(); // instanceId -> {inst, tabEl, panel, running}
  let activeId = null;
  let gridView = false;
  let instSelect = null; // 서버그룹>서버>그룹>인스턴스 선택 — ui.js의 UI.wireInstanceCascade

  function openInstance(inst) {
    if (tabs.has(inst.id)) { activate(inst.id); return; }
    const mode = inst.ssh_enabled ? 'SSH' : 'API';
    // API와 SSH 둘 다 실제로 가능한 경우(MI 타입 + SSH 설정됨)에만 방식 선택을 보여준다.
    // (APIM은 API 방식 미지원, SSH 미설정이면 API만 가능해서 고를 게 없음.)
    const bothAvailable = inst.type.startsWith('MI_') && inst.ssh_enabled;

    const tabEl = document.createElement('button');
    tabEl.className = 'subtab-btn';
    tabEl.innerHTML = `<span>${UI.esc(inst.name)} [${mode}]</span><span class="subtab-close">&times;</span>`;
    tabEl.querySelector('span:first-child').addEventListener('click', () => activate(inst.id));
    tabEl.querySelector('.subtab-close').addEventListener('click', (e) => { e.stopPropagation(); closeTab(inst.id); });
    document.getElementById('log-subtabbar').appendChild(tabEl);

    const methodControl = bothAvailable
      ? `<select data-role="method" class="input-sm">
           <option value="SSH" selected>SSH</option>
           <option value="API">API</option>
         </select>`
      : `<span>${mode}</span>`;

    // 파일 선택은 Management API로 목록을 받아와야 해서 MI 타입에서만 의미가 있다
    // (SSH는 항상 인스턴스에 설정된 log_path 전체 경로를 그대로 tail한다).
    const isMi = inst.type.startsWith('MI_');
    const fileControl = isMi
      ? `<select data-role="file" class="input-sm file-select"><option value="wso2carbon.log">wso2carbon.log</option></select>`
      : '';

    const panel = document.createElement('div');
    panel.className = 'log-panel';
    panel.innerHTML = `
      <div class="row">
        ${methodControl}
        ${fileControl}
        <button class="btn btn-sm" data-act="start">시작</button>
        <button class="btn btn-sm" data-act="stop">중지</button>
        <button class="btn btn-sm" data-act="clear">지우기</button>
        <label>필터:</label>
        <input type="text" placeholder="키워드 필터" class="input-sm" style="width:180px" data-role="filter">
      </div>
      <div class="log-console" data-role="console"></div>`;
    document.getElementById('log-panels').appendChild(panel);

    const state = { inst, tabEl, panel, running: false };
    tabs.set(inst.id, state);

    panel.querySelector('[data-act=start]').addEventListener('click', () => start(inst.id));
    panel.querySelector('[data-act=stop]').addEventListener('click', () => stop(inst.id));
    panel.querySelector('[data-act=clear]').addEventListener('click', () => {
      panel.querySelector('[data-role=console]').innerHTML = '';
    });
    const methodSel = panel.querySelector('[data-role=method]');
    if (methodSel) {
      methodSel.addEventListener('change', async () => {
        tabEl.querySelector('span:first-child').textContent = `${inst.name} [${methodSel.value}]`;
        if (state.running) { await stop(inst.id); await start(inst.id); }
      });
    }
    const fileSel = panel.querySelector('[data-role=file]');
    if (fileSel) {
      fileSel.addEventListener('change', async () => {
        if (state.running) { await stop(inst.id); await start(inst.id); }
      });
      loadFileOptions(inst, fileSel);
    }

    activate(inst.id);
    start(inst.id);
  }

  // 로그 파일 목록을 받아와 드롭다운을 채운다. 인스턴스에 설정된 로그 경로의
  // 파일명이 목록에 있으면 그걸, 없으면 기본값 wso2carbon.log를 선택해둔다.
  async function loadFileOptions(inst, fileSel) {
    const res = await Api.call('list_log_files', inst.id);
    if (!res || !res.success || !res.items) return;
    const configuredName = (inst.log_path || '').split(/[\\/]/).pop() || 'wso2carbon.log';
    const prev = fileSel.value;
    fileSel.innerHTML = res.items.map((f) =>
      `<option value="${UI.esc(f.FileName)}">${UI.esc(f.FileName)}</option>`).join('');
    const names = res.items.map((f) => f.FileName);
    if (names.includes(prev)) fileSel.value = prev;
    else if (names.includes('wso2carbon.log')) fileSel.value = 'wso2carbon.log';
    else if (names.includes(configuredName)) fileSel.value = configuredName;
  }

  function activate(id) {
    activeId = id;
    for (const [tid, s] of tabs) {
      s.tabEl.classList.toggle('active', tid === id);
      s.panel.classList.toggle('active', tid === id);
    }
  }

  async function start(id) {
    const s = tabs.get(id);
    if (!s || s.running) return;
    const methodSel = s.panel.querySelector('[data-role=method]');
    const method = methodSel ? methodSel.value : '';
    const fileSel = s.panel.querySelector('[data-role=file]');
    const logFilename = fileSel ? fileSel.value : '';
    const res = await Api.call('start_log_stream', id, 100, method, logFilename);
    if (res && res.success) {
      s.running = true;
      appendLine(s, `--- [${s.inst.name}] 로그 스트리밍 시작 (${res.mode}) ---`, 'other');
    } else {
      UI.toast(`로그 스트림 시작 실패: ${(res && res.error) || ''}`, 'error');
    }
  }

  async function stop(id) {
    const s = tabs.get(id);
    if (!s) return;
    await Api.call('stop_log_stream', id);
    s.running = false;
    appendLine(s, `--- [${s.inst.name}] 로그 스트리밍 중지 ---`, 'other');
  }

  function closeTab(id) {
    const s = tabs.get(id);
    if (!s) return;
    if (s.running) Api.call('stop_log_stream', id);
    s.tabEl.remove();
    s.panel.remove();
    tabs.delete(id);
    if (activeId === id) {
      activeId = null;
      const next = tabs.keys().next();
      if (!next.done) activate(next.value);
    }
  }

  function closeActive() {
    if (activeId != null) closeTab(activeId);
  }

  function toggleGridView() {
    gridView = !gridView;
    document.getElementById('log-panels').classList.toggle('grid-view', gridView);
    document.getElementById('btn-log-grid-toggle').textContent = gridView ? '탭 보기' : '바둑판 보기';
  }

  function levelOf(line) {
    const ll = line.toLowerCase();
    if (ll.includes('error') || ll.includes('exception')) return 'error';
    if (ll.includes('warn')) return 'warn';
    if (ll.includes('info')) return 'info';
    return 'other';
  }

  // 새 로그가 계속 들어오는 도중엔 매번 맨 아래로 스크롤을 강제해서 드래그
  // 선택(복사)이 불가능했다 — 사용자가 위로 스크롤했거나 콘솔 안에서 텍스트를
  // 선택 중이면 자동 스크롤을 멈추고, 맨 아래 근처에 있을 때만(=보고 있던 실시간
  // 흐름을 유지하고 싶을 때만) 계속 따라 내려간다.
  function isNearBottom(el, threshold = 30) {
    return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold;
  }

  function hasActiveSelectionIn(el) {
    const sel = window.getSelection();
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) return false;
    return el.contains(sel.anchorNode) || el.contains(sel.focusNode);
  }

  // 실시간으로 따라가는 중일 땐 최신 줄이 창 맨 아래가 아니라 세로 중간쯤에 오도록,
  // 콘솔 맨 끝에 콘솔 높이의 절반만큼 빈 여백을 항상 붙여둔다. 맨 아래(=이 여백의
  // 끝)까지 스크롤하면 실제 마지막 로그 줄은 자동으로 뷰포트 중간에 위치하게 된다.
  function ensureSpacer(consoleEl) {
    let spacer = consoleEl.querySelector('.log-console-spacer');
    if (!spacer) {
      spacer = document.createElement('div');
      spacer.className = 'log-console-spacer';
      consoleEl.appendChild(spacer);
    }
    spacer.style.height = `${Math.max(0, Math.floor(consoleEl.clientHeight / 2))}px`;
    return spacer;
  }

  function appendLine(s, text, level) {
    const consoleEl = s.panel.querySelector('[data-role=console]');
    const shouldStickToBottom = isNearBottom(consoleEl) && !hasActiveSelectionIn(consoleEl);
    const spacer = ensureSpacer(consoleEl);
    const div = document.createElement('div');
    div.className = `log-line lvl-${level}`;
    div.textContent = text;
    consoleEl.insertBefore(div, spacer);
    while (consoleEl.children.length > 5001) consoleEl.removeChild(consoleEl.firstChild);
    if (shouldStickToBottom) consoleEl.scrollTop = consoleEl.scrollHeight;
  }

  function onLine(e) {
    const { instance_id: instanceId, line } = e.detail;
    const s = tabs.get(instanceId);
    if (!s) return;
    const filter = s.panel.querySelector('[data-role=filter]').value.trim().toLowerCase();
    if (filter && !line.toLowerCase().includes(filter)) return;
    appendLine(s, line, levelOf(line));
  }

  async function addTabDialog() {
    // Instances.getFlatInstances()는 그룹(이름순) -> 그룹 내 인스턴스(이름순)로
    // 이미 정렬되어 있으므로 그대로 쓰면 된다 (db.database의 COLLATE NOCASE 정렬).
    const instances = Instances.getFlatInstances();
    if (!instances.length) return;
    const chosen = await UI.selectDialog('로그를 볼 인스턴스:',
      instances.map((i) => ({ value: i.id, label: `[${i.server_name} / ${i.group_name}] ${i.name} - ${i.host}` })),
      '인스턴스 선택');
    if (!chosen) return;
    const inst = instances.find((i) => String(i.id) === String(chosen));
    if (inst) openInstance(inst);
  }

  /* 배포/재시작/정지 등 다른 탭에서 액션 실행 직전에 호출 — 대상 인스턴스(들)의
   * 로그 탭을 열고(이미 열려있으면 재사용) 로그 탭으로 자동 전환한다.
   * 여러 인스턴스를 한 번에 배포/재시작했다면 그만큼 탭이 여러 개 열린다
   * (그리드 보기로 한눈에 볼 수 있음). */
  function openForInstances(instances) {
    if (!instances.length) return;
    for (const inst of instances) openInstance(inst);
    switchTab('logs');
  }

  function init() {
    document.getElementById('btn-log-add-tab').addEventListener('click', addTabDialog);
    document.getElementById('btn-log-close-tab').addEventListener('click', closeActive);
    document.getElementById('btn-log-grid-toggle').addEventListener('click', toggleGridView);
    window.addEventListener('log-line', onLine);
    window.addEventListener('tree-node-selected', (e) => {
      const { kind, data } = e.detail;
      const logsTabActive = document.querySelector('.tab-btn[data-tab="logs"]').classList.contains('active');
      // 로그뷰어 탭이 열려 있을 때 인스턴스를 직접 클릭하면 바로 그 로그 탭을 연다
      // (서버그룹/서버/그룹 클릭은 셀렉트만 그 깊이까지 맞추고, 자동으로 탭을 열지는 않음).
      if (logsTabActive && kind === 'instance') openInstance(data);
      instSelect.refresh(UI.treeNodeToChain(kind, data));
    });

    // 인스턴스를 골라 "열기"를 누르면 그 인스턴스의 로그 탭을 연다
    // ("+ 탭 추가"의 검색 다이얼로그와 별개로, 좌측 트리를 안 거치고도 바로 고를 수 있는 방법).
    instSelect = UI.wireInstanceCascade(
      document.getElementById('log-sg-select'), document.getElementById('log-type-select'),
      document.getElementById('log-server-select'),
      document.getElementById('log-group-select'), document.getElementById('log-instance-select'),
      () => {});
    document.getElementById('btn-log-open-selected').addEventListener('click', () => {
      const inst = instSelect.getInstance();
      if (!inst) { UI.toast('열 인스턴스를 선택하세요.', 'error'); return; }
      openInstance(inst);
    });
    window.addEventListener('instances-changed', () => instSelect.refresh());
  }

  return { init, openInstance, openForInstances };
})();
