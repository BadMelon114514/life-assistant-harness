// WS 推送验收：创建 0 分钟提醒 → /ws/monitor 应收到 assistant_notify 事件
const BASE = 'http://127.0.0.1:8080';
const WS = 'ws://127.0.0.1:8080/ws/monitor';
let pass = 0, fail = 0;
function check(name, ok, detail = '') { if (ok) { pass++; console.log('  [PASS] ' + name + (detail ? ' -- ' + detail : '')); } else { fail++; console.log('  [FAIL] ' + name + (detail ? ' -- ' + detail : '')); } }
(async () => {
  // 连接 WS
  const ws = new WebSocket(WS);
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
  console.log('WS 已连接');
  const events = [];
  ws.onmessage = (e) => { try { events.push(JSON.parse(e.data)); } catch (err) { } };

  const sess = 'ws_accept_' + Date.now();
  // 创建 0 分钟提醒
  const r1 = await (await fetch(BASE + '/api/assistant/schedule', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ instruct: 'WS推送验收提醒', delayMinutes: 0, title: 'WS验收', session: sess })
  })).json();
  check('创建提醒成功', r1.success === true, 'id=' + (r1.reminder && r1.reminder.id));

  // 等待轮询触发 + WS 事件（轮询 10s 间隔，等 25s）
  let got = null;
  for (let i = 0; i < 25; i++) {
    await new Promise(r => setTimeout(r, 1000));
    got = events.find(e => e.type === 'assistant_notify' && (e.content || '').includes('WS推送验收提醒'));
    if (got) break;
  }
  check('收到 assistant_notify 事件', !!got, got ? JSON.stringify(got) : '25s 内未收到');
  if (got) {
    check('事件含 title 与 content', !!got.title && !!got.content, 'title=' + got.title + ' content=' + got.content);
    check('事件带正确 session', got.session === sess, 'session=' + got.session);
  }
  // 清理
  await fetch(BASE + '/api/chat/history?session=' + sess, { method: 'DELETE' });
  ws.close();
  console.log('WS 推送验收汇总: ' + pass + ' PASS / ' + fail + ' FAIL');
  process.exit(fail > 0 ? 1 : 0);
})().catch(e => { console.error('异常:', e.message); process.exit(1); });
