// 定时提醒前端验收：
//  1) 对话"1分钟后提醒我喝水" → 回复完成后提醒已入后端队列（SCHEDULE 标记 / MCP 调度路径）
//  2) 0 分钟提醒触发 → 聊天框出现 ⏰ 提醒气泡（WebSocket 推送）且历史有记录
//  3) MCP 管理页展示 reminder 条目
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9226;
const PROFILE = path.join(__dirname, '.edge-reminder-test');
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

    // ============ 场景1：对话设定提醒 ============
    console.log('=== 场景1：对话"1分钟后提醒我喝水" ===');
    await ev('document.getElementById("chatMsg").value="1分钟后提醒我喝水"; sendChat();');
    let done = false;
    for (let i = 0; i < 150; i++) {
      const st = await ev('({abort: !!window.__abortCtrl, typing: !!document.querySelector("#chatBox .typing")})');
      if (!st.abort && !st.typing) { done = true; break; }
      await sleep(1000);
    }
    check('场景1 流式回复完成', done, done ? '' : '150s 内未完成（LLM 慢或异常）');
    const replyText = await ev('(() => { const els = document.querySelectorAll("#chatBox .msg.bot .bubble"); return els.length ? els[els.length - 1].textContent : ""; })()');
    const leftoverMarker = /\[\[SCHEDULE:/.test(replyText);
    check('场景1 回复中无残留 SCHEDULE 标记', !leftoverMarker, leftoverMarker ? replyText.slice(0, 80) : '');
    // 检查提醒是否入队：容忍两条路径（前端 SCHEDULE 标记 → 会话 sess；或 LLM 经 reminder MCP 调度 → default 会话）
    let queued = null;
    for (let i = 0; i < 15; i++) {
      const rows = await (await fetch(BASE + '/api/assistant/schedule')).json();
      const hit = (rows || []).find(x => String(x.instruct || '').includes('喝水') && String(x.title || '').includes('喝水'));
      if (hit) { queued = hit; break; }
      await sleep(1000);
    }
    check('场景1 提醒已入队（SCHEDULE 标记或 reminder MCP 调度路径）', !!queued,
          queued ? 'id=' + queued.id + ' instruct=' + queued.instruct + ' delay=' + queued.delayMinutes + ' session=' + queued.sessionKey : '未找到提醒记录');
    if (queued) {
      await fetch(BASE + '/api/assistant/schedule/' + queued.id, { method: 'DELETE' });
      check('场景1 测试提醒已清理', true, 'cancelled id=' + queued.id);
    }

    // ============ 场景2：触发推送 → 气泡出现 ============
    console.log('=== 场景2：0 分钟提醒触发 → 气泡 + 历史 ===');
    const r2 = await (await fetch(BASE + '/api/assistant/schedule', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ instruct: '站起来活动一下', delayMinutes: 0, title: '气泡验收', session: sess })
    })).json();
    check('场景2 创建 0 分钟提醒', r2.success === true, 'id=' + (r2.reminder && r2.reminder.id));
    let bubble = '';
    for (let i = 0; i < 45; i++) {
      bubble = await ev('(() => { const els = document.querySelectorAll("#chatBox .scheduled-banner"); for (let j = els.length - 1; j >= 0; j--) { const t = els[j].textContent || ""; if (t.includes("气泡验收") || t.includes("站起来")) return t; } return ""; })()');
      if (bubble) break;
      await sleep(1000);
    }
    check('场景2 聊天框出现本提醒的气泡（WebSocket 推送）', !!bubble, 'banner=' + bubble);
    await sleep(2000);
    // 历史记录验证（后端触发时写入，前端不再重复保存）
    const h2 = await (await fetch(BASE + '/api/chat/history?session=' + sess)).json();
    const remindMsgs = (h2 || []).filter(m => m.role === 'assistant' && String(m.content || '').includes('站起来活动一下'));
    check('场景2 历史存在提醒记录且无重复', remindMsgs.length === 1, 'count=' + remindMsgs.length);

    // ============ 场景3：MCP 管理页展示 reminder ============
    console.log('=== 场景3：MCP 管理页 ===');
    await ev('document.querySelector(".sidebar nav a[data-page=mcp]").click();');
    await sleep(1500);
    const mcpText = await ev('Array.from(document.querySelectorAll("table tr")).map(tr => tr.textContent).join("\\n")');
    check('场景3 MCP 页包含 reminder 条目', mcpText.includes('reminder'), '');
    const remMcp = await (await fetch(BASE + '/api/mcp')).json();
    const rem = (remMcp || []).find(m => m.name === 'reminder');
    check('场景3 reminder MCP 状态 active', rem && rem.status === 'active', 'status=' + (rem && rem.status));

    // 清理测试会话
    await fetch(BASE + '/api/chat/history/session/' + encodeURIComponent(sess), { method: 'DELETE' });
  } catch (e) {
    check('整体执行无异常', false, e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    console.log('定时提醒前端验收汇总: ' + pass + ' PASS / ' + fail + ' FAIL');
    process.exit(fail > 0 ? 1 : 0);
  }
})();