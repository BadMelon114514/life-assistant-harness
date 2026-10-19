// ============================================
// 生活助理 Harness - 前端逻辑
// ============================================

const mainContent = document.getElementById('mainContent');

// ---------- 工具函数 ----------
async function api(url, options = {}) {
    try {
        const resp = await fetch(url, {
            headers: { 'Content-Type': 'application/json' },
            ...options
        });
        return await resp.json();
    } catch (e) {
        return null; // 网络错误静默降级，避免未捕获 Promise rejection
    }
}

function toast(msg, type = 'info') {
    const el = document.getElementById('toast');
    el.textContent = msg;
    el.className = 'toast show';
    el.dataset.type = type;
    setTimeout(() => el.classList.remove('show'), 2500);
}

const ESC_MAP = { '&': '&amp;', '<': '&lt;', '>': '&gt;' };
const ESC_ATTR_MAP = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

function esc(str) {
    return str == null ? '' : String(str).replace(/[&<>]/g, c => ESC_MAP[c]);
}

/** 统一线条风图标：引用 index.html 的 SVG <symbol> 精灵（feather 风格） */
function ic(name, cls) {
    return `<svg class="ic ${cls || ''}"><use href="#ic-${name}"/></svg>`;
}

/** 转义 HTML 属性值（额外转义引号，防止属性注入 / XSS） */
function escAttr(str) {
    return str == null ? '' : String(str).replace(/[&<>"']/g, c => ESC_ATTR_MAP[c]);
}

/** 安全地把值嵌入 HTML 属性内的 JS 字符串字面量（如 onclick），先 JSON 序列化再转义属性 */
function jsStr(v) {
    return escAttr(JSON.stringify(v == null ? '' : String(v)));
}

function statusTag(status) {
    const map = {
        'WORK': '<span class="tag tag-green">工作中</span>',
        'IDLE': '<span class="tag tag-orange">待命中</span>',
        'SHUTDOWN': '<span class="tag tag-gray">已停止</span>',
        'pending': '<span class="tag tag-gray">待执行</span>',
        'in_progress': '<span class="tag tag-orange">进行中</span>',
        'completed': '<span class="tag tag-green">已完成</span>',
        'failed': '<span class="tag tag-red">失败</span>',
        'done': '<span class="tag tag-green">已解析</span>',
        'parsing': '<span class="tag tag-orange">解析中</span>',
        'failed_parse': '<span class="tag tag-red">解析失败</span>',
        'pending_parse': '<span class="tag tag-gray">待解析</span>'
    };
    return map[status] || `<span class="tag tag-gray">${esc(status)}</span>`;
}

// ---------- 模态框组件（替代原生 prompt/confirm） ----------
function openModal({ title, body, okText = '确定', cancelText = '取消', onOk, showCancel = true, closable = true }) {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `
        <div class="modal">
            <div class="modal-header">
                <h3>${esc(title)}</h3>
                ${closable ? '<button class="modal-close" onclick="this.closest(\'.modal-overlay\').remove()">×</button>' : ''}
            </div>
            <div class="modal-body">${body}</div>
            <div class="modal-footer">
                ${showCancel ? `<button class="btn" data-act="cancel">${esc(cancelText)}</button>` : ''}
                <button class="btn btn-primary" data-act="ok">${esc(okText)}</button>
            </div>
        </div>`;
    overlay.addEventListener('click', (e) => {
        if (!closable) return;
        if (e.target === overlay) overlay.remove();
    });
    overlay.querySelector('[data-act="cancel"]')?.addEventListener('click', () => overlay.remove());
    overlay.querySelector('[data-act="ok"]').addEventListener('click', async () => {
        const proceed = onOk ? await onOk(overlay) : true;
        if (proceed !== false) overlay.remove();
    });
    document.body.appendChild(overlay);
    return overlay;
}

function confirmBox(title, message) {
    return new Promise(resolve => {
        openModal({
            title,
            body: `<p style="line-height:1.7">${message}</p>`,
            okText: '确定',
            onOk: () => { resolve(true); },
            closable: true
        });
        // 监听关闭事件作为取消
        setTimeout(() => {
            const overlay = document.querySelector('.modal-overlay');
            if (overlay) overlay.addEventListener('click', (e) => {
                if (e.target === overlay) resolve(false);
            });
        }, 0);
    });
}

/** 弹出输入框，用于填写远程 URL；取消时返回空字符串 */
function promptForUrl(title, placeholder) {
    return new Promise(resolve => {
        openModal({
            title,
            body: `
                <div class="form-group">
                    <label>远程地址</label>
                    <input id="dlUrl" placeholder="${escAttr(placeholder || 'https://example.com/config.json')}">
                    <div class="muted mt">系统会先自动下载并扫描病毒/后门，安全内容才会导入。</div>
                </div>`,
            okText: '下载并扫描',
            onOk: (ov) => {
                const url = ov.querySelector('#dlUrl').value.trim();
                if (!url) { toast('请输入远程地址', 'warn'); return false; }
                resolve(url);
            }
        });
        // 关闭视为取消
        setTimeout(() => {
            const overlay = document.querySelector('.modal-overlay');
            if (overlay) overlay.addEventListener('click', (e) => {
                if (e.target === overlay) resolve('');
            });
        }, 0);
    });
}

// ---------- 页面路由 ----------
const pages = {
    dashboard: renderDashboard,
    monitor: renderMonitor,
    tasks: renderTasks,
    knowledge: renderKnowledge,
    memory: renderMemory,
    mcp: renderMcp,
    skills: renderSkills,
    settings: renderSettings
};

async function navigate(page) {
    if (window.__backendDown) { showOfflineScreen(); return; }
    const activeLink = document.querySelector('.sidebar nav a.active');
    const currentPage = activeLink ? activeLink.dataset.page : null;

    // 1) 切换页面时，若聊天流式尚未完成，先保存当前已累积内容（防丢失兜底）
    if (window.__pendingStreamSave && !window.__streamNavSaved) {
        try {
            window.__streamNavSaved = true;
            window.__pendingStreamSave();
        } catch (e) { }
        window.__pendingStreamSave = null;
    }

    // 2) 离开"聊天助手"页：把聊天页真实子节点整体摘除缓存（保留进行中的流式气泡）。
    //    注意：不能 cloneNode 整个 mainContent——恢复时会把 .main 嵌套进 .main，
    //    每切一次页多套一层 margin/padding，页面逐渐偏移变小；移动原节点则无嵌套、无重复 id。
    if (currentPage && currentPage !== page && currentPage === 'dashboard' && document.getElementById('chatBox')) {
        try {
            window.__dashboardNodes = Array.from(mainContent.childNodes);
            mainContent.replaceChildren();
        } catch (e) { }
    }

    document.querySelectorAll('.sidebar nav a').forEach(a =>
        a.classList.toggle('active', a.dataset.page === page));
    mainContent.scrollTop = 0;

    // 3) 切回"聊天助手"页：当前页不是聊天页（无 chatBox）且存在缓存时，把原节点原样放回，
    //    气泡不消失，流式输出继续更新；重复点击"生活助理"（此时已有 chatBox）则走全量重建
    if (page === 'dashboard' && window.__dashboardNodes && !document.getElementById('chatBox')) {
        mainContent.replaceChildren(...window.__dashboardNodes);
        window.__dashboardNodes = null;
        const box = document.getElementById('chatBox');
        if (box) {
            // 摘除期间图表可能被 resize 置 0 尺寸：销毁旧实例并重新渲染，避免图表空白
            box.querySelectorAll('.chart[data-option]').forEach(el => {
                try {
                    const inst = typeof echarts !== 'undefined' && echarts.getInstanceByDom ? echarts.getInstanceByDom(el) : null;
                    if (inst) inst.dispose();
                } catch (e) { }
                el.removeAttribute('data-rendered');
            });
            try { renderCharts(box); } catch (e) { }
        }
        // 原节点事件监听仍保留；幂等重绑一键导入委托，防御极端场景
        try { bindImportButtonDelegation(); } catch (e) { }
        if (box) box.scrollTop = box.scrollHeight;
        return;
    }
    // 全量重建聊天页（如重复点击"生活助理"导航）时，丢弃可能残留的过期缓存，避免误恢复旧快照
    if (page === 'dashboard') window.__dashboardNodes = null;

    if (pages[page]) await pages[page]();
}

document.querySelectorAll('.sidebar nav a').forEach(a => {
    a.addEventListener('click', () => navigate(a.dataset.page));
});

// ---------- 1. 生活助理 Dashboard ----------
async function renderDashboard() {
    const apiConfigured = await checkApiConfig();
    mainContent.innerHTML = `
        <div class="page-header">
            <div>
                <h1>${ic('home')} 生活助理</h1>
                <div class="sub">结合 AI、知识库、记忆与工具，辅助处理日常生活事务</div>
            </div>
            <div class="row">
                <span class="badge-date" id="todayDate"></span>
            </div>
        </div>
        ${apiConfigured ? '' : '<div class="warning-banner">⚠️ 尚未配置 API Key，AI 功能不可用。<a href="#" onclick="navigate(\'settings\');return false;">前往设置</a></div>'}
        <div class="grid grid-4" id="dashStats">
            <div class="stat-card"><div class="label">知识库文档</div><div class="num" id="statKb">-</div><div class="stat-icon">${ic('book', 'ic-lg')}</div></div>
            <div class="stat-card"><div class="label">运行中的 Teammate</div><div class="num" id="statMate">-</div><div class="stat-icon">${ic('bot', 'ic-lg')}</div></div>
            <div class="stat-card"><div class="label">进行中任务</div><div class="num" id="statRunning">-</div><div class="stat-icon">${ic('tasks', 'ic-lg')}</div></div>
            <div class="stat-card"><div class="label">记忆条目</div><div class="num" id="statMem">-</div><div class="stat-icon">${ic('memory', 'ic-lg')}</div></div>
        </div>
        <div class="card chat-card">
            <div class="row" style="justify-content:space-between;margin-bottom:12px">
                <h3 style="margin:0">💬 与助手对话</h3>
                <div class="row">
                    <span class="muted">${ic('bot', 'ic-sm')} 模型：</span>
                    <select id="modelSelect" onchange="switchModel(this.value)" style="width:auto" title="切换 AI 模型"></select>
                    <span class="muted" id="chatCount"></span>
                    <button class="btn btn-sm" id="searchToggleBtn" onclick="toggleSearch()" title="切换联网搜索开关">${ic('globe', 'ic-sm')} 联网搜索</button>
                    <button class="btn btn-sm" onclick="clearChat()">${ic('refresh', 'ic-sm')} 清空/回滚</button>
                </div>
            </div>
            <div class="session-bar" id="sessionBar">
                <div class="row">
                    <span class="muted">子对话：</span>
                    <select id="sessionSelect" onchange="switchSession(this.value)"></select>
                    <button class="btn btn-sm" onclick="newSession()">${ic('plus', 'ic-sm')} 新建对话</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteCurrentSession()">${ic('trash', 'ic-sm')} 删除当前对话</button>
                </div>
            </div>
            <div class="chat-box" id="chatBox"></div>
            <div class="chat-input">
                <input id="chatMsg" placeholder="输入你的问题，例如：帮我整理一份促销活动方案..." onkeydown="if(event.key==='Enter')sendChat()">
                <button id="chatStopBtn" class="btn btn-danger chat-stop-btn" style="display:none" onclick="stopCurrentChat()">⏹ 停止</button>
                <button class="btn btn-primary" onclick="sendChat()">发送</button>
            </div>
        </div>`;

    document.getElementById('todayDate').textContent = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });
    window.currentSession = window.currentSession || null;
    await loadDashboardStats();
    await loadSearchToggle();
    await loadSessions();
    await loadModels();
    await loadChatHistory();
    bindImportButtonDelegation(); // 聊天页事件委托（一键导入按钮点击）
}

/** 加载模型列表到下拉框（激活模型默认选中） */
async function loadModels() {
    const sel = document.getElementById('modelSelect');
    if (!sel) return;
    try {
        const models = await api('/api/models');
        sel.innerHTML = models.map(m =>
            `<option value="${m.id}" ${m.active === 1 ? 'selected' : ''}>${esc(m.name)}${m.active === 1 ? ' ✓' : ''}</option>`
        ).join('') || '<option value="">无模型</option>';
    } catch (e) {
        sel.innerHTML = '<option value="">模型不可用</option>';
    }
}

/** 切换激活的 AI 模型 */
async function switchModel(id) {
    if (!id) return;
    try {
        const resp = await api(`/api/models/${id}/activate`, { method: 'POST', body: '{}' });
        if (resp.success) {
            toast('✅ 已切换到模型：' + (resp.model && resp.model.name || ''));
            await loadModels();
        } else {
            toast('❌ 切换失败', 'error');
        }
    } catch (e) {
        toast('❌ 切换失败', 'error');
    }
}

/** 加载联网搜索开关状态并渲染按钮 */
async function loadSearchToggle() {
    const btn = document.getElementById('searchToggleBtn');
    if (!btn) return;
    try {
        const status = await api('/api/search/status');
        renderSearchToggle(btn, status.enabled);
    } catch (e) {
        renderSearchToggle(btn, true);
    }
}

/** 渲染联网搜索开关按钮样式 */
function renderSearchToggle(btn, enabled) {
    if (enabled) {
        btn.classList.add('btn-primary');
        btn.title = '联网搜索已开启，点击关闭';
        btn.innerHTML = `${ic('globe', 'ic-sm')} 联网搜索：开`;
    } else {
        btn.classList.remove('btn-primary');
        btn.title = '联网搜索已关闭，点击开启';
        btn.innerHTML = `${ic('globe', 'ic-sm')} 联网搜索：关`;
    }
}

/** 切换联网搜索开关 */
async function toggleSearch() {
    const btn = document.getElementById('searchToggleBtn');
    if (!btn) return;
    const currentOn = btn.classList.contains('btn-primary');
    const newVal = currentOn ? '0' : '1';
    try {
        await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'search.enabled', value: newVal }) });
        renderSearchToggle(btn, !currentOn);
        toast(newVal === '1' ? '✅ 联网搜索已开启' : '✅ 联网搜索已关闭');
    } catch (e) {
        toast('❌ 切换失败', 'error');
    }
}

