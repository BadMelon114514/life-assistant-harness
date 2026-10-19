// 深度探测 harness 风格：收集颜色/圆角/组件样式
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9231;
const PROFILE = path.join(__dirname, '.harness-probe2');
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
    await send('Runtime.enable');
    for (let i = 0; i < 30; i++) { if (await ev('document.body && document.body.children.length > 0')) break; await sleep(500); }
    await sleep(2500);
    // 1) 整体 DOM 骨架（前 3 层）
    console.log('=== DOM 骨架 ===');
    console.log(await ev('(() => { const out = []; function walk(el, depth) { if (depth > 3) return; const cls = typeof el.className === "string" ? el.className.split(/\\s+/).slice(0,3).join(".") : ""; out.push("  ".repeat(depth) + el.tagName.toLowerCase() + (cls ? "." + cls : "") + (el.children.length ? "" : " [" + (el.textContent||"").trim().slice(0,25) + "]")); for (const c of el.children) walk(c, depth + 1); } walk(document.body, 0); return out.slice(0, 80).join("\\n"); })()'));
    // 2) 高频背景色/圆角统计
    console.log('=== 高频样式统计 ===');
    console.log(await ev('(() => { const bg = {}, rad = {}, fg = {}; function add(m, k) { m[k] = (m[k] || 0) + 1; } for (const el of document.querySelectorAll("*")) { const s = getComputedStyle(el); const b = s.backgroundColor; const r = s.borderRadius; const f = s.color; if (b && b !== "rgba(0, 0, 0, 0)" && b !== "transparent") add(bg, b); if (r && r !== "0px") add(rad, r); if (f && f !== "rgb(15, 17, 21)") add(fg, f); } const top = (o, n) => Object.entries(o).sort((a,b)=>b[1]-a[1]).slice(0, n); return JSON.stringify({ bg: top(bg, 12), radius: top(rad, 8), fg: top(fg, 8) }, null, 1); })()'));
    // 3) 消息/气泡
    console.log('=== 消息气泡 ===');
    console.log(await ev('(() => { const msgs = document.querySelectorAll("[class*=message], [class*=bubble], [class*=chat]"); const out = []; for (const m of Array.from(msgs).slice(0, 4)) { const s = getComputedStyle(m); out.push({ cls: m.className, bg: s.backgroundColor, color: s.color, radius: s.borderRadius, border: s.border, maxW: s.maxWidth, pad: s.padding }); } return JSON.stringify(out, null, 1); })()'));
    // 4) 输入区/发送按钮
    console.log('=== 输入区 ===');
    console.log(await ev('(() => { const i = document.querySelector("textarea, [contenteditable=true], input[type=text], [class*=input]"); const btns = Array.from(document.querySelectorAll("button")).slice(0, 8); const bs = btns.map(b => { const s = getComputedStyle(b); return { cls: (b.className||"").toString().slice(0,40), bg: s.backgroundColor, color: s.color, radius: s.borderRadius, border: s.border, pad: s.padding }; }); const is = i ? (() => { const s = getComputedStyle(i); return { cls: (i.className||"").toString().slice(0,40), bg: s.backgroundColor, color: s.color, radius: s.borderRadius, border: s.border, pad: s.padding }; })() : null; return JSON.stringify({ input: is, buttons: bs }, null, 1); })()'));
    // 5) header/topbar
    console.log('=== header ===');
    console.log(await ev('(() => { const h = document.querySelector("header, [class*=header], [class*=topbar], [class*=titlebar]"); if (!h) return "no header"; const s = getComputedStyle(h); return JSON.stringify({ cls: h.className, bg: s.backgroundColor, color: s.color, border: s.borderBottom, height: s.height, pad: s.padding }); })()'));
  } catch (e) {
    console.log('PROBE ERROR:', e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    process.exit(0);
  }
})();