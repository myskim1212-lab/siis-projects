/* 공용 모달 / 토스트 / 이스케이프 유틸 */
const UI = (() => {
  function esc(s) {
    return String(s ?? '').replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
  }

  function toast(message, type = '') {
    let stack = document.querySelector('.toast-stack');
    if (!stack) {
      stack = document.createElement('div');
      stack.className = 'toast-stack';
      document.body.appendChild(stack);
    }
    const el = document.createElement('div');
    el.className = `toast ${type}`.trim();
    el.textContent = message;
    stack.appendChild(el);
    setTimeout(() => el.remove(), 3500);
  }

  function openModal(innerHtml, width, options = {}) {
    const { closeOnBackdrop = true, height, resizable = false } = options;
    const styleParts = [];
    if (width) styleParts.push(`width:${width}px`);
    if (height) styleParts.push(`height:${height}px`);
    if (resizable) styleParts.push('resize:both', 'overflow:auto');
    const styleAttr = styleParts.length ? ` style="${styleParts.join(';')}"` : '';
    const cls = resizable ? 'modal modal-resizable' : 'modal';
    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';
    backdrop.innerHTML = `<div class="${cls}"${styleAttr}>${innerHtml}</div>`;
    document.body.appendChild(backdrop);
    if (closeOnBackdrop) {
      backdrop.addEventListener('click', (e) => {
        if (e.target === backdrop) backdrop.remove();
      });
    }
    return backdrop;
  }

  function closeModal(backdrop) {
    backdrop.remove();
  }

  function confirm(message, title = '확인') {
    return new Promise((resolve) => {
      const backdrop = openModal(`
        <div class="modal-header">${esc(title)}</div>
        <div class="modal-body"><div>${esc(message)}</div></div>
        <div class="modal-footer">
          <button class="btn" data-act="cancel">취소</button>
          <button class="btn btn-primary" data-act="ok">확인</button>
        </div>`, 380);
      const close = (val) => { closeModal(backdrop); resolve(val); };
      backdrop.querySelector('[data-act=cancel]').onclick = () => close(false);
      backdrop.querySelector('[data-act=ok]').onclick = () => close(true);
    });
  }

  function prompt(message, defaultValue = '', title = '입력') {
    return new Promise((resolve) => {
      const backdrop = openModal(`
        <div class="modal-header">${esc(title)}</div>
        <div class="modal-body">
          <div>${esc(message)}</div>
          <input type="text" id="prompt-input" value="${esc(defaultValue)}"
                 style="border:1px solid var(--border);border-radius:6px;padding:8px;font-size:13px;">
        </div>
        <div class="modal-footer">
          <button class="btn" data-act="cancel">취소</button>
          <button class="btn btn-primary" data-act="ok">확인</button>
        </div>`, 380);
      const input = backdrop.querySelector('#prompt-input');
      input.focus();
      input.select();
      const close = (val) => { closeModal(backdrop); resolve(val); };
      backdrop.querySelector('[data-act=cancel]').onclick = () => close(null);
      backdrop.querySelector('[data-act=ok]').onclick = () => close(input.value);
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') close(input.value);
        if (e.key === 'Escape') close(null);
      });
    });
  }

  function selectDialog(message, options, title = '선택') {
    return new Promise((resolve) => {
      const opts = options.map((o) => `<option value="${esc(o.value)}">${esc(o.label)}</option>`).join('');
      const backdrop = openModal(`
        <div class="modal-header">${esc(title)}</div>
        <div class="modal-body">
          <div>${esc(message)}</div>
          <select id="select-input">${opts}</select>
        </div>
        <div class="modal-footer">
          <button class="btn" data-act="cancel">취소</button>
          <button class="btn btn-primary" data-act="ok">확인</button>
        </div>`, 380);
      const sel = backdrop.querySelector('#select-input');
      const close = (val) => { closeModal(backdrop); resolve(val); };
      backdrop.querySelector('[data-act=cancel]').onclick = () => close(null);
      backdrop.querySelector('[data-act=ok]').onclick = () => close(sel.value);
    });
  }

  /* 운영(PROD) 인스턴스 대상 작업 — 강한 시각적 경고 팝업 (타이핑 확인 없이 클릭 한 번) */
  // 운영(PROD) 인스턴스가 대상에 하나라도 있으면, 버튼을 누르기 전에 그 인스턴스가
  // 속한 서버그룹 이름을 똑같이 입력해야만 실행 버튼이 눌리게 한다 — "운영"처럼
  // 고정된 문구가 아니라 실제 서버그룹명을 쓰게 해서, 지금 어느 서버그룹을 건드리는
  // 건지 한 번 더 눈으로 확인하고 입력하게 만든다. 대상이 서버그룹 하나에 다 속해
  // 있으면 그 이름 그대로, 드물게 여러 서버그룹에 걸쳐 있으면(예: 필터를 안 거치고
  // 직접 모은 경우) 전부 쉼표로 이어붙인 걸 그대로 입력해야 한다.
  function requiredConfirmPhrase(instances) {
    const names = Array.from(new Set(instances.map((i) => i.server_group_name).filter(Boolean))).sort();
    return names.join(', ');
  }

  function typeToConfirmHtml(phrase) {
    return `
      <div class="field-row" style="margin-top:10px">
        <label style="width:auto">계속하려면 서버그룹명 "${esc(phrase)}"을(를) 입력</label>
        <input type="text" id="confirm-type-input" autocomplete="off" placeholder="${esc(phrase)}">
      </div>`;
  }

  // 실행 버튼을 처음부터 비활성화해두고, 입력값이 정확히 일치할 때만 눌리게 한다.
  function wireTypeToConfirm(backdrop, phrase) {
    const input = backdrop.querySelector('#confirm-type-input');
    const okBtn = backdrop.querySelector('[data-act=ok]');
    if (!input || !okBtn) return;
    okBtn.disabled = true;
    input.addEventListener('input', () => {
      okBtn.disabled = input.value.trim() !== phrase;
    });
    input.focus();
  }

  function confirmProd(actionLabel, prodInstances, totalCount) {
    return new Promise((resolve) => {
      const names = prodInstances.map((i) => i.name).join(', ');
      const phrase = requiredConfirmPhrase(prodInstances) || '확인';
      const backdrop = openModal(`
        <div class="modal-header danger-header">⚠️ 운영(PROD) 인스턴스 작업 확인</div>
        <div class="modal-body">
          <div class="danger-banner">
            선택한 ${totalCount}개 인스턴스 중 <b>${prodInstances.length}개가 운영(PROD)</b> 환경입니다.
            <div class="danger-instance-list">${esc(names)}</div>
            <b>${esc(actionLabel)}</b>을(를) 실행하면 실제 서비스에 영향을 줄 수 있습니다. 계속하시겠습니까?
          </div>
          ${typeToConfirmHtml(phrase)}
        </div>
        <div class="modal-footer">
          <button class="btn" data-act="cancel">취소</button>
          <button class="btn btn-danger" data-act="ok">${esc(actionLabel)} 실행</button>
        </div>`, 440);
      backdrop.querySelector('.modal').classList.add('modal-danger');
      wireTypeToConfirm(backdrop, phrase);

      const close = (val) => { closeModal(backdrop); resolve(val); };
      backdrop.querySelector('[data-act=cancel]').onclick = () => close(false);
      backdrop.querySelector('[data-act=ok]').onclick = () => close(true);
    });
  }

  /* 배포/재시작 등에서 "무슨 작업을, 어느 그룹의 어느 인스턴스에" 실행하는지
   * 한눈에 구분되도록 그룹별로 묶어 보여주는 확인창. 대상 중 하나라도 PROD면
   * confirmProd와 같은 강한 경고 스타일로 전환되지만, 목록은 항상 전체 대상을
   * 그룹별로 보여준다(PROD만 따로 추리지 않음 — 어떤 게 PROD인지는 배지로 표시).
   *
   * group_name만으로 묶으면(예전 방식) 서로 다른 서버 밑에 같은 이름의 그룹이 있을 때
   * (예: GATEWAY1과 GATEWAY2 둘 다 "MI43") 완전히 다른 인스턴스인데도 "MI43 (4개)"
   * 하나로 뭉뚱그려져서, 실제로는 서버가 다른 MI01/MI02가 이름만 보고는 구분이 안 되는
   * 문제가 있었다 — 서버그룹/서버까지 포함한 전체 경로로 묶어서 반드시 구분되게 한다. */
  function groupInstancesByGroupName(instances) {
    const map = new Map();
    for (const inst of instances) {
      const key = `${inst.server_group_name || ''} / ${inst.server_name || ''} / ${inst.group_name || '(그룹 없음)'}`;
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(inst);
    }
    return map;
  }

  function renderConfirmTargetList(instances) {
    const grouped = groupInstancesByGroupName(instances);
    const groups = Array.from(grouped.entries()).map(([groupName, insts]) => `
      <div class="confirm-group">
        <div class="confirm-group-name">${esc(groupName)} <span class="hint">(${insts.length}개)</span></div>
        <div class="confirm-inst-list">
          ${insts.map((i) => {
            const isProd = i.environment === 'PROD';
            const envClass = isProd ? 'env-prod' : 'env-dev';
            const envLabel = isProd ? '운영' : '개발';
            return `<span class="confirm-inst-chip"><span class="env-badge ${envClass}">${envLabel}</span> ${esc(i.name)}</span>`;
          }).join('')}
        </div>
      </div>`).join('');
    return `<div class="confirm-group-list">${groups}</div>`;
  }

  function confirmAction(actionLabel, instances, extraHtml = '') {
    return new Promise((resolve) => {
      const prodCount = instances.filter((i) => i.environment === 'PROD').length;
      const isDanger = prodCount > 0;
      const header = isDanger
        ? `<div class="modal-header danger-header">⚠️ ${esc(actionLabel)}</div>`
        : `<div class="modal-header">${esc(actionLabel)}</div>`;
      const warnLine = isDanger
        ? `<div class="danger-banner">대상 중 <b>${prodCount}개가 운영(PROD)</b> 환경입니다. 실제 서비스에 영향을 줄 수 있습니다.</div>`
        : '';
      const phrase = requiredConfirmPhrase(instances) || '확인';
      const backdrop = openModal(`
        ${header}
        <div class="modal-body">
          ${warnLine}
          ${extraHtml}
          <div class="confirm-summary">대상 인스턴스 — 총 ${instances.length}개</div>
          ${renderConfirmTargetList(instances)}
          ${isDanger ? typeToConfirmHtml(phrase) : ''}
        </div>
        <div class="modal-footer">
          <button class="btn" data-act="cancel">취소</button>
          <button class="btn ${isDanger ? 'btn-danger' : 'btn-primary'}" data-act="ok">${esc(actionLabel)} 실행</button>
        </div>`, 480, { closeOnBackdrop: false });
      if (isDanger) {
        backdrop.querySelector('.modal').classList.add('modal-danger');
        wireTypeToConfirm(backdrop, phrase);
      }

      const close = (val) => { closeModal(backdrop); resolve(val); };
      backdrop.querySelector('[data-act=cancel]').onclick = () => close(false);
      backdrop.querySelector('[data-act=ok]').onclick = () => close(true);
    });
  }

  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (e) {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.focus();
      ta.select();
      let ok = false;
      try { ok = document.execCommand('copy'); } catch (e2) { ok = false; }
      ta.remove();
      return ok;
    }
  }

  /* 모노스페이스 폰트 기준 한글/CJK는 2칸, 그 외는 1칸으로 계산 (붙여넣었을 때 정렬 맞추기 위함) */
  function displayWidth(s) {
    let w = 0;
    for (const ch of String(s)) {
      const c = ch.codePointAt(0);
      const wide = (c >= 0x1100 && c <= 0x115F) || (c >= 0x2E80 && c <= 0xA4CF)
        || (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0xF900 && c <= 0xFAFF)
        || (c >= 0xFF00 && c <= 0xFF60) || (c >= 0xFFE0 && c <= 0xFFE6);
      w += wide ? 2 : 1;
    }
    return w;
  }

  function padDisplay(s, target) {
    return s + ' '.repeat(Math.max(0, target - displayWidth(s)));
  }

  /* 라벨 폭을 맞춰 보기 좋게 정렬한 key: value 텍스트 블록 생성 */
  function formatRowsForCopy(title, rows) {
    const maxLabel = Math.max(...rows.map(([k]) => displayWidth(k)));
    const lines = rows.map(([k, v]) => `${padDisplay(k, maxLabel)} : ${v}`);
    const sepLen = Math.max(displayWidth(title), maxLabel + 4);
    return `${title}\n${'─'.repeat(sepLen)}\n${lines.join('\n')}`;
  }

  /* key/value 목록을 보여주는 상세 모달 — 상단에 전체 복사 버튼 하나만 둔다.
   * extraButtons: [{label, onClick}] — 푸터에 "닫기" 앞쪽으로 추가되는 커스텀 버튼
   * (예: 특정 값만 캐시 무시하고 새로고침하는 액션). 클릭하면 모달을 닫고 onClick 실행. */
  function detailModal(title, rows, width = 520, extraButtons = []) {
    const visibleRows = rows.filter(([, v]) => v != null && v !== '');
    const extraButtonsHtml = extraButtons
      .map((b, i) => `<button class="btn btn-sm" data-extra-act="${i}">${esc(b.label)}</button>`)
      .join('');
    const backdrop = openModal(`
      <div class="modal-header">
        <span>${esc(title)}</span>
        <button class="btn btn-sm" data-act="copy-all">📋 복사</button>
      </div>
      <div class="modal-body">
        ${visibleRows.map(([k, v]) => `
          <div class="field-row">
            <label>${esc(k)}</label>
            <div class="detail-value" style="white-space:pre-wrap">${esc(v)}</div>
          </div>`).join('')}
      </div>
      <div class="modal-footer">
        ${extraButtonsHtml}
        <button class="btn btn-primary" data-act="close">닫기</button>
      </div>`, width);

    backdrop.querySelector('[data-act=copy-all]').onclick = async () => {
      const text = formatRowsForCopy(title, visibleRows);
      const ok = await copyText(text);
      toast(ok ? '복사됨' : '복사 실패 (직접 드래그해서 복사해주세요)', ok ? 'success' : 'error');
    };
    extraButtons.forEach((b, i) => {
      backdrop.querySelector(`[data-extra-act="${i}"]`).onclick = () => { closeModal(backdrop); b.onClick(); };
    });
    backdrop.querySelector('[data-act=close]').onclick = () => closeModal(backdrop);
    return backdrop;
  }

  /* 우클릭 컨텍스트 메뉴. items: [{label, danger?, onClick}] */
  function contextMenu(x, y, items) {
    document.querySelectorAll('.context-menu').forEach((el) => el.remove());

    const menu = document.createElement('div');
    menu.className = 'context-menu';
    menu.style.left = `${x}px`;
    menu.style.top = `${y}px`;
    for (const item of items) {
      const el = document.createElement('div');
      el.className = `context-menu-item${item.danger ? ' danger' : ''}`;
      el.textContent = item.label;
      el.addEventListener('click', () => { close(); item.onClick(); });
      menu.appendChild(el);
    }
    document.body.appendChild(menu);

    // 메뉴가 화면 오른쪽/아래로 넘치면 반대쪽으로 붙인다
    const rect = menu.getBoundingClientRect();
    if (rect.right > window.innerWidth) menu.style.left = `${Math.max(0, x - rect.width)}px`;
    if (rect.bottom > window.innerHeight) menu.style.top = `${Math.max(0, y - rect.height)}px`;

    function close() {
      menu.remove();
      document.removeEventListener('click', onOutsideClick);
      document.removeEventListener('contextmenu', onOutsideClick);
      document.removeEventListener('keydown', onKeyDown);
    }
    function onOutsideClick(e) {
      if (!menu.contains(e.target)) close();
    }
    function onKeyDown(e) {
      if (e.key === 'Escape') close();
    }
    // 이 우클릭 자체가 바로 닫아버리지 않도록 다음 tick에 리스너 등록
    setTimeout(() => {
      document.addEventListener('click', onOutsideClick);
      document.addEventListener('contextmenu', onOutsideClick);
      document.addEventListener('keydown', onKeyDown);
    }, 0);

    return menu;
  }

  /* 서버그룹>서버타입>서버>그룹>인스턴스 연쇄 <select> 배선 — 데이터소스/레지스트리/
   * 로그뷰어처럼 "정확히 하나의 인스턴스"가 필요한 화면에서 좌측 트리 클릭과 완전히
   * 동기화된다. 서버타입은 서버의 운영환경(개발/운영/운영개발)으로 서버 단을 한 번 더
   * 좁히는 보조 필터다. sgSel/typeSel/serverSel/groupSel/instSel은 실제 <select>
   * 엘리먼트, onChange(inst|null)는 선택이 바뀔 때마다(사용자 조작이든 refresh()에
   * 의한 것이든) 호출된다. */
  function wireInstanceCascade(sgSel, typeSel, serverSel, groupSel, instSel, onChange) {
    // preferId를 명시적으로 안 주면(=사용자가 아직 아무것도 안 골랐거나, 상위 단계를
    // 막 바꿔서 하위를 다시 채우는 상황) 예전엔 <select>가 첫 항목을 자동으로 골라서
    // 처음 화면을 열자마자 특정 인스턴스가 이미 선택된 것처럼 보였다 — 배포/재시작/
    // 데이터소스 필터와 마찬가지로 "직접 고르기 전엔 아무것도 선택 안 됨"이 기본이
    //되도록, 후보가 있어도 맨 앞에 빈 플레이스홀더를 넣고 그걸 기본값으로 둔다.
    function fillOptions(sel, items, labelFn, preferId, placeholderLabel) {
      sel.innerHTML = `<option value="">${esc(placeholderLabel)}</option>` +
        items.map((it) => `<option value="${it.id}">${esc(labelFn(it))}</option>`).join('');
      if (preferId != null && items.some((it) => String(it.id) === String(preferId))) {
        sel.value = String(preferId);
      }
    }
    function currentServerGroup() {
      return (typeof Instances !== 'undefined' ? Instances.getServerGroups() : [])
        .find((sg) => String(sg.id) === sgSel.value);
    }
    function currentServer() {
      const sg = currentServerGroup();
      return sg ? sg.servers.find((s) => String(s.id) === serverSel.value) : null;
    }
    function currentGroup() {
      const s = currentServer();
      return s ? s.groups.find((g) => String(g.id) === groupSel.value) : null;
    }
    function currentInstance() {
      const g = currentGroup();
      return g ? g.instances.find((i) => String(i.id) === instSel.value) || null : null;
    }

    function populateSg(preferId) {
      const list = typeof Instances !== 'undefined' ? Instances.getServerGroups() : [];
      fillOptions(sgSel, list, (sg) => sg.name, preferId, list.length ? '서버그룹 선택...' : '(서버그룹 없음)');
    }
    function populateServer(preferId) {
      const sg = currentServerGroup();
      const typeVal = typeSel.value;
      const list = (sg ? sg.servers : []).filter((s) => !typeVal || s.environment === typeVal);
      fillOptions(serverSel, list, (s) => serverOptionLabel(s), preferId, list.length ? '서버 선택...' : '(서버 없음)');
    }
    function populateGroup(preferId) {
      const s = currentServer();
      const list = s ? s.groups : [];
      fillOptions(groupSel, list, (g) => g.name, preferId, list.length ? '그룹 선택...' : '(그룹 없음)');
    }
    function populateInstance(preferId) {
      const g = currentGroup();
      const list = g ? g.instances : [];
      fillOptions(instSel, list, (i) => `${i.name} (${i.host}:${i.port})`, preferId, list.length ? '인스턴스 선택...' : '(인스턴스 없음)');
    }

    function fireChange() { onChange(currentInstance()); }

    sgSel.addEventListener('change', () => { populateServer(); populateGroup(); populateInstance(); fireChange(); });
    typeSel.addEventListener('change', () => { populateServer(); populateGroup(); populateInstance(); fireChange(); });
    serverSel.addEventListener('change', () => { populateGroup(); populateInstance(); fireChange(); });
    groupSel.addEventListener('change', () => { populateInstance(); fireChange(); });
    instSel.addEventListener('change', fireChange);

    /* 데이터를 다시 불러왔거나 좌측 트리 클릭으로 선택이 바뀌었을 때 호출.
     * 두 가지 형태를 받는다:
     *   - refresh(instanceId) : 기존 방식 — 그 인스턴스가 속한 체인을 역산해 4단을 전부 맞춘다.
     *   - refresh({serverGroupId, serverId, groupId, instanceId}) : 트리에서 서버그룹/서버/그룹만
     *     클릭했을 때처럼 일부 단계만 지정 — 지정 안 된 하위 단계는 그 범위의 첫 항목으로 자동 선택된다
     *     (예: 서버까지만 주어지면 그 서버의 첫 그룹, 첫 인스턴스로 내려감).
     * 인자를 안 주면 전체 목록을 다시 채우되 지금 선택된 값은 가능한 한 그대로 유지한다. */
    function refresh(prefer) {
      let preferSgId = null;
      let preferServerId = null;
      let preferGroupId = null;
      let preferInstanceId = null;
      if (prefer != null && typeof prefer === 'object') {
        preferSgId = prefer.serverGroupId ?? null;
        preferServerId = prefer.serverId ?? null;
        preferGroupId = prefer.groupId ?? null;
        preferInstanceId = prefer.instanceId ?? null;
        typeSel.value = ''; // 트리 클릭은 서버를 직접 지정하므로 서버타입 필터는 초기화
      } else {
        preferInstanceId = prefer ?? null;
      }
      if (preferInstanceId != null && preferSgId == null && typeof Instances !== 'undefined') {
        outer:
        for (const sg of Instances.getServerGroups()) {
          for (const s of sg.servers) {
            for (const g of s.groups) {
              if (g.instances.some((i) => String(i.id) === String(preferInstanceId))) {
                preferSgId = sg.id; preferServerId = s.id; preferGroupId = g.id;
                break outer;
              }
            }
          }
        }
      }
      populateSg(preferSgId);
      populateServer(preferServerId);
      populateGroup(preferGroupId);
      populateInstance(preferInstanceId);
      fireChange();
    }

    return { refresh, getInstance: currentInstance };
  }

  /* 좌측 트리의 'tree-node-selected' 이벤트(detail: {kind, data})를 서버그룹/서버/그룹/
   * 인스턴스 id 체인으로 변환한다 — 클릭한 깊이보다 아래 단계는 null로 남겨서, 그 아래를
   * 어떻게 채울지(전체 필터로 둘지, 첫 항목을 자동 선택할지)는 호출부가 결정하게 한다.
   * 예: 서버를 클릭하면 {serverGroupId, serverId}만 채워지고 groupId/instanceId는 null. */
  /* 화면에 표시된 표를 CSV(엑셀에서 바로 열림)로 저장한다. rows는 이미 문자열로
   * 포맷된 2차원 배열 — 저장 여부(경로 선택 대화상자)는 Python 쪽(export_csv)이
   * 담당하므로, 여기서는 그 결과에 따라 토스트만 띄운다. */
  async function exportCsv(defaultFilename, headers, rows) {
    const res = await Api.call('export_csv', defaultFilename, headers, rows);
    if (!res.success) {
      if (res.error !== 'Cancelled') toast(`다운로드 실패: ${res.error || ''}`, 'error');
      return;
    }
    toast(`다운로드 완료: ${res.path}`);
  }

  function treeNodeToChain(kind, data) {
    if (kind === 'serverGroup') return { serverGroupId: data.id, serverId: null, groupId: null, instanceId: null };
    if (kind === 'server') return { serverGroupId: data.server_group_id, serverId: data.id, groupId: null, instanceId: null };
    if (kind === 'group') return { serverGroupId: data.server_group_id, serverId: data.server_id, groupId: data.id, instanceId: null };
    if (kind === 'instance') return { serverGroupId: data.server_group_id, serverId: data.server_id, groupId: data.group_id, instanceId: data.id };
    return { serverGroupId: null, serverId: null, groupId: null, instanceId: null };
  }

  return {
    esc, toast, openModal, closeModal, confirm, prompt, selectDialog, confirmProd, confirmAction,
    detailModal, contextMenu, wireInstanceCascade, treeNodeToChain, exportCsv,
  };
})();
