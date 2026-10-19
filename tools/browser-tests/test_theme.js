// 真实浏览器验证深色模式
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9224;
const PROFILE = path.join(__dirname, '.edge-test-theme');

async function waitForCdp(timeoutMs = 30000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json`);
      const list = await res.json();
      const page = list && list.find(t => t.type === 'page');
      if (page) return page.webSocketDebuggerUrl;
    } catch (e) { }
    await new Promise(r => setTimeout(r, 400));
  }
  throw new Error('CDP 未就绪');
}

(async () => {
  try { fs.rmSync(PROFILE, { recursive: true, force: true }); } catch (e) { }
  const edge = spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run',
    `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`,
    'http://127.0.0.1:8080/'
  ], { stdio: 'ignore' });
  try {
    const wsUrl = await waitForCdp();
    const ws = new WebSocket(wsUrl);
    await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
    let id = 0;
    const pending = new Map();
    ws.onmessage = e => {
      const m = JSON.parse(e.data);
      if (m.id && pending.has(m.id)) {
        const p = pending.get(m.id); pending.delete(m.id);
        m.error ? p.reject(new Error(JSON.stringify(m.error))) : p.resolve(m.result);
      }
    };
    const send = (method, params = {}) => new Promise((resolve, reject) => {
      const mid = ++id; pending.set(mid, { resolve, reject });
      ws.send(JSON.stringify({ id: mid, method, params }));
    });
    const evalJs = async (expression, awaitPromise = false) => {
      const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise });
      return r.result ? r.result.value : undefined;
    };
    await send('Page.enable'); await send('Runtime.enable');
    let ready = '', href = '';
    for (let i = 0; i < 40; i++) {
      try { ready = await evalJs('document.readyState'); href = await evalJs('window.location.href'); } catch (e) { }
      if (ready === 'complete' && href && href.includes('127.0.0.1')) break;
      await new Promise(r => setTimeout(r, 1000));
    }
    let hasBox = false;
    for (let i = 0; i < 30; i++) { hasBox = await evalJs("!!document.getElementById('chatBox')"); if (hasBox) break; await new Promise(r => setTimeout(r, 1000)); }
    console.log('[1] 页面就绪:', hasBox);

    // 初始主题（无记忆，跟随系统或默认）
    const t0 = await evalJs(`document.documentElement.dataset.theme`);
    const btn0 = await evalJs(`document.getElementById('themeToggle').textContent`);
    console.log('[2] 初始主题:', t0, '按钮:', btn0);

    // 点击切换按钮 → 深色
    await evalJs(`document.getElementById('themeToggle').click()`);
    await new Promise(r => setTimeout(r, 500));
    const t1 = await evalJs(`document.documentElement.dataset.theme`);
    const bg = await evalJs(`getComputedStyle(document.body).backgroundColor`);
    const stored = await evalJs(`localStorage.getItem('theme')`);
    console.log('[3] 切换后主题:', t1, ' body背景:', bg, ' localStorage:', stored);
    console.log('[4] 按钮图标:', await evalJs(`document.getElementById('themeToggle').textContent`));

    // 再切回浅色
    await evalJs(`document.getElementById('themeToggle').click()`);
    await new Promise(r => setTimeout(r, 300));
    console.log('[5] 再切回:', await evalJs(`document.documentElement.dataset.theme`));

    const pass = t1 === 'dark' && stored === 'dark' && t0 !== 'dark';
    console.log('===== ' + (pass ? 'PASS - 深色模式可用' : 'FAIL - t0=' + t0 + ' t1=' + t1) + ' =====');
  } catch (e) {
    console.error('测试异常:', e.message);
  } finally {
    try { edge.kill(); } catch (e) { }
    process.exit(0);
  }
})();
