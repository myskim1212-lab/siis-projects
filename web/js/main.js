/* 탭 전환 + 초기화 */
function switchTab(name) {
  document.querySelectorAll('.tab-btn').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  document.querySelectorAll('.tab-panel').forEach((p) => p.classList.toggle('active', p.id === `tab-${name}`));
  if (name === 'deploy') Deploy.reload();
  if (name === 'restart') Restart.reload();
  if (name === 'monitor') Monitor.reload();
  if (name === 'audit') Audit.refresh();
}

document.querySelectorAll('.tab-btn').forEach((btn) => {
  btn.addEventListener('click', () => switchTab(btn.dataset.tab));
});

Instances.init();
Deploy.init();
Restart.init();
DataSources.init();
Registry.init();
Logs.init();
Monitor.init();
Audit.init();
