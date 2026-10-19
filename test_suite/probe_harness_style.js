// 侦察 harness GUI 风格：提取关键计算样式 + 截图
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9230;
const PROFILE = path.join(__dirname, '.harness-style-probe');
(async () => {
  try { fs.rmSync(PROFILE, { recursive: true, force: true }); } catch (e) { }
  const edge = spawn(EDGE, ['--headless=new','--no-sandbox','--disable-gpu','--no-first-run','--remote-debugging-port='+PORT,'--user-data-dir='+PROFILE,'http://127.0.0.1:3080/'], { stdio: 'ignore' });
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
    await send('Page.enable');
    await send('Runtime.enable');
    for (let i = 0; i < 30; i++) { if (await ev('document.body && document.body.children.length > 0')) break; await sleep(500); }
    await sleep(2000);
    console.log('href:', await ev('location.href'));
    console.log('title:', await ev('document.title'));
    // 布局与关键样式
    console.log('--- body ---');
    console.log(JSON.stringify(await ev('(() => { const s = getComputedStyle(document.body); return { bg: s.backgroundColor, color: s.color, font: s.fontFamily, fontSize: s.fontSize, radius: s.borderRadius }; })()'), null, 1));
    // 侧边栏/导航
    console.log('--- nav/sidebar ---');
    console.log(JSON.stringify(await ev('(() => { const nav = document.querySelector("nav, aside, .sidebar, [class*=side]"); if (!nav) return "no nav"; const s = getComputedStyle(nav); return { cls: nav.className, bg: s.backgroundColor, width: s.width, color: s.color }; })()'), null, 1));
    // 主区域/卡片
    console.log('--- main/card ---');
    console.log(JSON.stringify(await ev('(() => { const main = document.querySelector("main, [class*=main], [class*=content]"); if (!main) return "no main"; const s = getComputedStyle(main); return { cls: main.className, bg: s.backgroundColor, padding: s.padding }; })()'), null, 1));
    console.log('--- card-like ---');
    console.log(JSON.stringify(await ev('(() => { const c = document.querySelector("[class*=card], [class*=panel], [class*=bubble], [class*=message]"); if (!c) return "no card"; const s = getComputedStyle(c); return { cls: c.className, bg: s.backgroundColor, radius: s.borderRadius, border: s.border, shadow: s.boxShadow }; })()'), null, 1));
    // 按钮/输入
    console.log('--- button/input ---');
    console.log(JSON.stringify(await ev('(() => { const b = document.querySelector("button, [class*=btn]"); const i = document.querySelector("input, textarea"); const bs = b ? getComputedStyle(b) : null; const is = i ? getComputedStyle(i) : null; return { btn: bs ? { cls: b.className, bg: bs.backgroundColor, color: bs.color, radius: bs.borderRadius, border: bs.border } : null, input: is ? { cls: i.className, bg: is.backgroundColor, color: is.color, radius: is.borderRadius, border: is.border } : null }; })()'), null, 1));
    // 根变量（CSS 自定义属性）
    console.log('--- css vars ---');
    console.log(await ev('(() => { const s = getComputedStyle(document.documentElement); const vars = {}; for (const k of s) { if (k.startsWith("--")) vars[k] = s.getPropertyValue(k).trim(); } return JSON.stringify(vars).slice(0, 3000); })()'));
    // 截图
    await sleep(1500);
    const shot = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(__dirname, 'harness_shot.png'), Buffer.from(shot.data, 'base64'));
    console.log('screenshot saved');
  } catch (e) {
    console.log('PROBE ERROR:', e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    process.exit(0);
  }
})();