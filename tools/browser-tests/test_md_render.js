// 真实浏览器验证：聊天助手回复 Markdown 渲染
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9223;
const PROFILE = path.join(__dirname, '.edge-test-profile-md');

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
    if (!hasBox) throw new Error('无 chatBox');

    // 发送含 Markdown 的请求（要求列表+加粗+标题）
    await evalJs(`document.getElementById('chatMsg').value='用 Markdown 格式介绍智能家居的三大优势，包含标题、列表和加粗'; sendChat();`);
    console.log('[2] 已发送，等待回复完成...');
    let done = false, mdHtml = '', hasMdRender = false, hasStrong = false, hasLi = false;
    for (let i = 0; i < 100; i++) {
      await new Promise(r => setTimeout(r, 1000));
      mdHtml = await evalJs(`document.querySelector('.msg.bot .md-render') ? document.querySelector('.msg.bot .md-render').innerHTML.slice(0,200) : ''`);
      const botCount = await evalJs(`document.querySelectorAll('.msg.bot').length`);
      const isDone = await evalJs(`(document.querySelectorAll('.msg.bot').length>0 && !document.querySelector('.msg.bot .typing'))`);
      if (mdHtml && (await evalJs(`document.querySelector('.msg.bot .md-render') ? true : false`))) {
        // 检查 markdown 元素
        hasStrong = await evalJs(`!!document.querySelector('.msg.bot .md-render strong, .msg.bot .md-render b')`);
        hasLi = await evalJs(`!!document.querySelector('.msg.bot .md-render li')`);
        hasMdRender = true;
        break;
      }
      if (i > 90) break;
    }
    console.log('[3] Markdown 渲染: md-render=' + hasMdRender);
    console.log('[4] 加粗 <strong> 渲染:', hasStrong);
    console.log('[5] 列表 <li> 渲染:', hasLi);
    console.log('[6] 内容片段:', mdHtml.slice(0, 120));
    const pass = hasMdRender && hasStrong && hasLi;
    console.log('===== ' + (pass ? 'PASS - 聊天回复已适配 Markdown 渲染' : 'FAIL') + ' =====');
  } catch (e) {
    console.error('测试异常:', e.message);
  } finally {
    try { edge.kill(); } catch (e) { }
    process.exit(0);
  }
})();
