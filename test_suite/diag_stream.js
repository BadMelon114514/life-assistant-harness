// 诊断：发送消息后逐秒打印 abort/typing/bubbles，直到完成（上限 60s）
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9227;
const PROFILE = path.join(__dirname, '.edge-diag');
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
    const ev = async (expr, ap = false) => { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: ap }); if (r.exceptionDetails) throw new Error('JSERR ' + JSON.stringify(r.exceptionDetails).slice(0, 200)); return r.result ? r.result.value : undefined; };
    const sleep = (ms) => new Promise(r => setTimeout(r, ms));
    await send('Runtime.enable');
    for (let i = 0; i < 30; i++) { if (await ev('!!document.getElementById("chatBox")')) break; await sleep(1000); }
    console.log('chatBox ready; sending...');
    await ev('document.getElementById("chatMsg").value="1分钟后提醒我喝水"; sendChat();');
    for (let i = 0; i < 60; i++) {
      await sleep(1000);
      const st = await ev('({abort: !!window.__abortCtrl, typing: !!document.querySelector("#chatBox .typing"), bots: document.querySelectorAll("#chatBox .msg.bot").length, users: document.querySelectorAll("#chatBox .msg.user").length})');
      console.log('t=' + (i + 1) + 's ' + JSON.stringify(st));
      if (!st.abort && !st.typing && st.bots >= 1) { console.log('DONE at ' + (i + 1) + 's'); break; }
    }
  } catch (e) {
    console.log('DIAG ERROR:', e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    process.exit(0);
  }
})();