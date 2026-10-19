// 图表往返验收：注入图表 → 渲染 → 切页往返 → 图表实例恢复且尺寸正常
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9225;
const PROFILE = path.join(__dirname, '.edge-chart-test');
let pass = 0, fail = 0;
function check(name, ok, detail = '') { if (ok) { pass++; console.log('  [PASS] ' + name + (detail ? ' -- ' + detail : '')); } else { fail++; console.log('  [FAIL] ' + name + (detail ? ' -- ' + detail : '')); } }
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
    const ev = async (expr, ap = false) => { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: ap }); if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails).slice(0, 200)); return r.result ? r.result.value : undefined; };
    const sleep = (ms) => new Promise(r => setTimeout(r, ms));
    await send('Runtime.enable');
    for (let i = 0; i < 30; i++) { if (await ev('!!document.getElementById("chatBox")')) break; await sleep(1000); }

    // 注入一个图表气泡并渲染
    const opt = JSON.stringify({ type: 'bar', title: '往返测试', x: ['A','B','C'], series: [{ name: '值', data: [3,5,2] }] });
    await ev(`(() => {
      const box = document.getElementById('chatBox');
      const div = document.createElement('div');
      div.className = 'msg bot';
      div.innerHTML = '<div class="avatar">🤖</div><div class="bubble"><div class="chart" id="c1"></div></div>';
      const chartEl = div.querySelector('#c1');
      chartEl.setAttribute('data-option', ${JSON.stringify(opt)});
      box.appendChild(div);
      renderCharts(box);
    })()`);
    await sleep(1200);
    const before = await ev('(() => { const c = document.getElementById("c1").querySelector("canvas"); return c ? { w: c.width, h: c.height, inst: !!window.__charts["c1"] || !!echarts.getInstanceByDom(document.getElementById("c1")) } : null; })()');
    console.log('    切页前图表: ', JSON.stringify(before));
    check('图表初始渲染成功', !!before && before.w > 0 && before.h > 0 && before.inst, JSON.stringify(before));

    // 往返 2 次
    for (let i = 0; i < 2; i++) {
      await ev('document.querySelector(".sidebar nav a[data-page=monitor]").click();');
      await sleep(700);
      await ev('document.querySelector(".sidebar nav a[data-page=dashboard]").click();');
      await sleep(700);
    }
    await sleep(800);
    const after = await ev('(() => { const el = document.getElementById("c1"); const c = el ? el.querySelector("canvas") : null; const inst = el && echarts.getInstanceByDom ? echarts.getInstanceByDom(el) : null; return { exists: !!el, canvasW: c ? c.width : 0, canvasH: c ? c.height : 0, inst: !!inst, cssH: el ? getComputedStyle(el).height : "0" }; })()');
    console.log('    往返后图表: ', JSON.stringify(after));
    check('往返后图表容器仍存在', after.exists, '');
    check('往返后图表实例已重建且尺寸正常', after.inst && after.canvasW > 0 && after.canvasH > 300, JSON.stringify(after));
    check('往返后无 .main 嵌套', await ev('document.querySelectorAll("#mainContent .main").length === 0'));
  } catch (e) {
    check('整体执行无异常', false, e.message);
  } finally {
    try { ws && ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
    console.log('图表往返验收汇总: ' + pass + ' PASS / ' + fail + ' FAIL');
    process.exit(fail > 0 ? 1 : 0);
  }
})();