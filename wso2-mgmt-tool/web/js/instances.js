/* 인스턴스 / 서버그룹 / 서버 / 인스턴스그룹 트리 관리 */
const Instances = (() => {
  let serverGroups = [];
  let flatInstances = [];
  let selection = null; // {kind:'serverGroup'|'server'|'group'|'instance', data}
  let treeFilter = ''; // 이름으로 트리를 좁혀 보여주는 검색어 (비어있으면 전체 표시)
  const instanceStatus = new Map(); // instanceId -> true(UP)|false(DOWN), 테스트해본 적 있는 것만 들어있음

  // 연결 테스트는 사용자가 "연결 테스트" 버튼을 눌렀을 때만 실행한다 — 예전엔
  // 백그라운드에서 주기적으로 자동 재확인했는데, 인스턴스를 재시작한 직후 이 자동
  // 폴링이 하필 그 짧은 초기화 구간에 로그인 요청을 보내 MI 쪽에 불필요한 에러 로그를
  // 남기는 문제가 있어 자동 폴링 자체를 없앴다.

  // 트리에서 펼쳐둔 서버그룹/서버/그룹의 id를 기억해둔다("sg:1", "srv:2", "grp:3" 형태의
  // 키) — 집합에 없으면 기본값은 "접힘"이다(처음 실행했을 때 트리 전체가 펼쳐진 채로
  // 나오면 부담스럽다는 피드백에 따라, 명시적으로 펼친 적 있는 항목만 펼침 상태로
  // 남긴다). 계층이 깊어(서버그룹>서버>그룹>인스턴스) 한눈에 보기 불편하다는 이전
  // 피드백에 따라 각 단계를 접을 수 있게 했고, 그 펼침 상태는 앱을 다시 켜도 유지된다.
  const TREE_EXPANDED_STORAGE_KEY = 'wso2mgmt.treeExpanded';

  function loadExpandedIds() {
    try {
      const arr = JSON.parse(localStorage.getItem(TREE_EXPANDED_STORAGE_KEY) || '[]');
      if (Array.isArray(arr)) return new Set(arr);
    } catch (e) { /* localStorage 접근 불가 또는 손상된 값 — 빈 상태(모두 접힘)로 시작 */ }
    return new Set();
  }

  function saveExpandedIds() {
    try { localStorage.setItem(TREE_EXPANDED_STORAGE_KEY, JSON.stringify(Array.from(expandedIds))); } catch (e) { /* 저장 안 돼도 이번 세션 동안은 적용됨 */ }
  }

  let expandedIds = loadExpandedIds();

  function toggleCollapsed(key) {
    if (expandedIds.has(key)) expandedIds.delete(key); else expandedIds.add(key);
    saveExpandedIds();
    render();
  }

  /* 서버그룹 헤더의 "펼치기/접기" 버튼 — 화살표는 한 단계씩만 펼치는데, 서버그룹
   * 하나 밑에 서버>그룹이 여러 개면 인스턴스까지 보려고 계속 눌러야 해서 번거롭다는
   * 요청에 따라, 그 서버그룹 아래 서버/그룹을 한 번에 전부 펼치거나 접는다. */
  function isServerGroupFullyExpanded(sg) {
    if (!expandedIds.has(`sg:${sg.id}`)) return false;
    for (const s of sg.servers) {
      if (!expandedIds.has(`srv:${s.id}`)) return false;
      for (const g of s.groups) {
        if (!expandedIds.has(`grp:${g.id}`)) return false;
      }
    }
    return true;
  }

  function setServerGroupExpansion(sg, expand) {
    const keys = [`sg:${sg.id}`];
    for (const s of sg.servers) {
      keys.push(`srv:${s.id}`);
      for (const g of s.groups) keys.push(`grp:${g.id}`);
    }
    for (const k of keys) {
      if (expand) expandedIds.add(k); else expandedIds.delete(k);
    }
    saveExpandedIds();
    render();
  }

  /* 서버 정렬 기준: 환경(개발 → 운영 → 운영개발) 우선, 같은 환경이면 이름순.
   * SERVER_ENVIRONMENTS(constants.js)에 이미 개발/운영/운영개발 순으로 정의돼 있어
   * 그 배열상의 위치를 그대로 정렬 우선순위로 사용한다. */
  function serverEnvironmentSortOrder(env) {
    const idx = SERVER_ENVIRONMENTS.findIndex((e) => e.value === env);
    return idx === -1 ? SERVER_ENVIRONMENTS.length : idx;
  }

  function sortServers(servers) {
    servers.sort((a, b) =>
      serverEnvironmentSortOrder(a.environment) - serverEnvironmentSortOrder(b.environment) ||
      a.name.localeCompare(b.name));
  }

  async function load() {
    serverGroups = await Api.call('get_server_groups');
    flatInstances = [];
    for (const sg of serverGroups) {
      sg.servers = await Api.call('get_servers', sg.id);
      sortServers(sg.servers);
      for (const s of sg.servers) {
        s.server_group_id = sg.id;
        s.server_group_name = sg.name;
        s.groups = await Api.call('get_groups', s.id);
        s.instances = [];
        for (const g of s.groups) {
          g.server_id = s.id;
          g.server_name = s.name;
          g.server_group_id = sg.id;
          g.server_group_name = sg.name;
          g.instances = await Api.call('get_instances', g.id);
          for (const inst of g.instances) {
            inst.group_name = g.name;
            inst.server_id = s.id;
            inst.server_name = s.name;
            inst.server_group_id = sg.id;
            inst.server_group_name = sg.name;
            inst.server_environment = s.environment;
          }
          s.instances.push(...g.instances);
        }
        flatInstances.push(...s.instances);
      }
    }
    render();
    window.dispatchEvent(new CustomEvent('instances-changed', { detail: { serverGroups, flatInstances } }));
  }

  function applyStatusBadge(instanceId, res) {
    instanceStatus.set(instanceId, !!res.success);
    const el = document.querySelector(`.tree-instance[data-instance-id="${instanceId}"] [data-status]`);
    if (el) {
      el.textContent = res.success ? 'UP' : 'DOWN';
      // className을 통째로 바꾸면 폭 고정용 col-status 클래스가 날아가서 컬럼이 밀린다 —
      // 상태 색상 클래스만 별도로 추가/교체한다.
      el.classList.remove('status-up', 'status-down');
      el.classList.add(res.success ? 'status-up' : 'status-down');
    }
    const inst = flatInstances.find((i) => i.id === instanceId);
    if (inst) updateServerGroupStatusBadge(inst.server_group_id);
  }

  /* 서버그룹 헤더의 "N/M UP" 요약 — 그 서버그룹 안의 인스턴스 중 접속 테스트를 한 번이라도
   * 해본 것만 대상으로 한다(한 번도 안 해본 인스턴스를 DOWN 취급해 잘못된 인상을 주지
   * 않기 위함). 하나도 테스트 안 해봤으면 null(배지를 아예 표시 안 함). */
  function serverGroupStatusRollup(sg) {
    const ids = sg.servers.flatMap((s) => s.instances.map((i) => i.id));
    const known = ids.filter((id) => instanceStatus.has(id));
    if (!known.length) return null;
    const up = known.filter((id) => instanceStatus.get(id)).length;
    return { up, total: known.length };
  }

  function updateServerGroupStatusBadge(sgId) {
    const sg = serverGroups.find((x) => x.id === sgId);
    const el = document.querySelector(`[data-sg-status="${sgId}"]`);
    if (!sg || !el) return;
    const rollup = serverGroupStatusRollup(sg);
    if (!rollup) { el.textContent = ''; el.className = 'sg-status-badge'; return; }
    el.textContent = `${rollup.up}/${rollup.total} UP`;
    el.className = `sg-status-badge ${rollup.up === rollup.total ? 'sg-status-ok' : 'sg-status-warn'}`;
  }

  function showEditDeleteMenu(e) {
    e.preventDefault();
    e.stopPropagation();
    const items = [{ label: '수정', onClick: editSelected }];
    if (selection?.kind === 'serverGroup') {
      items.push({ label: '서버 추가', onClick: addServer });
    }
    if (selection?.kind === 'server') {
      items.push({ label: '그룹 추가', onClick: addGroup });
    }
    if (selection?.kind === 'group') {
      items.push({ label: '인스턴스 추가', onClick: addInstance });
      items.push({ label: '복사해서 추가', onClick: copyToGroup });
    }
    if (selection?.kind === 'instance') {
      items.push({ label: '복사해서 추가', onClick: copySelected });
      items.push({ label: '다른 그룹으로 복사', onClick: copyToOtherGroup });
    }
    items.push({ label: '삭제', danger: true, onClick: deleteSelected });
    UI.contextMenu(e.clientX, e.clientY, items);
  }

  /* 검색어가 있으면 이름이 매칭되는 항목뿐 아니라, 그 항목이 보이도록 조상(부모)
   * 전체와 — 반대로 조상이 매칭되면 그 자손 전체도 — 함께 보여준다(표준적인
   * 트리 검색 UX). 서버그룹/서버/그룹 각각에 "이 계층 이하에 보여줄 게 있는지"를
   * 미리 계산해서 render()가 건너뛸 부분을 알 수 있게 한다. */
  function computeTreeVisibility(filter) {
    const matches = (name) => !filter || name.toLowerCase().includes(filter);
    return serverGroups.map((sg) => {
      const sgMatch = matches(sg.name);
      const servers = sg.servers.map((s) => {
        const sMatch = sgMatch || matches(s.name);
        const groups = s.groups.map((g) => {
          const gMatch = sMatch || matches(g.name);
          const instances = g.instances.filter((inst) => gMatch || matches(inst.name));
          return { g, visible: gMatch || instances.length > 0, instances };
        });
        return { s, visible: sMatch || groups.some((gv) => gv.visible), groups };
      });
      return { sg, visible: sgMatch || servers.some((sv) => sv.visible), servers };
    });
  }

  function render() {
    const tree = document.getElementById('instance-tree');
    tree.innerHTML = '';
    const filter = treeFilter.trim().toLowerCase();
    const visibility = computeTreeVisibility(filter);

    if (filter && !visibility.some((v) => v.visible)) {
      tree.innerHTML = `<div class="tree-empty hint">"${UI.esc(treeFilter.trim())}"과(와) 일치하는 항목이 없습니다.</div>`;
      return;
    }

    for (const { sg, visible: sgVisible, servers } of visibility) {
      if (!sgVisible) continue;
      const sgBox = document.createElement('div');
      sgBox.className = 'tree-server-group';

      const sgKey = `sg:${sg.id}`;
      const sgCollapsed = !expandedIds.has(sgKey);
      const sgHeader = document.createElement('div');
      sgHeader.className = 'tree-server-group-header';
      sgHeader.dataset.sgId = sg.id;
      const rollup = serverGroupStatusRollup(sg);
      const rollupClass = rollup ? (rollup.up === rollup.total ? 'sg-status-ok' : 'sg-status-warn') : '';
      const rollupText = rollup ? `${rollup.up}/${rollup.total} UP` : '';
      const fullyExpanded = isServerGroupFullyExpanded(sg);
      sgHeader.innerHTML = `<span class="tree-toggle">${sgCollapsed ? '▸' : '▾'}</span><span class="tree-icon">🏢</span><span title="${UI.esc(sg.name)}">${UI.esc(sg.name)}</span><span class="sg-status-badge ${rollupClass}" data-sg-status="${sg.id}">${rollupText}</span><button class="tree-expand-all-btn" data-sg-id="${sg.id}">${fullyExpanded ? '전체 접기' : '전체 펼치기'}</button>`;
      sgHeader.querySelector('.tree-toggle').addEventListener('click', (e) => { e.stopPropagation(); toggleCollapsed(sgKey); });
      sgHeader.querySelector('.tree-expand-all-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        setServerGroupExpansion(sg, !fullyExpanded);
      });
      sgHeader.addEventListener('click', () => selectServerGroup(sg, sgHeader));
      sgHeader.addEventListener('contextmenu', (e) => { selectServerGroup(sg, sgHeader); showEditDeleteMenu(e); });
      sgBox.appendChild(sgHeader);

      if (!sgCollapsed) for (const { s, visible: sVisible, groups } of servers) {
        if (!sVisible) continue;
        const srvKey = `srv:${s.id}`;
        const srvCollapsed = !expandedIds.has(srvKey);
        const sEl = document.createElement('div');
        sEl.className = 'tree-server';
        sEl.dataset.serverId = s.id;
        const envClass = serverEnvironmentClass(s.environment);
        sEl.innerHTML = `<span class="tree-toggle">${srvCollapsed ? '▸' : '▾'}</span><span class="tree-icon">🖥️</span><span class="env-badge ${envClass}">${UI.esc(serverEnvironmentLabel(s.environment))}</span>
          <span title="${UI.esc(s.name)}">${UI.esc(s.name)}</span>
          <span class="hint">${UI.esc(s.ip || '')}</span>`;
        sEl.querySelector('.tree-toggle').addEventListener('click', (e) => { e.stopPropagation(); toggleCollapsed(srvKey); });
        sEl.addEventListener('click', (e) => { e.stopPropagation(); selectServer(s, sEl); });
        sEl.addEventListener('contextmenu', (e) => { selectServer(s, sEl); showEditDeleteMenu(e); });
        sgBox.appendChild(sEl);

        if (!srvCollapsed) for (const { g, visible: gVisible, instances } of groups) {
          if (!gVisible) continue;
          const grpKey = `grp:${g.id}`;
          const grpCollapsed = !expandedIds.has(grpKey);
          const gEl = document.createElement('div');
          gEl.className = 'tree-group';
          gEl.dataset.groupId = g.id;
          gEl.innerHTML = `<span class="tree-toggle">${grpCollapsed ? '▸' : '▾'}</span><span class="tree-icon">🎛️</span><span title="${UI.esc(g.name)}">${UI.esc(g.name)}</span>`;
          gEl.querySelector('.tree-toggle').addEventListener('click', (e) => { e.stopPropagation(); toggleCollapsed(grpKey); });
          gEl.addEventListener('click', (e) => { e.stopPropagation(); selectGroup(g, gEl); });
          gEl.addEventListener('contextmenu', (e) => { selectGroup(g, gEl); showEditDeleteMenu(e); });
          // 인스턴스를 드래그해서 놓으면 그 인스턴스를 이 그룹으로 복사한다(다른
          // 서버그룹/서버 밑의 그룹이어도 가능) — 다이얼로그가 열려 이름 등을 확인/수정 후 저장.
          gEl.addEventListener('dragover', (e) => { e.preventDefault(); gEl.classList.add('drop-target'); });
          gEl.addEventListener('dragleave', () => gEl.classList.remove('drop-target'));
          gEl.addEventListener('drop', (e) => {
            e.preventDefault();
            gEl.classList.remove('drop-target');
            const srcId = e.dataTransfer.getData('text/plain');
            const src = flatInstances.find((i) => String(i.id) === String(srcId));
            if (src) openInstanceDialog(null, g.id, src);
          });
          sgBox.appendChild(gEl);

          if (!grpCollapsed) for (const inst of instances) {
            const iEl = document.createElement('div');
            iEl.className = 'tree-instance';
            iEl.dataset.instanceId = inst.id;
            iEl.draggable = true;
            const envClass = inst.environment === 'PROD' ? 'env-prod' : 'env-dev';
            const known = instanceStatus.get(inst.id);
            const statusClass = known === undefined ? '' : (known ? 'status-up' : 'status-down');
            const statusText = known === undefined ? '' : (known ? 'UP' : 'DOWN');
            const productVersionTitle = inst.product ? `${inst.product}${inst.version ? ' ' + inst.version : ''}` : '';
            iEl.innerHTML = `
              <span class="tree-icon">⚙️</span>
              <span class="col-name" title="${UI.esc(inst.name)}">${UI.esc(inst.name)}</span>
              <span class="col-env ${envClass}">${UI.esc(environmentLabel(inst.environment))}</span>
              <span class="col-type ${instanceTypeColorClass(inst.type)}" title="${UI.esc(productVersionTitle)}">${UI.esc(instanceTypeShortLabel(inst.type))}</span>
              <span class="col-status ${statusClass}" data-status>${statusText}</span>`;
            iEl.addEventListener('click', (e) => { e.stopPropagation(); selectInstance(inst, iEl); });
            iEl.addEventListener('contextmenu', (e) => { selectInstance(inst, iEl); showEditDeleteMenu(e); });
            iEl.addEventListener('dragstart', (e) => {
              e.dataTransfer.setData('text/plain', String(inst.id));
              e.dataTransfer.effectAllowed = 'copy';
            });
            sgBox.appendChild(iEl);
          }
        }
      }
      tree.appendChild(sgBox);
    }
    restoreSelectionHighlight();
  }

  /* render()가 검색어 입력마다 트리 DOM을 통째로 새로 그리므로, 그때마다 기존 선택
   * 항목의 시각적 강조(.selected)가 날아간다 — selection 상태 자체는 그대로 두고
   * 화면에만 다시 표시해준다 (검색으로 걸러져 안 보이는 항목이면 그냥 넘어감). */
  function restoreSelectionHighlight() {
    if (!selection) return;
    const selector = {
      serverGroup: () => `.tree-server-group-header[data-sg-id="${selection.data.id}"]`,
      server: () => `.tree-server[data-server-id="${selection.data.id}"]`,
      group: () => `.tree-group[data-group-id="${selection.data.id}"]`,
      instance: () => `.tree-instance[data-instance-id="${selection.data.id}"]`,
    }[selection.kind];
    const el = selector && document.querySelector(selector());
    if (el) el.classList.add('selected');
  }

  function clearTreeSelectionHighlight() {
    document.querySelectorAll('.tree-instance.selected, .tree-server.selected, .tree-group.selected, .tree-server-group-header.selected')
      .forEach((el) => el.classList.remove('selected'));
  }

  /* 트리에서 뭘 클릭하든(서버그룹/서버/그룹/인스턴스) 다른 탭들이 필터/선택을
   * 그 위치까지 맞출 수 있도록 공통 이벤트로 알린다 — 배포/재시작 탭은 서버그룹>서버>그룹
   * 필터를, 데이터소스/레지스트리/로그뷰어 탭은 서버그룹>서버>그룹>인스턴스 4단 셀렉트를
   * 각 kind에 맞는 깊이까지만(예: 서버 클릭이면 서버까지만) 동기화한다. */
  function notifyTreeSelection(kind, data) {
    window.dispatchEvent(new CustomEvent('tree-node-selected', { detail: { kind, data } }));
  }

  function selectServerGroup(sg, el) {
    selection = { kind: 'serverGroup', data: sg };
    clearTreeSelectionHighlight();
    if (el) el.classList.add('selected');
    const instCount = sg.servers.reduce((n, s) => n + s.instances.length, 0);
    setStatus(`선택: 서버그룹 [${sg.name}] (서버 ${sg.servers.length}개, 인스턴스 ${instCount}개)`);
    notifyTreeSelection('serverGroup', sg);
  }

  function selectServer(s, el) {
    selection = { kind: 'server', data: s };
    clearTreeSelectionHighlight();
    if (el) el.classList.add('selected');
    setStatus(`선택: 서버 [${s.server_group_name} / ${s.name}] (그룹 ${s.groups.length}개, 인스턴스 ${s.instances.length}개)`);
    notifyTreeSelection('server', s);
  }

  function selectGroup(g, el) {
    selection = { kind: 'group', data: g };
    clearTreeSelectionHighlight();
    if (el) el.classList.add('selected');
    setStatus(`선택: 그룹 [${g.server_group_name} / ${g.server_name} / ${g.name}] (${g.instances.length}개 인스턴스)`);
    notifyTreeSelection('group', g);
  }

  function selectInstance(inst, el) {
    selection = { kind: 'instance', data: inst };
    clearTreeSelectionHighlight();
    if (el) el.classList.add('selected');
    const productLabel = inst.product ? `${inst.product}${inst.version ? ' ' + inst.version : ''} / ` : '';
    setStatus(`선택: ${productLabel}[${inst.type}] ${inst.name} (${inst.host}:${inst.port})`);
    notifyTreeSelection('instance', inst);
  }

  function setStatus(msg) {
    document.getElementById('status-bar').textContent = msg;
  }

  function selectedInstances() {
    if (!selection) return [];
    if (selection.kind === 'instance') return [selection.data];
    if (selection.kind === 'group') return selection.data.instances;
    if (selection.kind === 'server') return selection.data.instances;
    if (selection.kind === 'serverGroup') return selection.data.servers.flatMap((s) => s.instances);
    return [];
  }

  // ── 서버그룹 컨텍스트 선택 도우미 ────────────────────────────────────────

  async function pickServerGroupId() {
    if (!serverGroups.length) { UI.toast('먼저 서버그룹을 추가하세요.', 'error'); return null; }
    const chosen = await UI.selectDialog('서버그룹:',
      serverGroups.map((sg) => ({ value: sg.id, label: sg.name })), '서버그룹 선택');
    return chosen ? Number(chosen) : null;
  }

  async function pickServerId(serverGroupId) {
    const sg = serverGroups.find((x) => x.id === serverGroupId);
    const servers = sg ? sg.servers : [];
    if (!servers.length) { UI.toast('이 서버그룹엔 서버가 없습니다. 먼저 서버를 추가하세요.', 'error'); return null; }
    const chosen = await UI.selectDialog('서버:', servers.map((s) => ({ value: s.id, label: s.name })), '서버 선택');
    return chosen ? Number(chosen) : null;
  }

  async function pickGroupId(serverId) {
    const ctx = findServerContext(serverId);
    const groups = ctx ? ctx.server.groups : [];
    if (!groups.length) { UI.toast('이 서버엔 그룹이 없습니다. 먼저 그룹을 추가하세요.', 'error'); return null; }
    const chosen = await UI.selectDialog('그룹:', groups.map((g) => ({ value: g.id, label: g.name })), '그룹 선택');
    return chosen ? Number(chosen) : null;
  }

  function findServerContext(serverId) {
    for (const sg of serverGroups) {
      const s = sg.servers.find((sv) => sv.id === serverId);
      if (s) return { serverGroup: sg, server: s };
    }
    return null;
  }

  function findGroupContext(groupId) {
    for (const sg of serverGroups) {
      for (const s of sg.servers) {
        const g = s.groups.find((gr) => gr.id === groupId);
        if (g) return { serverGroup: sg, server: s, group: g };
      }
    }
    return null;
  }

  /* 현재 트리 선택 상태로부터 서버그룹 id를 최대한 유추한다 (없으면 null). */
  function serverGroupIdFromSelection() {
    if (!selection) return null;
    if (selection.kind === 'serverGroup') return selection.data.id;
    if (selection.kind === 'server') return selection.data.server_group_id;
    if (selection.kind === 'group') return selection.data.server_group_id;
    if (selection.kind === 'instance') return selection.data.server_group_id;
    return null;
  }

  /* 현재 트리 선택 상태로부터 서버 id를 최대한 유추한다 (없으면 null). */
  function serverIdFromSelection() {
    if (!selection) return null;
    if (selection.kind === 'server') return selection.data.id;
    if (selection.kind === 'group') return selection.data.server_id;
    if (selection.kind === 'instance') return selection.data.server_id;
    return null;
  }

  // ── 서버그룹 추가 / 수정 / 삭제 ──────────────────────────────────────────

  function openServerGroupDialog(sg) {
    const d = sg || { id: null, name: '', description: '' };
    const backdrop = UI.openModal(`
      <div class="modal-header">${sg ? '서버그룹 수정' : '서버그룹 추가'}</div>
      <div class="modal-body">
        <div class="field-group">
          <div class="field-row"><label>이름 *</label><input type="text" id="f-name" value="${UI.esc(d.name)}"></div>
          <div class="field-row"><label>설명</label><input type="text" id="f-desc" value="${UI.esc(d.description)}"></div>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn" data-act="cancel">취소</button>
        <button class="btn btn-primary" data-act="ok">확인</button>
      </div>`, 380, { closeOnBackdrop: false });

    backdrop.querySelector('[data-act=cancel]').onclick = () => UI.closeModal(backdrop);
    backdrop.querySelector('[data-act=ok]').onclick = async () => {
      const name = backdrop.querySelector('#f-name').value.trim();
      if (!name) { UI.toast('이름을 입력하세요.', 'error'); return; }
      await Api.call('save_server_group', {
        id: d.id, name, description: backdrop.querySelector('#f-desc').value.trim(),
      });
      UI.closeModal(backdrop);
      await load();
    };
  }

  function addServerGroup() { openServerGroupDialog(null); }

  // ── 서버 추가 / 수정 / 삭제 ──────────────────────────────────────────────

  function openServerDialog(server, serverGroupId) {
    const d = server || {
      id: null, server_group_id: serverGroupId, name: '', environment: 'DEV', ip: '', description: '',
      ssh_enabled: false, ssh_host: '', ssh_port: 22, ssh_user: '', ssh_pass: '', ssh_key_path: '',
    };
    const backdrop = UI.openModal(`
      <div class="modal-header">${server ? '서버 수정' : '서버 추가'}</div>
      <div class="modal-body">
        <div class="field-group">
          <div class="field-row"><label>서버그룹 *</label>
            <select id="f-server-group">${serverGroups.map((sg) => `<option value="${sg.id}">${UI.esc(sg.name)}</option>`).join('')}</select>
          </div>
          <div class="field-row"><label>이름 *</label><input type="text" id="f-name" value="${UI.esc(d.name)}"></div>
          <div class="field-row"><label>환경 *</label>
            <select id="f-env">${SERVER_ENVIRONMENTS.map((e) => `<option value="${e.value}">${UI.esc(e.label)}</option>`).join('')}</select>
          </div>
          <div class="field-row"><label>서버 IP</label><input type="text" id="f-ip" value="${UI.esc(d.ip || '')}" placeholder="예: 10.0.0.1"></div>
          <div class="hint">여기 입력한 IP는 이 서버 아래 인스턴스를 새로 등록할 때 Host 값의 기본값으로 쓰입니다
            (인스턴스별로 나중에 개별 수정 가능).</div>
          <div class="field-row"><label>설명</label><input type="text" id="f-desc" value="${UI.esc(d.description)}"></div>
        </div>
        <div class="field-group">
          <div class="field-group-title">OS SSH 접속</div>
          <div class="field-row"><label><input type="checkbox" id="f-ssh-enabled" ${d.ssh_enabled ? 'checked' : ''}> SSH 접속 사용</label></div>
          <div class="hint">이 서버 아래 모든 인스턴스가 재시작/정지, 로그 tail -f 등에 이 SSH 계정을 공유해서 씁니다.</div>
          <div class="field-row" id="ssh-row-host"><label>SSH Host</label><input type="text" id="f-ssh-host" value="${UI.esc(d.ssh_host || '')}"></div>
          <div class="field-row" id="ssh-row-port"><label>SSH Port</label><input type="number" id="f-ssh-port" value="${d.ssh_port || 22}"></div>
          <div class="field-row" id="ssh-row-user"><label>SSH 계정</label><input type="text" id="f-ssh-user" value="${UI.esc(d.ssh_user || '')}"></div>
          <div class="field-row" id="ssh-row-pass"><label>SSH 패스워드</label><input type="password" id="f-ssh-pass" value="${UI.esc(d.ssh_pass || '')}"></div>
          <div class="field-row" id="ssh-row-key"><label>PEM 키 경로</label><input type="text" id="f-ssh-key" value="${UI.esc(d.ssh_key_path || '')}"></div>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn" data-act="cancel">취소</button>
        <button class="btn btn-primary" data-act="ok">확인</button>
      </div>`, 420, { closeOnBackdrop: false });

    backdrop.querySelector('#f-server-group').value = d.server_group_id;
    backdrop.querySelector('#f-env').value = d.environment;

    const sshRows = ['#ssh-row-host', '#ssh-row-port', '#ssh-row-user', '#ssh-row-pass', '#ssh-row-key']
      .map((s) => backdrop.querySelector(s));
    const sshChk = backdrop.querySelector('#f-ssh-enabled');
    const toggleSsh = () => sshRows.forEach((r) => r.classList.toggle('disabled', !sshChk.checked));
    sshChk.addEventListener('change', toggleSsh);
    toggleSsh();

    backdrop.querySelector('[data-act=cancel]').onclick = () => UI.closeModal(backdrop);
    backdrop.querySelector('[data-act=ok]').onclick = async () => {
      const name = backdrop.querySelector('#f-name').value.trim();
      if (!name) { UI.toast('이름을 입력하세요.', 'error'); return; }
      await Api.call('save_server', {
        id: d.id,
        server_group_id: Number(backdrop.querySelector('#f-server-group').value),
        name,
        environment: backdrop.querySelector('#f-env').value,
        ip: backdrop.querySelector('#f-ip').value.trim(),
        description: backdrop.querySelector('#f-desc').value.trim(),
        ssh_enabled: sshChk.checked,
        ssh_host: backdrop.querySelector('#f-ssh-host').value.trim(),
        ssh_port: Number(backdrop.querySelector('#f-ssh-port').value || 22),
        ssh_user: backdrop.querySelector('#f-ssh-user').value.trim(),
        ssh_pass: backdrop.querySelector('#f-ssh-pass').value,
        ssh_key_path: backdrop.querySelector('#f-ssh-key').value.trim(),
      });
      UI.closeModal(backdrop);
      await load();
    };
  }

  async function addServer() {
    let serverGroupId = serverGroupIdFromSelection();
    if (serverGroupId == null) serverGroupId = await pickServerGroupId();
    if (serverGroupId == null) return;
    openServerDialog(null, serverGroupId);
  }

  // ── 인스턴스그룹 추가 / 수정 / 삭제 ──────────────────────────────────────

  function openGroupDialog(group, serverId) {
    const d = group || { id: null, server_id: serverId, name: '', type: 'MI', description: '' };
    const backdrop = UI.openModal(`
      <div class="modal-header">${group ? '그룹 수정' : '그룹 추가'}</div>
      <div class="modal-body">
        <div class="field-group">
          <div class="field-row"><label>서버그룹</label>
            <select id="f-server-group">${serverGroups.map((sg) => `<option value="${sg.id}">${UI.esc(sg.name)}</option>`).join('')}</select>
          </div>
          <div class="field-row"><label>서버 *</label>
            <select id="f-server"></select>
          </div>
          <div class="field-row"><label>이름 *</label><input type="text" id="f-name" value="${UI.esc(d.name)}"></div>
          <div class="field-row"><label>설명</label><input type="text" id="f-desc" value="${UI.esc(d.description)}"></div>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn" data-act="cancel">취소</button>
        <button class="btn btn-primary" data-act="ok">확인</button>
      </div>`, 400, { closeOnBackdrop: false });

    const initialCtx = findServerContext(d.server_id);
    const sgSel = backdrop.querySelector('#f-server-group');
    const serverSel = backdrop.querySelector('#f-server');
    sgSel.value = initialCtx ? initialCtx.serverGroup.id : (serverGroups[0] ? serverGroups[0].id : '');

    function populateServerOptions(sgIdVal, preferServerId) {
      const sg = serverGroups.find((x) => String(x.id) === String(sgIdVal));
      const servers = sg ? sg.servers : [];
      if (!servers.length) {
        serverSel.innerHTML = '<option value="">(이 서버그룹엔 서버가 없습니다)</option>';
        return;
      }
      serverSel.innerHTML = servers.map((s) => `<option value="${s.id}">${UI.esc(serverOptionLabel(s))}</option>`).join('');
      if (preferServerId != null && servers.some((s) => String(s.id) === String(preferServerId))) {
        serverSel.value = String(preferServerId);
      }
    }
    populateServerOptions(sgSel.value, d.server_id);
    sgSel.addEventListener('change', () => populateServerOptions(sgSel.value, null));

    backdrop.querySelector('[data-act=cancel]').onclick = () => UI.closeModal(backdrop);
    backdrop.querySelector('[data-act=ok]').onclick = async () => {
      const name = backdrop.querySelector('#f-name').value.trim();
      if (!name) { UI.toast('이름을 입력하세요.', 'error'); return; }
      if (!serverSel.value) { UI.toast('서버를 선택하세요 (서버그룹에 서버가 없으면 먼저 서버를 추가하세요).', 'error'); return; }
      await Api.call('save_group', {
        id: d.id, server_id: Number(serverSel.value), name, type: d.type,
        description: backdrop.querySelector('#f-desc').value.trim(),
      });
      UI.closeModal(backdrop);
      await load();
    };
  }

  async function addGroup() {
    let serverId = serverIdFromSelection();
    if (serverId == null) {
      const serverGroupId = serverGroupIdFromSelection() ?? await pickServerGroupId();
      if (serverGroupId == null) return;
      serverId = await pickServerId(serverGroupId);
    }
    if (serverId == null) return;
    openGroupDialog(null, serverId);
  }

  // ── 공통 수정 / 삭제 ─────────────────────────────────────────────────────

  async function editSelected() {
    if (!selection) return;
    if (selection.kind === 'instance') {
      openInstanceDialog(selection.data, selection.data.group_id);
    } else if (selection.kind === 'group') {
      openGroupDialog(selection.data, selection.data.server_id);
    } else if (selection.kind === 'server') {
      openServerDialog(selection.data, selection.data.server_group_id);
    } else {
      openServerGroupDialog(selection.data);
    }
  }

  async function deleteSelected() {
    if (!selection) return;
    if (selection.kind === 'instance') {
      const inst = selection.data;
      if (!await UI.confirm(`인스턴스 [${inst.name}]을 삭제하시겠습니까?`, '삭제 확인')) return;
      await Api.call('delete_instance', inst.id);
    } else if (selection.kind === 'group') {
      const g = selection.data;
      if (!await UI.confirm(`그룹 [${g.name}] 및 하위 인스턴스를 모두 삭제하시겠습니까?`, '삭제 확인')) return;
      await Api.call('delete_group', g.id);
    } else if (selection.kind === 'server') {
      const s = selection.data;
      if (!await UI.confirm(`서버 [${s.name}] 및 하위 그룹/인스턴스를 모두 삭제하시겠습니까?`, '삭제 확인')) return;
      await Api.call('delete_server', s.id);
    } else {
      const sg = selection.data;
      if (!await UI.confirm(`서버그룹 [${sg.name}] 및 하위 서버/그룹/인스턴스를 모두 삭제하시겠습니까?`, '삭제 확인')) return;
      await Api.call('delete_server_group', sg.id);
    }
    selection = null;
    await load();
  }

  // ── 인스턴스 추가 / 복사 ─────────────────────────────────────────────────

  async function addInstance() {
    let groupId = null;
    if (selection?.kind === 'group') groupId = selection.data.id;
    else if (selection?.kind === 'instance') groupId = selection.data.group_id;

    if (groupId == null) {
      let serverId = serverIdFromSelection();
      if (serverId == null) {
        const serverGroupId = serverGroupIdFromSelection() ?? await pickServerGroupId();
        if (serverGroupId == null) return;
        serverId = await pickServerId(serverGroupId);
      }
      if (serverId == null) return;
      groupId = await pickGroupId(serverId);
    }
    if (groupId == null) return;
    openInstanceDialog(null, groupId);
  }

  function copySelected() {
    if (!selection || selection.kind !== 'instance') {
      UI.toast('복사할 인스턴스를 먼저 선택하세요.', 'error');
      return;
    }
    const src = selection.data;
    openInstanceDialog(null, src.group_id, src);
  }

  /* 인스턴스 우클릭에서 "다른 그룹으로 복사" — copySelected와 달리 원본은 이미 정해져
   * 있고(우클릭한 인스턴스), 대상 그룹을 서버그룹/서버 상관없이 전체 그룹 중에서 고른다.
   * 드래그 앤 드롭으로 그룹에 직접 놓는 것과 동일한 결과(다이얼로그를 열어 이름 등을
   * 확인/수정 후 저장)를 컨텍스트 메뉴로도 쓸 수 있게 한 것이다. */
  async function copyToOtherGroup() {
    if (!selection || selection.kind !== 'instance') return;
    const src = selection.data;
    const options = [];
    for (const sg of serverGroups) {
      for (const s of sg.servers) {
        for (const g of s.groups) {
          options.push({ value: g.id, label: `[${sg.name} / ${s.name}] ${g.name}` });
        }
      }
    }
    if (!options.length) { UI.toast('복사할 대상 그룹이 없습니다. 먼저 그룹을 추가하세요.', 'error'); return; }
    const chosen = await UI.selectDialog(`[${src.name}]을(를) 복사할 대상 그룹:`, options, '대상 그룹 선택');
    if (!chosen) return;
    openInstanceDialog(null, Number(chosen), src);
  }

  /* 그룹 우클릭에서 "복사해서 추가" — 그 그룹엔 아직 인스턴스가 없을 수도 있으므로
   * (=복사 원본이 될 인스턴스가 그 그룹 안엔 없음) 원본은 전체 인스턴스 중에서 고르고,
   * 대상 그룹은 우클릭한 그룹으로 고정한다. 다이얼로그에서 대상을 다시 바꿀 수도 있다. */
  async function copyToGroup() {
    if (!selection || selection.kind !== 'group') return;
    const targetGroup = selection.data;
    if (!flatInstances.length) {
      UI.toast('복사할 인스턴스가 아직 하나도 없습니다. 먼저 인스턴스를 등록하세요.', 'error');
      return;
    }
    const chosen = await UI.selectDialog('설정을 복사해올 원본 인스턴스:',
      flatInstances.map((i) => ({ value: i.id, label: `[${i.server_group_name} / ${i.server_name} / ${i.group_name}] ${i.name}` })),
      '복사 원본 선택');
    if (!chosen) return;
    const src = flatInstances.find((i) => String(i.id) === String(chosen));
    if (src) openInstanceDialog(null, targetGroup.id, src);
  }

  async function testSelected() {
    const scoped = selectedInstances();
    const instances = scoped.length ? scoped : flatInstances; // 선택 없으면 전체 테스트
    if (!instances.length) { UI.toast('테스트할 인스턴스가 없습니다.', 'error'); return; }

    const scopeLabel = !scoped.length ? '전체'
      : selection.kind === 'serverGroup' ? `서버그룹 [${selection.data.name}]`
      : selection.kind === 'server' ? `서버 [${selection.data.server_group_name} / ${selection.data.name}]`
      : selection.kind === 'group' ? `그룹 [${selection.data.server_group_name} / ${selection.data.server_name} / ${selection.data.name}]`
      : `[${selection.data.name}]`;

    setStatus(`연결 테스트 중: ${scopeLabel} (${instances.length}개)...`);
    const results = await Promise.all(instances.map((inst) =>
      Api.call('test_connection', inst.id).then((res) => { applyStatusBadge(inst.id, res); return res; })));
    const successCount = results.filter((r) => r.success).length;

    setStatus(`연결 테스트 완료: ${scopeLabel} — 성공 ${successCount}/${results.length}`);
    UI.toast(`연결 테스트 완료 (${scopeLabel}): 성공 ${successCount}/${results.length}`,
      successCount === results.length ? undefined : 'error');
  }

  // ── 인스턴스 등록 / 수정 다이얼로그 ────────────────────────────────────

  // 기본 경로 하나로 bin/lib/시퀀스/로그 경로를 한번에 채우기 위한 헬퍼.
  // jdbc_registry_path는 파일시스템 경로가 아니라 Carbon 레지스트리 가상 경로라
  // 기본 경로와 무관하게 별도로 남겨둔다.
  // 제품별 데이터플레인 서비스 포트 기본값 — EI(구형 ESB 계열)는 8280, MI/APIM은 8290.
  // 실제 서버가 다른 값을 쓰면(예: carbon offset 적용) 사용자가 직접 고친 값을 그대로 두고,
  // "다른 제품의 기본값 그대로 남아있는" 경우에만 새 제품의 기본값으로 바꿔준다.
  function defaultServicePort(product) {
    return product === 'EI' ? 8280 : 8290;
  }

  function defaultBasePath(type) {
    if (type === 'APIM_GATEWAY') return '/app/ipaas/gateway01';
    if (type.startsWith('MI_')) return '/app/ipaas/mi01';
    if (type.startsWith('APIM_')) return '/app/ipaas/manager01';
    return '';
  }

  function derivedPaths(basePath) {
    const b = basePath.replace(/\/+$/, '');
    return {
      bin_path: `${b}/bin`,
      lib_path: `${b}/lib`,
      sequence_path: `${b}/repository/deployment/server/synapse-configs/default/sequences`,
      log_path: `${b}/repository/logs/wso2carbon.log`,
    };
  }

  function openInstanceDialog(inst, groupId, copyFrom) {
    const initialGroupId = groupId != null ? groupId : (copyFrom ? copyFrom.group_id : null);
    const d = inst || (copyFrom ? {
      ...copyFrom,
      id: null,
      group_id: initialGroupId,
      name: `${copyFrom.name}_복사`,
    } : {
      id: null, group_id: initialGroupId, name: '', type: 'MI_ALL_IN_ONE', product: '', version: '', environment: 'DEV', host: '', port: 9164,
      service_port: 8290, base_path: defaultBasePath('MI_ALL_IN_ONE'),
      ...derivedPaths(defaultBasePath('MI_ALL_IN_ONE')),
      jdbc_registry_path: 'registry/config/jdbc',
      api_log_max_mb: 10,
      description: '', admin_user: 'admin', admin_pass: '', token_url: '',
      service_script: 'micro-integrator.sh',
    });

    const initialCtx = findGroupContext(d.group_id);
    // 신규 등록(수정도 복사도 아님)이면서 서버에 IP가 설정돼 있으면, Host의 기본값을
    // 그 서버의 IP로 미리 채워둔다 (아래에서도 서버를 바꿀 때마다 같은 규칙으로 갱신됨).
    if (!inst && !copyFrom && initialCtx && initialCtx.server.ip && !d.host) {
      d.host = initialCtx.server.ip;
    }

    const backdrop = UI.openModal(`
      <div class="modal-header">${inst ? '인스턴스 수정' : (copyFrom ? '인스턴스 복사 추가' : '인스턴스 등록')}</div>
      <div class="modal-body">
        <div class="field-group">
          <div class="field-group-title">기본 정보</div>
          <div class="field-row"><label>이름 *</label><input type="text" id="f-name" value="${UI.esc(d.name)}"></div>
          <div class="field-row"><label>서버그룹 *</label>
            <select id="f-server-group">${serverGroups.map((sg) => `<option value="${sg.id}">${UI.esc(sg.name)}</option>`).join('')}</select>
          </div>
          <div class="field-row"><label>서버 *</label>
            <select id="f-server"></select>
          </div>
          <div class="field-row"><label>그룹 *</label>
            <select id="f-group"></select>
          </div>
          <div class="field-row"><label>제품명</label>
            <select id="f-product"><option value="">(미지정)</option>${PRODUCTS.map((p) => `<option value="${p}">${p}</option>`).join('')}</select>
          </div>
          <div class="field-row"><label>버전</label>
            <select id="f-version"><option value="">(미지정)</option>${VERSIONS.map((v) => `<option value="${v}">${v}</option>`).join('')}</select>
          </div>
          <div class="field-row"><label>타입</label>
            <select id="f-type">${INSTANCE_TYPES.map((t) => `<option value="${t.value}">${UI.esc(t.label)}</option>`).join('')}</select>
          </div>
          <div class="field-row"><label>환경 *</label>
            <select id="f-env">${ENVIRONMENTS.map((e) => `<option value="${e.value}">${UI.esc(e.label)}</option>`).join('')}</select>
          </div>
          <div class="field-row"><label>Host *</label><input type="text" id="f-host" value="${UI.esc(d.host)}"></div>
          <div class="hint">비워두면 선택한 서버의 IP를 기본값으로 사용합니다. 서버를 바꾸면 이 값이
            직접 수정한 적 없는 한(=이전 서버의 IP 그대로인 한) 새 서버의 IP로 자동 갱신되고,
            직접 다른 값으로 고쳤다면 그대로 유지됩니다.</div>
          <div class="field-row"><label>Port (관리)</label><input type="number" id="f-port" value="${d.port}"></div>
          <div class="field-row"><label>서비스 Port</label><input type="number" id="f-service-port" value="${d.service_port}"></div>
          <div class="hint">배포된 API/커넥터 호출에 쓰는 데이터플레인 포트(인증 불필요) — 관리 포트(9164/9443)와
            다릅니다. 제품별 기본값이 다르니 실제 서버 설정과 맞는지 꼭 확인하세요: MI/APIM은 보통 8290,
            EI(구형 ESB 계열)는 보통 8280. 이 값이 실제와 다르면 로그 뷰어 API, JDBC 커넥터 접속 테스트 등이
            엉뚱한 서버를 찌를 수 있습니다.</div>
          <div class="field-row"><label>기본 경로</label><input type="text" id="f-base-path" value="${UI.esc(d.base_path || '')}" placeholder="/app/ipaas/mi01"></div>
          <div class="hint">기본 경로를 입력하고 다른 곳을 클릭하면 아래 로그/lib/시퀀스/bin 경로를 "기본 경로 + 하위경로"로 자동 채웁니다 (채운 뒤 개별 수정도 가능). EI도 Carbon 기반이라 동일한 디렉터리 구조를 씁니다.</div>
          <div class="field-row"><label>로그 경로</label><input type="text" id="f-logpath" value="${UI.esc(d.log_path)}"></div>
          <div class="field-row ei-hide-row"><label>API 로그 최대 크기(MB)</label><input type="number" id="f-api-log-max-mb" value="${d.api_log_max_mb || 10}" min="1"></div>
          <div class="hint ei-hide-row">로그 뷰어를 API 방식으로 쓸 때, 이 크기를 넘는 로그 파일은 조회를 거부하고 SSH를 안내합니다 (SSH는 tail -f라 크기 제한 없음).</div>
          <div class="field-row"><label>lib 경로</label><input type="text" id="f-lib-path" value="${UI.esc(d.lib_path)}" placeholder="/opt/wso2mi/lib"></div>
          <div class="field-row"><label>시퀀스 경로</label><input type="text" id="f-sequence-path" value="${UI.esc(d.sequence_path)}" placeholder="/opt/wso2mi/repository/deployment/server/synapse-configs/default/sequences"></div>
          <div class="field-row ei-hide-row"><label>JDBC 레지스트리 경로</label><input type="text" id="f-jdbc-registry-path" value="${UI.esc(d.jdbc_registry_path)}" placeholder="registry/config/jdbc"></div>
          <div class="field-row"><label>bin 경로</label><input type="text" id="f-bin-path" value="${UI.esc(d.bin_path)}" placeholder="/opt/wso2mi/bin"></div>
          <div class="field-row"><label>실행 스크립트</label><input type="text" id="f-service-script" value="${UI.esc(d.service_script)}" placeholder="micro-integrator.sh"></div>
          <div class="hint">SSH 재시작/정지에 사용 (bin 경로 + 실행 스크립트). MI는 micro-integrator.sh,
            APIM은 api-manager.sh, EI는 integrator.sh — graceful 옵션은 지원하지 않습니다.</div>
          <div class="field-row"><label>설명</label><input type="text" id="f-desc" value="${UI.esc(d.description)}"></div>
        </div>
        <div class="field-group">
          <div class="field-group-title">인증 / API</div>
          <div class="field-row"><label>Admin 계정</label><input type="text" id="f-user" value="${UI.esc(d.admin_user)}"></div>
          <div class="field-row"><label>Admin 패스워드</label><input type="password" id="f-pass" value="${UI.esc(d.admin_pass)}"></div>
          <div class="field-row ei-hide-row"><label>Token URL</label><input type="text" id="f-token" value="${UI.esc(d.token_url)}"></div>
          <div class="hint ei-show-row" style="display:none">EI는 Host/Port + Admin 계정·비밀번호로 SOAP 웹서비스(NDataSourceAdmin)를
            Basic Auth로 직접 호출하므로 Token URL은 필요하지 않습니다 (API 로그 최대 크기/JDBC 레지스트리
            경로는 SIIS 커넥터 전용 기능이라 EI엔 해당 없음 — 기본/로그/lib/시퀀스/bin 경로는 EI도 Carbon
            기반이라 MI와 동일하게 적용됩니다). 서비스 Port는 EI도 사용합니다 — JDBC 접속 테스트, 로그 뷰어
            API 등이 이 포트로 호출되며, EI의 기본값(8280)은 MI(8290)와 다르니 반드시 확인하세요.</div>
          <div class="hint">OS SSH 접속 정보(재시작/정지, 로그 tail -f 등에 쓰는 계정)는 인스턴스가 아니라
            이 인스턴스가 속한 "서버" 등록/수정 화면에서 관리합니다 — 같은 서버 위 인스턴스는 보통
            같은 OS 계정을 쓰기 때문입니다.</div>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn" data-act="cancel">취소</button>
        <button class="btn btn-primary" data-act="ok">확인</button>
      </div>`, 700, { closeOnBackdrop: false });

    const typeSel = backdrop.querySelector('#f-type');
    typeSel.value = d.type;
    const productSel = backdrop.querySelector('#f-product');
    productSel.value = d.product || '';
    backdrop.querySelector('#f-version').value = d.version || '';
    backdrop.querySelector('#f-env').value = d.environment;

    // EI는 이 도구에서 SOAP 웹서비스(NDataSourceAdmin, Host/Port + Admin 계정으로 직접
    // Basic Auth 호출)로만 데이터소스를 다뤄서, MI 전용 배포/로그 경로들과 Token URL은
    // 의미가 없다 — 제품명이 EI면 그 입력칸들을 숨겨 불필요한 입력을 막는다.
    function updateFieldVisibilityForProduct() {
      const isEi = productSel.value === 'EI';
      backdrop.querySelectorAll('.ei-hide-row').forEach((el) => { el.style.display = isEi ? 'none' : ''; });
      backdrop.querySelectorAll('.ei-show-row').forEach((el) => { el.style.display = isEi ? '' : 'none'; });

      // 서비스 Port가 비어있거나 "다른 제품의 기본값" 그대로면(=사용자가 직접 고친 적 없어
      // 보이면) 지금 선택한 제품의 기본값으로 맞춰준다. 8280/8290이 아닌 값(직접 수정한 값)은
      // 절대 건드리지 않는다.
      const servicePortInput = backdrop.querySelector('#f-service-port');
      const knownDefaults = [8280, 8290];
      const cur = Number(servicePortInput.value);
      if (!cur || knownDefaults.includes(cur)) {
        servicePortInput.value = defaultServicePort(productSel.value);
      }
    }
    updateFieldVisibilityForProduct();
    productSel.addEventListener('change', updateFieldVisibilityForProduct);

    const sgSel = backdrop.querySelector('#f-server-group');
    const serverSel = backdrop.querySelector('#f-server');
    const groupSel = backdrop.querySelector('#f-group');
    const hostInput = backdrop.querySelector('#f-host');

    // Host 자동 채움 추적용 — 사용자가 직접 값을 바꾸지 않은 한(=지금 값이 이전에
    // 자동으로 채워준 IP와 같은 한) 서버를 바꿀 때마다 새 서버의 IP로 계속 갱신한다.
    let lastAutoHost = (initialCtx && initialCtx.server.ip === d.host) ? d.host : null;

    function populateGroupOptions(serverIdVal, preferGroupId) {
      const ctx = findServerContext(Number(serverIdVal));
      const groupsHere = ctx ? ctx.server.groups : [];
      if (!groupsHere.length) {
        groupSel.innerHTML = '<option value="">(이 서버엔 그룹이 없습니다 — 그룹을 먼저 추가하세요)</option>';
      } else {
        groupSel.innerHTML = groupsHere.map((g) => `<option value="${g.id}">${UI.esc(g.name)}</option>`).join('');
        if (preferGroupId != null && groupsHere.some((g) => String(g.id) === String(preferGroupId))) {
          groupSel.value = String(preferGroupId);
        }
      }
      const serverIp = ctx ? ctx.server.ip : '';
      if (serverIp && (!hostInput.value.trim() || hostInput.value === lastAutoHost)) {
        hostInput.value = serverIp;
        lastAutoHost = serverIp;
      }
    }

    // 서버그룹을 바꾸면 그 서버그룹의 서버 목록으로 다시 채운다. preferServerId가 그
    // 서버그룹에 실제로 있으면 그걸 선택해두고, 없으면 첫 번째 서버를 기본 선택한다.
    function populateServerOptions(sgIdVal, preferServerId, preferGroupId) {
      const sg = serverGroups.find((x) => String(x.id) === String(sgIdVal));
      const servers = sg ? sg.servers : [];
      if (!servers.length) {
        serverSel.innerHTML = '<option value="">(이 서버그룹엔 서버가 없습니다)</option>';
        groupSel.innerHTML = '<option value=""></option>';
        return;
      }
      serverSel.innerHTML = servers.map((s) => `<option value="${s.id}">${UI.esc(serverOptionLabel(s))}</option>`).join('');
      if (preferServerId != null && servers.some((s) => String(s.id) === String(preferServerId))) {
        serverSel.value = String(preferServerId);
      }
      populateGroupOptions(serverSel.value, preferGroupId);
    }

    sgSel.value = initialCtx ? initialCtx.serverGroup.id : (serverGroups[0] ? serverGroups[0].id : '');
    populateServerOptions(sgSel.value, initialCtx ? initialCtx.server.id : null, d.group_id);
    sgSel.addEventListener('change', () => populateServerOptions(sgSel.value, null, null));
    serverSel.addEventListener('change', () => populateGroupOptions(serverSel.value, null));

    const basePathInput = backdrop.querySelector('#f-base-path');
    const applyBasePath = () => {
      const bp = basePathInput.value.trim();
      if (!bp) return;
      const paths = derivedPaths(bp);
      backdrop.querySelector('#f-logpath').value = paths.log_path;
      backdrop.querySelector('#f-lib-path').value = paths.lib_path;
      backdrop.querySelector('#f-sequence-path').value = paths.sequence_path;
      backdrop.querySelector('#f-bin-path').value = paths.bin_path;
    };
    basePathInput.addEventListener('change', applyBasePath);
    // 타입을 바꿨는데 기본 경로가 비어있으면(신규 등록 등) 그 타입의 기본값을 채워준다.
    // 이미 기본 경로가 입력돼 있으면 사용자가 정한 값을 덮어쓰지 않는다.
    typeSel.addEventListener('change', () => {
      if (!basePathInput.value.trim()) {
        basePathInput.value = defaultBasePath(typeSel.value);
        applyBasePath();
      }
    });

    backdrop.querySelector('[data-act=cancel]').onclick = () => UI.closeModal(backdrop);
    backdrop.querySelector('[data-act=ok]').onclick = async () => {
      const name = backdrop.querySelector('#f-name').value.trim();
      const host = hostInput.value.trim();
      if (!name) { UI.toast('이름을 입력하세요.', 'error'); return; }
      if (!host) { UI.toast('Host를 입력하세요 (서버에 IP가 설정돼 있으면 자동으로 채워집니다).', 'error'); return; }
      if (!groupSel.value) { UI.toast('그룹을 선택하세요 (서버에 그룹이 없으면 먼저 그룹을 추가하세요).', 'error'); return; }
      const payload = {
        id: d.id,
        group_id: Number(groupSel.value),
        name,
        host,
        type: backdrop.querySelector('#f-type').value,
        product: backdrop.querySelector('#f-product').value,
        version: backdrop.querySelector('#f-version').value,
        environment: backdrop.querySelector('#f-env').value,
        port: Number(backdrop.querySelector('#f-port').value || 9164),
        service_port: Number(backdrop.querySelector('#f-service-port').value || 8290),
        base_path: basePathInput.value.trim(),
        description: backdrop.querySelector('#f-desc').value.trim(),
        admin_user: backdrop.querySelector('#f-user').value.trim(),
        admin_pass: backdrop.querySelector('#f-pass').value,
        token_url: backdrop.querySelector('#f-token').value.trim(),
        log_path: backdrop.querySelector('#f-logpath').value.trim(),
        api_log_max_mb: Number(backdrop.querySelector('#f-api-log-max-mb').value || 10),
        lib_path: backdrop.querySelector('#f-lib-path').value.trim(),
        sequence_path: backdrop.querySelector('#f-sequence-path').value.trim(),
        jdbc_registry_path: backdrop.querySelector('#f-jdbc-registry-path').value.trim(),
        bin_path: backdrop.querySelector('#f-bin-path').value.trim(),
        service_script: backdrop.querySelector('#f-service-script').value.trim(),
      };
      await Api.call('save_instance', payload);
      UI.closeModal(backdrop);
      await load();
    };
  }

  // ── 인스턴스 등록정보 export / import ────────────────────────────────

  function buildExportChecklistHtml(preChecked) {
    if (!serverGroups.length) return '<div class="hint">등록된 인스턴스가 없습니다.</div>';
    let html = '';
    for (const sg of serverGroups) {
      const sgInstCount = sg.servers.reduce((n, s) => n + s.instances.length, 0);
      if (!sgInstCount) continue;
      const sgChecked = sg.servers.every((s) => s.instances.every((i) => preChecked.has(i.id)));
      html += `<div class="field-row"><label>
          <input type="checkbox" class="exp-sg-chk" data-sg-id="${sg.id}" ${sgChecked ? 'checked' : ''}>
          <b>${UI.esc(sg.name)}</b> <span class="hint">(${sgInstCount}개)</span></label></div>`;
      for (const s of sg.servers) {
        if (!s.instances.length) continue;
        const serverChecked = s.instances.every((i) => preChecked.has(i.id));
        html += `<div class="field-row" style="padding-left:20px"><label>
            <input type="checkbox" class="exp-server-chk" data-server-id="${s.id}" data-sg-id="${sg.id}" ${serverChecked ? 'checked' : ''}>
            ${UI.esc(s.name)} <span class="hint">(${s.instances.length}개)</span></label></div>`;
        for (const g of s.groups) {
          if (!g.instances.length) continue;
          const groupChecked = g.instances.every((i) => preChecked.has(i.id));
          html += `<div class="field-row" style="padding-left:40px"><label>
              <input type="checkbox" class="exp-group-chk" data-group-id="${g.id}" data-server-id="${s.id}" data-sg-id="${sg.id}" ${groupChecked ? 'checked' : ''}>
              ${UI.esc(g.name)} <span class="hint">(${g.instances.length}개)</span></label></div>`;
          for (const inst of g.instances) {
            html += `<div class="field-row" style="padding-left:60px"><label>
                <input type="checkbox" class="exp-inst-chk" data-instance-id="${inst.id}" data-group-id="${g.id}" data-server-id="${s.id}" data-sg-id="${sg.id}" ${preChecked.has(inst.id) ? 'checked' : ''}>
                ${UI.esc(inst.name)} <span class="hint">(${UI.esc(inst.host)}:${inst.port})</span></label></div>`;
          }
        }
      }
    }
    return html;
  }

  async function exportInstances() {
    const preChecked = new Set(selectedInstances().map((i) => i.id));
    const scopeHint = !selection
      ? '내보낼 인스턴스를 아래에서 직접 체크하거나, 바로 "전체 내보내기"를 누르세요.'
      : selection.kind === 'serverGroup'
        ? `현재 좌측 트리에서 서버그룹 [${selection.data.name}]이 선택되어 있어 그 인스턴스들을 미리 체크했습니다.`
        : `현재 좌측 트리에서 [${selection.data.name}]이(가) 선택되어 있어 미리 체크했습니다.`;

    const backdrop = UI.openModal(`
      <div class="modal-header">내보내기 범위 선택</div>
      <div class="modal-body">
        <div class="hint">${scopeHint}</div>
        <div class="field-group" style="max-height:320px;overflow-y:auto">
          ${buildExportChecklistHtml(preChecked)}
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn" data-act="cancel">취소</button>
        <button class="btn" data-act="selected">체크한 항목만 내보내기</button>
        <button class="btn btn-primary" data-act="all">전체 내보내기</button>
      </div>`, 480, { closeOnBackdrop: false });

    // 서버그룹 ↔ 서버 ↔ 그룹 ↔ 인스턴스 체크박스 4단 상호 연동.
    // "일부만 체크"된 상태는 상위 체크를 그냥 풀어버리면 마치 아무것도 안 골랐다는
    // 것처럼 보이므로, indeterminate(대시 표시)로 구분한다:
    //   전부 체크 → checked / 하나도 없음 → unchecked / 일부만 → indeterminate
    const syncGroupState = (groupId) => {
      const gChk = backdrop.querySelector(`.exp-group-chk[data-group-id="${groupId}"]`);
      if (!gChk) return;
      const siblings = backdrop.querySelectorAll(`.exp-inst-chk[data-group-id="${groupId}"]`);
      const total = siblings.length;
      const checkedCount = Array.from(siblings).filter((x) => x.checked).length;
      gChk.checked = total > 0 && checkedCount === total;
      gChk.indeterminate = checkedCount > 0 && checkedCount < total;
    };
    const syncServerState = (serverId) => {
      const sChk = backdrop.querySelector(`.exp-server-chk[data-server-id="${serverId}"]`);
      if (!sChk) return;
      const siblings = backdrop.querySelectorAll(`.exp-inst-chk[data-server-id="${serverId}"]`);
      const total = siblings.length;
      const checkedCount = Array.from(siblings).filter((x) => x.checked).length;
      sChk.checked = total > 0 && checkedCount === total;
      sChk.indeterminate = checkedCount > 0 && checkedCount < total;
    };
    const syncServerGroupState = (sgId) => {
      const sgChk = backdrop.querySelector(`.exp-sg-chk[data-sg-id="${sgId}"]`);
      if (!sgChk) return;
      const siblings = backdrop.querySelectorAll(`.exp-inst-chk[data-sg-id="${sgId}"]`);
      const total = siblings.length;
      const checkedCount = Array.from(siblings).filter((x) => x.checked).length;
      sgChk.checked = total > 0 && checkedCount === total;
      sgChk.indeterminate = checkedCount > 0 && checkedCount < total;
    };

    backdrop.querySelectorAll('.exp-sg-chk').forEach((sgChk) => {
      sgChk.addEventListener('change', () => {
        sgChk.indeterminate = false; // 클릭했으니 명확히 전체 체크/전체 해제로 확정
        backdrop.querySelectorAll(`.exp-inst-chk[data-sg-id="${sgChk.dataset.sgId}"]`)
          .forEach((iChk) => { iChk.checked = sgChk.checked; });
        backdrop.querySelectorAll(`.exp-server-chk[data-sg-id="${sgChk.dataset.sgId}"], .exp-group-chk[data-sg-id="${sgChk.dataset.sgId}"]`)
          .forEach((chk) => { chk.checked = sgChk.checked; chk.indeterminate = false; });
      });
      syncServerGroupState(sgChk.dataset.sgId); // 초기 렌더 시 미리 체크된 상태 기준으로 표시
    });
    backdrop.querySelectorAll('.exp-server-chk').forEach((sChk) => {
      sChk.addEventListener('change', () => {
        sChk.indeterminate = false;
        backdrop.querySelectorAll(`.exp-inst-chk[data-server-id="${sChk.dataset.serverId}"]`)
          .forEach((iChk) => { iChk.checked = sChk.checked; });
        backdrop.querySelectorAll(`.exp-group-chk[data-server-id="${sChk.dataset.serverId}"]`)
          .forEach((gChk) => { gChk.checked = sChk.checked; gChk.indeterminate = false; });
        syncServerGroupState(sChk.dataset.sgId);
      });
      syncServerState(sChk.dataset.serverId);
    });
    backdrop.querySelectorAll('.exp-group-chk').forEach((gChk) => {
      gChk.addEventListener('change', () => {
        gChk.indeterminate = false;
        backdrop.querySelectorAll(`.exp-inst-chk[data-group-id="${gChk.dataset.groupId}"]`)
          .forEach((iChk) => { iChk.checked = gChk.checked; });
        syncServerState(gChk.dataset.serverId);
        syncServerGroupState(gChk.dataset.sgId);
      });
      syncGroupState(gChk.dataset.groupId);
    });
    backdrop.querySelectorAll('.exp-inst-chk').forEach((iChk) => {
      iChk.addEventListener('change', () => {
        syncGroupState(iChk.dataset.groupId);
        syncServerState(iChk.dataset.serverId);
        syncServerGroupState(iChk.dataset.sgId);
      });
    });

    const runExport = async (instanceIds) => {
      UI.closeModal(backdrop);
      const scopeLabel = instanceIds ? `선택한 인스턴스 ${instanceIds.length}개` : '전체 서버그룹/서버/그룹/인스턴스';
      const ok = await UI.confirm(
        `${scopeLabel}를 파일로 내보냅니다.\n⚠️ Admin/SSH 비밀번호가 파일에 평문으로 포함되니 안전하게 보관하세요.`,
        '내보내기 확인');
      if (!ok) return;
      const res = await Api.call('export_instances', instanceIds || null);
      if (!res.success) {
        if (res.error !== 'Cancelled') UI.toast(`내보내기 실패: ${res.error}`, 'error');
        return;
      }
      UI.toast(`내보내기 완료: 서버그룹 ${res.server_group_count}개, 서버 ${res.server_count}개, ` +
        `그룹 ${res.group_count}개, 인스턴스 ${res.instance_count}개 → ${res.path}`);
    };

    backdrop.querySelector('[data-act=cancel]').onclick = () => UI.closeModal(backdrop);
    backdrop.querySelector('[data-act=all]').onclick = () => runExport(null);
    backdrop.querySelector('[data-act=selected]').onclick = () => {
      const ids = Array.from(backdrop.querySelectorAll('.exp-inst-chk:checked'))
        .map((el) => Number(el.dataset.instanceId));
      if (!ids.length) { UI.toast('체크한 인스턴스가 없습니다.', 'error'); return; }
      runExport(ids);
    };
  }

  async function importInstances() {
    const path = await Api.call('pick_import_file');
    if (!path) return;

    const preview = await Api.call('preview_import_file', path);
    if (!preview.success) { UI.toast(`파일을 읽을 수 없습니다: ${preview.error}`, 'error'); return; }
    if (!preview.server_group_count) { UI.toast('파일에 가져올 서버그룹/인스턴스가 없습니다.', 'error'); return; }

    const sgLines = preview.server_groups
      .map((sg) => {
        const serverLines = sg.servers
          .map((s) => {
            const groupLines = s.groups
              .map((g) => `      · ${g.name} (${g.instance_names.length}개: ${g.instance_names.join(', ') || '없음'})`)
              .join('\n');
            return `    - ${s.name}\n${groupLines}`;
          })
          .join('\n');
        return `- ${sg.name}\n${serverLines}`;
      })
      .join('\n');
    const ok = await UI.confirm(
      `[${path}]\n\n` +
      `서버그룹 ${preview.server_group_count}개, 인스턴스 ${preview.instance_count}개를 가져옵니다:\n${sgLines}\n\n` +
      '같은 이름의 서버그룹/서버/그룹은 기존 것을 재사용합니다. 인스턴스는 같은 그룹 안에 같은 이름이 ' +
      '이미 있으면 그 인스턴스를 업데이트하고, 없으면 새로 추가합니다. (서버/그룹 계층이 없는 구버전 ' +
      '파일은 서버그룹당 기본 서버 + 기본 그룹으로 자동 이관됩니다.)',
      '가져오기 확인 — 무엇을 가져오는지 확인하세요');
    if (!ok) return;

    const res = await Api.call('import_instances', path);
    if (!res.success) { UI.toast(`가져오기 실패: ${res.error}`, 'error'); return; }
    const hasErrors = res.errors && res.errors.length;
    let msg = `가져오기 완료: 서버그룹 ${res.created_server_groups}개 생성, 서버 ${res.created_servers}개 생성, ` +
      `그룹 ${res.created_groups}개 생성, 인스턴스 ${res.created_instances}개 생성 / ${res.updated_instances}개 업데이트`;
    if (hasErrors) msg += ` (오류 ${res.errors.length}건 — 감사 로그 참고)`;
    UI.toast(msg, hasErrors ? 'error' : undefined);
    await load();
  }

  async function resetAllData() {
    const serverGroupCount = serverGroups.length;
    const instanceCount = flatInstances.length;
    const ok = await UI.confirm(
      `현재 등록된 서버그룹 ${serverGroupCount}개, 인스턴스 ${instanceCount}개와 배포 이력, 감사 로그, ` +
      'JNDI 캐시, 토큰 캐시를 모두 삭제합니다.\n\n' +
      '계속하면 먼저 DB 백업 파일을 저장할 경로를 선택하게 되며, 이후 복구는 그 백업 파일로만 ' +
      '가능합니다 (되돌리기 버튼 없음).\n\n정말로 전체 초기화를 진행하시겠습니까?',
      '⚠ 전체 정보 초기화 — 되돌릴 수 없습니다');
    if (!ok) return;

    const res = await Api.call('reset_all_data');
    if (!res.success) {
      if (res.error !== 'Cancelled') UI.toast(`초기화 실패: ${res.error}`, 'error');
      return;
    }
    UI.toast(`초기화 완료 (백업: ${res.backup_path}). 화면을 새로고침합니다.`);
    location.reload();
  }

  // 자주 안 쓰는(또는 위험한) 액션은 툴바를 어지럽히지 않도록 "더보기" 드롭다운에
  // 모아둔다 — 우클릭 컨텍스트 메뉴에 이미 쓰던 UI.contextMenu를 버튼 클릭 위치에
  // 그대로 재사용.
  function showMoreMenu(e) {
    e.stopPropagation();
    const rect = e.currentTarget.getBoundingClientRect();
    UI.contextMenu(rect.left, rect.bottom + 4, [
      { label: '복사 추가', onClick: copySelected },
      { label: '내보내기', onClick: exportInstances },
      { label: '가져오기', onClick: importInstances },
      { label: '전체 정보 초기화', danger: true, onClick: resetAllData },
    ]);
  }

  // 서버그룹/서버/그룹/인스턴스 4개의 "+추가" 버튼을 하나로 묶은 드롭다운 — 각 항목은
  // 기존 addXxx()를 그대로 호출하므로(선택 상태로부터 상위 컨텍스트 자동 유추) 동작은 동일.
  function showAddMenu(e) {
    e.stopPropagation();
    const rect = e.currentTarget.getBoundingClientRect();
    UI.contextMenu(rect.left, rect.bottom + 4, [
      { label: '🏢 서버그룹 추가', onClick: addServerGroup },
      { label: '🖥️ 서버 추가', onClick: addServer },
      { label: '🎛️ 그룹 추가', onClick: addGroup },
      { label: '⚙️ 인스턴스 추가', onClick: addInstance },
    ]);
  }

  function init() {
    document.getElementById('btn-add').addEventListener('click', showAddMenu);
    document.getElementById('btn-edit').addEventListener('click', editSelected);
    document.getElementById('btn-delete').addEventListener('click', deleteSelected);
    document.getElementById('btn-test').addEventListener('click', testSelected);
    document.getElementById('btn-more').addEventListener('click', showMoreMenu);
    document.getElementById('tree-search').addEventListener('input', (e) => {
      treeFilter = e.target.value;
      render();
    });
    load();
  }

  return { init, load, getServerGroups: () => serverGroups, getFlatInstances: () => flatInstances };
})();
