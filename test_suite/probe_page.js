// 快速探针：页面加载后 8 秒 dump 状态
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9224;
const PROFILE = path.join(__dirname, '.edge-probe');
(async () => {
  try { fs.rmSync(PROFILE, { recursive: true, force: true }); } catch (e) { }
  const edge = spawn(EDGE, ['--headless=new','--no-sandbox','--disable-gpu','--no-first-run','--remote-debugging-port='+PORT,'--user-data-dir='+PROFILE,'http://127.0.0.1:8080/'], { stdio: 'ignore' });
  let ws = null;
  try {
    let wsUrl = null;
    for (let i = 0; i < 40 && !wsUrl; i++) {
      try {
        const res = await fetch('http://127.0.0.1:' + PORT + '/json');
        const list = await res.json();
        const page = list.find(t => t.type === 'page');
        if (page) wsUrl = page.webSocketDebuggerUrl;
      } catch (e) { }
      if (!wsUrl) await new Promise(r => setTimeout(r, 500));
    }
    console.log('wsUrl:', wsUrl);
    ws = new WebSocket(wsUrl);
    await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
    let msgId = 0; const pending = new Map();
    ws.onmessage = (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { const p = pending.get(m.id); pending.delete(m.id); m.error ? p.reject(m.error) : p.resolve(m.result); } };
    const send = (method, params = {}) => new Promise((res, rej) => { const id = ++msgId; pending.set(id, { resolve: res, reject: rej }); ws.send(JSON.stringify({ id, method, params })); });
    const ev = async (expr, ap = false) => { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: ap }); return r.result ? r.result.value : undefined; };
    await send('Runtime.enable');
    await send('Page.enable');
    await new Promise(r => setTimeout(r, 8000));
    console.log('href:', await ev('window.location.href'));
    console.log('title:', await ev('document.title'));
    console.log('ready:', await ev('document.readyState'));
    console.log('mainContent:', await ev('!!document.getElementById("mainContent")'));
    console.log('chatBox:', await ev('!!document.getElementById("chatBox")'));
    console.log('bodyLen:', await ev('document.body.innerHTML.length'));
    console.log('bodyHead:', await ev('document.body.innerHTML.slice(0, 300)'));
    console.log('jsError:', await ev('window.__lastErr || "(none)"'));
  } catch (e) {
    console.log('PROBE ERROR:', e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    process.exit(0);
  }
})();