// 真实浏览器前端测试：打开页面 → 发送"新增技能" → 等待草稿按钮 → 点击导入 → 验证
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9222;
const PROFILE = path.join(__dirname, '.edge-test-profile');

async function waitForCdp(timeoutMs = 30000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json`);
      const list = await res.json();
      // 优先选 type=page 的主标签页 target
      const page = list && list.find(t => t.type === 'page');
      if (page) return page.webSocketDebuggerUrl;
    } catch (e) { /* 未就绪 */ }
    await new Promise(r => setTimeout(r, 400));
  }
  throw new Error('CDP 端口未就绪');
}

(async () => {
  // 清理旧 profile
  try { fs.rmSync(PROFILE, { recursive: true, force: true }); } catch (e) { }
  // 启动 Edge headless
  const edge = spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run',
    `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`,
    'http://127.0.0.1:8080/'
  ], { stdio: 'ignore' });

  try {
    const wsUrl = await waitForCdp();
    const ws = new WebSocket(wsUrl);
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
    // 等待目标页加载完成（readyState === complete 且 URL 正确）
    let ready = '';
    let href = '';
    let title = '';
    for (let i = 0; i < 40; i++) {
      try {
        ready = await evalJs(`document.readyState`);
        href = await evalJs(`window.location.href`);
        title = await evalJs(`document.title`);
      } catch (e) { }
      if (ready === 'complete' && href && href.includes('127.0.0.1')) break;
      await new Promise(r => setTimeout(r, 1000));
    }
    console.log('[0] 页面状态 readyState=' + ready + ' href=' + href + ' title=' + title);
    if (!href || !href.includes('127.0.0.1')) throw new Error('未连接到目标页面: ' + href);
    // 等待 dashboard 渲染完成（轮询 chatBox，最长 30 秒）
    let hasChatBox = false;
    for (let i = 0; i < 30; i++) {
      hasChatBox = await evalJs(`!!document.getElementById('chatBox')`);
      if (hasChatBox) break;
      await new Promise(r => setTimeout(r, 1000));
    }
    console.log('[1] 页面渲染 chatBox:', hasChatBox);
    if (!hasChatBox) throw new Error('页面未渲染 chatBox');

    // 触发"新增技能"对话（真实流式）
    await evalJs(`document.getElementById('chatMsg').value='新增一个商品价格对比的技能'; sendChat();`);
    console.log('[2] 已发送"新增技能"消息，等待草稿按钮...');

    // 轮询按钮出现（最长 90 秒）
    let btnFound = false;
    for (let i = 0; i < 90; i++) {
      btnFound = await evalJs(`!!document.querySelector('.import-draft-btn')`);
      if (btnFound) break;
      await new Promise(r => setTimeout(r, 1000));
    }
    console.log('[3] 一键导入按钮出现:', btnFound);

    // 检查 window.lastDraft
    const lastDraft = await evalJs(`window.lastDraft ? {type: window.lastDraft.type, name: (window.lastDraft.name || '').slice(0,30)} : null`);
    console.log('[4] window.lastDraft:', JSON.stringify(lastDraft));

    if (btnFound && lastDraft) {
      // 点击按钮并等待导入
      const clickResult = await evalJs(`(async()=>{
        const btn = document.querySelector('.import-draft-btn');
        btn.click();
        await new Promise(r=>setTimeout(r,4000));
        return { disabled: btn.disabled, text: btn.textContent };
      })()`, true);
      console.log('[5] 点击后按钮状态:', JSON.stringify(clickResult));

      // 验证技能是否创建成功
      const skills = await (await fetch('http://127.0.0.1:8080/api/skills')).json();
      const target = skills.find(s => s.name === (lastDraft.name || ''));
      console.log('[6] 技能页是否创建:', target ? '成功: ' + target.name + ' (id=' + target.id + ')' : '未找到');
      // 清理测试技能
      if (target) {
        await fetch('http://127.0.0.1:8080/api/skills/' + target.id, { method: 'DELETE' });
        console.log('    (测试技能已清理 id=' + target.id + ')');
      }
    }
  } catch (e) {
    console.error('测试异常:', e.message);
  } finally {
    try { edge.kill(); } catch (e) { }
    process.exit(0);
  }
})();
