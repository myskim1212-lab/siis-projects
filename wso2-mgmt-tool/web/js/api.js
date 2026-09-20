/* window.pywebview.api 래퍼 — pywebviewready 대기 */
const Api = (() => {
  let readyPromise = null;

  function whenReady() {
    if (readyPromise) return readyPromise;
    readyPromise = new Promise((resolve) => {
      if (window.pywebview && window.pywebview.api) {
        resolve();
        return;
      }
      window.addEventListener('pywebviewready', () => resolve());
    });
    return readyPromise;
  }

  async function call(method, ...args) {
    await whenReady();
    return window.pywebview.api[method](...args);
  }

  return { call, whenReady };
})();
