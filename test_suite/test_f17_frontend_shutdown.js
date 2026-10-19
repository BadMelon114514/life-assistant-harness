// 阶段17 前端验收：设置页「关闭后端服务」→ 二次确认 → 离线页 → 重启恢复（Edge CDP 真实浏览器）
const { spawn } = require('child_process');
const fs = require('fs');
const net = require('net');
const path = require('path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9225;
const PROFILE = path.join(__dirname, '.edge-test-profile-f17');
const BASE = 'http://127.0.0.1:8080';
const CTX = 'http://127.0.0.1:8765';
const ROOT = path.dirname(__dirname);
const LAUNCHER = path.join(ROOT, 'dist', '生活助理Harness一键启动.exe');

let passCount = 0, failCount = 0;
function check(name, ok, detail = '') {
  if (ok) { passCount++; console.log('  [PASS] ' + name + (detail ? ' -- ' + detail : '')); }
  else { failCount++; console.log('  [FAIL] ' + name + (detail ? ' -- ' + detail : '')); }
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

function portOpen(port, timeout = 1000) {
  return new Promise((resolve) => {
    const s = net.connect({ host: '127.0.0.1', port });
    let done = false;
    const finish = (v) => { if (!done) { done = true; try { s.destroy(); } catch (e) {} resolve(v); } };
    s.setTimeout(timeout);
    s.on('connect', () => finish(true));
    s.on('timeout', () => finish(false));
    s.on('error', () => finish(false));
  });
}
async function waitPortsClosed(timeoutMs = 20000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    if (!(await portOpen(8080)) && !(await portOpen(8765))) return true;
    await sleep(400);
  }
  return false;
}
async function waitHealth(url, timeoutMs = 120000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    try {
      const r = await fetch(url, { signal: AbortSignal.timeout(3000) });
      if (r.ok) return true;
    } catch (e) { }
    await sleep(1000);
  }
  return false;
}
async function waitCdp(timeoutMs = 30000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    try {
      const res = await fetch('http://127.0.0.1:' + PORT + '/json');
      const list = await res.json();
      const page = list && list.find(t => t.type === 'page');
      if (page) return page.webSocketDebuggerUrl;
    } catch (e) { }
    await sleep(400);
  }
  throw new Error('CDP 端口未就绪');
}

