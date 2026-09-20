/* JVM 모니터링 패널 — 서버그룹/서버/그룹 단위로 범위를 선택하면 그 안의 모든 MI
 * 인스턴스의 힙/GC/파일디스크립터/메모리풀/JDBC 커넥션풀 상태(SIIS 커넥터의
 * JvmInfoMediator, dbinfo와는 별개 엔드포인트)를 한 화면에 격자로 보여준다.
 * 인스턴스 개수에 따라 카드 밀도(full/compact/mini)가 자동으로 바뀐다. */
const Monitor = (() => {
  let highlightedId = null; // 트리에서 클릭한 인스턴스 — 카드 테두리로만 강조, 범위 자체는 그대로 둠
  let checkedIds = new Set(); // 필터로 좁힌 목록 중 실제로 모니터링할 인스턴스로 체크된 것들

  // JVM 정보 주기 조회(백그라운드 pollTimer)는 필터 범위가 정해졌다고 저절로 시작되면
  // 안 되고, 사용자가 "모니터링 시작" 버튼을 눌렀을 때부터만 시작돼야 한다(트리 클릭이나
  // 필터 변경만으로 범위가 잡혀도 그 자체로는 반복 수집을 켜지 않는다). "새로고침"으로
  // 그 순간 상태만 한 번 보는 것은 이 값과 무관하게 언제든 가능하다.
  let monitoringActive = false;

  // 추이(히스토리) 데이터 — 앱 실행 중에만 메모리에 보관, 재시작 시 초기화됨.
  // instanceId -> [{ts, heapPct, threadCount, cpuPct, fdPct}, ...] (오래된 것부터)
  const history = new Map();
  const MAX_SAMPLES = 120; // 기본 30초 주기 기준 최근 1시간

  const POLL_SEC_DEFAULT = 30;
  const POLL_SEC_MIN = 5;
  const POLL_SEC_STORAGE_KEY = 'wso2mgmt.monitorPollSec';
  let pollTimer = null;
  let requestSeq = 0; // 응답이 오는 사이 범위가 바뀌면 낡은 응답을 버리기 위한 순번

  function loadPollSec() {
    try {
      const n = parseInt(localStorage.getItem(POLL_SEC_STORAGE_KEY), 10);
      if (Number.isFinite(n) && n >= POLL_SEC_MIN) return n;
    } catch (e) { /* localStorage 접근 불가 — 기본값 사용 */ }
    return POLL_SEC_DEFAULT;
  }

  let pollSec = loadPollSec();

  function restartPollTimer() {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(() => { if (monitoringActive && checkedIds.size) refreshAll({ silent: true }); }, pollSec * 1000);
  }

  function updateMonitorStatusUi() {
    const btn = document.getElementById('btn-monitor-toggle');
    const status = document.getElementById('monitor-status');
    btn.textContent = monitoringActive ? '모니터링 정지' : '모니터링 시작';
    status.textContent = monitoringActive ? `실행 중 (${pollSec}초 주기)` : '중지됨';
    status.classList.toggle('result-success', monitoringActive);
  }

  /* "모니터링 시작" — 누른 시점에 체크되어 있던 인스턴스를 대상으로 삼아(targetInstances가
   * 매 주기마다 현재 체크 상태를 다시 읽으므로, 시작 후 체크를 바꾸면 그 범위로 따라간다)
   * 그때부터 pollTimer의 반복 조회를 허용한다. "정지"는 반복 조회만 멈추고 마지막으로
   * 받은 카드 내용은 화면에 그대로 남겨둔다. 체크된 인스턴스가 하나도 없으면 시작할 수
   * 없다(전체 인스턴스에 한꺼번에 병렬 호출이 나가는 걸 막기 위함). */
  function toggleMonitoring() {
    if (!monitoringActive) {
      if (!checkedIds.size) { UI.toast('모니터링할 인스턴스를 선택하세요.', 'error'); return; }
      monitoringActive = true;
      updateMonitorStatusUi();
      refreshAll();
    } else {
      monitoringActive = false;
      updateMonitorStatusUi();
    }
  }

  function recordSample(instanceId, data) {
    const fd = data.file_descriptors || {};
    const os = data.os || {};
    const th = data.threads || {};
    const heapPct = usageRatio(data.heap);
    const sample = {
      ts: Date.now(),
      heapPct,
      threadCount: th.current ?? null,
      cpuPct: os.process_cpu_load != null ? Math.round(os.process_cpu_load * 1000) / 10 : null,
      fdPct: fd.usage_pct ?? null,
    };
    if (!history.has(instanceId)) history.set(instanceId, []);
    const arr = history.get(instanceId);
    arr.push(sample);
    if (arr.length > MAX_SAMPLES) arr.splice(0, arr.length - MAX_SAMPLES);
  }

  function formatBytes(n) {
    if (n == null || n < 0) return '-';
    if (n < 1024) return `${n} B`;
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
    if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`;
    return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`;
  }

  function formatDuration(ms) {
    const sec = Math.floor(ms / 1000);
    const d = Math.floor(sec / 86400);
    const h = Math.floor((sec % 86400) / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    const parts = [];
    if (d) parts.push(`${d}일`);
    if (d || h) parts.push(`${h}시간`);
    parts.push(`${m}분 ${s}초`);
    return parts.join(' ');
  }

  function healthClass(status) {
    if (status === 'CRITICAL') return 'result-failed';
    if (status === 'WARNING') return 'result-partial';
    return 'result-success';
  }

  function statusDotClass(status) {
    if (status === 'CRITICAL') return 'critical';
    if (status === 'WARNING') return 'warning';
    if (status === 'OK') return 'ok';
    return 'unknown';
  }

  function pctBarClass(pct) {
    if (pct == null) return '';
    if (pct >= 90) return 'critical';
    if (pct >= 75) return 'warn';
    return '';
  }

  function usageBarHtml(pct) {
    if (pct == null) return '';
    return `<div class="monitor-bar"><div class="monitor-bar-fill ${pctBarClass(pct)}" style="width:${Math.min(100, Math.max(0, pct))}%"></div></div>`;
  }

  function usageRatio(usage) {
    if (!usage || usage.max == null || usage.max < 0) return null;
    if (usage.max === 0) return 0;
    return Math.round((usage.used / usage.max) * 1000) / 10;
  }

  function usageText(usage) {
    if (!usage) return '-';
    const pct = usageRatio(usage);
    const maxText = usage.max != null && usage.max >= 0 ? formatBytes(usage.max) : '제한 없음';
    return `${formatBytes(usage.used)} / ${maxText}${pct != null ? ` (${pct}%)` : ''}`;
  }

  // ── 추이 차트 (인스턴스마다 독립된 DOM id를 붙여 여러 개를 동시에 그린다) ──
  const CHART_METRICS = [
    { slug: 'heap', key: 'heapPct', label: 'Heap 사용률', unit: '%', color: 'var(--primary)' },
    { slug: 'threads', key: 'threadCount', label: '스레드 수', unit: '개', color: 'var(--purple)' },
    { slug: 'cpu', key: 'cpuPct', label: 'CPU 사용률', unit: '%', color: 'var(--info)' },
    { slug: 'fd', key: 'fdPct', label: 'FD 사용률', unit: '%', color: 'var(--warn)' },
  ];

  // suffix는 상세보기 모달처럼 같은 인스턴스의 차트를 그리드 카드와 동시에 화면에
  // 띄울 때 DOM id가 겹치지 않도록 구분하는 용도다.
  function chartElId(instanceId, slug, suffix = '') {
    return `monitor-chart-${slug}-${instanceId}${suffix}`;
  }

  function formatChartValue(v, unit) {
    return v == null ? '-' : `${Math.round(v * 10) / 10}${unit}`;
  }

  function renderChartPanel(elId, metric, samples) {
    const el = document.getElementById(elId);
    if (!el) return;
    const points = samples.filter((s) => s[metric.key] != null);
    if (points.length < 2) {
      el.innerHTML = `<div class="chart-label"><span>${UI.esc(metric.label)}</span></div>
        <div class="hint">데이터 수집 중... (${points.length}개)</div>`;
      return;
    }
    const values = points.map((p) => p[metric.key]);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    const n = values.length;
    const coords = values.map((v, i) => {
      const x = (i / (n - 1)) * 100;
      const y = 38 - ((v - min) / span) * 34;
      return [x, y];
    });
    const lineStr = coords.map(([x, y]) => `${x.toFixed(2)},${y.toFixed(2)}`).join(' ');
    const areaStr = `0,40 ${lineStr} 100,40`;
    const latest = values[values.length - 1];
    el.innerHTML = `
      <div class="chart-label"><span>${UI.esc(metric.label)}</span><span class="chart-value">${formatChartValue(latest, metric.unit)}</span></div>
      <svg class="chart-svg" viewBox="0 0 100 40" preserveAspectRatio="none">
        <polygon points="${areaStr}" fill="${metric.color}" fill-opacity="0.12" stroke="none"></polygon>
        <polyline points="${lineStr}" fill="none" stroke="${metric.color}" stroke-width="1.5" vector-effect="non-scaling-stroke"></polyline>
      </svg>
      <div class="chart-caption"><span>최소 ${formatChartValue(min, metric.unit)}</span><span>최대 ${formatChartValue(max, metric.unit)}</span></div>`;
  }

  // ── 서버그룹 > 서버타입 > 서버 > 그룹 필터 — deploy/restart 탭과 동일한 패턴으로,
  // 서버그룹을 고르기 전에는 아무 인스턴스도 후보 목록(체크리스트)에 나타나지 않는다.
  // 서버타입은 서버의 운영환경(개발/운영/운영개발)으로 서버 단을 한 번 더 좁히는
  // 보조 필터다. 이 필터로 좁힌 후보 중 실제 모니터링 대상은 체크리스트에서 체크한
  // 것만이다(targetInstances 참고) — deploy/restart의 "체크한 것만 실제 대상" 패턴과
  // 동일하게, 필터에 보이는 것과 실제 모니터링 대상을 분리해 의도치 않은 확대를 막는다. ──
  function visibleInstances() {
    const sgId = document.getElementById('monitor-server-group-filter').value;
    if (!sgId) return [];
    const typeId = document.getElementById('monitor-server-type-filter').value;
    const serverId = document.getElementById('monitor-server-filter').value;
    const groupId = document.getElementById('monitor-group-filter').value;
    return Instances.getFlatInstances().filter((i) =>
      String(i.server_group_id) === sgId &&
      (!typeId || i.server_environment === typeId) &&
      (!serverId || String(i.server_id) === serverId) &&
      (!groupId || String(i.group_id) === groupId));
  }

  function hasScope() {
    return !!document.getElementById('monitor-server-group-filter').value;
  }

  /* 실제로 모니터링(새로고침/시작/반복 폴링)할 인스턴스 — 체크리스트에서 체크된
   * 것만 대상이다. 필터를 바꿔도 체크 상태 자체는 여기서 걸러지지 않고 그대로
   * 유지되며(삭제된 인스턴스만 방어적으로 제외), 필터 변경 시 체크를 비우는 건
   * onFilterChanged/applyTreeChain의 몫이다. */
  function targetInstances() {
    const all = Instances.getFlatInstances();
    return all.filter((i) => checkedIds.has(i.id));
  }

  function renderServerFilterOptions() {
    const sgId = document.getElementById('monitor-server-group-filter').value;
    const typeId = document.getElementById('monitor-server-type-filter').value;
    const sel = document.getElementById('monitor-server-filter');
    const prev = sel.value;
    if (!sgId) {
      sel.innerHTML = '<option value="">전체 서버</option>';
      sel.disabled = true;
      renderGroupFilterOptions();
      return;
    }
    sel.disabled = false;
    const sg = Instances.getServerGroups().find((x) => String(x.id) === sgId);
    const servers = (sg ? sg.servers : []).filter((s) => !typeId || s.environment === typeId);
    sel.innerHTML = '<option value="">전체 서버</option>' +
      servers.map((s) => `<option value="${s.id}">${UI.esc(serverOptionLabel(s))}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
    renderGroupFilterOptions();
  }

  function renderGroupFilterOptions() {
    const sgId = document.getElementById('monitor-server-group-filter').value;
    const serverId = document.getElementById('monitor-server-filter').value;
    const sel = document.getElementById('monitor-group-filter');
    const prev = sel.value;
    if (!sgId || !serverId) {
      sel.innerHTML = '<option value="">전체 그룹</option>';
      sel.disabled = true;
      return;
    }
    sel.disabled = false;
    const sg = Instances.getServerGroups().find((x) => String(x.id) === sgId);
    const server = sg ? sg.servers.find((s) => String(s.id) === serverId) : null;
    const groups = server ? server.groups : [];
    sel.innerHTML = '<option value="">전체 그룹</option>' +
      groups.map((g) => `<option value="${g.id}">${UI.esc(g.name)}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
  }

  function renderServerGroupFilterOptions() {
    const sel = document.getElementById('monitor-server-group-filter');
    const prev = sel.value;
    sel.innerHTML = '<option value="">서버그룹 선택...</option>' +
      Instances.getServerGroups().map((sg) => `<option value="${sg.id}">${UI.esc(sg.name)}</option>`).join('');
    if (Array.from(sel.options).some((o) => o.value === prev)) sel.value = prev;
    renderServerFilterOptions();
  }

  /* 좌측 트리 클릭 → 그 위치까지 필터를 맞춘다(서버까지만 클릭하면 그룹은 "전체"로
   * 남겨서 그 서버의 모든 인스턴스가 체크리스트에 보이게 함). 인스턴스를 클릭하면 그
   * 인스턴스가 속한 데까지 필터를 맞추고 체크리스트에서 카드 하나만 테두리로
   * 강조하되(체크 여부와는 무관), 같은 그룹의 나머지 인스턴스도 함께 보여준다.
   * deploy/restart와 동일하게, 필터가 바뀌면 체크 상태는 비운다 — 화면에 보이는
   * 범위와 실제 모니터링 대상이 어긋나지 않도록 하기 위함이다. */
  function applyTreeChain(chain) {
    highlightedId = chain.instanceId ?? null;
    checkedIds.clear();
    const sgSel = document.getElementById('monitor-server-group-filter');
    const typeSel = document.getElementById('monitor-server-type-filter');
    const serverSel = document.getElementById('monitor-server-filter');
    const groupSel = document.getElementById('monitor-group-filter');
    sgSel.value = chain.serverGroupId != null ? String(chain.serverGroupId) : '';
    typeSel.value = ''; // 트리 클릭은 서버를 직접 지정하므로 서버타입 필터는 초기화
    renderServerFilterOptions();
    serverSel.value = chain.serverId != null ? String(chain.serverId) : '';
    renderGroupFilterOptions();
    groupSel.value = chain.groupId != null ? String(chain.groupId) : '';
    renderList();
    if (monitoringActive) refreshAll();
  }

  // ── 카드 렌더링 — 인스턴스 개수에 따라 밀도(full/compact/mini)를 정하고,
  // 밀도별로 카드에 담는 정보량을 조절한다 (많을수록 압축). compact 구간(3~8개)은
  // 항상 2행으로 배치되도록 열 개수를 ceil(count/2)로 계산해서 화면 폭을 꽉 채운다
  // (4개 → 2x2, 8개 → 4x2). 그 이상은 지금까지처럼 mini(가장 작은 단위)로 표현한다. ──
  function densityFor(count) {
    if (count <= 2) return 'full';
    if (count <= 8) return 'compact';
    return 'mini';
  }

  function gridColumnsFor(density, count) {
    if (density === 'compact') return Math.ceil(count / 2);
    return null; // full/mini는 auto-fit(minmax)로 CSS가 알아서 채움
  }

  function cardHeaderHtml(inst, health) {
    const dotCls = statusDotClass(health ? health.status : null);
    return `
      <div class="monitor-card-head">
        <span><span class="monitor-status-dot ${dotCls}"></span><span class="monitor-card-name">${UI.esc(inst.name)}</span></span>
        <span class="${instanceTypeColorClass(inst.type)}" style="font-size:10.5px">${UI.esc(instanceTypeShortLabel(inst.type))}</span>
      </div>
      <div class="hint">${UI.esc(inst.server_name)} / ${UI.esc(inst.group_name)} · ${UI.esc(inst.host)}:${inst.port}</div>`;
  }

  function renderLoadingCard(inst) {
    return `<div class="monitor-card" data-instance-id="${inst.id}">${cardHeaderHtml(inst, null)}<div class="hint">조회 중...</div></div>`;
  }

  function renderUnsupportedCard(inst) {
    return `<div class="monitor-card unsupported" data-instance-id="${inst.id}">${cardHeaderHtml(inst, null)}<div class="hint">MI 인스턴스가 아니라 지원되지 않습니다.</div></div>`;
  }

  function renderErrorCard(inst, error) {
    return `<div class="monitor-card" data-instance-id="${inst.id}">${cardHeaderHtml(inst, null)}<div class="result-failed" style="font-size:12.5px">조회 실패: ${UI.esc(error || '')}</div></div>`;
  }

  // 메모리풀/JDBC 풀 테이블 행 — full·compact 카드가 공유. 개수를 임의로 자르지
  // 않고 전부 넣는다 — 테이블 자체가 스크롤(.table-wrap{overflow:auto})되므로
  // JDBC 데이터소스가 수십~100개가 넘어가도 스크롤만 하면 전부 확인할 수 있다.
  function buildPoolRows(pools) {
    return pools.map((p) => {
      const pct = usageRatio(p.usage);
      return `<tr><td>${UI.esc(p.name)}</td><td>${formatBytes((p.usage || {}).used)}</td><td>${pct != null ? pct + '%' : '-'}</td></tr>`;
    }).join('') || '<tr><td colspan="3" class="hint">정보 없음</td></tr>';
  }

  // Max Active가 큰(=풀이 넉넉히 설정된, 대체로 더 중요하거나 부하가 큰) 데이터소스가
  // 먼저 보이도록 내림차순 정렬 — 상세보기·단일 인스턴스 뷰 모두 이 순서를 공유한다.
  function sortDsPoolsByMaxActiveDesc(dsPools) {
    return [...dsPools].sort((a, b) => (b.max_active ?? -1) - (a.max_active ?? -1));
  }

  function buildDsRows(dsPools) {
    return sortDsPoolsByMaxActiveDesc(dsPools).map((p) =>
      `<tr><td>${UI.esc(p.jndi_name)}</td><td>${p.active ?? '-'}/${p.max_active ?? '-'}</td><td>${p.wait_count ?? '-'}</td></tr>`
    ).join('') || '<tr><td colspan="3" class="hint">캐시된 데이터소스 없음</td></tr>';
  }

  function dsTableHtml(dsPools) {
    return `
      <div class="hint">JDBC 커넥션 풀 (${dsPools.length}개, Max Active 내림차순)</div>
      <div class="table-wrap"><table><thead><tr><th>JNDI</th><th>Active/Max</th><th>Wait</th></tr></thead><tbody>${buildDsRows(dsPools)}</tbody></table></div>`;
  }

  // 장시간 실행 스레드 — 백엔드(JvmInfoExecutor)가 "이 수집기가 처음 관측한 시점 이후
  // 경과 시간" 기준 내림차순으로 이미 정렬해서 주지만, 방어적으로 한 번 더 정렬한다.
  function buildThreadRows(threads) {
    const sorted = [...threads].sort((a, b) => (b.observed_age_ms ?? -1) - (a.observed_age_ms ?? -1));
    return sorted.map((t) =>
      `<tr><td>${UI.esc(t.name)}</td><td>${UI.esc(t.state)}</td><td>${formatDuration(t.observed_age_ms || 0)}</td>` +
      `<td>${t.cpu_time_ms != null ? formatDuration(t.cpu_time_ms) : '-'}</td></tr>`
    ).join('') || '<tr><td colspan="4" class="hint">정보 없음</td></tr>';
  }

  function threadTableHtml(threads, instanceId) {
    return `
      <div class="row" style="justify-content:space-between;align-items:center">
        <div class="hint">장시간 실행 스레드 상위 ${threads.length}개 (모니터링 시작 이후 관측 경과 시간 기준)</div>
        <button class="btn btn-sm monitor-thread-export-btn" data-instance-id="${instanceId}">엑셀 다운로드</button>
      </div>
      <div class="table-wrap"><table><thead><tr><th>스레드명</th><th>상태</th><th>경과시간</th><th>CPU시간</th></tr></thead><tbody>${buildThreadRows(threads)}</tbody></table></div>`;
  }

  /* 스레드 표는 그리드 카드(단일 인스턴스일 때 인라인) / 상세보기 모달 두 군데에서
   * 똑같이 렌더링되는데, 둘 다 lastResults에 저장된 최신 조회 결과를 그대로 쓴다
   * (카드가 다시 그려져도 클릭 시점 데이터가 바뀌지 않도록). */
  function exportThreads(instanceId) {
    const entry = lastResults.get(Number(instanceId));
    if (!entry) { UI.toast('내보낼 스레드 정보가 없습니다.', 'error'); return; }
    const threads = entry.data.long_running_threads || [];
    if (!threads.length) { UI.toast('장시간 실행 스레드가 없습니다.', 'error'); return; }
    const headers = ['스레드명', '상태', '경과시간(ms)', 'CPU시간(ms)'];
    const rows = threads.map((t) => [
      t.name, t.state, String(t.observed_age_ms ?? ''), t.cpu_time_ms != null ? String(t.cpu_time_ms) : '',
    ]);
    UI.exportCsv(`threads_${entry.inst.name}_${Date.now()}.csv`, headers, rows);
  }

  function detailButtonHtml(instanceId) {
    return `<div class="row" style="justify-content:flex-end"><button class="btn btn-sm monitor-detail-btn" data-instance-id="${instanceId}">상세보기 (JNDI/스레드)</button></div>`;
  }

  // 상세보기 모달 렌더링에 쓸 최신 조회 결과 — 카드는 새로고침마다 다시 그려지므로
  // 클릭 시점에 필요한 데이터를 여기서 조회한다.
  const lastResults = new Map(); // instanceId -> {inst, data}

  // 상세보기는 별도로 요약해서 보여주지 않고, 인스턴스 1개만 볼 때와 완전히 같은
  // full 카드를 그대로 모달에 띄운다 — 그리드에 이미 같은 인스턴스의 카드가 떠 있을
  // 수 있으므로 차트 DOM id만 '-modal' 접미사로 분리해 서로 겹치지 않게 한다.
  function openDetailModal(inst, data) {
    const backdrop = UI.openModal(`
      <div class="modal-header">${UI.esc(inst.name)} — 상세 정보</div>
      <div class="modal-body">${renderFullCard(inst, data, 1, '-modal')}</div>
      <div class="modal-footer">
        <button class="btn" data-act="close">닫기</button>
      </div>`, 980, { height: 720, resizable: true });
    backdrop.querySelector('[data-act=close]').addEventListener('click', () => UI.closeModal(backdrop));
    backdrop.addEventListener('click', (e) => {
      const exportBtn = e.target.closest('.monitor-thread-export-btn');
      if (exportBtn) exportThreads(exportBtn.dataset.instanceId);
    });
    const samples = history.get(inst.id) || [];
    for (const m of CHART_METRICS) renderChartPanel(chartElId(inst.id, m.slug, '-modal'), m, samples);
  }

  function renderFullCard(inst, data, count, idSuffix = '') {
    const health = data.health || { status: 'OK', reasons: [] };
    const th = data.threads || {};
    const gc = data.gc || [];
    const fd = data.file_descriptors || {};
    const pools = data.memory_pools || [];
    const dsPools = data.datasource_pools || [];
    const showInlineDetail = count <= 1;

    const reasonsHtml = health.reasons && health.reasons.length
      ? `<ul class="monitor-health-reasons">${health.reasons.map((r) => `<li>${UI.esc(r)}</li>`).join('')}</ul>`
      : '';
    const gcHtml = gc.length
      ? gc.slice(0, 3).map((g) => `${UI.esc(g.name)}: ${g.collection_count}회/${g.collection_time_ms}ms`).join(' · ')
      : '없음';

    const chartsHtml = CHART_METRICS.map((m) => `<div class="chart-box"><div id="${chartElId(inst.id, m.slug, idSuffix)}"></div></div>`).join('');

    // JNDI/장시간 스레드 정보는 인스턴스를 1개만 보고 있을 때만(화면에 여유가 있을 때만)
    // 카드 하단에 바로 펼쳐 보여준다 — 2개 이상을 같이 볼 때는 각 카드가 좁아지므로
    // "상세보기" 버튼으로 옮겨 필요할 때만 모달로 확인한다.
    return `
      <div class="monitor-card full" data-instance-id="${inst.id}">
        ${cardHeaderHtml(inst, health)}
        <div><span class="${healthClass(health.status)}" style="font-size:13px">${UI.esc(health.status)}</span>${reasonsHtml}</div>
        <div class="row"><label style="width:64px">Heap</label><span>${usageText(data.heap)}</span></div>
        <div class="row"><label style="width:64px">Non-Heap</label><span>${usageText(data.non_heap)}</span></div>
        <div class="row"><label style="width:64px">업타임</label><span>${formatDuration(data.uptime_ms || 0)}</span></div>
        <div class="row"><label style="width:64px">스레드</label><span>현재 ${th.current ?? '-'} · 최대 ${th.peak ?? '-'} · 데몬 ${th.daemon ?? '-'}</span></div>
        <div class="row"><label style="width:64px">GC</label><span class="hint">${gcHtml}</span></div>
        <div class="row"><label style="width:64px">FD</label><span>${fd.max != null ? `${fd.open}/${fd.max} (${fd.usage_pct}%)` : '지원 안 함'}</span></div>
        <div class="hint">메모리풀 (${pools.length}개)</div>
        <div class="table-wrap"><table><thead><tr><th>메모리풀</th><th>사용</th><th>사용률</th></tr></thead><tbody>${buildPoolRows(pools)}</tbody></table></div>
        <div class="chart-grid">${chartsHtml}</div>
        ${showInlineDetail ? dsTableHtml(dsPools) + threadTableHtml(data.long_running_threads || [], inst.id) : detailButtonHtml(inst.id)}
      </div>`;
  }

  function renderCompactCard(inst, data) {
    const health = data.health || { status: 'OK', reasons: [] };
    const heapPct = usageRatio(data.heap);
    const th = data.threads || {};
    const fd = data.file_descriptors || {};
    const pools = data.memory_pools || [];

    const reasonsHtml = health.reasons && health.reasons.length
      ? `<ul class="monitor-health-reasons">${health.reasons.map((r) => `<li>${UI.esc(r)}</li>`).join('')}</ul>`
      : '';
    let worst = null;
    for (const p of pools) {
      const pct = usageRatio(p.usage);
      if (pct != null && (!worst || pct > worst.pct)) worst = { name: p.name, pct };
    }

    // full 카드보다 한 단계 압축했지만(GC 상세·메모리풀 표 생략), 힙/논힙/가동시간/
    // 스레드/FD 등 핵심 지표는 full과 동일하게 다 보여준다. JNDI/장시간 스레드 목록은
    // 2개 이상을 같이 보는 compact 카드에서는 항상 "상세보기" 버튼으로 분리한다.
    return `
      <div class="monitor-card compact" data-instance-id="${inst.id}">
        ${cardHeaderHtml(inst, health)}
        <div><span class="${healthClass(health.status)}" style="font-size:12.5px">${UI.esc(health.status)}</span>${reasonsHtml}</div>
        <div class="row"><label style="width:68px">Heap</label><span>${usageText(data.heap)}</span></div>
        ${usageBarHtml(heapPct)}
        <div class="row"><label style="width:68px">Non-Heap</label><span>${usageText(data.non_heap)}</span></div>
        <div class="row"><label style="width:68px">업타임</label><span>${formatDuration(data.uptime_ms || 0)} · 스레드 ${th.current ?? '-'}/${th.peak ?? '-'}</span></div>
        <div class="row"><label style="width:68px">FD</label><span>${fd.max != null ? `${fd.open}/${fd.max} (${fd.usage_pct}%)` : '지원 안 함'} · 최대풀 ${worst ? `${UI.esc(worst.name)} ${worst.pct}%` : '-'}</span></div>
        <div class="chart-box"><div id="${chartElId(inst.id, 'heap')}"></div></div>
        ${detailButtonHtml(inst.id)}
      </div>`;
  }

  function renderMiniCard(inst, data) {
    const health = data.health || { status: 'OK', reasons: [] };
    const heapPct = usageRatio(data.heap);
    const th = data.threads || {};
    const fd = data.file_descriptors || {};
    return `
      <div class="monitor-card mini" data-instance-id="${inst.id}">
        ${cardHeaderHtml(inst, health)}
        ${usageBarHtml(heapPct)}
        <div class="hint">Heap ${heapPct != null ? heapPct + '%' : '-'} · 스레드 ${th.current ?? '-'} · FD ${fd.usage_pct != null ? fd.usage_pct + '%' : '-'}
          · <span class="monitor-detail-btn monitor-detail-link" data-instance-id="${inst.id}">상세보기</span></div>
      </div>`;
  }

  /* 필터가 바뀌면 체크 상태를 비우므로(onFilterChanged, applyTreeChain 참고 — deploy/
   * restart와 동일하게 "화면에 보이는 범위 = 실제 대상"을 지키기 위함) 이 요약줄은
   * 평상시엔 항상 현재 체크리스트와 일치해야 정상이다. 그래도 방어적으로, 삭제된
   * id처럼 더 이상 존재하지 않는 항목은 여기서 걸러내면서 checkedIds 자체에서도
   * 정리하고, 지금 실제로 체크된 전체 목록을 눈에 보이게 표시한다. */
  function renderCheckedSummary() {
    const el = document.getElementById('monitor-checked-summary');
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
    el.textContent = `현재 선택된 모니터링 대상 (전체 ${targets.length}개): ${names.join(', ')}`;
  }

  /* 필터로 좁힌 후보 인스턴스를 체크박스 목록으로 보여준다 — 배포/재시작 탭과 동일한
   * 패턴으로, 여기서 체크한 것만 실제 모니터링(새로고침/시작/반복 폴링) 대상이 된다.
   * 체크 자체는 API 호출을 일으키지 않으므로(체크만으로는 조회하지 않음) 범위를
   * 마음대로 넓혔다 좁혔다 해도 안전하다 — 이미 모니터링이 실행 중일 때만 체크
   * 변경이 곧바로 새 조회로 이어진다. */
  function renderList() {
    const container = document.getElementById('monitor-instance-list');
    container.innerHTML = '';
    if (!hasScope()) {
      container.innerHTML = '<div class="hint" style="padding:8px">서버그룹을 선택하면 인스턴스 목록이 표시됩니다.</div>';
      renderCheckedSummary();
      return;
    }
    const instances = visibleInstances();
    if (!instances.length) {
      container.innerHTML = '<div class="hint" style="padding:8px">선택 범위에 인스턴스가 없습니다.</div>';
      renderCheckedSummary();
      return;
    }
    for (const inst of instances) {
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
        if (monitoringActive) refreshAll();
      });
      container.appendChild(row);
    }
    renderCheckedSummary();
    const hlRow = highlightedId != null && container.querySelector(`[data-instance-id="${highlightedId}"]`);
    if (hlRow) hlRow.scrollIntoView({ block: 'nearest' });
  }

  function selectAllInstances() {
    checkedIds = new Set(visibleInstances().map((i) => i.id));
    renderList();
    if (monitoringActive) refreshAll();
  }

  function selectNoneInstances() {
    checkedIds.clear();
    renderList();
    if (monitoringActive) refreshAll();
  }

  /* 필터(서버그룹/서버타입/서버/그룹)가 바뀌면 체크 상태를 비운다 — deploy/restart와
   * 동일하게, 화면에서 안 보이게 된 이전 선택이 조용히 모니터링 대상에 남아있는 것을
   * 막기 위함이다. */
  function onFilterChanged() {
    checkedIds.clear();
    renderList();
    if (monitoringActive) refreshAll();
  }

  async function refreshAll(opts = {}) {
    const silent = opts.silent === true;
    const seq = ++requestSeq;
    const grid = document.getElementById('monitor-grid');
    const summaryEl = document.getElementById('monitor-summary');
    const instances = targetInstances();
    if (!instances.length) {
      grid.className = 'monitor-grid';
      grid.style.gridTemplateColumns = '';
      grid.innerHTML = '<div class="hint">모니터링할 인스턴스를 선택하세요.</div>';
      summaryEl.innerHTML = '';
      return;
    }

    const density = densityFor(instances.length);
    grid.className = `monitor-grid density-${density}`;
    const cols = gridColumnsFor(density, instances.length);
    grid.style.gridTemplateColumns = cols ? `repeat(${cols}, 1fr)` : '';

    const miInstances = instances.filter((i) => i.type.startsWith('MI_'));

    if (!silent) {
      grid.innerHTML = instances.map((i) => (i.type.startsWith('MI_') ? renderLoadingCard(i) : renderUnsupportedCard(i))).join('');
    }

    const resultsById = new Map();
    if (miInstances.length) {
      const res = await Api.call('get_jvm_info_multi', miInstances.map((i) => i.id));
      if (seq !== requestSeq) return; // 그 사이 범위가 바뀌어 더 최신 요청이 시작됨
      for (const r of (res.results || [])) resultsById.set(r.instance_id, r);
    } else if (seq !== requestSeq) {
      return;
    }

    let ok = 0, warn = 0, crit = 0, fail = 0;
    const htmlParts = [];
    for (const inst of instances) {
      if (!inst.type.startsWith('MI_')) { htmlParts.push(renderUnsupportedCard(inst)); continue; }
      const r = resultsById.get(inst.id);
      if (!r || !r.success) {
        fail++;
        htmlParts.push(renderErrorCard(inst, r ? r.error : '응답 없음'));
        continue;
      }
      recordSample(inst.id, r);
      lastResults.set(inst.id, { inst, data: r });
      const health = r.health || { status: 'OK', reasons: [] };
      if (health.status === 'CRITICAL') crit++; else if (health.status === 'WARNING') warn++; else ok++;
      if (density === 'full') htmlParts.push(renderFullCard(inst, r, instances.length));
      else if (density === 'compact') htmlParts.push(renderCompactCard(inst, r));
      else htmlParts.push(renderMiniCard(inst, r));
    }

    grid.innerHTML = htmlParts.join('');

    // 차트는 SVG라 innerHTML 문자열에 직접 담기 어려워, DOM에 자리(placeholder div)만
    // 만들어둔 뒤 여기서 별도로 채운다.
    if (density === 'full' || density === 'compact') {
      for (const inst of instances) {
        if (!inst.type.startsWith('MI_')) continue;
        const r = resultsById.get(inst.id);
        if (!r || !r.success) continue;
        const samples = history.get(inst.id) || [];
        if (density === 'full') {
          for (const m of CHART_METRICS) renderChartPanel(chartElId(inst.id, m.slug), m, samples);
        } else {
          renderChartPanel(chartElId(inst.id, 'heap'), CHART_METRICS[0], samples);
        }
      }
    }

    if (highlightedId != null) {
      const hlEl = grid.querySelector(`[data-instance-id="${highlightedId}"]`);
      if (hlEl) {
        hlEl.classList.add('highlighted');
        if (!silent) hlEl.scrollIntoView({ block: 'nearest' });
      }
    }

    summaryEl.innerHTML = `<span class="monitor-count-pill">전체 ${instances.length}</span> ` +
      `<span class="monitor-count-pill ok">정상 ${ok}</span>` +
      (warn ? ` <span class="monitor-count-pill warn">경고 ${warn}</span>` : '') +
      (crit ? ` <span class="monitor-count-pill critical">위험 ${crit}</span>` : '') +
      (fail ? ` <span class="monitor-count-pill critical">실패 ${fail}</span>` : '');
  }

  /* 다른 탭에서 트리 클릭 후 이 탭으로 돌아왔을 수도 있으니, 탭이 활성화될 때마다
   * 체크리스트를 최신 데이터로 다시 그린다 — 체크 상태 자체는 그대로 유지한다(단순
   * 탭 재진입/데이터 갱신으로 사용자의 선택이 지워지면 안 되므로). 모니터링이 실행
   * 중이면 그 체크 대상으로 실제 조회도 다시 한다. */
  function reload() {
    renderServerGroupFilterOptions();
    renderList();
    if (monitoringActive) refreshAll();
  }

  function init() {
    document.getElementById('monitor-server-group-filter').addEventListener('change', () => { renderServerFilterOptions(); onFilterChanged(); });
    document.getElementById('monitor-server-type-filter').addEventListener('change', () => { renderServerFilterOptions(); onFilterChanged(); });
    document.getElementById('monitor-server-filter').addEventListener('change', () => { renderGroupFilterOptions(); onFilterChanged(); });
    document.getElementById('monitor-group-filter').addEventListener('change', () => onFilterChanged());
    document.getElementById('monitor-select-all').addEventListener('click', selectAllInstances);
    document.getElementById('monitor-select-none').addEventListener('click', selectNoneInstances);
    document.getElementById('btn-monitor-refresh').addEventListener('click', () => {
      if (!checkedIds.size) { UI.toast('모니터링할 인스턴스를 선택하세요.', 'error'); return; }
      refreshAll();
    });
    document.getElementById('btn-monitor-toggle').addEventListener('click', toggleMonitoring);
    updateMonitorStatusUi();

    const pollInput = document.getElementById('monitor-poll-sec');
    pollInput.value = pollSec;
    pollInput.addEventListener('change', () => {
      const val = Number(pollInput.value);
      if (!Number.isFinite(val) || val < POLL_SEC_MIN) {
        UI.toast(`${POLL_SEC_MIN}초 이상의 숫자를 입력하세요.`, 'error');
        pollInput.value = pollSec;
        return;
      }
      pollSec = Math.floor(val);
      try { localStorage.setItem(POLL_SEC_STORAGE_KEY, String(pollSec)); } catch (e) { /* 저장 불가해도 이번 세션 동안은 적용됨 */ }
      restartPollTimer();
      updateMonitorStatusUi();
      UI.toast(`추이 수집 주기를 ${pollSec}초로 설정했습니다.`);
    });

    document.getElementById('btn-monitor-history-reset').addEventListener('click', () => {
      const ids = targetInstances().map((i) => i.id);
      if (!ids.length) return;
      for (const id of ids) history.delete(id);
      UI.toast('추이 기록을 초기화했습니다.');
      if (monitoringActive) refreshAll();
    });

    // 카드는 새로고침마다 통째로 다시 그려지므로, 버튼에 직접 리스너를 다는 대신
    // 그리드 컨테이너에서 위임해 처리한다.
    document.getElementById('monitor-grid').addEventListener('click', (e) => {
      const detailBtn = e.target.closest('.monitor-detail-btn');
      if (detailBtn) {
        const entry = lastResults.get(Number(detailBtn.dataset.instanceId));
        if (entry) openDetailModal(entry.inst, entry.data);
        return;
      }
      const exportBtn = e.target.closest('.monitor-thread-export-btn');
      if (exportBtn) exportThreads(exportBtn.dataset.instanceId);
    });

    window.addEventListener('instances-changed', reload);
    window.addEventListener('tree-node-selected', (e) => applyTreeChain(UI.treeNodeToChain(e.detail.kind, e.detail.data)));
    restartPollTimer();
  }

  return { init, reload };
})();
