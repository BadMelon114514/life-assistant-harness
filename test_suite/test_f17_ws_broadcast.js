// 阶段17 补充验收：关闭指令通过 WebSocket 广播给其他已打开页面（多标签页场景）
const { spawn } = require('child_process');
const net = require('net');
const path = require('path');

const BASE = 'http://127.0.0.1:8080';
const ROOT = path.dirname(__dirname);
const LAUNCHER = path.join(ROOT, 'dist', '生活助理Harness一键启动.exe');

let passCount = 0, failCount = 0;
function check(name, ok, detail = '') {
  if (ok) { passCount++; console.log('  [PASS] ' + name + (detail ? ' -- ' + detail : '')); }
  else { failCount++; console.log('  [FAIL] ' + name + (detail ? ' -- ' + detail : '')); }
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
function portOpen(port) {
  return new Promise((resolve) => {
    const s = net.connect({ host: '127.0.0.1', port });
    let done = false;
    const fin = (v) => { if (!done) { done = true; try { s.destroy(); } catch (e) {} resolve(v); } };
    s.setTimeout(800);
    s.on('connect', () => fin(true));
    s.on('timeout', () => fin(false));
    s.on('error', () => fin(false));
  });
}
async function waitHealth(url, ms = 120000) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) {
    try { const r = await fetch(url, { signal: AbortSignal.timeout(3000) }); if (r.ok) return true; } catch (e) { }
    await sleep(1000);
  }
  return false;
}

(async () => {
  try {
    const ws = new WebSocket('ws://127.0.0.1:8080/ws/monitor');
    const messages = [];
    const opened = await new Promise((resolve) => {
      ws.onopen = () => resolve(true);
      ws.onerror = () => resolve(false);
      setTimeout(() => resolve(false), 5000);
    });
    check('1 另一页面 WebSocket 连接成功', opened);
    ws.onmessage = (e) => {
      try { messages.push(JSON.parse(e.data)); } catch (err) { }
    };
    ws.onerror = () => { };
    ws.onclose = () => { };
    ws.onclose = () => { };

    const resp = await fetch(BASE + '/api/system/shutdown', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Shutdown-Confirm': 'yes' },
      body: JSON.stringify({ confirm: true })
    });
    const data = await resp.json();
    check('2 关闭接口返回成功', resp.ok && data.success === true, JSON.stringify(data));

    let got = null;
    for (let i = 0; i < 30; i++) {
      got = messages.find(m => m.type === 'server_shutdown');
      if (got) break;
      await sleep(200);
    }
    check('3 收到 server_shutdown 广播', !!got, got ? JSON.stringify(got) : '5s 内未收到');

    let closed = false;
    for (let i = 0; i < 40; i++) {
      if (!(await portOpen(8080)) && !(await portOpen(8765))) { closed = true; break; }
      await sleep(400);
    }
    check('4 广播后服务停止', closed);

    spawn(LAUNCHER, [], { cwd: ROOT, detached: true, stdio: 'ignore' }).unref();
    check('5 重新启动恢复 8080', await waitHealth(BASE + '/actuator/health'));
    check('6 重新启动恢复 8765', await waitHealth('http://127.0.0.1:8765/health', 30000));
    try { ws.close(); } catch (e) { }
  } catch (e) {
    console.error('[ERROR]', e && e.message ? e.message : e);
    failCount++;
  }
  const total = passCount + failCount;
  console.log('\n[SUMMARY] ' + passCount + '/' + total + ' PASS');
  process.exitCode = failCount ? 1 : 0;
  setTimeout(() => process.exit(process.exitCode), 150);
})();