/** 加载当前子对话的历史聊天记录并渲染到对话区 */
async function loadChatHistory() {
    const box = document.getElementById('chatBox');
    if (!box) return;
    try {
        const url = window.currentSession ? `/api/chat/history?session=${encodeURIComponent(window.currentSession)}` : '/api/chat/history';
        const messages = await api(url);
        if (messages.length === 0) {
            box.innerHTML = welcomeMessage();
        } else {
            box.innerHTML = messages.map(m => {
                if (m.role === 'user') {
                    return `<div class="msg user"><div class="avatar">${ic('user')}</div><div class="bubble">${esc(m.content)}</div></div>`;
                }
                return `<div class="msg bot"><div class="avatar">${ic('bot')}</div><div class="bubble">${buildReplyHtml(m.content, null)}</div></div>`;
            }).join('');
            renderCharts(box);
        }
        const countEl = document.getElementById('chatCount');
        if (countEl) countEl.textContent = messages.length > 0 ? `${messages.length} 条历史消息` : '';
        box.scrollTop = box.scrollHeight;
    } catch (e) {
        box.innerHTML = welcomeMessage();
    }
}

// ============ 子对话（多会话）管理 ============

/** 加载会话列表并渲染到下拉框 */
async function loadSessions() {
    const sel = document.getElementById('sessionSelect');
    if (!sel) return;
    try {
        const resp = await api('/api/chat/history/sessions');
        const sessions = resp.sessions || [];
        const options = sessions.map(s => {
            const title = (s.firstUserMessage || '新对话').substring(0, 20);
            return `<option value="${escAttr(s.sessionKey)}">${esc(title)}（${s.messageCount}条）</option>`;
        }).join('');
        sel.innerHTML = options || '<option value="">无对话</option>';
        if (window.currentSession) {
            sel.value = window.currentSession;
        }
    } catch (e) {}
}

/** 新建子对话并切换过去 */
async function newSession() {
    try {
        const resp = await api('/api/chat/history/session', { method: 'POST', body: '{}' });
        window.currentSession = resp.sessionKey;
        await loadSessions();
        await loadChatHistory();
        toast('✅ 已创建新对话');
    } catch (e) {
        toast('❌ 新建失败', 'error');
    }
}

/** 切换子对话 */
async function switchSession(key) {
    window.currentSession = key || null;
    await loadChatHistory();
    await loadSessions();
}

/** 删除当前子对话窗口及其全部记录 */
async function deleteCurrentSession() {
    if (!window.currentSession) {
        toast('当前没有可删除的对话', 'warn');
        return;
    }
    openModal({
        title: '删除子对话',
        body: `<p style="line-height:1.7">确定删除当前子对话窗口及其全部记录吗？<br><span class="muted">此操作不可恢复。</span></p>`,
        okText: '删除',
        okClass: 'btn-danger',
        onOk: async () => {
            await api(`/api/chat/history/session/${encodeURIComponent(window.currentSession)}`, { method: 'DELETE' });
            window.currentSession = null;
            await loadSessions();
            await loadChatHistory();
            toast('✅ 子对话已删除');
        }
    });
}

/** 首屏欢迎语（无历史时展示） */
function welcomeMessage() {
    return `<div class="msg bot">
        <div class="avatar">${ic('bot')}</div>
        <div class="bubble">你好！我是你的生活助理。我可以：<br>
        · 查询天气、今日热点，规划出行与生活安排<br>
        · 检索本地知识库中的资料（文本/表格/PPT）<br>
        · 联网搜索最新信息（如「搜索…」「查一下…」）<br>
        · 管理日程提醒、任务清单，处理生活数据<br>
        · 自动搜集编写 Skill/MCP 技能<br>
        · 阅读/修改项目库文件夹（新增工具脚本、修改代码，危险操作需你确认）<br>
        · 对数据做统计分析并生成图表（柱状图/折线图/饼图）<br>
        · 记住你的偏好和重要信息<br><br>
        <span class="suggest" onclick="quickChat('获取天气 今日热点')">「获取天气 今日热点」</span>
        <span class="suggest" onclick="quickChat('帮我规划一份周末出行安排')">「周末出行规划」</span>
        <span class="suggest" onclick="quickChat('搜索一下今天的热门生活资讯')">「搜索今日资讯」</span>
        <span class="suggest" onclick="quickChat('总结知识库里的重要信息')">「总结知识库」</span>
        <span class="suggest" onclick="quickChat('对数据做分析并生成图表')">「数据分析图表」</span></div>
    </div>`;
}

/** 清空聊天记录（回滚对话），恢复为初始欢迎界面 */
async function clearChat() {
    openModal({
        title: '清空/回滚对话',
        body: `<p style="line-height:1.7">确定清空当前所有聊天记录吗？<br><span class="muted">清空后对话将回到初始状态，此操作不可恢复。</span></p>`,
        okText: '确认清空',
        okClass: 'btn-danger',
        onOk: async () => {
            const url = window.currentSession ? `/api/chat/history?session=${encodeURIComponent(window.currentSession)}` : '/api/chat/history';
            await api(url, { method: 'DELETE' });
            toast('✅ 聊天记录已清空');
            const box = document.getElementById('chatBox');
            if (box) {
                box.innerHTML = welcomeMessage();
                document.getElementById('chatCount').textContent = '';
            }
            await loadSessions();
        }
    });
}

async function loadDashboardStats() {
    try {
        const [kb, monitor, tasks, mem] = await Promise.all([
            api('/api/knowledge'),
            api('/api/monitor/overview'),
            api('/api/tasks'),
            api('/api/memory')
        ]);
        document.getElementById('statKb').textContent = kb.length;
        document.getElementById('statMate').textContent = monitor.summary.working + '/' + monitor.summary.total;
        document.getElementById('statRunning').textContent = tasks.filter(t => t.status === 'in_progress').length;
        document.getElementById('statMem').textContent = mem.length;
    } catch (e) {}
}

async function checkApiConfig() {
    try {
        const settings = await api('/api/settings');
        const key = settings.find(s => s.settingKey === 'api.key');
        return key && key.settingValue;
    } catch (e) { return false; }
}

function quickChat(msg) {
    const input = document.getElementById('chatMsg');
    if (input) {
        input.value = msg;
        sendChat();
    }
}

async function sendChat() {
    const input = document.getElementById('chatMsg');
    const box = document.getElementById('chatBox');
    const msg = input.value.trim();
    if (!msg) return;
    input.value = '';

    // 发送新消息时清除上一轮的技能/MCP 草稿（流式完成后不再清空，需保留供"一键导入"按钮使用）
    window.lastDraft = null;

    // 天气请求：附加用户所在城市，避免天气工具按服务器 IP 定位（返回非用户本地天气）
    let sendMsg = msg;
    if (/(天气|气温|温度|下雨|晴|多云|冷不冷|热不热|天气预报)/.test(msg)) {
        let city = '';
        try { city = await getUserCity(); } catch (e) { city = ''; } // 定位失败不阻塞发送
        if (city) {
            sendMsg = `${msg}\n【用户当前所在城市：${city}】`;
        }
    }

    // 保存用户消息（记录当前子对话，保存原始消息）
    api('/api/chat/history', { method: 'POST', body: JSON.stringify({ role: 'user', content: msg, session: window.currentSession || null }) });

    box.innerHTML += `<div class="msg user"><div class="avatar">${ic('user')}</div><div class="bubble">${esc(msg)}</div></div>`;
    box.scrollTop = box.scrollHeight;

    // 创建流式回复气泡（边生成边展示，类似思考过程），并附加"终止"按钮
    const replyId = 'rpy' + Date.now();
    box.innerHTML += `<div class="msg bot" id="${replyId}"><div class="avatar">${ic('bot')}</div><div class="bubble">${typingDots()}</div></div>`;
    box.scrollTop = box.scrollHeight;

    // 终止控制器：点击发送栏"停止"按钮时 abort 流式请求 + 调后端 stop 中断任务
    const abortCtrl = new AbortController();
    window.__abortCtrl = abortCtrl;
    let stopped = false;
    // 显示发送栏"⏹ 停止"按钮（全局单例，首个 token 后显示）
    const stopBtn = document.getElementById('chatStopBtn');
    const showStopBtn = () => {
        if (!stopped && stopBtn) stopBtn.style.display = 'inline-block';
    };
    // 流式跟随：若用户上滚查看历史则不强制滚动到底部
    // 用文档级捕获监听：切页恢复的是克隆 DOM，原 box 可能已被替换，捕获模式对任意 chatBox 生效
    let userScrolledUp = false;
    const trackScroll = () => {
        const live = document.getElementById('chatBox') || box;
        userScrolledUp = live.scrollTop + live.clientHeight < live.scrollHeight - 40;
    };
    document.removeEventListener('scroll', trackScroll, true);
    document.addEventListener('scroll', trackScroll, true);

    let fullReply = '';
    // 注册"页面切换时保存流式中间态"钩子（navigate 会调用；流式完成后以完整回复替换中间态记录）
    window.__streamNavSaved = false;
    window.__streamPartialCleanup = null;
    window.__pendingStreamSave = () => {
        if (fullReply && fullReply.trim()) {
            // 保存中间态回复，并记录其 id：流式完成时先删除中间态再保存完整回复，避免重复/丢失
            window.__streamPartialCleanup = api('/api/chat/history', {
                method: 'POST',
                body: JSON.stringify({ role: 'assistant', content: fullReply, session: window.currentSession || null })
            }).then(resp => {
                if (resp && resp.success && resp.message && resp.message.id) {
                    return api(`/api/chat/history/${resp.message.id}`, { method: 'DELETE' });
                }
                return null;
            }).catch(() => null);
        }
    };
    // 流式渲染节流：合并相邻 token，避免每个 token 都全量重渲染 Markdown 与图表
    let streamRenderTimer = null;
    let streamRenderPending = false;
    const flushStreamRender = () => {
        if (streamRenderTimer) { clearTimeout(streamRenderTimer); streamRenderTimer = null; }
        streamRenderPending = false;
    };
    const throttleRender = (text) => {
        if (!streamRenderPending) {
            updateBotBubble(replyId, text);
            streamRenderPending = true;
        }
        if (streamRenderTimer) clearTimeout(streamRenderTimer);
        streamRenderTimer = setTimeout(() => {
            streamRenderTimer = null;
            streamRenderPending = false;
            updateBotBubble(replyId, text);
        }, 60);
    };
    try {
        let pendingConfirm = null; // 待确认的高危操作
        let pendingSummary = null; // 长回复摘要（供折叠展示）
        const resp = await fetchStream('/api/assistant/chat-stream', {
            message: sendMsg,
            session: window.currentSession || null
        }, {
            signal: abortCtrl.signal,
            onToken: (chunk) => {
                fullReply += chunk;
                throttleRender(fullReply);
                showStopBtn();
                // 流式输出跟随最新内容（用户上滚时暂停跟随；box 可能已被切页后的克隆 DOM 替换）
                if (!userScrolledUp) {
                    const live = document.getElementById('chatBox') || box;
                    live.scrollTop = live.scrollHeight;
                }
            },
            onConfirm: (op, arg1, arg2) => {
                pendingConfirm = { op, arg1, arg2 };
            },
            onSkillDraft: (draft) => {
                window.lastDraft = { type: 'skill', ...draft };
                renderImportButton(replyId, 'skill');
            },
            onMcpDraft: (draft) => {
                window.lastDraft = { type: 'mcp', ...draft };
                renderImportButton(replyId, 'mcp');
            },
            onSummary: (s) => {
                pendingSummary = s;
            },
            onDone: (finalReply) => {
                flushStreamRender();
                fullReply = finalReply || fullReply;
                if (stopBtn) { stopBtn.style.display = 'none'; stopBtn.disabled = false; stopBtn.textContent = '⏹ 停止'; }
                window.__abortCtrl = null;
                // 检测并自动设定后台定时任务（移除标记，不让用户看到原始标记）
                fullReply = processScheduleMarkers(fullReply, window.currentSession || null);
                updateBotBubbleHtml(replyId, buildReplyHtml(fullReply, pendingSummary));
            }
        });
        // 高危操作：弹确认框，用户确认后执行
        if (pendingConfirm) {
            const pc = pendingConfirm;
            const fileOps = ['write_file', 'delete_file', 'run_script', 'mkdir'];
            const isFileOp = fileOps.includes(pc.op);
            const opLabels = { write_file: '写入/覆盖文件（或新增工具脚本）', delete_file: '删除文件/目录', run_script: '执行项目内脚本', mkdir: '创建目录' };
            const body = isFileOp
                ? `<p style="line-height:1.7">助手请求执行<b style="color:var(--red)">文件操作</b>：</p>
                   <div class="form-group" style="margin-top:10px">
                       <label>操作类型</label>
                       <code style="display:block;padding:8px;background:var(--surface-2)">${esc(opLabels[pc.op] || pc.op)}</code>
                   </div>
                   <div class="form-group">
                       <label>目标路径（项目根目录内）</label>
                       <code style="display:block;padding:8px;background:var(--surface-2);word-break:break-all">${esc(pc.arg1)}</code>
                   </div>
                   ${pc.arg2 ? `<div class="form-group"><label>参数 / 内容</label><pre style="white-space:pre-wrap;padding:8px;background:var(--surface-2);font-size:12px;max-height:180px;overflow:auto;margin:0">${esc(pc.arg2)}</pre></div>` : ''}
                   <div class="muted">此操作将修改项目库文件夹，请确认是否允许执行？</div>`
                : `<p style="line-height:1.7">助手请求执行以下操作，涉及<b style="color:var(--red)">高危动作</b>：</p>
                   <div class="form-group" style="margin-top:10px">
                       <label>执行目标</label>
                       <code style="display:block;padding:8px;background:var(--surface-2);word-break:break-all">${esc(pc.arg1)}</code>
                   </div>
                   <div class="muted">请确认是否允许执行？此操作可能修改或删除数据。</div>`;
            openModal({
                title: '⚠️ 高危操作确认',
                body,
                okText: '✅ 确认执行',
                okClass: 'btn-danger',
                cancelText: '取消',
                onOk: async (ov) => {
                    try {
                        const payload = isFileOp
                            ? { op: pc.op, path: pc.arg1, content: pc.arg2 }
                            : { endpoint: pc.arg1, args: pc.arg2 };
                        const r = await api('/api/assistant/confirm', {
                            method: 'POST',
                            body: JSON.stringify(payload)
                        });
                        if (r.success) {
                            const result = r.result || '(无返回)';
                            fullReply += '\n\n✅ 操作已执行，返回结果：\n' + result;
                            updateBotBubbleHtml(replyId, buildReplyHtml(fullReply, pendingSummary));
                            toast('✅ 高危操作已确认执行');
                        } else {
                            fullReply += '\n\n❌ 操作执行失败：' + (r.error || '未知错误');
                            updateBotBubbleHtml(replyId, buildReplyHtml(fullReply, pendingSummary));
                        }
                    } catch (e) {
                        fullReply += '\n\n❌ 操作执行失败';
                        updateBotBubbleHtml(replyId, buildReplyHtml(fullReply, pendingSummary));
                    }
                }
            });
        }
        // 保存助手回复（记录当前子对话）
        // 注意：window.lastDraft 不能在此清空——草稿在流式中已生成，需保留供用户点击"一键导入"。
        // 若流式期间曾切换页面（navigate 已保存中间态），先删除中间态记录再保存完整回复，避免重复或丢失
        const saveFullReply = () => api('/api/chat/history', {
            method: 'POST',
            body: JSON.stringify({ role: 'assistant', content: fullReply, session: window.currentSession || null })
        });
        if (window.__streamPartialCleanup) {
            const cleanup = window.__streamPartialCleanup;
            cleanup.then(saveFullReply).catch(saveFullReply);
        } else {
            saveFullReply();
        }
        window.__streamNavSaved = false;
        window.__streamPartialCleanup = null;
        window.__pendingStreamSave = null;
        updateChatCount();
    } catch (e) {
        flushStreamRender();
        const isAbort = e && (e.name === 'AbortError' || /abort/i.test(String(e && e.message || '')));
        if (stopBtn) { stopBtn.style.display = 'none'; stopBtn.disabled = false; stopBtn.textContent = '⏹ 停止'; }
        window.__abortCtrl = null;
        // 异常/终止路径同样处理中间态：先删除流式期间保存的中间态记录，再保存最终内容
        const saveReplacement = (content) => {
            const saveNow = () => api('/api/chat/history', {
                method: 'POST',
                body: JSON.stringify({ role: 'assistant', content, session: window.currentSession || null })
            });
            if (window.__streamPartialCleanup) {
                const cleanup = window.__streamPartialCleanup;
                cleanup.then(saveNow).catch(saveNow);
            } else {
                saveNow();
            }
        };
        if (isAbort) {
            // 用户主动终止：保留已生成内容，标记为已终止
            if (fullReply && fullReply.trim()) {
                updateBotBubble(replyId, fullReply + '\n\n_（已由用户终止）_');
                saveReplacement(fullReply);
            } else {
                updateBotBubble(replyId, '（任务已被终止）');
            }
        } else {
            // 请求失败：若已累积内容则保存，避免流式内容丢失
            if (fullReply && fullReply.trim()) {
                saveReplacement(fullReply);
            }
            updateBotBubble(replyId, '⚠️ 请求失败，请检查后端服务');
        }
        window.__streamNavSaved = false;
        window.__streamPartialCleanup = null;
        window.__pendingStreamSave = null;
    }
    const liveBox = document.getElementById('chatBox') || box;
    liveBox.scrollTop = liveBox.scrollHeight;
}

