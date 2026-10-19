// 前端布局与鲁棒性验收：多视口响应式 + 表格/长文本溢出 + 主题/弹窗/切页/缩放风暴
// 运行：node test_suite/test_f19_layout_robustness.js（需 Edge + 已启动 8080）
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9224;
const PROFILE = path.join(__dirname, '.edge-test-profile-f19');
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
      const list = await (await fetch('http://127.0.0.1:' + PORT + '/json')).json();
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
    '--headless=new', '--disable-gpu', '--no-first-run', '--window-size=1440,900',
    '--remote-debugging-port=' + PORT, '--user-data-dir=' + PROFILE, BASE + '/'
  ], { stdio: 'ignore' });

  let ws = null;
  try {
    ws = new WebSocket(await waitForCdp());
    await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
    let msgId = 0;
    const pending = new Map();
    const exceptions = [];
    ws.onmessage = (e) => {
      const m = JSON.parse(e.data);
      if (m.method === 'Runtime.exceptionThrown') {
        try { exceptions.push(m.params.exceptionDetails.exception.description || m.params.exceptionDetails.text); } catch (err) { }
      }
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
      if (r.exceptionDetails) throw new Error('JS异常: ' + JSON.stringify(r.exceptionDetails).slice(0, 400));
      return r.result ? r.result.value : undefined;
    };
    const sleep = (ms) => new Promise(r => setTimeout(r, ms));
    const setViewport = async (w, h) => {
      await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: false });
      await sleep(350);
    };
    const nav = async (page) => {
      await evalJs('document.querySelector(".sidebar nav a[data-page=' + page + ']").click()');
      await sleep(900);
    };
    const metrics = () => evalJs(`(() => {
      const d = document.documentElement, b = document.body;
      const main = document.getElementById('mainContent');
      const side = document.querySelector('.sidebar');
      const mr = main.getBoundingClientRect(), sr = side.getBoundingClientRect();
      const style = getComputedStyle(main);
      return {
        vw: innerWidth, vh: innerHeight,
        docW: Math.max(d.scrollWidth, b.scrollWidth), docH: Math.max(d.scrollHeight, b.scrollHeight),
        mainLeft: Math.round(mr.left), mainWidth: Math.round(mr.width), mainPadL: parseFloat(style.paddingLeft),
        sideLeft: Math.round(sr.left), sideRight: Math.round(sr.right), sideWidth: Math.round(sr.width),
        navLabelVisible: (() => { const el = document.querySelector('.sidebar nav a .nav-label'); return el ? getComputedStyle(el).display !== 'none' : false; })(),
        statusBarVisible: (() => { const el = document.querySelector('.sidebar .status-bar'); return el ? getComputedStyle(el).display !== 'none' : false; })(),
        themeBtnW: (() => { const el = document.getElementById('themeToggle'); return el ? Math.round(el.getBoundingClientRect().width) : 0; })(),
        chatBox: (() => { const el = document.getElementById('chatBox'); if (!el) return null; const r = el.getBoundingClientRect(); return { w: Math.round(r.width), h: Math.round(r.height) }; })(),
        cards: document.querySelectorAll('#mainContent .card').length
      };
    })()`);

    await send('Page.enable');
    await send('Runtime.enable');
    let ready = '';
    for (let i = 0; i < 40; i++) { try { ready = await evalJs('document.readyState'); } catch (e) { } if (ready === 'complete') break; await sleep(500); }
    for (let i = 0; i < 30; i++) { if (await evalJs('!!document.getElementById("chatBox")')) break; await sleep(500); }
    check('页面加载并渲染聊天框', await evalJs('!!document.getElementById("chatBox")'), 'ready=' + ready);

    // ---------- A. 桌面宽屏 1440 ----------
    console.log('=== A. 桌面 1440x900 ===');
    await setViewport(1440, 900);
    let m = await metrics();
    check('A1 页面无横向溢出', m.docW <= m.vw + 1, 'docW=' + m.docW + ' vw=' + m.vw);
    check('A2 主区从侧栏右侧开始', Math.abs(m.mainLeft - m.sideWidth) <= 2, 'mainLeft=' + m.mainLeft + ' sidebar=' + m.sideWidth);
    check('A3 主区未超出视口', m.mainLeft + m.mainWidth <= m.vw + 1, (m.mainLeft + m.mainWidth) + '/' + m.vw);
    check('A4 侧栏导航文字可见', m.navLabelVisible);
    check('A5 内容宽度受 1180px 限制', m.mainWidth - 2 * m.mainPadL <= 1180 + 2, 'content=' + (m.mainWidth - 2 * m.mainPadL));
    check('A6 聊天框高度自适应且在范围内', !!m.chatBox && m.chatBox.h >= 260 && m.chatBox.h <= 521, 'h=' + (m.chatBox ? m.chatBox.h : 0));

    // ---------- B. 1024 窄桌面 ----------
    console.log('=== B. 1024x768 ===');
    await setViewport(1024, 768);
    m = await metrics();
    check('B1 无横向溢出', m.docW <= m.vw + 1, m.docW + '/' + m.vw);
    check('B2 侧栏保持 232px 且文字可见', m.navLabelVisible && m.sideWidth === 232, 'side=' + m.sideWidth);
    check('B3 聊天框高度不超屏', m.chatBox && m.chatBox.h <= 520, 'h=' + (m.chatBox || {}).h);

    // ---------- C. 平板 768：侧栏收窄 ----------
    console.log('=== C. 768x900 ===');
    await setViewport(768, 900);
    m = await metrics();
    check('C1 无横向溢出', m.docW <= m.vw + 1, m.docW + '/' + m.vw);
    check('C2 侧栏收窄为 64px', m.sideWidth === 64, 'side=' + m.sideWidth);
    check('C3 导航文字隐藏但图标保留', !m.navLabelVisible && m.sideWidth === 64);
    check('C4 主题/关闭按钮仍可见', m.statusBarVisible && m.themeBtnW > 0, 'status=' + m.statusBarVisible + ' themeW=' + m.themeBtnW);
    check('C5 主区与侧栏无重叠', Math.abs(m.mainLeft - 64) <= 2, 'mainLeft=' + m.mainLeft);
    check('C6 图标未溢出侧栏', await evalJs('(() => { const a = document.querySelector(".sidebar nav a"); const r = a.getBoundingClientRect(); return r.right <= innerWidth && r.width <= 64; })()'));

    // ---------- D. 手机 390：全页面遍历 ----------
    console.log('=== D. 390x844 全页面 ===');
    await setViewport(390, 844);
    m = await metrics();
    check('D1 无横向溢出', m.docW <= m.vw + 1, m.docW + '/' + m.vw);
    check('D2 侧栏 56px', m.sideWidth === 56, 'side=' + m.sideWidth);
    const pages = ['monitor', 'tasks', 'knowledge', 'memory', 'mcp', 'skills', 'settings', 'dashboard'];
    for (const p of pages) {
      await nav(p);
      const mm = await metrics();
      const okPage = mm.docW <= mm.vw + 1 && Math.abs(mm.mainLeft - 56) <= 2;
      check('D3-' + p + ' 页面无溢出且主区对齐', okPage, 'docW=' + mm.docW + '/' + mm.vw + ' cards=' + mm.cards);
    }
    // 手机端宽表在卡片内滚动，不撑破页面
    await nav('mcp');
    const tbl = await evalJs(`(() => {
      const t = document.querySelector('#mainContent table');
      if (!t) return null;
      const card = t.closest('.card');
      return { tableW: t.getBoundingClientRect().width, cardW: card.getBoundingClientRect().width, scrollW: t.scrollWidth, clientW: t.clientWidth, disp: getComputedStyle(t).display };
    })()`);
    check('D4 手机端宽表不超出卡片', tbl && tbl.tableW <= tbl.cardW + 2, tbl ? 'table=' + Math.round(tbl.tableW) + ' card=' + Math.round(tbl.cardW) : 'no table');
    check('D5 宽表在自身容器内可滚动', tbl && tbl.scrollW >= tbl.clientW, tbl ? 'scroll=' + tbl.scrollW + '/' + tbl.clientW : 'no table');

    // ---------- E. 手机端长文本气泡 ----------
    console.log('=== E. 长文本/长单词气泡换行 ===');
    await nav('dashboard');
    await evalJs(`(() => {
      const box = document.getElementById('chatBox');
      const div = document.createElement('div');
      div.className = 'msg bot';
      div.id = 'f19LongMsg';
      div.innerHTML = '<div class="avatar">B</div><div class="bubble">' + 'A'.repeat(260) + '中文长文本测试'.repeat(20) + '</div>';
      box.appendChild(div);
      box.scrollTop = box.scrollHeight;
    })()`);
    const wrap = await evalJs(`(() => {
      const b = document.querySelector('#f19LongMsg .bubble');
      const box = document.getElementById('chatBox');
      return { bubbleScroll: b.scrollWidth, bubbleClient: b.clientWidth, boxScroll: box.scrollWidth, boxClient: box.clientWidth, docW: document.documentElement.scrollWidth, vw: innerWidth };
    })()`);
    check('E1 超长无空格文本不撑破气泡', wrap.bubbleScroll <= wrap.bubbleClient + 2, wrap.bubbleScroll + '/' + wrap.bubbleClient);
    check('E2 聊天容器无横向溢出', wrap.boxScroll <= wrap.boxClient + 2 && wrap.docW <= wrap.vw + 1, 'box=' + wrap.boxScroll + '/' + wrap.boxClient + ' doc=' + wrap.docW);

    // ---------- F. 手机端主题切换 + 弹窗 ----------
    console.log('=== F. 手机端主题与弹窗 ===');
    const themeBefore = await evalJs('document.documentElement.getAttribute("data-theme") || "light"');
    await evalJs('document.getElementById("themeToggle").click()');
    await sleep(300);
    const themeAfter = await evalJs('document.documentElement.getAttribute("data-theme") || "light"');
    check('F1 小屏主题切换可用', themeBefore !== themeAfter, themeBefore + ' -> ' + themeAfter);
    const overflowDark = (await metrics()).docW;
    check('F2 暗色布局无溢出', overflowDark <= 390 + 1, 'docW=' + overflowDark);
    await evalJs('openModal({title:"布局测试", body:"确认弹窗在手机屏幕内完整显示"})');
    await sleep(300);
    const modal = await evalJs(`(() => { const m = document.querySelector('.modal'); if (!m) return null; const r = m.getBoundingClientRect(); return { l: Math.round(r.left), r: Math.round(r.right), w: Math.round(r.width), vw: innerWidth }; })()`);
    check('F3 弹窗完整在视口内', !!modal && modal.l >= 0 && modal.r <= modal.vw + 1, modal ? ('l=' + modal.l + ' r=' + modal.r + ' vw=' + modal.vw) : 'no modal');
    await evalJs('document.querySelector(".modal-overlay").remove()');

    // ---------- G. 桌面短视口 ----------
    console.log('=== G. 1440x600 短视口 ===');
    await setViewport(1440, 600);
    m = await metrics();
    check('G1 无横向溢出', m.docW <= m.vw + 1, m.docW + '/' + m.vw);
    check('G2 聊天框高度不挤压输入区', m.chatBox && m.chatBox.h >= 260 && m.chatBox.h <= 520, 'h=' + (m.chatBox || {}).h);
    const inputIn = await evalJs(`(() => { const c = document.querySelector('.chat-card').getBoundingClientRect(); const i = document.querySelector('.chat-input').getBoundingClientRect(); return c.top < innerHeight && i.width > 0; })()`);
    check('G3 输入区可到达', inputIn);

    // ---------- H. 缩放风暴 ----------
    console.log('=== H. 连续缩放 12 次 ===');
    const widths = [1600, 1280, 1024, 900, 768, 640, 480, 390, 360, 600, 1100, 1440];
    let stormOk = true, badW = '';
    for (const w of widths) {
      await setViewport(w, 800);
      const mm = await metrics();
      if (mm.docW > mm.vw + 1 || Math.abs(mm.mainLeft - mm.sideWidth) > 2) { stormOk = false; badW = w + ':doc=' + mm.docW + '/' + mm.vw + ',left=' + mm.mainLeft + '/' + mm.sideWidth; break; }
    }
    check('H1 缩放过程中不溢出/不重叠', stormOk, badW || '12 个宽度全部通过');
    await setViewport(1440, 900);
    const back = await evalJs('({chat: !!document.getElementById("chatBox"), long: !!document.getElementById("f19LongMsg")})');
    check('H2 缩放后聊天页仍完整', back.chat && back.long);
    await evalJs('document.getElementById("f19LongMsg").remove()');

    // ---------- I. 控制台异常 ----------
    check('I1 全程无未捕获 JS 异常', exceptions.length === 0, exceptions.slice(0, 3).join(' | '));

    console.log('='.repeat(60));
    console.log('布局与鲁棒性验收汇总: ' + passCount + ' PASS / ' + failCount + ' FAIL (共 ' + (passCount + failCount) + ' 项)');
    process.exitCode = failCount === 0 ? 0 : 1;
  } finally {
    try { if (ws) ws.close(); } catch (e) { }
    try { edge.kill(); } catch (e) { }
  }
})().catch(e => { console.error(e); process.exitCode = 1; });