(async () => {
  try { fs.rmSync(PROFILE, { recursive: true, force: true }); } catch (e) { }
  const edge = spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run',
    '--remote-debugging-port=' + PORT, '--user-data-dir=' + PROFILE,
    BASE + '/'
  ], { stdio: 'ignore' });

  let ws = null;
  try {
    ws = new WebSocket(await waitCdp());
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
    await send('Page.enable');
    await send('Runtime.enable');

    let ready = '';
    for (let i = 0; i < 40; i++) {
      try { ready = await evalJs('document.readyState'); } catch (e) { }
      if (ready === 'complete') break;
      await sleep(1000);
    }
    let chat = false;
    for (let i = 0; i < 30; i++) {
      chat = await evalJs('!!document.getElementById("chatBox")');
      if (chat) break;
      await sleep(1000);
    }
    check('0 页面加载并渲染聊天页', chat, 'readyState=' + ready);

    // ===== 侧栏电源按钮 =====
    const sideBtn = await evalJs('(() => { const b = document.getElementById("shutdownBtn"); return b ? {title: b.title, icon: !!b.querySelector("use[href=\'#ic-power\']")} : null; })()');
    check('1 侧栏存在电源关闭按钮', !!sideBtn && sideBtn.title.indexOf('关闭后端') >= 0, JSON.stringify(sideBtn));
    check('1b 按钮使用统一 SVG 图标 ic-power', !!sideBtn && sideBtn.icon);

    // ===== 进入设置页 =====
    await evalJs('document.querySelector(".sidebar nav a[data-page=settings]").click();');
    await sleep(900);
    const settings = await evalJs('(() => { const b = document.getElementById("btnShutdown"); return {page: !!document.getElementById("setSysName"), btn: !!b, label: b ? b.textContent.trim() : ""}; })()');
    check('2 设置页渲染完成', settings.page);
    check('2b 设置页存在「关闭后端服务」按钮', settings.btn && settings.label.indexOf('关闭后端服务') >= 0, settings.label);

    // ===== 点击按钮 → 二次确认弹窗 =====
    await evalJs('document.getElementById("btnShutdown").click();');
    await sleep(500);
    const modal = await evalJs('(() => { const m = document.querySelector(".modal-overlay"); return m ? {text: m.textContent, ok: m.querySelector("[data-act=ok]") ? m.querySelector("[data-act=ok]").textContent.trim() : "", cancel: !!m.querySelector("[data-act=cancel]")} : null; })()');
    check('3 弹出二次确认弹窗', !!modal);
    check('3b 弹窗说明 8080 与 8765', !!modal && modal.text.indexOf('8080') >= 0 && modal.text.indexOf('8765') >= 0);
    check('3c 确认按钮文案为「确认关闭」', !!modal && modal.ok === '确认关闭', modal ? modal.ok : '');
    check('3d 可取消', !!modal && modal.cancel);

    // ===== 确认关闭 =====
    await evalJs('document.querySelector(".modal-overlay [data-act=ok]").click();');

    let offline = false;
    for (let i = 0; i < 40; i++) {
      const st = await evalJs('(() => { const h = document.querySelector("#mainContent h1"); const cs = document.getElementById("connStatus"); return {h1: h ? h.textContent.trim() : "", conn: cs ? cs.textContent.trim() : "", body: document.getElementById("mainContent").textContent, active: document.querySelectorAll(".sidebar nav a.active").length}; })()');
      if (st.h1.indexOf('服务已关闭') >= 0) {
        offline = true;
        check('4 页面切换为离线状态', true, 'h1=' + st.h1);
        check('4b 状态栏显示「服务已关闭」', st.conn === '服务已关闭', st.conn);
        check('4c 离线页包含重启指引', st.body.indexOf('重新启动') >= 0 && st.body.indexOf('生活助理Harness一键启动.exe') >= 0);
        check('4d 清空导航选中态', st.active === 0, 'active=' + st.active);
        break;
      }
      await sleep(500);
    }
    if (!offline) check('4 页面切换为离线状态', false, '10s 内未出现离线页');

    const closed = await waitPortsClosed(20000);
    check('5 后端 8080 与上下文存储 8765 均已停止', closed);

    // 离线状态下点击导航仍停留在离线页（不发起无效请求）
    await evalJs('document.querySelector(".sidebar nav a[data-page=dashboard]").click();');
    await sleep(700);
    const stillOffline = await evalJs('(() => { const h = document.querySelector("#mainContent h1"); return h ? h.textContent.trim() : ""; })()');
    check('6 离线状态点击导航仍显示离线页', stillOffline.indexOf('服务已关闭') >= 0, stillOffline);

    // ===== 重启恢复 =====
    spawn(LAUNCHER, [], { cwd: ROOT, detached: true, stdio: 'ignore' }).unref();
    const up = await waitHealth(BASE + '/actuator/health', 120000);
    check('7 重新启动后 8080 恢复', up);
    const ctxUp = await waitHealth(CTX + '/health', 30000);
    check('7b 重新启动后 8765 恢复', ctxUp);

    await send('Page.reload');
    await sleep(3500);
    let chat2 = false;
    for (let i = 0; i < 20; i++) {
      chat2 = await evalJs('!!document.getElementById("chatBox")');
      if (chat2) break;
      await sleep(800);
    }
    const title = await evalJs('document.title');
    check('8 重启后页面恢复正常', chat2 && title.indexOf('生活助理 Harness') >= 0, 'title=' + title);
  } catch (e) {
    console.error('[ERROR]', e && e.message ? e.message : e);
    failCount++;
  } finally {
    try { if (ws) ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
  }
  const total = passCount + failCount;
  console.log('\n[SUMMARY] ' + passCount + '/' + total + ' PASS');
  process.exit(failCount ? 1 : 0);
})();