/** 停止当前流式任务（发送栏"⏹ 停止"按钮） */
function stopCurrentChat() {
    const btn = document.getElementById('chatStopBtn');
    if (btn) {
        btn.textContent = '⏹ 已停止';
        btn.disabled = true;
    }
    // 前端中断 SSE
    if (window.__abortCtrl) {
        try { window.__abortCtrl.abort(); } catch (e) { }
        window.__abortCtrl = null;
    }
    // 后端中断 AI 线程
    api('/api/assistant/stop', { method: 'POST', body: JSON.stringify({ session: window.currentSession || null }) });
}

/** 打字中的动画点 */
function typingDots() {
    return '<span class="typing">思考中<span>.</span><span>.</span><span>.</span></span>';
}

/** 更新机器人气泡内容（原始文本，渲染为 Markdown 并保留内嵌图表） */
function updateBotBubble(id, text) {
    const el = document.getElementById(id);
    if (el) {
        el.querySelector('.bubble').innerHTML = renderMarkdownWithCharts(text);
        renderCharts(el);
    }
}

/** 更新机器人气泡内容（已构建 HTML，不二次转义） */
function updateBotBubbleHtml(id, html) {
    const el = document.getElementById(id);
    if (el) {
        el.querySelector('.bubble').innerHTML = html;
        renderCharts(el);
    }
}

const CHART_RE = /<div class=["']?chart["']?[^>]*data-option=["'][^"']*["'][^>]*><\/div>/gi;

/** 从文本中提取图表容器占位，返回剥离后的文本与图表 HTML 数组 */
function extractChartMarkers(text) {
    const markers = [];
    let out = '';
    let last = 0;
    let m;
    CHART_RE.lastIndex = 0;
    while ((m = CHART_RE.exec(text)) !== null) {
        out += text.substring(last, m.index);
        const marker = '%%CHART' + markers.length + '%%';
        markers.push(m[0]);
        out += marker;
        last = m.index + m[0].length;
    }
    out += text.substring(last);
    return { text: out, markers };
}

/** 将占位符还原为图表容器 HTML */
function restoreChartMarkers(html, markers) {
    markers.forEach((chartHtml, i) => {
        html = html.split('%%CHART' + i + '%%').join(chartHtml);
    });
    return html;
}

/**
 * 将聊天助手回复渲染为 Markdown（适配标题/列表/加粗/表格/代码块等）：
 *  - 先提取图表容器占位（保留原样供 ECharts 渲染）
 *  - 其余文本先 esc 转义防 XSS，再用 marked 渲染 markdown
 *  - 还原图表容器，整体包裹 .md-render 样式
 */
function renderMarkdownWithCharts(text) {
    if (!text) return '';
    const { text: clean, markers } = extractChartMarkers(String(text));
    let html = esc(clean);
    if (typeof marked !== 'undefined' && marked.parse) {
        try {
            html = marked.parse(html, { breaks: true });
        } catch (e) { /* 渲染失败降级为转义文本 */ }
    }
    html = restoreChartMarkers(html, markers);
    return `<div class="md-render">${html}</div>`;
}

/** 将后端图表数据 {type,title,x,series} 转为 ECharts option */
function buildChartOption(opt, isDark) {
    if (!opt) return {};
    const dark = isDark !== undefined ? isDark : document.documentElement.dataset.theme === 'dark';
    const axisColor = dark ? '#9aa3b5' : '#61666b';
    const titleColor = dark ? '#e4e6eb' : undefined;
    const splitColor = dark ? 'rgba(255,255,255,.12)' : undefined;
    const titleCfg = opt.title ? { text: opt.title, left: 'center', top: 4, textStyle: { fontSize: 14, color: titleColor } } : undefined;
    if (opt.type === 'pie') {
        const ser0 = (opt.series && opt.series[0]) || {};
        return {
            tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
            backgroundColor: 'transparent',
            legend: { orient: 'vertical', left: 10, top: 'center', type: 'scroll', textStyle: { color: axisColor } },
            title: titleCfg,
            series: [{
                name: ser0.name || '数据',
                type: 'pie',
                radius: ['32%', '62%'],
                center: ['55%', '55%'],
                data: (opt.x || []).map((x, i) => ({ name: String(x), value: (ser0.data && ser0.data[i]) || 0 }))
            }]
        };
    }
    const isLine = opt.type === 'line';
    const series = (opt.series || []).map(s => ({
        name: s.name,
        type: isLine ? 'line' : 'bar',
        smooth: isLine,
        symbolSize: isLine ? 7 : undefined,
        data: s.data,
        itemStyle: isLine ? undefined : { borderRadius: [4, 4, 0, 0] },
        areaStyle: isLine ? { opacity: 0.12 } : undefined
    }));
    return {
        tooltip: { trigger: 'axis' },
        legend: { data: (opt.series || []).map(s => s.name), top: 30, textStyle: { color: axisColor } },
        title: titleCfg,
        grid: { left: 48, right: 20, top: 62, bottom: 44 },
        xAxis: {
            type: 'category', data: opt.x || [],
            axisLabel: { rotate: (opt.x || []).length > 6 ? 30 : 0, color: axisColor },
            axisLine: { lineStyle: { color: splitColor || 'rgba(15,17,21,.15)' } }
        },
        yAxis: {
            type: 'value',
            axisLabel: { color: axisColor },
            splitLine: { lineStyle: { color: splitColor || 'rgba(15,17,21,.08)' } }
        },
        series
    };
}

/** 判断图表数据是否可用（无数据时跳过渲染，显示占位） */
function hasChartData(opt) {
    if (!opt) return false;
    if (opt.x && opt.x.length) return true;
    return !!(opt.series || []).some(s => s && s.data && s.data.length);
}

/** 全局单例 resize 监听：统一缩放所有已渲染图表（替代每图一个监听） */
function ensureChartResizeHandler() {
    if (window.__chartResizeBound) return;
    window.__chartResizeBound = true;
    let timer = null;
    window.addEventListener('resize', () => {
        if (timer) clearTimeout(timer);
        timer = setTimeout(() => {
            const charts = window.__charts;
            if (!charts) return;
            Object.values(charts).forEach(c => { try { c.resize(); } catch (e) { } });
        }, 120);
    });
}

/** 扫描容器内 .chart[data-option] 元素并用 ECharts 渲染 */
function renderCharts(container) {
    if (!container || typeof echarts === 'undefined') return;
    container.querySelectorAll('.chart[data-option]').forEach(el => {
        if (el.dataset.rendered) return;
        // 折叠隐藏内容延后渲染（隐藏容器中 echarts 无法正确初始化）
        if (el.closest('.collapsed-body') && el.closest('.collapsed-body').classList.contains('hide')) return;
        let opt = null;
        try { opt = JSON.parse(el.dataset.option); } catch (e) { el.remove(); return; }
        el.dataset.rendered = '1';
        el.style.width = '100%';
        el.style.height = '340px';
        // 空数据容错：无可用数据时显示占位提示，避免空白图表
        if (!hasChartData(opt)) {
            el.innerHTML = ic('chart') + ' 暂无数据';
            el.style.display = 'flex';
            el.style.alignItems = 'center';
            el.style.justifyContent = 'center';
            el.style.color = 'var(--text-light, #999)';
            return;
        }
        const chart = echarts.init(el);
        chart.setOption(buildChartOption(opt, document.documentElement.dataset.theme === 'dark'));
        // 记录实例供主题切换重渲染
        window.__charts = window.__charts || {};
        window.__charts[el.id] = chart;
        ensureChartResizeHandler();
        setTimeout(() => chart.resize(), 300);
    });
}

/** 通过 fetch 读取 SSE 流式接口 */
async function fetchStream(url, body, handlers) {
    // 支持通过 handlers.signal（AbortController.signal）终止流式请求
    const controller = handlers && handlers.signal
        ? { signal: handlers.signal }
        : {};
    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        ...controller
    });
    if (!response.ok || !response.body) {
        throw new Error('HTTP ' + response.status);
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let finalReply = '';
    while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        // 解析 SSE 事件（以空行分隔）
        let idx;
        while ((idx = buffer.indexOf('\n\n')) >= 0) {
            const eventBlock = buffer.slice(0, idx);
            buffer = buffer.slice(idx + 2);
            // 提取 event 名称
            const eventNameMatch = eventBlock.match(/^event:\s*(.+)$/m);
            const eventName = eventNameMatch ? eventNameMatch[1].trim() : 'message';
            // 提取 data（多行 data: 需拼接，SseEmitter 会把换行拆成多行 data）
            const dataLines = eventBlock.match(/^data:\s*(.*)$/gm) || [];
            const data = dataLines.map(l => l.replace(/^data:\s*/, '')).join('\n');
            if (eventName === 'token' && handlers.onToken) {
                handlers.onToken(data);
            } else if (eventName === 'confirm' && handlers.onConfirm) {
                // 高危操作确认：格式 op|arg1|arg2（mcp|endpoint|args 或 write_file|path|content）
                // 用 slice join 保证内容中的 | 不被拆散
                const parts = data.split('|');
                handlers.onConfirm(parts[0] || '', parts[1] || '', parts.slice(2).join('|') || '');
            } else if (eventName === 'skill_draft' && handlers.onSkillDraft) {
                try { handlers.onSkillDraft(JSON.parse(data)); } catch (e) { }
            } else if (eventName === 'mcp_draft' && handlers.onMcpDraft) {
                try { handlers.onMcpDraft(JSON.parse(data)); } catch (e) { }
            } else if (eventName === 'summary' && handlers.onSummary) {
                try { handlers.onSummary(JSON.parse(data)); } catch (e) { }
            } else if (eventName === 'done' && handlers.onDone) {
                finalReply = data;
                handlers.onDone(data);
            } else if (eventName === 'error') {
                throw new Error(data || '流式错误');
            }
        }
    }
    return finalReply;
}

/** 渲染单个运行步骤 */
function renderStep(s) {
    let icon = '⬜';
    let color = 'var(--text-light)';
    if (s.status === 'done') { icon = '✅'; color = '#16a34a'; }
    else if (s.status === 'running') { icon = '⏳'; color = '#f59e0b'; }
    else if (s.status === 'fail') { icon = '❌'; color = '#dc2626'; }
    else if (s.status === 'skip') { icon = '⏭️'; color = '#9ca3af'; }
    const dur = s.durationMs ? ` <span class="muted">(${s.durationMs}ms)</span>` : '';
    return `<div style="padding:1px 0"><span style="color:${color}">${icon}</span> <strong>${esc(s.step)}</strong>${dur}${s.detail ? `<span class="muted"> — ${esc(s.detail)}</span>` : ''}</div>`;
}

/** 更新顶部历史消息计数 */
function updateChatCount() {
    const countEl = document.getElementById('chatCount');
    if (!countEl) return;
    try {
        api('/api/chat/history').then(msgs => {
            countEl.textContent = msgs.length > 0 ? `${msgs.length} 条历史消息` : '';
        });
    } catch (e) {}
}

/**
 * 构建回复 HTML：超长回复自动折叠为"总结 + 展开全文"卡片。
 * @param text 完整回复文本
 * @param summary 后端生成的摘要对象 {summary, length}，可为 null
 */
