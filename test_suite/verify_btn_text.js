// 验证联网搜索按钮与测试连接按钮文本正确渲染
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9234;
const PROFILE = path.join(__dirname, '.btn-check');
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
    for (let i = 0; i < 30; i++) { if (await ev('!!document.getElementById("searchToggleBtn")')) break; await sleep(1000); }
    await sleep(800);
    const info = await ev('(() => { const b = document.getElementById("searchToggleBtn"); return { html: b.innerHTML, text: b.textContent.trim(), hasIc: !!b.querySelector(".ic"), hasLiteral: b.innerHTML.includes("${") }; })()');
    console.log('联网搜索按钮:', JSON.stringify(info));
    await ev('toggleSearch();');
    await sleep(600);
    const info2 = await ev('(() => { const b = document.getElementById("searchToggleBtn"); return { text: b.textContent.trim(), hasIc: !!b.querySelector(".ic"), hasLiteral: b.innerHTML.includes("${") }; })()');
    console.log('切换后按钮:', JSON.stringify(info2));
    await ev('document.querySelector(".sidebar nav a[data-page=settings]").click();');
    await sleep(1200);
    const testBtn = await ev('(() => { const btns = Array.from(document.querySelectorAll("button")); const b = btns.find(x => x.textContent.includes("测试连接")); return b ? { text: b.textContent.trim(), html: b.innerHTML.slice(0, 120), hasLiteral: b.innerHTML.includes("${") } : null; })()');
    console.log('测试连接按钮:', JSON.stringify(testBtn));
  } catch (e) {
    console.log('CHECK ERROR:', e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    process.exit(0);
  }
})();