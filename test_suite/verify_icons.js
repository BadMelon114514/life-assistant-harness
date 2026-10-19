// 验证 SVG 图标渲染：导航/头像/按钮/标题/暗色主题
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9233;
const PROFILE = path.join(__dirname, '.icon-check');
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
    console.log('导航 SVG 图标数:', await ev('document.querySelectorAll(".sidebar nav .ic").length'));
    console.log('导航残留 emoji:', await ev('(/[\u{1F300}-\u{1FAFF}]/u.test(document.querySelector(".sidebar").textContent))'));
    console.log('头像 SVG:', await ev('(() => { const a = document.querySelector(".msg .avatar .ic, #chatBox .avatar .ic"); return !!a; })()'));
    console.log('stat 图标 SVG:', await ev('document.querySelectorAll(".stat-icon .ic").length'));
    console.log('h1 图标 SVG:', await ev('document.querySelectorAll(".page-header h1 .ic").length'));
    console.log('主题按钮 SVG:', await ev('!!document.querySelector("#themeToggle .ic")'));
    // 切换暗色
    await ev('document.documentElement.dataset.theme = "dark"');
    await sleep(400);
    console.log('暗色下主题按钮 SVG:', await ev('!!document.querySelector("#themeToggle .ic")'));
    const shot = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(__dirname, 'icons_style.png'), Buffer.from(shot.data, 'base64'));
    console.log('screenshot saved');
  } catch (e) {
    console.log('CHECK ERROR:', e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    process.exit(0);
  }
})();