function buildReplyHtml(text, summary) {
    const THRESHOLD = 220; // 超过该长度自动折叠
    if (!text || text.length <= THRESHOLD) return renderMarkdownWithCharts(text);
    let summaryText = (summary && summary.summary && summary.summary.trim())
        ? summary.summary.trim()
        : extractLeadSummary(text);
    // 空泛总结兜底：若后端总结是"无法提供/无内容"等无效信息，改用正文首段提取
    const emptyWords = ['无法提供', '无法总结', '无法获取', '暂无法', '没有内容', '无内容', '无相关信息', '无法回答', '抱歉'];
    const isEmptySummary = summaryText.length < 6 || emptyWords.some(w => summaryText.includes(w));
    if (isEmptySummary) summaryText = extractLeadSummary(text);
    // 防御性硬截断：后端理论上已截断到 10000 字以内，但若 LLM 偶尔突破再补一刀
    if (summaryText.length > 10200) {
        let p = -1;
        for (let i = 0; i < 10200 && i < summaryText.length; i++) {
            const c = summaryText.charAt(i);
            if (c === '。' || c === '！' || c === '？' || c === ';' || c === '、') p = i;
        }
        summaryText = p > 4000 ? summaryText.substring(0, p + 1) : summaryText.substring(0, 10000) + '…';
    }
    return `<div class="collapsed-reply">
        <div class="collapsed-head">📌 总结：${esc(summaryText)}</div>
        <button class="btn btn-sm collapsed-toggle" onclick="toggleCollapse(this)">展开全文（共 ${text.length} 字）</button>
        <div class="collapsed-body hide">${renderMarkdownWithCharts(text)}</div>
    </div>`;
}

