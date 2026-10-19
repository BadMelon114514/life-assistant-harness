// local-geo 前端验收：
//  1) 对话"现在几点了？我在哪个城市？" → 回复包含本机时间与定位城市
//  2) MCP 管理页展示 local-geo 条目且 active
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9228;
const PROFILE = path.join(__dirname, '.edge-geo-test');
const BASE = 'http://127.0.0.1:8080';
let pass = 0, fail = 0;
function check(name, ok, detail = '') { if (ok) { pass++; console.log('  [PASS] ' + name + (detail ? ' -- ' + detail : '')); } else { fail++; console.log('  [FAIL] ' + name + (detail ? ' -- ' + detail : '')); } }
(async () => {
  try { fs.rmSync(PROFILE, { recursive: true, force: true }); } catch (e) { }
  const edge = spawn(EDGE, ['--headless=new','--no-sandbox','--disable-gpu','--no-first-run','--remote-debugging-port='+PORT,'--user-data-dir='+PROFILE, BASE + '/'], { stdio: 'ignore' });
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
    console.log('[1] chatBox 就绪');

    // 独立会话
    const sessResp = await (await fetch(BASE + '/api/chat/history/session', { method: 'POST', body: '{}' })).json();
    const sess = sessResp.sessionKey;
    await ev('window.currentSession=' + JSON.stringify(sess) + '; loadChatHistory();', true);
    await sleep(600);

    // ============ 场景1：对话获取本机时间与定位 ============
    console.log('=== 场景1：对话"现在几点了？我在哪个城市？" ===');
    await ev('document.getElementById("chatMsg").value="现在几点了？我在哪个城市？"; sendChat();');
    let done = false;
    for (let i = 0; i < 150; i++) {
      const st = await ev('({abort: !!window.__abortCtrl, typing: !!document.querySelector("#chatBox .typing")})');
      if (!st.abort && !st.typing) { done = true; break; }
      await sleep(1000);
    }
    check('场景1 流式回复完成', done, done ? '' : '150s 内未完成');
    const replyText = await ev('(() => { const els = document.querySelectorAll("#chatBox .msg.bot .bubble"); return els.length ? els[els.length - 1].textContent : ""; })()');
    console.log('    回复: ' + String(replyText).slice(0, 120).replace(/\n/g, ' '));
    check('场景1 回复包含本机时间', /\d{1,2}[:：点]\d{0,2}/.test(replyText) || replyText.includes('时间'), '');
    const hasCity = /(福建|厦门|城市|位于|位置|定位|坐标)/.test(replyText);
    check('场景1 回复包含定位城市（经 local-geo 工具调度）', hasCity, '');

    // ============ 场景2：MCP 管理页 ============
    console.log('=== 场景2：MCP 管理页 ===');
    await ev('document.querySelector(".sidebar nav a[data-page=mcp]").click();');
    await sleep(1500);
    const mcpText = await ev('Array.from(document.querySelectorAll("table tr")).map(tr => tr.textContent).join("\\n")');
    check('场景2 MCP 页包含 local-geo 条目', mcpText.includes('local-geo'), '');
    const mcps = await (await fetch(BASE + '/api/mcp')).json();
    const geo = (mcps || []).find(m => m.name === 'local-geo');
    check('场景2 local-geo 状态 active', geo && geo.status === 'active', 'status=' + (geo && geo.status));

    // 清理测试会话
    await fetch(BASE + '/api/chat/history/session/' + encodeURIComponent(sess), { method: 'DELETE' });
  } catch (e) {
    check('整体执行无异常', false, e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    console.log('local-geo 前端验收汇总: ' + pass + ' PASS / ' + fail + ' FAIL');
    process.exit(fail > 0 ? 1 : 0);
  }
})();