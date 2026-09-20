/* 감사 로그 패널 — 검색/필터/CSV 내보내기 */
const Audit = (() => {
  let rows = [];

  function instanceNames(idsCsv) {
    if (!idsCsv) return '';
    const map = new Map(Instances.getFlatInstances().map((i) => [String(i.id), i]));
    return idsCsv.split(',').filter(Boolean).map((id) => {
      const inst = map.get(id.trim());
      return inst ? `[${inst.group_name}] ${inst.name}` : id.trim();
    }).join(', ');
  }

  async function refresh() {
    rows = await Api.call('get_audit_logs', 2000, '', '');
    applyFilter();
  }

  function applyFilter() {
    const action = document.getElementById('audit-filter-action').value;
    const instFilter = document.getElementById('audit-filter-instance').value.trim().toLowerCase();
    const kwFilter = document.getElementById('audit-filter-keyword').value.trim().toLowerCase();

    const visible = rows.filter((r) => {
      if (action && r.action !== action) return false;
      if (instFilter) {
        const hay = `${instanceNames(r.instance_ids)} ${r.target_name || ''}`.toLowerCase();
        if (!hay.includes(instFilter)) return false;
      }
      const combined = `${r.result || ''} ${r.detail || ''}`.toLowerCase();
      if (kwFilter && !combined.includes(kwFilter)) return false;
      return true;
    });
    render(visible);
  }

  function resultClass(result) {
    if (result === 'SUCCESS') return 'result-success';
    if (result === 'FAILED') return 'result-failed';
    if (result === 'PARTIAL') return 'result-partial';
    return '';
  }

  function render(visible) {
    const tbody = document.querySelector('#audit-table tbody');
    tbody.innerHTML = '';
    for (const r of visible) {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>${UI.esc(r.timestamp || '')}</td>
        <td>${UI.esc(instanceNames(r.instance_ids))}</td>
        <td>${UI.esc(r.action || '')}</td>
        <td>${UI.esc(r.target_name || '')}</td>
        <td class="${resultClass(r.result)}">${UI.esc(r.result || '')}</td>
        <td>${UI.esc(r.detail || '')}</td>`;
      tbody.appendChild(tr);
    }
  }

  async function exportCsv() {
    if (!rows.length) { UI.toast('내보낼 데이터가 없습니다.'); return; }
    const res = await Api.call('export_audit_csv', rows);
    if (res.success) UI.toast(`저장됨: ${res.path}`, 'success');
    else if (res.error !== 'Cancelled') UI.toast(`내보내기 실패: ${res.error}`, 'error');
  }

  function init() {
    document.getElementById('audit-filter-action').addEventListener('change', applyFilter);
    document.getElementById('audit-filter-instance').addEventListener('input', applyFilter);
    document.getElementById('audit-filter-keyword').addEventListener('input', applyFilter);
    document.getElementById('btn-audit-refresh').addEventListener('click', refresh);
    document.getElementById('btn-audit-csv').addEventListener('click', exportCsv);
    window.addEventListener('instances-changed', () => { if (rows.length) applyFilter(); });
  }

  return { init, refresh };
})();