/** 从文本中提取所有媒体卡片 HTML（供独立气泡渲染） */
/** 无后端摘要或摘要无效时，前端规则兜底：取首个非空段落前 10000 字 */
function extractLeadSummary(text) {
    const stripped = String(text || '')
        .replace(/\[CHART_JSON\][\s\S]*?\[\/CHART_JSON\]/g, '')
        .replace(/<div class="?chart"?[\s\S]*?<\/div>/g, ' ')
        .replace(/【内置工具[\s\S]*?返回】/g, ' ')
        .replace(/<[^>]+>/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
    const clean = stripped || '无摘要';
    return clean.length > 10000 ? clean.substring(0, 10000) + '…' : clean;
}

/** 折叠卡片展开/收起 */
function toggleCollapse(btn) {
    const wrap = btn.closest('.collapsed-reply');
    if (!wrap) return;
    const body = wrap.querySelector('.collapsed-body');
    if (!body) return;
    const willShow = body.classList.contains('hide');
    body.classList.toggle('hide', !willShow);
    const total = body.textContent.length;
    btn.textContent = willShow ? `收起全文 ▲` : `展开全文（共 ${total} 字）`;
    if (willShow) {
        // 展开后再渲染内嵌图表（隐藏容器中初始化无效）
        renderCharts(body);
        setTimeout(() => renderCharts(body), 300);
    }
}

/**
 * 获取用户所在城市：浏览器 Geolocation（优先，需授权）+ IP 定位（兜底）。
 * 供天气查询使用，让 wttr.in 按用户位置而非服务器 IP 返回天气。
 * 定位失败返回空字符串（此时后端按服务器 IP 兜底）。
 */
/** 兼容旧浏览器：优先 AbortSignal.timeout，降级 AbortController+setTimeout，都不支持则无 signal */
function safeAbortSignal(ms) {
    try {
        if (typeof AbortSignal !== 'undefined' && AbortSignal.timeout) return AbortSignal.timeout(ms);
        if (typeof AbortController !== 'undefined') {
            const c = new AbortController();
            setTimeout(() => c.abort(), ms);
            return c.signal;
        }
    } catch (e) { }
    return undefined;
}

function getUserCity() {
    return new Promise((resolve) => {
        let settled = false;
        const done = (city) => {
            if (!settled) { settled = true; resolve(city || ''); }
        };
        // 2. IP 定位多服务级联（依次尝试，任一成功即返回用户浏览器出口 IP 所在城市）
        const IP_SERVICES = [
            { url: 'https://ipapi.co/json/', pick: d => d.city },
            { url: 'https://ipinfo.io/json', pick: d => d.city },
            { url: 'https://ip-api.com/json/?lang=zh-CN', pick: d => d.city }
        ];
        const ipFallback = (idx) => {
            const i = idx || 0;
            if (i >= IP_SERVICES.length) { done(''); return; }
            const svc = IP_SERVICES[i];
            fetch(svc.url, { signal: safeAbortSignal(4000) })
                .then(r => r.json())
                .then(d => {
                    const c = svc.pick(d);
                    if (c) done(c);
                    else ipFallback(i + 1);
                })
                .catch(() => ipFallback(i + 1));
        };
        // 1. 浏览器地理定位（最高精度，需用户授权）→ 逆地理编码多服务级联
        if (navigator.geolocation) {
            navigator.geolocation.getCurrentPosition(
                (pos) => {
                    const { latitude, longitude } = pos.coords;
                    const REV = [
                        { url: `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${latitude}&longitude=${longitude}&localityLanguage=zh`, pick: d => d.city || d.locality },
                        { url: `https://nominatim.openstreetmap.org/reverse?format=json&lat=${latitude}&lon=${longitude}&accept-language=zh`, pick: d => d.address && (d.address.city || d.address.town || d.address.county) }
                    ];
                    const revTry = (ri) => {
                        if (ri >= REV.length) { ipFallback(0); return; }
                        const s = REV[ri];
                        fetch(s.url, { signal: safeAbortSignal(4000) })
                            .then(r => r.json())
                            .then(d => {
                                const c = s.pick(d);
                                if (c) done(c);
                                else revTry(ri + 1);
                            })
                            .catch(() => revTry(ri + 1));
                    };
                    revTry(0);
                },
                () => ipFallback(0),
                { timeout: 3500, maximumAge: 600000 }
            );
        } else {
            ipFallback(0);
        }
        // 总超时兜底（5 秒），避免阻塞发送；失败返回空 → 后端按服务器 IP 兜底
        setTimeout(() => done(''), 5000);
    });
}

/** 在机器人消息容器内（气泡之后）渲染一键导入按钮（Skill 或 MCP 草稿）。
 *  放在气泡外侧，避免后续 updateBotBubble 重置气泡 innerHTML 时按钮被清掉。
 *  点击事件采用 chatBox 委托（事件委托），避免 DOM 缓存克隆时丢失 onclick。 */
function renderImportButton(replyId, type) {
    const el = document.getElementById(replyId);
    if (!el) return;
    const bubble = el.querySelector('.bubble');
    if (!bubble) return;
    // 避免重复渲染
    const existing = el.querySelector('.import-draft-btn');
    if (existing) existing.remove();
    const btn = document.createElement('button');
    btn.className = 'btn btn-primary import-draft-btn ' + (type === 'skill' ? 'import-skill' : 'import-mcp');
    btn.style.marginTop = '10px';
    btn.style.display = 'block';
    btn.style.fontSize = '14px';
    btn.textContent = type === 'skill' ? '✅ 一键导入这个技能' : '✅ 一键导入这个 MCP';
    bubble.insertAdjacentElement('afterend', btn);
}

/** 聊天框事件委托：处理一键导入按钮点击（兼容 DOM 缓存克隆的按钮） */
function bindImportButtonDelegation() {
    const box = document.getElementById('chatBox');
    if (!box || box.__delegBound) return;
    box.__delegBound = true;
    box.addEventListener('click', (e) => {
        const btn = e.target.closest('.import-draft-btn');
        if (!btn) return;
        if (btn.classList.contains('import-skill')) {
            importGeneratedSkill(btn);
        } else if (btn.classList.contains('import-mcp')) {
            importGeneratedMcp(btn);
        }
    });
}

/** 一键导入聊天助手生成的 Skill 草稿 */
async function importGeneratedSkill(btn) {
    const draft = window.lastDraft;
    if (!draft || draft.type !== 'skill') { toast('没有可导入的技能草稿', 'warn'); return; }
    // 兼容两种草稿结构：直接属性 {name,...} 或 {data:{name,...}} 包装
    const d = draft.data || draft;
    if (!d.name || !d.content) { toast('技能草稿数据不完整，请重新生成', 'error'); return; }
    const restoreBtn = () => { btn.disabled = false; btn.textContent = '✅ 一键导入这个技能'; };
    btn.disabled = true;
    btn.textContent = '导入中...';
    try {
        const resp = await api('/api/skills/import-draft', {
            method: 'POST',
            body: JSON.stringify({
                name: d.name,
                category: d.category,
                description: d.description,
                content: d.content
            })
        });
        if (!resp) {
            toast('❌ 导入失败：网络错误（请检查后端服务）', 'error');
            restoreBtn();
            return;
        }
        if (resp.success) {
            toast('✅ 技能已导入');
            btn.textContent = '✅ 已导入';
        } else if (resp.blocked) {
            toast('⛔ 已被安全扫描拦截：' + (resp.error || ''), 'error');
            btn.textContent = '⛔ 已拦截';
        } else {
            toast('❌ ' + (resp.error || '导入失败'), 'error');
            restoreBtn();
        }
    } catch (e) {
        toast('❌ 导入失败：' + (e && e.message || '未知异常'), 'error');
        restoreBtn();
    }
}

/** 一键导入聊天助手生成的 MCP 草稿 */
async function importGeneratedMcp(btn) {
    const draft = window.lastDraft;
    if (!draft || draft.type !== 'mcp') { toast('没有可导入的 MCP 草稿', 'warn'); return; }
    // 兼容两种草稿结构：直接属性 {name,...} 或 {data:{name,...}} 包装
    const d = draft.data || draft;
    if (!d.name) { toast('MCP 草稿数据不完整，请重新生成', 'error'); return; }
    btn.disabled = true;
    btn.textContent = '导入中...';
    try {
        const resp = await api('/api/mcp/import-draft', {
            method: 'POST',
            body: JSON.stringify({
                name: d.name,
                serverType: d.serverType,
                endpoint: d.endpoint,
                description: d.description,
                config: d.config || null
            })
        });
        if (resp.success) {
            toast('✅ MCP 已导入');
            btn.textContent = '✅ 已导入';
        } else if (resp.blocked) {
            toast('⛔ 已被安全扫描拦截：' + (resp.error || ''), 'error');
            btn.textContent = '⛔ 已拦截';
        } else {
            toast('❌ ' + (resp.error || '导入失败'), 'error');
            btn.disabled = false;
            btn.textContent = '✅ 一键导入这个 MCP';
        }
    } catch (e) {
        toast('❌ 导入失败', 'error');
        btn.disabled = false;
        btn.textContent = '✅ 一键导入这个 MCP';
    }
}

// ---------- 2. 进程监控 Monitor ----------
async function renderMonitor() {
    mainContent.innerHTML = `
        <div class="page-header">
            <div>
                <h1>${ic('activity')} Teammate 进程监控</h1>
                <div class="sub">实时监控各工作进程状态与进度汇总（WebSocket 实时推送）</div>
            </div>
            <button class="btn btn-primary" onclick="spawnMate()">${ic('plus', 'ic-sm')} 新增进程</button>
        </div>
        <div class="grid grid-4" id="monitorStats"></div>
        <div class="card">
            <h3>${ic('refresh', 'ic-sm')} 进程实时状态 <span class="muted" style="font-weight:400;font-size:12px">（WebSocket 实时刷新）</span></h3>
            <table>
                <thead><tr>
                    <th>进程名称</th><th>角色</th><th>状态</th><th>当前任务</th>
                    <th style="width:180px">进度</th><th>心跳</th><th>操作</th>
                </tr></thead>
                <tbody id="mateTable"></tbody>
            </table>
        </div>
        <div class="card">
            <h3>📜 实时日志</h3>
            <div class="log-box" id="monitorLogs"></div>
        </div>`;

    await loadMonitorOverview();
}

async function loadMonitorOverview() {
    try {
        const data = await api('/api/monitor/overview');
        const s = data.summary;
        document.getElementById('monitorStats').innerHTML = `
            <div class="stat-card"><div class="label">进程总数</div><div class="num">${s.total}</div><div class="stat-icon">🧩</div></div>
            <div class="stat-card"><div class="label" style="color:var(--green)">工作中</div><div class="num">${s.working}</div><div class="stat-icon">⚡</div></div>
            <div class="stat-card"><div class="label" style="color:var(--orange)">待命中</div><div class="num">${s.idle}</div><div class="stat-icon">💤</div></div>
            <div class="stat-card"><div class="label">平均进度</div><div class="num">${s.avgProgress}%</div><div class="stat-icon">📈</div></div>`;

        const tbody = document.getElementById('mateTable');
        tbody.innerHTML = data.teammates.map(t => `
            <tr>
                <td><strong>${esc(t.name)}</strong></td>
                <td>${esc(t.role || '-')}</td>
                <td>${statusTag(t.status)}</td>
                <td class="task-cell">${esc(t.currentTask || '待命')}</td>
                <td>
                    <div class="row">
                        <div class="progress-wrap flex-1"><div class="progress-bar" style="width:${t.progress||0}%"></div></div>
                        <span class="muted">${t.progress||0}%</span>
                    </div>
                </td>
                <td class="muted">${t.heartbeatAt ? new Date(t.heartbeatAt).toLocaleTimeString() : '-'}</td>
                <td>
                    <div class="row">
                        <button class="btn btn-sm" onclick="editMate(${jsStr(t.name)},${jsStr(t.role||'')},${jsStr(t.currentTask||'')})">${ic('edit', 'ic-sm')} 编辑</button>
                        <button class="btn btn-sm btn-danger" onclick="stopMate(${jsStr(t.name)})">停止</button>
                        <button class="btn btn-sm btn-danger" onclick="deleteMate(${jsStr(t.name)})">${ic('trash', 'ic-sm')} 删除</button>
                    </div>
                </td>
            </tr>`).join('') || '<tr><td colspan="7" class="center muted">暂无进程，点击「新增进程」创建</td></tr>';

        const logsEl = document.getElementById('monitorLogs');
        logsEl.innerHTML = data.logs.map(l =>
            `<div class="log-line"><span class="log-time">${new Date(l.createdAt).toLocaleTimeString()}</span> <span class="log-level lv-${(l.level||'info').toLowerCase()}">${esc(l.level)}</span> <span class="log-src">${esc(l.source)}</span>: ${esc(l.message)}</div>`
        ).join('') || '<span class="muted">暂无日志</span>';
        logsEl.scrollTop = logsEl.scrollHeight;
    } catch (e) {}
}

async function spawnMate() {
    openModal({
        title: '➕ 新增 Teammate 进程',
        body: `
            <div class="form-group">
                <label>进程名称</label>
                <input id="mName" placeholder="mate-xxx" value="mate-${Math.floor(Math.random()*1000)}">
            </div>
            <div class="form-group">
                <label>角色 / 职责</label>
                <input id="mRole" placeholder="如：客服处理员" value="通用办公助手">
            </div>
            <div class="form-group">
                <label>初始任务（可选）</label>
                <input id="mTask" placeholder="待命">
            </div>`,
        okText: '启动进程',
        onOk: async (ov) => {
            const name = ov.querySelector('#mName').value.trim();
            const role = ov.querySelector('#mRole').value.trim();
            const task = ov.querySelector('#mTask').value.trim();
            if (!name) { toast('请输入进程名称', 'warn'); return false; }
            await api('/api/teammates', { method: 'POST', body: JSON.stringify({ name, role: role || '通用办公助手', task }) });
            toast('✅ 进程已启动');
            await loadMonitorOverview();
        }
    });
}

async function stopMate(name) {
    openModal({
        title: '🛑 停止进程',
        body: `<p style="line-height:1.7">确定停止进程 <strong>${esc(name)}</strong> 吗？<br><span class="muted">停止后将进入 SHUTDOWN 状态，不再认领任务。</span></p>`,
        okText: '停止',
        okClass: 'btn-danger',
        onOk: async () => {
            await api(`/api/teammates/${name}/shutdown`, { method: 'POST', body: '{}' });
            toast('✅ 已停止');
            await loadMonitorOverview();
        }
    });
}

async function editMate(name, role, task) {
    openModal({
        title: '编辑 Teammate',
        body: `
            <div class="form-group">
                <label>进程名称（不可修改）</label>
                <input value="${escAttr(name)}" disabled>
            </div>
            <div class="form-group">
                <label>角色 / 职责</label>
                <input id="mRole" value="${escAttr(role || '')}" placeholder="如：客服处理员">
            </div>
            <div class="form-group">
                <label>任务描述</label>
                <input id="mTask" value="${escAttr(task || '')}" placeholder="待命">
            </div>`,
        okText: '保存修改',
        onOk: async (ov) => {
            const newRole = ov.querySelector('#mRole').value.trim();
            const newTask = ov.querySelector('#mTask').value.trim();
            if (!newRole && !newTask) { toast('请至少填写一项', 'warn'); return false; }
            const resp = await api(`/api/teammates/${encodeURIComponent(name)}`, {
                method: 'PUT',
                body: JSON.stringify({ role: newRole, task: newTask })
            });
            if (resp.success) {
                toast('✅ 已保存');
                await loadMonitorOverview();
            } else {
                toast('❌ ' + (resp.error || '保存失败'), 'error');
            }
        }
    });
}

async function deleteMate(name) {
    openModal({
        title: '删除 Teammate',
        body: `<div style="line-height:1.8">
            <p>确定要删除 Teammate <strong>${esc(name)}</strong> 吗？</p>
            <p class="muted">删除后该进程将被<strong style="color:var(--danger)">永久移除</strong>，相关任务需重新分配，此操作不可恢复。</p>
            <p>如需确认，请在下方输入进程名称：</p>
            <input id="delConfirm" placeholder="输入 ${esc(name)} 以确认" style="width:100%;box-sizing:border-box">
        </div>`,
        okText: '确认删除',
        okClass: 'btn-danger',
        onOk: async (ov) => {
            const typed = ov.querySelector('#delConfirm').value.trim();
            if (typed !== name) {
                toast('⚠️ 输入的名称不匹配，已取消删除', 'warn');
                return false;
            }
            const resp = await api(`/api/teammates/${encodeURIComponent(name)}`, { method: 'DELETE' });
            if (resp.success) {
                toast('🗑 已删除');
                await loadMonitorOverview();
            } else {
                toast('❌ ' + (resp.error || '删除失败'), 'error');
            }
        }
    });
}

// ---------- 3. 任务看板 Tasks ----------
async function renderTasks() {
    mainContent.innerHTML = `
        <div class="page-header">
            <div>
                <h1>${ic('tasks')} 任务看板</h1>
                <div class="sub">大目标拆小任务，支持依赖关系与自动认领（s12）</div>
            </div>
            <button class="btn btn-primary" onclick="createTask()">${ic('plus', 'ic-sm')} 新建任务</button>
        </div>
        <div class="board">
            <div class="board-col">
                <div class="board-head"><span>⏳ 待执行</span><span class="count" id="cntPending">0</span></div>
                <div id="taskPending" class="board-body"></div>
            </div>
            <div class="board-col">
                <div class="board-head"><span>${ic('refresh', 'ic-sm')} 进行中</span><span class="count" id="cntRunning">0</span></div>
                <div id="taskRunning" class="board-body"></div>
            </div>
            <div class="board-col">
                <div class="board-head"><span>${ic('check', 'ic-sm')} 已完成</span><span class="count" id="cntDone">0</span></div>
                <div id="taskDone" class="board-body"></div>
            </div>
        </div>`;
    await loadTasks();
}

async function loadTasks() {
    try {
        const tasks = await api('/api/tasks');
        const pending = document.getElementById('taskPending');
        const running = document.getElementById('taskRunning');
        const done = document.getElementById('taskDone');
        const cP = document.getElementById('cntPending');
        const cR = document.getElementById('cntRunning');
        const cD = document.getElementById('cntDone');
        pending.innerHTML = running.innerHTML = done.innerHTML = '';
        cP.textContent = cR.textContent = cD.textContent = 0;

        tasks.forEach(t => {
            const card = `
                <div class="task-card">
                    <div class="row" style="justify-content:space-between">
                        <strong>${esc(t.title)}</strong>${statusTag(t.status)}
                    </div>
                    ${t.description ? `<div class="muted mt" style="font-size:13px">${esc(t.description)}</div>` : ''}
                    ${t.blockedBy ? `<div class="dep mt">🔗 依赖: ${esc(t.blockedBy)}</div>` : ''}
                    <div class="row mt">
                        <div class="progress-wrap flex-1"><div class="progress-bar ${t.status==='completed'?'bar-green':''}" style="width:${t.progress||0}%"></div></div>
                        <span class="muted">${t.progress||0}%</span>
                    </div>
                    ${t.owner ? `<div class="muted mt" style="font-size:12px">${ic('user', 'ic-sm')} ${esc(t.owner)}</div>` : ''}
                    ${t.result ? `<div class="muted mt" style="font-size:12px">📄 ${esc(t.result)}</div>` : ''}
                    <div class="row mt">
                        ${t.status === 'pending' ? `<button class="btn btn-sm" onclick="completeTask(${jsStr(t.taskKey)})">认领并完成</button>` : ''}
                        <button class="btn btn-sm btn-danger" onclick="deleteTask(${jsStr(t.taskKey)})">删除</button>
                    </div>
                </div>`;
            if (t.status === 'pending') { pending.innerHTML += card; cP.textContent++; }
            else if (t.status === 'in_progress') { running.innerHTML += card; cR.textContent++; }
            else { done.innerHTML += card; cD.textContent++; }
        });

        pending.innerHTML += pending.innerHTML === '' ? '<div class="empty">暂无任务</div>' : '';
        running.innerHTML += running.innerHTML === '' ? '<div class="empty">暂无任务</div>' : '';
        done.innerHTML += done.innerHTML === '' ? '<div class="empty">暂无任务</div>' : '';
    } catch (e) {}
}

async function createTask() {
    // 拉取现有任务供选择依赖
    let existing = [];
    try { existing = await api('/api/tasks'); } catch (e) {}
    const depOptions = existing.map(t => `<option value="${escAttr(t.taskKey)}">${esc(t.taskKey)} - ${esc(t.title)}</option>`).join('');

    openModal({
        title: '新建任务',
        body: `
            <div class="form-group">
                <label>任务标题 <span style="color:var(--red)">*</span></label>
                <input id="tTitle" placeholder="如：收集竞品价格">
            </div>
            <div class="form-group">
                <label>任务描述 <span class="muted">（可用逗号拆分为多个步骤）</span></label>
                <textarea id="tDesc" rows="3" placeholder="如：爬取竞品A价格,爬取竞品B价格,汇总对比表"></textarea>
            </div>
            <div class="form-group">
                <label>依赖任务（多选，完成后方可执行）</label>
                <select id="tDep" multiple size="4" style="height:auto">
                    ${depOptions || '<option disabled>暂无可选依赖</option>'}
                </select>
                <div class="muted mt">按住 Ctrl 可多选</div>
            </div>`,
        okText: '创建任务',
        onOk: async (ov) => {
            const title = ov.querySelector('#tTitle').value.trim();
            if (!title) { toast('请输入任务标题', 'warn'); return false; }
            const desc = ov.querySelector('#tDesc').value.trim();
            const blockedBy = Array.from(ov.querySelector('#tDep').selectedOptions).map(o => o.value);
            await api('/api/tasks', {
                method: 'POST',
                body: JSON.stringify({ title, description: desc, blockedBy })
            });
            toast('✅ 任务已创建，Teammate 将自动认领');
            await loadTasks();
        }
    });
}

async function completeTask(key) {
    openModal({
        title: '✅ 完成任务',
        body: `<p style="line-height:1.7">确认手动认领并完成任务 <strong>${esc(key)}</strong>？</p>`,
        okText: '确认完成',
        onOk: async () => {
            await api(`/api/tasks/${key}/complete`, { method: 'POST', body: JSON.stringify({ result: '手动完成' }) });
            toast('✅ 任务已完成');
            await loadTasks();
        }
    });
}

async function deleteTask(key) {
    openModal({
        title: '删除任务',
        body: `<p style="line-height:1.7">确定删除任务 <strong>${esc(key)}</strong>？<br><span style="color:var(--red)">此操作不可恢复。</span></p>`,
        okText: '删除',
        okClass: 'btn-danger',
        onOk: async () => {
            await api(`/api/tasks/${key}`, { method: 'DELETE' });
            toast('✅ 已删除');
            await loadTasks();
        }
    });
}

// ---------- 4. 本地知识库 Knowledge ----------
async function renderKnowledge() {
    mainContent.innerHTML = `
        <div class="page-header">
            <div>
                <h1>${ic('book')} 本地知识库</h1>
                <div class="sub">上传本地知识文件，自动识别文本、表格、PPT、PDF</div>
            </div>
        </div>
        <div class="card">
            <div class="drop-zone" id="dropZone" onclick="document.getElementById('fileInput').click()"
                 ondragover="this.classList.add('dragover');event.preventDefault()"
                 ondragleave="this.classList.remove('dragover')"
                 ondrop="handleDrop(event)">
                <div class="big">📁</div>
                <div class="dz-title">点击或拖拽文件到此处上传</div>
                <div class="muted mt">支持 txt · md · csv · json · pdf · docx · xlsx · pptx</div>
                <input type="file" id="fileInput" class="hide" multiple onchange="handleFiles(this.files)">
            </div>
        </div>
        <div class="row" style="margin-bottom:16px">
            <div class="search-box flex-1">
                <span class="search-ico">🔍</span>
                <input id="kbSearch" placeholder="搜索知识库内容..." onkeydown="if(event.key==='Enter')loadKnowledge()">
            </div>
            <button class="btn btn-primary" onclick="loadKnowledge()">搜索</button>
        </div>
        <div class="card">
            <h3>📂 已收录文档 <span class="muted" style="font-weight:400;font-size:12px" id="kbCount"></span></h3>
            <table>
                <thead><tr><th>文件名</th><th>类型</th><th>大小</th><th>状态</th><th>摘要</th><th>操作</th></tr></thead>
                <tbody id="kbTable"></tbody>
            </table>
        </div>
        <div class="card hide" id="kbViewerCard">
            <div class="row" style="justify-content:space-between;margin-bottom:12px">
                <h3 style="margin:0" id="kbViewerTitle">📄 文档内容</h3>
                <button class="btn btn-sm" onclick="closeViewer()">✕ 关闭</button>
            </div>
            <div id="kbViewerBody" style="max-height:520px;overflow:auto;line-height:1.8;font-size:14px"></div>
        </div>`;
    await loadKnowledge();
}

async function handleDrop(e) {
    e.preventDefault();
    e.target.classList.remove('dragover');
    handleFiles(e.dataTransfer.files);
}

async function handleFiles(files) {
    for (const file of files) {
        const fd = new FormData();
        fd.append('file', file);
        fd.append('tags', '');
        toast(`⬆️ 上传 ${file.name}...`);
        try {
            const resp = await fetch('/api/knowledge/upload', { method: 'POST', body: fd });
            const data = await resp.json();
            if (data.success) toast(`✅ ${file.name} 解析完成`);
            else toast(`❌ ${file.name}: ${data.error}`, 'error');
        } catch (err) {
            toast(`❌ ${file.name} 上传失败`, 'error');
        }
    }
    await loadKnowledge();
}

async function loadKnowledge() {
    const keyword = document.getElementById('kbSearch') ? document.getElementById('kbSearch').value : '';
    const qs = keyword ? `?keyword=${encodeURIComponent(keyword)}` : '';
    try {
        const docs = await api('/api/knowledge' + qs);
        document.getElementById('kbCount').textContent = `（共 ${docs.length} 篇）`;
        const tbody = document.getElementById('kbTable');
        tbody.innerHTML = docs.map(d => `
            <tr>
                <td><strong>${esc(d.fileName)}</strong></td>
                <td><span class="tag tag-blue">${esc(d.fileType)}</span></td>
                <td class="muted">${formatSize(d.fileSize)}</td>
                <td>${statusTag(d.parseStatus)}</td>
                <td class="muted" style="max-width:280px">${esc((d.summary || '').substring(0, 55))}${(d.summary||'').length>55?'...':''}</td>
                <td><button class="btn btn-sm" onclick="viewDoc(${jsStr(d.docKey)})">查看</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteDoc(${jsStr(d.docKey)},${jsStr(d.fileName)})">删除</button></td>
            </tr>`).join('') || '<tr><td colspan="6" class="center muted">暂无文档，请上传文件</td></tr>';
    } catch (e) {}
}

function formatSize(bytes) {
    if (!bytes) return '-';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024*1024) return (bytes/1024).toFixed(1) + ' KB';
    return (bytes/1024/1024).toFixed(1) + ' MB';
}

