/* ============ 交互原型路由（模拟真实软件导航） ============ */
/* 用法：在可点击元素上加 data 属性，无需写 JS：
     data-goto="view-id"    跳转到某屏（压栈）
     data-back               返回上一屏（弹栈）
     data-open="overlay-id"  打开弹窗/底部弹层
     data-close              关闭当前弹窗/弹层
     data-toast="文案"       弹出轻提示
   弹窗里的确认按钮通常 = data-close + data-goto 或 data-toast 组合。
*/
(function () {
  const stack = [];

  function show(id) {
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    const v = document.getElementById(id);
    if (v) { v.classList.add('active'); window.scrollTo(0, 0); }
  }
  function current() { const c = document.querySelector('.view.active'); return c ? c.id : null; }

  function goto(id) {
    const cur = current();
    if (cur) stack.push(cur);
    closeAllOverlay();
    show(id);
  }
  function back() {
    closeAllOverlay();
    const prev = stack.pop();
    if (prev) show(prev);
  }
  function open(id) { const el = document.getElementById(id); if (el) el.classList.add('active'); }
  function close(id) { const el = document.getElementById(id); if (el) el.classList.remove('active'); }
  function closeAllOverlay() { document.querySelectorAll('.overlay.active').forEach(e => e.classList.remove('active')); }

  function toast(msg) {
    let t = document.getElementById('app-toast');
    if (!t) {
      t = document.createElement('div');
      t.id = 'app-toast'; t.className = 'toast';
      document.querySelector('.app').appendChild(t);
    }
    t.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M20 6 9 17l-5-5"/></svg>' + msg;
    t.classList.add('active');
    clearTimeout(t._h);
    t._h = setTimeout(() => t.classList.remove('active'), 2000);
  }

  // 绑定所有 data-* 交互
  function wire() {
    const on = (sel, fn) => document.querySelectorAll(sel).forEach(el => {
      if (el._wired) return; el._wired = true;
      el.addEventListener('click', e => { e.preventDefault(); fn(el); });
    });
    on('[data-goto]', el => goto(el.getAttribute('data-goto')));
    on('[data-back]', () => back());
    on('[data-open]', el => open(el.getAttribute('data-open')));
    on('[data-close]', () => closeAllOverlay());
    on('[data-toast]', el => { toast(el.getAttribute('data-toast')); });
  }

  document.addEventListener('DOMContentLoaded', () => {
    wire();
    const first = document.querySelector('.view');
    if (first) first.classList.add('active');
  });

  window.App = { goto, back, open, close, closeAllOverlay, toast, wire, show };
})();
