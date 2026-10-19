// 验证项目页面新风格（harness 化）：提取计算样式
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9232;
const PROFILE = path.join(__dirname, '.style-check');
(async () => {
  try { fs.rmSync(PROFILE, { recursive: true, force: true }); } catch (e) { }
  const edge = spawn(EDGE, ['--headless=new','--no-sandbox','--disable-gpu','--no-first-run','--remote-debugging-port='+PORT,'--user-data-dir='+PROFILE,'http://127.0.0.1:8080/'], { stdio: 'ignore' });
  let ws = null;
  try {
    let wsUrl = null;
    for (let i = 0; i < 40 && !wsUrl; i++) {
      try { const list = await (await fetch('http://127.0.0.1:' + PORT + '/json')).json(); const p = list.find(t => t.type === 'page'); if (p) wsUrl = p.webSocketDebuggerUrl; } catch (e) { }
      if (!wsUrl) await new Promise(r => setTimeout(r, 500));
    }
    ws = new WebSocket(wsUrl);
    await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
    let msgId = 0; const pending = new Map();
    ws.onmessage = (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { const p = pending.get(m.id); pending.delete(m.id); m.error ? p.reject(m.error) : p.resolve(m.result); } };
    const send = (method, params = {}) => new Promise((res, rej) => { const id = ++msgId; pending.set(id, { resolve: res, reject: rej }); ws.send(JSON.stringify({ id, method, params })); });
    const ev = async (expr, ap = false) => { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: ap }); if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails).slice(0, 300)); return r.result ? r.result.value : undefined; };
    const sleep = (ms) => new Promise(r => setTimeout(r, ms));
    await send('Runtime.enable');
    for (let i = 0; i < 30; i++) { if (await ev('!!document.getElementById("chatBox")')) break; await sleep(1000); }
    await sleep(1200);
    console.log('=== 页面（浅色）关键样式 ===');
    console.log(JSON.stringify(await ev('(() => { const gs = (sel) => { const el = document.querySelector(sel); if (!el) return null; const s = getComputedStyle(el); return { bg: s.backgroundColor, color: s.color, radius: s.borderRadius, border: s.border }; }; return { body: gs("body"), sidebar: gs(".sidebar"), navActive: gs(".sidebar nav a.active"), card: gs(".card"), btn: gs(".btn-primary"), input: gs(".chat-input input"), bubbleBot: gs(".msg.bot .bubble"), tag: gs(".tag"), h1: gs(".page-header h1") }; })()'), null, 1));
    console.log('=== 暗色切换 ===');
    await ev('document.documentElement.dataset.theme = "dark"');
    await sleep(600);
    console.log(JSON.stringify(await ev('(() => { const gs = (sel) => { const el = document.querySelector(sel); if (!el) return null; const s = getComputedStyle(el); return { bg: s.backgroundColor, color: s.color }; }; return { body: gs("body"), sidebar: gs(".sidebar"), navActive: gs(".sidebar nav a.active"), card: gs(".card") }; })()'), null, 1));
    const shot = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(__dirname, 'app_new_style.png'), Buffer.from(shot.data, 'base64'));
    console.log('screenshot saved');
  } catch (e) {
    console.log('CHECK ERROR:', e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    process.exit(0);
  }
})();