/** 在知识库页内打开并浏览文档内容（适配 txt/md/pdf/docx/pptx/csv/xlsx/json 等格式） */
async function viewDoc(key) {
    const doc = await api(`/api/knowledge/${key}`);
    const card = document.getElementById('kbViewerCard');
    const title = document.getElementById('kbViewerTitle');
    const body = document.getElementById('kbViewerBody');
    if (!card || !title || !body) return;

    title.innerHTML = `📄 ${esc(doc.fileName)} <span class="muted" style="font-weight:400;font-size:12px">类型: ${esc(doc.fileType)} · 大小: ${formatSize(doc.fileSize)}</span>`;
    body.innerHTML = renderContent(doc);
    card.classList.remove('hide');
    card.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/** 关闭页内浏览面板 */
function closeViewer() {
    const card = document.getElementById('kbViewerCard');
    if (card) card.classList.add('hide');
}

/** 根据文件类型渲染内容（适配展示） */
function renderContent(doc) {
    const type = (doc.fileType || '').toLowerCase();
    const content = doc.content || '(无内容)';

    // 表格类：csv / xlsx —— 尝试解析为表格
    if (type === 'csv' || type === 'xlsx' || type === 'xls') {
        const tableHtml = tryParseTable(content);
        if (tableHtml) {
            return `<div style="overflow-x:auto"><table style="border:1px solid var(--border);border-collapse:collapse">${tableHtml}</table></div>`;
        }
    }

    // JSON：格式化美化展示
    if (type === 'json') {
        try {
            const parsed = JSON.parse(content);
            const pretty = JSON.stringify(parsed, null, 2);
            return `<pre style="white-space:pre-wrap;background:var(--code-bg);padding:16px;border:1px solid var(--border);font-size:13px">${esc(pretty)}</pre>`;
        } catch (e) { /* 非标准 JSON，降级为文本 */ }
    }

    // Markdown：渲染为格式化文档（标题/列表/表格等）
    if (type === 'md' || type === 'markdown' || /\.md$/.test(doc.fileName || '')) {
        if (typeof marked !== 'undefined' && marked.parse) {
            try {
                // 先转义防 XSS，再由 marked 解析 markdown 语法
                const html = marked.parse(esc(content), { breaks: true });
                return `<div class="md-render" style="padding:16px;background:var(--card);border:1px solid var(--border);border-radius:8px;font-size:14px;line-height:1.7;overflow-x:auto">${html}</div>`;
            } catch (e) { /* 渲染失败降级纯文本 */ }
        }
    }

    // 默认纯文本展示
    return `<pre style="white-space:pre-wrap;background:var(--code-bg);padding:16px;border:1px solid var(--border);font-size:13px;margin:0">${esc(content)}</pre>`;
}

/** 尝试把表格内容解析为 HTML 表格；失败返回 null */
function tryParseTable(content) {
    if (!content) return null;
    // 处理换行，兼容 \r\n
    const lines = content.split(/\r?\n/).filter(l => l.trim() !== '');
    if (lines.length === 0) return null;
    let hasTab = lines.some(l => l.includes('\t'));
    let hasComma = lines.some(l => l.includes(','));
    let sep = hasTab ? '\t' : (hasComma ? ',' : null);
    if (!sep) return null;

    const parseRow = (line) => {
        // 简单 CSV 解析：支持引号包裹的字段
        const cells = [];
        let cur = '', inQuote = false;
        for (let i = 0; i < line.length; i++) {
            const ch = line[i];
            if (ch === '"') {
                if (inQuote && line[i + 1] === '"') { cur += '"'; i++; }
                else inQuote = !inQuote;
            } else if (ch === sep && !inQuote) {
                cells.push(cur); cur = '';
            } else { cur += ch; }
        }
        cells.push(cur);
        return cells.map(c => esc(c.trim()));
    };

    const rows = lines.map(parseRow);
    const maxCols = Math.max(...rows.map(r => r.length));
    const header = rows[0].length > 0 && rows.length > 1
        ? `<tr>${rows[0].map(c => `<th style="border:1px solid var(--border);padding:6px 10px;background:var(--surface-2);text-align:left">${c}</th>`).join('')}</tr>`
        : '';

    const startIdx = header ? 1 : 0;
    const dataRows = rows.slice(startIdx).map(r => {
        while (r.length < maxCols) r.push('');
        return `<tr>${r.map(c => `<td style="border:1px solid var(--border);padding:6px 10px">${c}</td>`).join('')}</tr>`;
    }).join('');

    return header + dataRows;
}

async function deleteDoc(key, name) {
    openModal({
        title: '删除文档',
        body: `<p style="line-height:1.7">确定删除知识文档 <strong>${esc(name)}</strong>？<br><span style="color:var(--red)">此操作不可恢复。</span></p>`,
        okText: '删除',
        okClass: 'btn-danger',
        onOk: async () => {
            await api(`/api/knowledge/${key}`, { method: 'DELETE' });
            toast('✅ 已删除');
            await loadKnowledge();
        }
    });
}

// ---------- 5. 记忆库 Memory ----------
async function renderMemory() {
    mainContent.innerHTML = `
        <div class="page-header">
            <div>
                <h1>${ic('memory')} 记忆库</h1>
                <div class="sub">系统长期记忆，可在对话中自动注入（s09）</div>
            </div>
            <button class="btn btn-primary" onclick="addMemory()">${ic('plus', 'ic-sm')} 新增记忆</button>
        </div>
        <div class="card">
            <table>
                <thead><tr><th>键名</th><th>内容</th><th>重要性</th><th>分类</th><th>操作</th></tr></thead>
                <tbody id="memTable"></tbody>
            </table>
        </div>`;
    await loadMemory();
}

async function loadMemory() {
    try {
        const mems = await api('/api/memory');
        document.getElementById('memTable').innerHTML = mems.map(m => `
            <tr>
                <td><strong>${esc(m.memoryKey)}</strong></td>
                <td class="muted">${esc((m.content||'').substring(0,50))}${(m.content||'').length>50?'...':''}</td>
                <td>${'⭐'.repeat(Math.min(5, m.importance||1))}<span class="muted"> ${m.importance||1}/10</span></td>
                <td><span class="tag tag-blue">${esc(m.category||'通用')}</span></td>
                <td><button class="btn btn-sm btn-danger" onclick="deleteMemory(${jsStr(m.memoryKey)})">删除</button></td>
            </tr>`).join('') || '<tr><td colspan="5" class="center muted">暂无记忆</td></tr>';
    } catch (e) {}
}

async function addMemory() {
    openModal({
        title: '新增记忆',
        body: `
            <div class="form-group">
                <label>记忆键名 <span style="color:var(--red)">*</span></label>
                <input id="mKey" placeholder="如：目标客单价">
            </div>
            <div class="form-group">
                <label>记忆内容 <span style="color:var(--red)">*</span></label>
                <textarea id="mContent" rows="3" placeholder="要记住的信息..."></textarea>
            </div>
            <div class="grid grid-2">
                <div class="form-group">
                    <label>重要性（1-10）</label>
                    <input id="mImportance" type="number" min="1" max="10" value="5">
                </div>
                <div class="form-group">
                    <label>分类</label>
                    <input id="mCategory" value="通用">
                </div>
            </div>`,
        okText: '保存记忆',
        onOk: async (ov) => {
            const key = ov.querySelector('#mKey').value.trim();
            const content = ov.querySelector('#mContent').value.trim();
            if (!key || !content) { toast('请填写键名和内容', 'warn'); return false; }
            await api('/api/memory', {
                method: 'POST',
                body: JSON.stringify({
                    memoryKey: key,
                    content,
                    importance: parseInt(ov.querySelector('#mImportance').value) || 5,
                    category: ov.querySelector('#mCategory').value.trim() || '通用'
                })
            });
            toast('✅ 记忆已保存');
            await loadMemory();
        }
    });
}

async function deleteMemory(key) {
    openModal({
        title: '删除记忆',
        body: `<p style="line-height:1.7">确定删除记忆 <strong>${esc(key)}</strong>？</p>`,
        okText: '删除',
        okClass: 'btn-danger',
        onOk: async () => {
            await api(`/api/memory/${key}`, { method: 'DELETE' });
            toast('✅ 已删除');
            await loadMemory();
        }
    });
}

// ---------- 7. MCP 服务管理 ----------
async function renderMcp() {
    mainContent.innerHTML = `
        <div class="page-header">
            <div>
                <h1>${ic('plug')} MCP 服务管理</h1>
                <div class="sub">自行添加外部工具/数据服务（stdio / http / sse），支持远程下载导入（自动病毒/后门扫描）</div>
            </div>
            <div class="row">
                <button class="btn" onclick="importMcpFromUrl()">${ic('download', 'ic-sm')} 从 URL 导入</button>
                <button class="btn" onclick="importMcpFromOpenclaw()">${ic('download', 'ic-sm')} 从内置库导入</button>
                <button class="btn btn-primary" onclick="addMcp()">${ic('plus', 'ic-sm')} 新增服务</button>
            </div>
        </div>
        <div class="card">
            <table>
                <thead><tr><th>名称</th><th>类型</th><th>地址/命令</th><th>说明</th><th>状态</th><th>最近测试</th><th>操作</th></tr></thead>
                <tbody id="mcpTable"></tbody>
            </table>
        </div>
        <div class="card">
            <h3>💡 使用说明</h3>
            <div class="muted" style="line-height:2">
                · <strong>http/sse</strong>：填写服务基地址，保存后可「测试连接」验证连通性。<br>
                · <strong>stdio</strong>：填写启动命令（如 <code>npx -y @modelcontextprotocol/server-filesystem ./data</code>），用于本地进程型服务。<br>
                · <strong>从 URL 导入</strong>：粘贴远端配置地址，系统自动下载并扫描病毒/后门，安全内容才会导入。<br>
                · 启用（active）的服务才被视为在线；点击「启用/停用」可切换状态。
            </div>
        </div>`;
    await loadMcp();
}

async function importMcpFromUrl() {
    const url = await promptForUrl('从 URL 导入 MCP 服务', '粘贴 MCP 配置的远程地址（JSON 或纯文本）');
    if (!url) return;
    const resp = await api('/api/mcp/download', { method: 'POST', body: JSON.stringify({ url }) });
    if (resp.success) {
        toast('✅ MCP 已下载并通过安全扫描');
        await loadMcp();
    } else if (resp.blocked) {
        toast('⛔ 已阻止导入：' + resp.error, 'error');
    } else {
        toast('❌ ' + (resp.error || '导入失败'), 'error');
    }
}

async function importMcpFromOpenclaw() {
    try {
        toast('⏳ 正在从内置库导入常用 MCP 服务...');
        const resp = await api('/api/mcp/import-openclaw', { method: 'POST', body: '{}' });
        if (resp.success) {
            toast(`✅ 已导入 ${resp.importedCount || 0} 个 MCP 服务` + (resp.skippedCount ? `，跳过 ${resp.skippedCount} 个` : ''));
            await loadMcp();
        } else {
            toast('❌ ' + (resp.error || '导入失败'), 'error');
        }
    } catch (e) {
        toast('❌ 导入失败: ' + e.message, 'error');
    }
}

async function loadMcp() {
    try {
        const servers = await api('/api/mcp');
        document.getElementById('mcpTable').innerHTML = servers.map(s => `
            <tr>
                <td><strong>${esc(s.name)}</strong></td>
                <td><span class="tag tag-blue">${esc(s.serverType || 'http')}</span></td>
                <td class="muted" style="max-width:260px;word-break:break-all">${esc(s.endpoint || '-')}</td>
                <td class="muted">${esc(s.description || '-')}</td>
                <td>${s.status === 'active' ? '<span class="tag tag-green">已启用</span>' : '<span class="tag tag-gray">已停用</span>'}</td>
                <td class="muted">${s.lastChecked ? new Date(s.lastChecked).toLocaleString() : '-'}</td>
                <td>
                    <button class="btn btn-sm" onclick="testMcp(${s.id})">测试</button>
                    <button class="btn btn-sm" onclick="toggleMcp(${s.id})">${s.status === 'active' ? '停用' : '启用'}</button>
                    <button class="btn btn-sm" onclick="editMcp(${s.id})">编辑</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteMcp(${s.id},${jsStr(s.name)})">删除</button>
                </td>
            </tr>`).join('') || '<tr><td colspan="7" class="center muted">暂无 MCP 服务，点击「新增服务」添加</td></tr>';
    } catch (e) {}
}

function mcpModal(server) {
    const isEdit = !!server;
    server = server || { name: '', serverType: 'http', endpoint: '', description: '', config: '' };
    openModal({
        title: isEdit ? '编辑 MCP 服务' : '新增 MCP 服务',
        body: `
            <div class="form-group">
                <label>服务名称 <span style="color:var(--red)">*</span></label>
                <input id="mcpName" value="${escAttr(server.name)}" placeholder="如：文件系统服务">
            </div>
            <div class="form-group">
                <label>服务类型</label>
                <select id="mcpType">
                    <option value="http" ${server.serverType==='http'?'selected':''}>HTTP</option>
                    <option value="sse" ${server.serverType==='sse'?'selected':''}>SSE</option>
                    <option value="stdio" ${server.serverType==='stdio'?'selected':''}>stdio（本地命令）</option>
                </select>
            </div>
            <div class="form-group">
                <label>服务地址 / 命令 <span style="color:var(--red)">*</span></label>
                <input id="mcpEndpoint" value="${escAttr(server.endpoint)}" placeholder="http://localhost:9000 或 npx -y @some/server">
            </div>
            <div class="form-group">
                <label>功能说明</label>
                <input id="mcpDesc" value="${escAttr(server.description)}" placeholder="该服务提供什么能力">
            </div>
            <div class="form-group">
                <label>配置参数（JSON，可选）</label>
                <textarea id="mcpConfig" rows="3" placeholder='{"key":"value"}'>${esc(server.config || '')}</textarea>
            </div>`,
        okText: '保存',
        onOk: async (ov) => {
            const name = ov.querySelector('#mcpName').value.trim();
            const endpoint = ov.querySelector('#mcpEndpoint').value.trim();
            if (!name || !endpoint) { toast('请填写名称和服务地址', 'warn'); return false; }
            await api('/api/mcp', {
                method: 'POST',
                body: JSON.stringify({
                    id: isEdit ? server.id : null,
                    name,
                    serverType: ov.querySelector('#mcpType').value,
                    endpoint,
                    description: ov.querySelector('#mcpDesc').value.trim(),
                    config: ov.querySelector('#mcpConfig').value.trim()
                })
            });
            toast('✅ 服务已保存');
            await loadMcp();
        }
    });
}

function addMcp() { mcpModal(null); }

async function editMcp(id) {
    const servers = await api('/api/mcp');
    const s = servers.find(x => x.id === id);
    if (s) mcpModal(s);
}

async function toggleMcp(id) {
    await api(`/api/mcp/${id}/toggle`, { method: 'POST', body: '{}' });
    toast('✅ 状态已切换');
    await loadMcp();
}

async function testMcp(id) {
    const resp = await api(`/api/mcp/${id}/test`, { method: 'POST', body: '{}' });
    if (resp.success) toast('✅ ' + (resp.message || '连接成功'));
    else toast('❌ ' + (resp.message || '连接失败'), 'error');
    await loadMcp();
}

async function deleteMcp(id, name) {
    openModal({
        title: '删除服务',
        body: `<p style="line-height:1.7">确定删除 MCP 服务 <strong>${esc(name)}</strong>？<br><span style="color:var(--red)">此操作不可恢复。</span></p>`,
        okText: '删除',
        okClass: 'btn-danger',
        onOk: async () => {
            await api(`/api/mcp/${id}`, { method: 'DELETE' });
            toast('✅ 已删除');
            await loadMcp();
        }
    });
}

// ---------- 8. Skill 技能管理 ----------
async function renderSkills() {
    mainContent.innerHTML = `
        <div class="page-header">
            <div>
                <h1>${ic('tool')} Skill 技能管理</h1>
                <div class="sub">自定义技能/指令，启用后自动注入 AI 对话，支持远程下载导入（自动病毒/后门扫描）</div>
            </div>
            <div class="row">
                <button class="btn" onclick="importSkillFromUrl()">${ic('download', 'ic-sm')} 从 URL 导入</button>
                <button class="btn" onclick="importSkillFromOpenclaw()">${ic('download', 'ic-sm')} 从内置库导入</button>
                <button class="btn btn-primary" onclick="addSkill()">${ic('plus', 'ic-sm')} 新增技能</button>
            </div>
        </div>
        <div class="card">
            <table>
                <thead><tr><th>名称</th><th>分类</th><th>说明</th><th>指令内容</th><th>状态</th><th>操作</th></tr></thead>
                <tbody id="skillTable"></tbody>
            </table>
        </div>
        <div class="card">
            <h3>💡 使用说明</h3>
            <div class="muted" style="line-height:2">
                · 技能即「专家指令」，填写技能描述和具体处理步骤，AI 在对话中会遵循这些指令。<br>
                · 只有「启用」的技能会被注入 AI 对话。<br>
                · <strong>从 URL 导入</strong>：粘贴技能定义地址，系统自动下载并扫描病毒/后门，安全内容才会导入。<br>
                · 示例：商品文案生成、竞品分析、活动策划等。
            </div>
        </div>`;
    await loadSkills();
}

async function importSkillFromUrl() {
    const url = await promptForUrl('从 URL 导入 Skill 技能', '粘贴技能定义的远程地址（JSON）');
    if (!url) return;
    const resp = await api('/api/skills/download', { method: 'POST', body: JSON.stringify({ url }) });
    if (resp.success) {
        toast('✅ Skill 已下载并通过安全扫描');
        await loadSkills();
    } else if (resp.blocked) {
        toast('⛔ 已阻止导入：' + resp.error, 'error');
    } else {
        toast('❌ ' + (resp.error || '导入失败'), 'error');
    }
}

async function importSkillFromOpenclaw() {
    try {
        toast('⏳ 正在从内置库导入技能...');
        const resp = await api('/api/skills/import-openclaw', { method: 'POST', body: '{}' });
        if (resp.success) {
            toast(`✅ 已导入 ${resp.importedCount || 0} 个技能` + (resp.skippedCount ? `，跳过 ${resp.skippedCount} 个` : ''));
            await loadSkills();
        } else {
            toast('❌ ' + (resp.error || '导入失败'), 'error');
        }
    } catch (e) {
        toast('❌ 导入失败: ' + e.message, 'error');
    }
}

async function loadSkills() {
    try {
        const skills = await api('/api/skills');
        document.getElementById('skillTable').innerHTML = skills.map(s => `
            <tr>
                <td><strong>${esc(s.name)}</strong></td>
                <td><span class="tag tag-blue">${esc(s.category || '通用')}</span></td>
                <td class="muted" style="max-width:220px">${esc(s.description || '-')}</td>
                <td class="muted" style="max-width:300px">${esc((s.content || '').substring(0, 60))}${(s.content||'').length>60?'...':''}</td>
                <td>${s.enabled === 1 ? '<span class="tag tag-green">已启用</span>' : '<span class="tag tag-gray">已停用</span>'}</td>
                <td>
                    <button class="btn btn-sm" onclick="toggleSkill(${s.id})">${s.enabled === 1 ? '停用' : '启用'}</button>
                    <button class="btn btn-sm" onclick="editSkill(${s.id})">编辑</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteSkill(${s.id},${jsStr(s.name)})">删除</button>
                </td>
            </tr>`).join('') || '<tr><td colspan="6" class="center muted">暂无技能，点击「新增技能」添加</td></tr>';
    } catch (e) {}
}

function skillModal(skill) {
    const isEdit = !!skill;
    skill = skill || { name: '', category: '通用', description: '', content: '', enabled: 1 };
    openModal({
        title: isEdit ? '编辑技能' : '新增技能',
        body: `
            <div class="form-group">
                <label>技能名称 <span style="color:var(--red)">*</span></label>
                <input id="skName" value="${escAttr(skill.name)}" placeholder="如：商品文案生成">
            </div>
            <div class="grid grid-2">
                <div class="form-group">
                    <label>分类</label>
                    <input id="skCategory" value="${escAttr(skill.category || '通用')}" placeholder="内容创作/运营分析">
                </div>
                <div class="form-group">
                    <label>启用状态</label>
                    <select id="skEnabled">
                        <option value="1" ${skill.enabled===1?'selected':''}>启用</option>
                        <option value="0" ${skill.enabled===0?'selected':''}>停用</option>
                    </select>
                </div>
            </div>
            <div class="form-group">
                <label>技能说明</label>
                <input id="skDesc" value="${escAttr(skill.description)}" placeholder="简要说明该技能的作用">
            </div>
            <div class="form-group">
                <label>指令内容 <span style="color:var(--red)">*</span></label>
                <textarea id="skContent" rows="5" placeholder="描述技能的具体处理步骤与要求，AI 会遵循执行...">${esc(skill.content || '')}</textarea>
            </div>`,
        okText: '保存',
        onOk: async (ov) => {
            const name = ov.querySelector('#skName').value.trim();
            const content = ov.querySelector('#skContent').value.trim();
            if (!name || !content) { toast('请填写名称和指令内容', 'warn'); return false; }
            await api('/api/skills', {
                method: 'POST',
                body: JSON.stringify({
                    id: isEdit ? skill.id : null,
                    name,
                    category: ov.querySelector('#skCategory').value.trim() || '通用',
                    description: ov.querySelector('#skDesc').value.trim(),
                    content,
                    enabled: parseInt(ov.querySelector('#skEnabled').value)
                })
            });
            toast('✅ 技能已保存');
            await loadSkills();
        }
    });
}

function addSkill() { skillModal(null); }

async function editSkill(id) {
    const skills = await api('/api/skills');
    const s = skills.find(x => x.id === id);
    if (s) skillModal(s);
}

async function toggleSkill(id) {
    await api(`/api/skills/${id}/toggle`, { method: 'POST', body: '{}' });
    toast('✅ 状态已切换');
    await loadSkills();
}

async function deleteSkill(id, name) {
    openModal({
        title: '删除技能',
        body: `<p style="line-height:1.7">确定删除技能 <strong>${esc(name)}</strong>？<br><span style="color:var(--red)">此操作不可恢复。</span></p>`,
        okText: '删除',
        okClass: 'btn-danger',
        onOk: async () => {
            await api(`/api/skills/${id}`, { method: 'DELETE' });
            toast('✅ 已删除');
            await loadSkills();
        }
    });
}

// ---------- 6. 设置 Settings ----------
async function renderSettings() {
    mainContent.innerHTML = `
        <div class="page-header">
            <div>
                <h1>${ic('settings')} 系统设置</h1>
                <div class="sub">API Key 与系统参数管理</div>
            </div>
        </div>
        <div class="grid grid-2">
            <div class="card">
                <h3>🔑 AI 服务配置 <span class="tag tag-green" id="aiStatus">检查中</span></h3>
                <div class="form-group">
                    <label>服务提供商</label>
                    <select id="setProvider">
                        <option value="deepseek">DeepSeek（deepseek-v4-flash）</option>
                        <option value="openai">OpenAI 兼容</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>API 服务地址</label>
                    <input id="setBaseUrl" placeholder="https://api.deepseek.com">
                </div>
                <div class="form-group">
                    <label>默认模型</label>
                    <input id="setModel" placeholder="deepseek-v4-flash">
                </div>
                <div class="form-group">
                    <label>API 密钥</label>
                    <div class="password-wrap">
                        <input id="setApiKey" type="password" placeholder="输入你的 API Key" autocomplete="off">
                        <button class="btn btn-sm" onclick="togglePwd()" type="button">👁</button>
                    </div>
                    <div class="muted mt">已保存的密钥会以掩码显示。如需修改，直接输入新密钥保存。</div>
                </div>
                <div class="row">
                    <button class="btn btn-primary" onclick="saveAiSettings()">保存 AI 配置</button>
                    <button class="btn" onclick="testAi()">${ic('plug', 'ic-sm')} 测试连接</button>
                </div>
            </div>
            <div class="card">
                <h3>🧩 多模型管理 <span class="muted" style="font-weight:400;font-size:12px">支持配置多个模型并切换</span></h3>
                <table>
                    <thead><tr><th>显示名</th><th>提供商</th><th>模型</th><th>状态</th><th>操作</th></tr></thead>
                    <tbody id="modelTable"></tbody>
                </table>
                <button class="btn btn-primary mt" onclick="addModel()">${ic('plus', 'ic-sm')} 新增模型</button>
            </div>
            <div class="card">
                <h3>🏷️ 系统参数</h3>
                <div class="form-group">
                    <label>系统名称</label>
                    <input id="setSysName">
                </div>
                <div class="form-group">
                    <label>默认工作进程数</label>
                    <input id="setWorkerCount" type="number" min="1" max="20">
                </div>
                <div class="form-group">
                    <label>任务自动认领</label>
                    <select id="setAutoAssign">
                        <option value="1">启用（推荐）</option>
                        <option value="0">停用</option>
                    </select>
                </div>
                <button class="btn btn-primary" onclick="saveSysSettings()">保存系统参数</button>
            </div>
            <div class="card">
                <h3>${ic('globe', 'ic-sm')} 联网搜索配置</h3>
                <div class="form-group">
                    <label>启用联网搜索</label>
                    <select id="setSearchEnabled">
                        <option value="1">启用（推荐）</option>
                        <option value="0">停用</option>
                    </select>
                    <div class="muted mt">启用后，在「生活助理」提问时若包含「搜索 / 查一下 / 最新」等意图词，会自动联网检索并注入回答。</div>
                </div>
                <div class="form-group">
                    <label>搜索提供方</label>
                    <select id="setSearchProvider">
                        <option value="duckduckgo">DuckDuckGo（免密钥）</option>
                        <option value="bing">Bing</option>
                    </select>
                </div>
                <button class="btn btn-primary" onclick="saveSearchSettings()">保存搜索配置</button>
            </div>
            <div class="card">
                <h3>${ic('power', 'ic-sm')} 危险操作</h3>
                <div class="muted" style="line-height:1.9">
                    关闭后端服务会同时停止上下文存储服务（8765）。<br>
                    关闭后聊天、监控、MCP、Skill 等功能不可用；聊天记录、知识库、记忆与任务数据均不会丢失。<br>
                    重新启动：双击桌面的「生活助理 Harness」快捷方式。
                </div>
                <button class="btn btn-danger mt" id="btnShutdown" onclick="shutdownBackend()">${ic('power', 'ic-sm')} 关闭后端服务</button>
            </div>
        </div>
        <div class="card">
            <h3>ℹ️ 关于</h3>
            <div class="muted" style="line-height:2">
                生活助理 Harness，基于「机制很多，循环一个」的 Agent 架构理念构建。<br>
                · 数据本地化部署，存储于 <code>./data</code> 目录<br>
                · AI 服务：DeepSeek（deepseek-v4-flash）<br>
                · 联网搜索 + MCP 服务 + Skill 技能扩展<br>
                · WebSocket 实时监控：<code>/ws/monitor</code>
            </div>
        </div>`;

    // 加载当前设置
    try {
        const settings = await api('/api/settings');
        const get = (k) => {
            const s = settings.find(x => x.settingKey === k);
            return s ? s.settingValue : '';
        };
        document.getElementById('setProvider').value = get('api.provider') || 'deepseek';
        document.getElementById('setBaseUrl').value = get('api.baseUrl') || 'https://api.deepseek.com';
        document.getElementById('setModel').value = get('api.model') || 'deepseek-v4-flash';
        const hasKey = get('api.key');
        document.getElementById('setApiKey').placeholder = hasKey ? '已保存（******）' : '输入你的 API Key';
        document.getElementById('aiStatus').textContent = hasKey ? '已配置' : '未配置';
        document.getElementById('aiStatus').className = hasKey ? 'tag tag-green' : 'tag tag-orange';
        document.getElementById('setSysName').value = get('system.name');
        document.getElementById('setWorkerCount').value = get('system.workerCount') || '3';
        document.getElementById('setAutoAssign').value = get('agent.autoAssign') || '1';
        document.getElementById('setSearchEnabled').value = get('search.enabled') || '1';
        document.getElementById('setSearchProvider').value = get('search.provider') || 'duckduckgo';
    } catch (e) {}
    await loadModelTable();
}

// ============ 多模型管理 ============

async function loadModelTable() {
    const tbody = document.getElementById('modelTable');
    if (!tbody) return;
    try {
        const models = await api('/api/models');
        tbody.innerHTML = models.map(m => `
            <tr>
                <td><strong>${esc(m.name)}</strong></td>
                <td class="muted">${esc(m.provider || '-')}</td>
                <td class="muted">${esc(m.model || '-')}</td>
                <td>${m.active === 1 ? '<span class="tag tag-green">当前使用</span>' : '<span class="tag tag-gray">未激活</span>'}</td>
                <td>
                    ${m.active !== 1 ? `<button class="btn btn-sm" onclick="activateModel(${m.id})">启用</button>` : ''}
                    <button class="btn btn-sm" onclick="editModel(${m.id})">编辑</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteModel(${m.id})">删除</button>
                </td>
            </tr>`).join('') || '<tr><td colspan="5" class="center muted">暂无模型配置，点击「新增模型」添加</td></tr>';
    } catch (e) {}
}

function modelModal(model) {
    const isEdit = !!model;
    model = model || { name: '', provider: 'deepseek', baseUrl: 'https://api.deepseek.com', model: '', apiKey: '' };
    openModal({
        title: isEdit ? '编辑模型' : '新增模型',
        body: `
            <div class="form-group">
                <label>显示名称 <span style="color:var(--red)">*</span></label>
                <input id="mName" value="${escAttr(model.name)}" placeholder="如：DeepSeek V4 Flash / GPT-4o">
            </div>
            <div class="form-group">
                <label>提供商</label>
                <select id="mProvider">
                    <option value="deepseek" ${model.provider==='deepseek'?'selected':''}>DeepSeek</option>
                    <option value="openai" ${model.provider==='openai'?'selected':''}>OpenAI</option>
                    <option value="custom" ${model.provider==='custom'?'selected':''}>自定义</option>
                </select>
            </div>
            <div class="form-group">
                <label>服务地址</label>
                <input id="mBaseUrl" value="${escAttr(model.baseUrl)}" placeholder="https://api.deepseek.com">
            </div>
            <div class="form-group">
                <label>模型名 <span style="color:var(--red)">*</span></label>
                <input id="mModel" value="${escAttr(model.model)}" placeholder="deepseek-v4-flash / gpt-4o">
            </div>
            <div class="form-group">
                <label>API 密钥</label>
                <input id="mApiKey" value="${escAttr(model.apiKey || '')}" placeholder="${model.apiKey ? '已保存（如需修改请重新输入）' : '输入 API Key'}">
            </div>`,
        okText: '保存',
        onOk: async (ov) => {
            const name = ov.querySelector('#mName').value.trim();
            const modelName = ov.querySelector('#mModel').value.trim();
            if (!name || !modelName) { toast('请填写名称和模型名', 'warn'); return false; }
            const apiKey = ov.querySelector('#mApiKey').value.trim();
            await api('/api/models', {
                method: 'POST',
                body: JSON.stringify({
                    id: isEdit ? model.id : null,
                    name,
                    provider: ov.querySelector('#mProvider').value,
                    baseUrl: ov.querySelector('#mBaseUrl').value.trim(),
                    model: modelName,
                    apiKey: apiKey || (isEdit ? model.apiKey : '')
                })
            });
            toast('✅ 模型已保存');
            await loadModelTable();
        }
    });
}

function addModel() { modelModal(null); }

async function editModel(id) {
    const models = await api('/api/models');
    const m = models.find(x => x.id === id);
    if (m) modelModal(m);
}

async function activateModel(id) {
    const resp = await api(`/api/models/${id}/activate`, { method: 'POST', body: '{}' });
    if (resp.success) toast('✅ 已切换到：' + (resp.model && resp.model.name || ''));
    await loadModelTable();
    await loadModels();
}

async function deleteModel(id) {
    openModal({
        title: '删除模型',
        body: `<p style="line-height:1.7">确定删除该模型配置？<br><span class="muted">删除后需重新配置。</span></p>`,
        okText: '删除',
        okClass: 'btn-danger',
        onOk: async () => {
            await api(`/api/models/${id}`, { method: 'DELETE' });
            toast('✅ 已删除');
            await loadModelTable();
            await loadModels();
        }
    });
}

function togglePwd() {
    const input = document.getElementById('setApiKey');
    input.type = input.type === 'password' ? 'text' : 'password';
}

async function testAi() {
    const btn = event.target;
    btn.textContent = '测试中...';
    btn.disabled = true;
    try {
        const settings = await api('/api/settings');
        const get = (k) => { const s = settings.find(x => x.settingKey === k); return s ? s.settingValue : ''; };
        const hasKey = get('api.key');
        if (!hasKey) {
            toast('请先配置 API Key', 'warn');
            return;
        }
        // 通过 AI 对话接口发送测试
        const resp = await api('/api/assistant/chat', {
            method: 'POST',
            body: JSON.stringify({ message: '请回复"连接成功"四个字' })
        });
        if (resp.reply && !resp.error) {
            toast('✅ AI 连接成功：' + resp.reply.substring(0, 30));
            document.getElementById('aiStatus').textContent = '已配置';
            document.getElementById('aiStatus').className = 'tag tag-green';
        } else {
            toast('❌ 连接失败：' + (resp.reply || '未知错误'), 'error');
        }
    } catch (e) {
        toast('❌ 连接失败', 'error');
    } finally {
        btn.innerHTML = `${ic('plug', 'ic-sm')} 测试连接`;
        btn.disabled = false;
    }
}

async function saveAiSettings() {
    const payload = {
        provider: document.getElementById('setProvider').value,
        baseUrl: document.getElementById('setBaseUrl').value,
        model: document.getElementById('setModel').value,
        apiKey: document.getElementById('setApiKey').value
    };
    const settings = await api('/api/settings');
    const get = (k) => { const s = settings.find(x => x.settingKey === k); return s ? s.settingValue : ''; };

    await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'api.provider', value: payload.provider }) });
    await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'api.baseUrl', value: payload.baseUrl }) });
    await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'api.model', value: payload.model }) });
    if (payload.apiKey) {
        await api('/api/settings/api-key', { method: 'POST', body: JSON.stringify({ value: payload.apiKey }) });
    }
    toast('✅ AI 配置已保存');
    await renderSettings();
}

