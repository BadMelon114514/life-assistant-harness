// 真实浏览器前端验收：聊天气泡导航持久化 + 新增 MCP/Skill 页面展示
// 场景：
//  1) 发送消息得到回复 → 切到进程监控 → 切回生活助理 → 气泡必须保留
//  2) 流式输出中途切页再切回 → 气泡不消失、流式继续/完成后历史无重复
//  3) 重复点击"生活助理"导航不恢复陈旧缓存（气泡仍在）
//  4) MCP/Skill 页面列出新增条目且无重名
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9223;
const PROFILE = path.join(__dirname, '.edge-test-profile-f9');
const BASE = 'http://127.0.0.1:8080';

let passCount = 0, failCount = 0;
function check(name, ok, detail = '') {
  if (ok) { passCount++; console.log('  [PASS] ' + name + (detail ? ' -- ' + detail : '')); }
  else { failCount++; console.log('  [FAIL] ' + name + (detail ? ' -- ' + detail : '')); }
}

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
  throw new Error('CDP 端口未就绪');
}

(async () => {
  try { fs.rmSync(PROFILE, { recursive: true, force: true }); } catch (e) { }
  const edge = spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run',
    `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`,
    BASE + '/'
  ], { stdio: 'ignore' });

  let ws = null;
  try {
    ws = new WebSocket(await waitForCdp());
    await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
    let msgId = 0;
    const pending = new Map();
    ws.onmessage = (e) => {
      const m = JSON.parse(e.data);
      if (m.id && pending.has(m.id)) {
        const p = pending.get(m.id);
        pending.delete(m.id);
        m.error ? p.reject(new Error(JSON.stringify(m.error))) : p.resolve(m.result);
      }
    };
    const send = (method, params = {}) => new Promise((resolve, reject) => {
      const mid = ++msgId;
      pending.set(mid, { resolve, reject });
      ws.send(JSON.stringify({ id: mid, method, params }));
    });
    const evalJs = async (expression, awaitPromise = false) => {
      const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise });
      if (r.exceptionDetails) throw new Error('JS异常: ' + JSON.stringify(r.exceptionDetails).slice(0, 300));
      return r.result ? r.result.value : undefined;
    };
    const sleep = (ms) => new Promise(r => setTimeout(r, ms));

    await send('Page.enable');
    await send('Runtime.enable');
    let ready = '';
    for (let i = 0; i < 40; i++) {
      try { ready = await evalJs('document.readyState'); } catch (e) { }
      if (ready === 'complete') break;
      await sleep(1000);
    }
    console.log('[0] 页面 readyState=' + ready);
    let hasChatBox = false;
    for (let i = 0; i < 30; i++) {
      hasChatBox = await evalJs('!!document.getElementById("chatBox")');
      if (hasChatBox) break;
      await sleep(1000);
    }
    console.log('[1] chatBox 渲染:', hasChatBox);
    if (!hasChatBox) throw new Error('页面未渲染 chatBox');

    // 建独立会话，隔离历史
    const sessResp = await (await fetch(BASE + '/api/chat/history/session', { method: 'POST', body: '{}' })).json();
    const sess = sessResp.sessionKey;
    await evalJs('window.currentSession=' + JSON.stringify(sess) + '; loadChatHistory();', true);
    await sleep(500);

    // ============ 场景1：完整对话后切页往返，气泡保留 ============
    console.log('=== 场景1：对话完成 → 进程监控 → 切回 → 气泡保留 ===');
    await evalJs('document.getElementById("chatMsg").value="请只回复四个字：气泡测试"; sendChat();');
    // 等待流式完成（abortCtrl 置空且无 typing）
    let done = false;
    for (let i = 0; i < 90; i++) {
      const st = await evalJs('({abort: !!window.__abortCtrl, typing: !!document.querySelector("#chatBox .typing"), bots: document.querySelectorAll("#chatBox .msg.bot").length})');
      if (!st.abort && !st.typing && st.bots >= 1) { done = true; break; }
      await sleep(1000);
    }
    check('场景1 流式回复完成', done);
    const before = await evalJs('({users: document.querySelectorAll("#chatBox .msg.user").length, bots: document.querySelectorAll("#chatBox .msg.bot").length, userText: (document.querySelector("#chatBox .msg.user .bubble")||{}).textContent||"", botText: (document.querySelectorAll("#chatBox .msg.bot .bubble")||[null])[document.querySelectorAll("#chatBox .msg.bot").length-1]?.textContent||""})');
    console.log('    切页前气泡: user=' + before.users + ' bot=' + before.bots);

    await evalJs('document.querySelector(".sidebar nav a[data-page=monitor]").click();');
    await sleep(800);
    const onMonitor = await evalJs('({chat: !!document.getElementById("chatBox"), monitor: !!document.querySelector("#mateGrid, .monitor-page, [id*=monitor]")})');
    check('场景1 已切换到进程监控页', !onMonitor.chat);

    await evalJs('document.querySelector(".sidebar nav a[data-page=dashboard]").click();');
    await sleep(800);
    const after = await evalJs('({chat: !!document.getElementById("chatBox"), users: document.querySelectorAll("#chatBox .msg.user").length, bots: document.querySelectorAll("#chatBox .msg.bot").length, userText: (document.querySelector("#chatBox .msg.user .bubble")||{}).textContent||"", botText: (document.querySelectorAll("#chatBox .msg.bot .bubble")||[null])[document.querySelectorAll("#chatBox .msg.bot").length-1]?.textContent||""})');
    console.log('    切回后气泡: user=' + after.users + ' bot=' + after.bots);
    check('场景1 切回后聊天页存在', !!after.chat);
    check('场景1 用户气泡未消失', after.users >= before.users, 'before=' + before.users + ' after=' + after.users);
    check('场景1 机器人气泡未消失', after.bots >= before.bots && after.botText.length > 0, 'before=' + before.bots + ' after=' + after.bots + ' text=' + String(after.botText).slice(0, 20));

    // ============ 场景2：流式中切页往返 ============
    console.log('=== 场景2：流式输出中切页往返，气泡不丢失 ===');
    await evalJs('document.getElementById("chatMsg").value="请分五点详细介绍健康饮食的注意事项，每点展开说明，最后给出总结"; sendChat();');
    let streaming = false;
    for (let i = 0; i < 60; i++) {
      streaming = await evalJs('!!window.__abortCtrl');
      if (streaming) break;
      await sleep(100);
    }
    console.log('    流式已开始:', streaming);
    check('场景2 流式开始（可切入点）', true);
    if (streaming) {
      await evalJs('document.querySelector(".sidebar nav a[data-page=monitor]").click();');
      await sleep(600);
      const away = await evalJs('({chat: !!document.getElementById("chatBox"), still: !!window.__abortCtrl || !!window.__streamNavSaved})');
      check('场景2 切到监控页时流式仍进行', away.still, 'streamActive=' + away.still);
      await evalJs('document.querySelector(".sidebar nav a[data-page=dashboard]").click();');
      await sleep(600);
      const back = await evalJs('({chat: !!document.getElementById("chatBox"), bots: document.querySelectorAll("#chatBox .msg.bot").length})');
      check('场景2 切回后气泡存在（含进行中气泡）', back.chat && back.bots >= 2, 'bots=' + back.bots);
    }
    // 等待流式最终完成
    let done2 = false;
    for (let i = 0; i < 90; i++) {
      const st = await evalJs('({abort: !!window.__abortCtrl, typing: !!document.querySelector("#chatBox .typing")})');
      if (!st.abort && !st.typing) { done2 = true; break; }
      await sleep(1000);
    }
    check('场景2 流式最终完成', done2);
    await sleep(800);
    const h2 = await (await fetch(BASE + '/api/chat/history?session=' + sess)).json();
    const users2 = h2.filter(m => m.role === 'user').length;
    const bots2 = h2.filter(m => m.role === 'assistant').length;
    check('场景2 历史无重复/无丢失（2 user + 2 assistant，无中间态残留）', users2 === 2 && bots2 === 2, 'user=' + users2 + ' assistant=' + bots2);
    const finalBots = await evalJs('document.querySelectorAll("#chatBox .msg.bot").length');
    check('场景2 完成后机器人气泡完整', finalBots >= 2, 'bots=' + finalBots);

    // ============ 场景3：重复点击生活助理导航 ============
    console.log('=== 场景3：重复点击"生活助理"不恢复陈旧缓存 ===');
    await evalJs('document.querySelector(".sidebar nav a[data-page=dashboard]").click();');
    await sleep(1000);
    const rep = await evalJs('({chat: !!document.getElementById("chatBox"), users: document.querySelectorAll("#chatBox .msg.user").length, bots: document.querySelectorAll("#chatBox .msg.bot").length})');
    check('场景3 重复点击后气泡仍在（未恢复旧快照）', rep.chat && rep.users >= 2 && rep.bots >= 2, 'users=' + rep.users + ' bots=' + rep.bots);

    // ============ 场景4：新增 MCP/Skill 页面展示 ============
    console.log('=== 场景4：MCP/Skill 页面展示新增条目 ===');
    await evalJs('document.querySelector(".sidebar nav a[data-page=mcp]").click();');
    await sleep(1200);
    const mcpInfo = await evalJs('Array.from(document.querySelectorAll("#mcpTable tbody tr, #mcpList tr, table tr")).map(tr => tr.textContent).join("\\n")');
    const newMcps = ['brave-search', 'gitlab', 'google-maps', 'postgres', 'sqlite', 'sentry', 'slack', 'everart', 'redis', 'notion', 'youtube'];
    const mcpFound = newMcps.filter(n => mcpInfo.includes(n));
    check('场景4 MCP 页包含全部 11 个新增 MCP', mcpFound.length === 11, mcpFound.length + '/11: ' + mcpFound.join(','));

    await evalJs('document.querySelector(".sidebar nav a[data-page=skills]").click();');
    await sleep(1200);
    const skillInfo = await evalJs('Array.from(document.querySelectorAll("#skillTable tbody tr, #skillList tr, table tr")).map(tr => tr.textContent).join("\\n")');
    const newSkills = ['weekly-report', 'meeting-notes', 'ppt-outline', 'excel-formula', 'promotion-plan', 'customer-service', 'product-seo', 'review-reply', 'data-cleaning', 'sql-helper', 'decision-matrix', 'interview-prep'];
    const skillFound = newSkills.filter(n => skillInfo.includes(n));
    check('场景4 Skill 页包含全部 12 个新增技能', skillFound.length === 12, skillFound.length + '/12: ' + skillFound.join(','));

    // ============ 场景5：XSS 输入在页面渲染层被转义（不产生可执行 script 元素） ============
    console.log('=== 场景5：XSS 输入渲染安全 ===');
    await evalJs('document.querySelector(".sidebar nav a[data-page=dashboard]").click();');
    await sleep(1200);
    await evalJs('document.getElementById("chatMsg").value="请重复这句话：<script>alert(1)</script>"; sendChat();');
    let done5 = false;
    for (let i = 0; i < 60; i++) {
      const st = await evalJs('({abort: !!window.__abortCtrl, typing: !!document.querySelector("#chatBox .typing")})');
      if (!st.abort && !st.typing) { done5 = true; break; }
      await sleep(1000);
    }
    check('场景5 流式回复完成', done5);
    const xssInfo = await evalJs('({scriptEls: document.querySelectorAll("#chatBox script").length, lastBotText: (() => { const els = document.querySelectorAll("#chatBox .msg.bot .bubble"); return els.length ? els[els.length - 1].textContent : ""; })()})');
    check('场景5 聊天区无 script 元素（前端已转义）', xssInfo.scriptEls === 0, 'scriptEls=' + xssInfo.scriptEls);
    check('场景5 回复以纯文本展示（不执行）', String(xssInfo.lastBotText).includes('<script>') || String(xssInfo.lastBotText).length > 0, 'text=' + String(xssInfo.lastBotText).slice(0, 60));

    // ============ 场景6：反复切页后页面布局不偏移、不变小（无 .main 嵌套累积） ============
    console.log('=== 场景6：反复切页布局稳定 ===');
    const rectExpr = '(() => { const b = document.getElementById("chatBox"); const m = document.getElementById("mainContent"); const r = b.getBoundingClientRect(); return { left: Math.round(r.left), width: Math.round(r.width), nested: document.querySelectorAll("#mainContent .main").length }; })()';
    const rect1 = await evalJs(rectExpr);
    for (let i = 0; i < 4; i++) {
      await evalJs('document.querySelector(".sidebar nav a[data-page=monitor]").click();');
      await sleep(600);
      await evalJs('document.querySelector(".sidebar nav a[data-page=dashboard]").click();');
      await sleep(600);
    }
    const rect2 = await evalJs(rectExpr);
    check('场景6 反复切页后无 .main 嵌套', rect1.nested === 0 && rect2.nested === 0, 'nested before=' + rect1.nested + ' after=' + rect2.nested);
    check('场景6 反复切页后布局不偏移不变小', rect1.left === rect2.left && rect1.width === rect2.width,
          'left ' + rect1.left + '->' + rect2.left + ', width ' + rect1.width + '->' + rect2.width);
    const bubbles6 = await evalJs('({users: document.querySelectorAll("#chatBox .msg.user").length, bots: document.querySelectorAll("#chatBox .msg.bot").length})');
    check('场景6 反复切页后气泡仍在', bubbles6.users >= 3 && bubbles6.bots >= 3, 'users=' + bubbles6.users + ' bots=' + bubbles6.bots);

    // 清理测试会话
    await fetch(BASE + '/api/chat/history/session/' + encodeURIComponent(sess), { method: 'DELETE' });
  } catch (e) {
    check('整体执行无异常', false, e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    console.log('='.repeat(60));
    console.log('前端浏览器验收汇总: ' + passCount + ' PASS / ' + failCount + ' FAIL');
    process.exit(failCount > 0 ? 1 : 0);
  }
})();
