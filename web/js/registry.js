/* 레지스트리 리소스 조회/생성/수정/삭제 — 좌측에서 선택한 MI 인스턴스를 대상으로 동작.
 * 저장은 존재 여부를 몰라도 되도록 백엔드(MIClient.save_registry_resource)가 PUT을 먼저
 * 시도하고 "존재하지 않음" 오류일 때만 POST로 재시도한다 — 여기서는 그 결과(mode)만 반영. */
const Registry = (() => {
  let currentInstance = null;
  let instSelect = null; // 서버그룹>서버>그룹>인스턴스 선택 — ui.js의 UI.wireInstanceCascade
  const TREE_ROOT = 'registry';
  let treeState = {}; // path -> { expanded, items(null=미로딩), loading, error }
  let selectedLeafPath = null;
  let treeFilter = ''; // 이미 펼쳐둔 폴더 안에서 즉시(네트워크 요청 없이) 걸러낼 검색어

  function setLabel() {
    const label = document.getElementById('registry-instance-label');
    if (!currentInstance) {
      label.textContent = '좌측에서 인스턴스를 선택하세요.';
      return;
    }
    label.textContent = `[${instanceTypeShortLabel(currentInstance.type)}] ${currentInstance.name} (${currentInstance.host}:${currentInstance.port})`;
  }

  function setStatus(text) {
    document.getElementById('registry-status').textContent = text;
  }

  function guardInstance() {
    if (!currentInstance) { UI.toast('좌측에서 인스턴스를 선택하세요.', 'error'); return false; }
    if (!currentInstance.type.startsWith('MI_')) {
      UI.toast('MI 인스턴스에서만 레지스트리 조회/저장이 가능합니다.', 'error');
      return false;
    }
    return true;
  }

  function getPath() {
    return document.getElementById('registry-path').value.trim();
  }

  // ── 트리 브라우징 ─────────────────────────────────────────────────────

  function getNodeState(path) {
    return treeState[path] || (treeState[path] = { expanded: false, items: null, loading: false, error: null });
  }

  function renderTree() {
    const container = document.getElementById('registry-tree');
    container.innerHTML = '';
    if (!currentInstance || !currentInstance.type.startsWith('MI_')) {
      container.innerHTML = '<div class="registry-tree-row hint">MI 인스턴스를 선택하면 표시됩니다.</div>';
      return;
    }
    const frag = document.createDocumentFragment();
    appendNode(frag, TREE_ROOT, TREE_ROOT, 0, true);
    container.appendChild(frag);
  }

  function appendMessageRow(frag, text, depth) {
    const row = document.createElement('div');
    row.className = 'registry-tree-row hint';
    row.style.paddingLeft = `${depth * 16 + 8}px`;
    row.textContent = text;
    frag.appendChild(row);
  }

  function appendNode(frag, path, name, depth, isDir) {
    const row = document.createElement('div');
    row.className = `registry-tree-row ${isDir ? 'is-dir' : 'is-leaf'}`;
    row.style.paddingLeft = `${depth * 16 + 8}px`;
    if (!isDir && path === selectedLeafPath) row.classList.add('is-leaf-selected');
    const st = isDir ? getNodeState(path) : null;
    const prefix = isDir ? (st.expanded ? '▾ ' : '▸ ') : '　';
    row.textContent = prefix + name;
    row.addEventListener('click', () => (isDir ? toggleNode(path) : selectLeaf(path)));
    frag.appendChild(row);

    if (isDir && st.expanded) {
      if (st.loading) { appendMessageRow(frag, '불러오는 중...', depth + 1); return; }
      if (st.error) { appendMessageRow(frag, `오류: ${st.error}`, depth + 1); return; }
      const items = st.items || [];
      if (!items.length) { appendMessageRow(frag, '(비어있음)', depth + 1); return; }
      const visible = treeFilter ? items.filter((it) => it.name.toLowerCase().includes(treeFilter)) : items;
      if (!visible.length) { appendMessageRow(frag, '(필터와 일치하는 항목 없음)', depth + 1); return; }
      for (const item of visible) {
        appendNode(frag, `${path}/${item.name}`, item.name, depth + 1, item.mediaType === 'directory');
      }
    }
  }

  async function loadChildren(path) {
    const st = getNodeState(path);
    st.loading = true;
    st.error = null;
    renderTree();
    const res = await Api.call('list_registry_resources', currentInstance.id, path);
    st.loading = false;
    if (res.success) st.items = res.items;
    else st.error = res.error || '조회 실패';
    renderTree();
  }

  function toggleNode(path) {
    const st = getNodeState(path);
    st.expanded = !st.expanded;
    if (st.expanded && st.items === null) loadChildren(path);
    else renderTree();
  }

  function selectLeaf(path) {
    selectedLeafPath = path;
    document.getElementById('registry-path').value = path;
    doLoad();
    renderTree();
  }

  // ── 이름 포함 검색 ────────────────────────────────────────────────────
  // Management API의 searchKey 파라미터는 이름을 "포함"하는 항목을 찾는 게 아니라
  // 정확한 이름 하나를 직접 조회하는 용도라(디컴파일로 확인, MI_MANAGEMENT_API.md
  // 5-3 참고) 서버에 맡길 수 없다. 대신 백엔드가 트리를 직접 훑어서 필터링한다.

  function clearSearchResults() {
    const el = document.getElementById('registry-search-results');
    el.style.display = 'none';
    el.innerHTML = '';
  }

  async function doSearch() {
    if (!guardInstance()) return;
    const keyword = document.getElementById('registry-search').value.trim();
    if (!keyword) { clearSearchResults(); return; }
    const resultsEl = document.getElementById('registry-search-results');
    resultsEl.style.display = '';
    resultsEl.innerHTML = '<div class="checklist-item hint">검색 중...</div>';
    const res = await Api.call('search_registry_resources', currentInstance.id, TREE_ROOT, keyword);
    if (!res.success) {
      resultsEl.innerHTML = `<div class="checklist-item hint">검색 실패: ${UI.esc(res.error || '')}</div>`;
      return;
    }
    if (!res.matches.length) {
      resultsEl.innerHTML = '<div class="checklist-item hint">일치하는 항목이 없습니다.</div>';
      return;
    }
    resultsEl.innerHTML = '';
    for (const m of res.matches) {
      const row = document.createElement('div');
      row.className = 'checklist-item';
      row.style.cursor = 'pointer';
      row.textContent = (m.is_dir ? '📁 ' : '　') + m.path;
      row.addEventListener('click', () => { if (!m.is_dir) selectLeaf(m.path); });
      resultsEl.appendChild(row);
    }
    if (res.truncated) {
      const note = document.createElement('div');
      note.className = 'checklist-item hint';
      note.textContent = '결과가 많아 일부만 표시됩니다. 검색어를 더 구체적으로 입력해보세요.';
      resultsEl.appendChild(note);
    }
  }

  function refreshParentOf(path) {
    const idx = path.lastIndexOf('/');
    if (idx < 0) return;
    const parent = path.substring(0, idx);
    const st = treeState[parent];
    if (st && st.expanded) { st.items = null; loadChildren(parent); }
  }

  function resetTree() {
    treeState = {};
    selectedLeafPath = null;
    if (!currentInstance || !currentInstance.type.startsWith('MI_')) { renderTree(); return; }
    const root = getNodeState(TREE_ROOT);
    root.expanded = true;
    loadChildren(TREE_ROOT);
  }

  function applyInstance(inst) {
    currentInstance = inst;
    setLabel();
    setStatus('-');
    document.getElementById('registry-content').value = '';
    document.getElementById('registry-search').value = '';
    treeFilter = '';
    clearSearchResults();
    resetTree();
  }

  /* 좌측 트리에서 서버그룹/서버/그룹/인스턴스 중 무엇을 클릭하든 그 깊이까지 4단
   * 셀렉트를 맞춘다 — 지정 안 된 하위 단계는 wireInstanceCascade가 첫 항목으로 채운다. */
  function onTreeNodeSelected(e) {
    instSelect.refresh(UI.treeNodeToChain(e.detail.kind, e.detail.data));
  }

  /* 슬래시 없는 파일명만 입력하면 인스턴스에 설정된 JDBC 레지스트리 경로를 붙여서
   * 완성한다 (예: "DeleteInsert.yaml" -> "registry/config/jdbc/DeleteInsert.yaml"). */
  function resolvePath(raw) {
    if (raw.includes('/')) return raw;
    const base = (currentInstance && currentInstance.jdbc_registry_path) || 'registry/config/jdbc';
    return `${base.replace(/\/+$/, '')}/${raw}`;
  }

  async function doLoad() {
    if (!guardInstance()) return;
    const raw = getPath();
    if (!raw) { UI.toast('경로를 입력하세요.', 'error'); return; }
    const path = resolvePath(raw);
    if (path !== raw) document.getElementById('registry-path').value = path;
    setStatus('조회 중...');
    const res = await Api.call('get_registry_resource', currentInstance.id, path);
    if (!res.success) { setStatus(`조회 실패: ${res.error || ''}`); return; }
    if (!res.exists) {
      document.getElementById('registry-content').value = '';
      setStatus('존재하지 않는 경로입니다. 내용을 입력하고 저장하면 새로 생성됩니다.');
      return;
    }
    document.getElementById('registry-content').value = res.content;
    setStatus('존재함 (저장하면 수정됩니다)');
  }

  /* 대상이 PROD 인스턴스면 강렬한 경고 팝업, 아니면 일반 확인 팝업. */
  async function confirmProdAware(actionLabel, fallbackMsg, fallbackTitle) {
    if (currentInstance.environment === 'PROD') return UI.confirmProd(actionLabel, [currentInstance], 1);
    return UI.confirm(fallbackMsg, fallbackTitle);
  }

  async function doSave() {
    if (!guardInstance()) return;
    const path = getPath();
    if (!path) { UI.toast('경로를 입력하세요.', 'error'); return; }
    const content = document.getElementById('registry-content').value;
    const ok = await confirmProdAware(`레지스트리 저장 (${path})`,
      `[${currentInstance.name}]의 [${path}]에 저장하시겠습니까?`, '저장 확인');
    if (!ok) return;
    setStatus('저장 중...');
    const res = await Api.call('save_registry_resource', currentInstance.id, path, content);
    if (!res.success) { setStatus(`저장 실패: ${res.error || ''}`); UI.toast('저장 실패', 'error'); return; }
    const modeLabel = res.mode === 'CREATE' ? '생성됨' : '수정됨';
    setStatus(`저장 완료 (${modeLabel})`);
    UI.toast('저장했습니다.', 'success');
    refreshParentOf(path);
  }

  async function doDelete() {
    if (!guardInstance()) return;
    const path = getPath();
    if (!path) { UI.toast('경로를 입력하세요.', 'error'); return; }
    const ok = await confirmProdAware(`레지스트리 삭제 (${path})`,
      `[${currentInstance.name}]에서 [${path}]을(를) 삭제하시겠습니까?`, '삭제 확인');
    if (!ok) return;
    const res = await Api.call('delete_registry_resource', currentInstance.id, path);
    if (!res.success) { setStatus(`삭제 실패: ${res.error || ''}`); UI.toast('삭제 실패', 'error'); return; }
    document.getElementById('registry-content').value = '';
    setStatus('삭제 완료');
    UI.toast('삭제했습니다.', 'success');
    if (path === selectedLeafPath) selectedLeafPath = null;
    refreshParentOf(path);
  }

  function init() {
    document.getElementById('btn-registry-load').addEventListener('click', doLoad);
    document.getElementById('btn-registry-save').addEventListener('click', doSave);
    document.getElementById('btn-registry-delete').addEventListener('click', doDelete);
    document.getElementById('btn-registry-tree-refresh').addEventListener('click', resetTree);
    document.getElementById('btn-registry-search').addEventListener('click', doSearch);
    document.getElementById('btn-registry-search-clear').addEventListener('click', () => {
      document.getElementById('registry-search').value = '';
      treeFilter = '';
      clearSearchResults();
      renderTree();
    });
    document.getElementById('registry-search').addEventListener('input', () => {
      treeFilter = document.getElementById('registry-search').value.trim().toLowerCase();
      renderTree();
    });
    document.getElementById('registry-search').addEventListener('keydown', (e) => {
      if (e.key === 'Enter') doSearch();
    });
    window.addEventListener('tree-node-selected', onTreeNodeSelected);

    instSelect = UI.wireInstanceCascade(
      document.getElementById('registry-sg-select'), document.getElementById('registry-type-select'),
      document.getElementById('registry-server-select'),
      document.getElementById('registry-group-select'), document.getElementById('registry-instance-select'),
      applyInstance);
    window.addEventListener('instances-changed', () => instSelect.refresh(currentInstance ? currentInstance.id : null));

    setLabel();
    renderTree();
  }

  return { init };
})();