async function saveSysSettings() {
    await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'system.name', value: document.getElementById('setSysName').value }) });
    await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'system.workerCount', value: document.getElementById('setWorkerCount').value }) });
    await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'agent.autoAssign', value: document.getElementById('setAutoAssign').value }) });
    toast('✅ 系统参数已保存');
}

async function saveSearchSettings() {
    await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'search.enabled', value: document.getElementById('setSearchEnabled').value }) });
    await api('/api/settings', { method: 'POST', body: JSON.stringify({ key: 'search.provider', value: document.getElementById('setSearchProvider').value }) });
    toast('✅ 联网搜索配置已保存');
}

// ---------- 后端服务关闭（前端一键停止 8080 + 8765） ----------
/** 标记后端已关闭：停止 WS 重连、更新状态栏并展示离线页 */
function markBackendDown() {
    window.__backendDown = true;
    try { if (ws) ws.close(); } catch (e) { }
    const el = document.getElementById('connStatus');
    if (el) { el.textContent = '服务已关闭'; el.classList.remove('online'); }
    document.querySelectorAll('.sidebar nav a').forEach(a => a.classList.remove('active'));
    showOfflineScreen();
}

/** 离线页：提示如何重新启动服务 */
function showOfflineScreen() {
    mainContent.innerHTML = `
        <div class="page-header">
            <div>
                <h1>${ic('power')} 服务已关闭</h1>
                <div class="sub">后端服务与上下文存储服务已停止运行</div>
            </div>
        </div>
        <div class="card">
            <h3>重新启动</h3>
            <div class="muted" style="line-height:2">
                1. 关闭此页面；<br>
                2. 双击桌面上的「生活助理 Harness」快捷方式（或运行 <code>dist\\生活助理Harness一键启动.exe</code>）；<br>
                3. 启动完成后浏览器会自动打开。
            </div>
        </div>
        <div class="card">
            <h3>数据安全</h3>
            <div class="muted" style="line-height:2">
                聊天记录、知识库、记忆、任务等数据已保存在本地数据库与 <code>./data</code> 目录，重新启动后不会丢失。
            </div>
        </div>`;
}

/** 设置页/侧栏「关闭后端服务」按钮：二次确认后调用后端优雅停机接口 */
function shutdownBackend() {
    if (window.__backendDown) { toast('服务已关闭，请双击桌面快捷方式重新启动'); return; }
    openModal({
        title: '关闭后端服务',
        body: `<p style="line-height:1.8">将停止 <b>后端服务（8080）</b> 与 <b>上下文存储服务（8765）</b>。<br>关闭后聊天、任务、MCP、Skill 等功能都将不可用，页面会切换为离线状态。<br><br>确定要关闭吗？</p>`,
        okText: '确认关闭',
        onOk: async () => {
            try {
                const resp = await fetch('/api/system/shutdown', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-Shutdown-Confirm': 'yes' },
                    body: JSON.stringify({ confirm: true })
                });
                let data = null;
                try { data = await resp.json(); } catch (e) { }
                if (!resp.ok || !data || !data.success) {
                    toast('关闭失败：' + (data && data.message ? data.message : ('HTTP ' + resp.status)), 'error');
                    return false;
                }
                toast('后端服务正在关闭…');
                setTimeout(markBackendDown, 300);
                return true;
            } catch (e) {
                toast('关闭失败：' + e.message, 'error');
                return false;
            }
        }
    });
}

// ---------- WebSocket 实时监控 ----------
let ws = null;
let wsRetry = 0;
function connectWs() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    try { ws = new WebSocket(`${proto}://${location.host}/ws/monitor`); } catch (e) { return; }
    ws.onopen = () => {
        document.getElementById('connStatus').textContent = '已连接';
        document.getElementById('connStatus').classList.add('online');
        wsRetry = 0;
    };
    ws.onclose = () => {
        const el = document.getElementById('connStatus');
        el.classList.remove('online');
        // 前端主动关闭后端后不再重连
        if (window.__backendDown) { el.textContent = '服务已关闭'; return; }
        el.textContent = '连接断开';
        wsRetry++;
        setTimeout(connectWs, Math.min(3000 + wsRetry * 500, 10000));
    };
    ws.onmessage = (e) => {
        try {
            const data = JSON.parse(e.data);
            if (data.type === 'snapshot') {
                const el = document.getElementById('connStatus');
                el.textContent = '已连接 (实时)';
                el.classList.add('online');
                const active = document.querySelector('.sidebar nav a.active');
                if (active && active.dataset.page === 'monitor' && document.getElementById('monitorStats')) {
                    loadMonitorOverview();
                }
            } else if (data.type === 'server_shutdown') {
                // 服务端广播停机（可能是别的页面/窗口触发的关闭）
                toast('后端服务即将关闭', 'warn');
                setTimeout(markBackendDown, 800);
            } else if (data.type === 'assistant_notify') {
                // 后台定时任务完成，主动推送消息插入到聊天框（并保存到历史）
                pushAssistantNotification(data);
            }
        } catch (err) {}
    };
}

// ---------- 初始化 ----------
/**
 * 解析助手回复中的 [[SCHEDULE:instruct=...;delayMinutes=...;title=...]] 标记，
 * 自动调用后端 /api/assistant/schedule 设定后台定时任务，并从回复中移除标记。
 * 多个标记依次处理。
 */
/**
 * 接收 WebSocket 推送的 assistant_notify 通知（后台定时任务完成），
 * 将其作为助手消息插入到聊天框并保存到历史。
 */
function pushAssistantNotification(data) {
    const title = data.title || '⏰ 定时任务';
    const content = data.content || '';
    const box = document.getElementById('chatBox');
    if (!box) return;
    // 历史记录由后端触发定时任务时写入（浏览器离线也不丢），前端只负责气泡展示，避免重复保存
    const active = document.querySelector('.sidebar nav a.active');
    if (!active || active.dataset.page !== 'dashboard') return;
    // 插入气泡
    const replyId = 'rpy' + Date.now();
    box.insertAdjacentHTML('beforeend',
        `<div class="msg bot" id="${replyId}"><div class="avatar">${ic('bot')}</div>
         <div class="bubble"><div class="scheduled-banner">${esc(title)}</div>${renderMarkdownWithCharts(content)}</div></div>`);
    box.scrollTop = box.scrollHeight;
    try { toast('⏰ ' + title); } catch (e) { }
}

function processScheduleMarkers(text, session) {
    if (!text || text.indexOf('[[SCHEDULE:') < 0) return text;
    const re = /\[\[SCHEDULE:([^\]]+)\]\]/g;
    let m;
    while ((m = re.exec(text)) !== null) {
        const params = {};
        m[1].split(';').forEach(kv => {
            const i = kv.indexOf('=');
            if (i > 0) {
                const k = kv.substring(0, i).trim();
                const v = kv.substring(i + 1).trim();
                if (k) params[k] = v;
            }
        });
        const instruct = params.instruct || '';
        const delay = parseInt(params.delayMinutes || '0', 10);
        const title = params.title || '⏰ 定时任务';
        if (instruct && delay > 0) {
            // 异步调用 schedule 端点（不阻塞渲染）
            api('/api/assistant/schedule', {
                method: 'POST',
                body: JSON.stringify({ session, instruct, delayMinutes: delay, title })
            }).then(r => {
                if (r && r.success) {
                    toast('⏰ 已设定后台任务：' + delay + ' 分钟后执行');
                } else {
                    toast('⚠️ 后台任务设定失败：' + (r && r.error || '未知错误'), 'error');
                }
            });
        }
    }
    return text.replace(re, '').replace(/\n{3,}/g, '\n\n').trim();
}

/** 初始化主题：读取 localStorage 记忆，应用深色/浅色模式 */
function initTheme() {
    const saved = localStorage.getItem('theme');
    const theme = saved || (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    applyTheme(theme);
}

/** 应用主题：设置 html[data-theme] 并更新切换按钮图标 */
function applyTheme(theme) {
    document.documentElement.dataset.theme = theme;
    const btn = document.getElementById('themeToggle');
    if (btn) btn.innerHTML = theme === 'dark' ? ic('sun') : ic('moon');
    // 主题切换后重新渲染已加载图表（适配深色）
    const box = document.getElementById('chatBox');
    if (box) {
        box.querySelectorAll('.chart[data-option]').forEach(el => {
            if (el.dataset.rendered && window.__charts && window.__charts[el.id]) {
                try {
                    const opt = JSON.parse(el.dataset.option);
                    window.__charts[el.id].setOption(buildChartOption(opt, theme === 'dark'));
                } catch (e) { }
            }
        });
    }
}

/** 切换深色/浅色模式并记忆 */
function toggleTheme() {
    const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    applyTheme(next);
    try { localStorage.setItem('theme', next); } catch (e) { }
}

async function init() {
    initTheme();
    connectWs();
    await navigate('dashboard');
}

init();
