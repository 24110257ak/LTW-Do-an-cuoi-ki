(() => {
'use strict';
const popup = document.getElementById('techChatPopup');
document.querySelectorAll('[data-chat-toggle]').forEach(button => button.addEventListener('click', () => {
    popup.hidden = !popup.hidden;
    document.querySelectorAll('[data-chat-toggle]').forEach(b => b.setAttribute('aria-expanded', String(!popup.hidden)));
    if (!popup.hidden) popup.dispatchEvent(new Event('chat-open'));
}));
const root = document.querySelector('[data-techchat]');
if (!root) return;
const el = name => root.querySelector(`[data-chat="${name}"]`), inbox = root.dataset.inbox === 'true';
let config, current, connection, reconnectTimer, stopped = false, sessions = [], page = 0, moreSessions = false;
let history = new Map(), beforeId = null, lastSyncedId = null, hasOlder = false, sending = false, pending = null, version = 0, refreshing = false, openingBranch = false;
const readThrough = new Map();
let loadingOlder = false;
const nearBottom = () => el('messages').scrollHeight - el('messages').scrollTop - el('messages').clientHeight < 100;
const visible = () => !document.hidden && (inbox || (popup && !popup.hidden));
const conversationVisible = () => visible() && (inbox || root.dataset.view === 'conversation');
function setView(view) {
    if (inbox) return;
    root.dataset.view = view;
    el('branchPicker').hidden = view !== 'branches';
    el('navigation').hidden = view === 'branches';
    el('layout').hidden = view === 'branches';
    el('sessionPanel').hidden = true;
    el('conversation').hidden = view !== 'conversation';
    if (config) busy();
}
function renderBranches() {
    if (inbox || !config) return;
    const list = el('branchList'); list.replaceChildren();
    if (!config.branches.length) list.append(node('p', 'Hiện chưa có chi nhánh nhận tư vấn.', 'tc-empty'));
    for (const branch of config.branches) {
        const branchSessions = sessions.filter(session => session.branchId === branch.branchId);
        const unread = branchSessions.reduce((sum, session) => sum + session.unreadCount, 0);
        const active = branchSessions.some(session => session.status === 'OPEN');
        const button = node('button', null, 'tc-branch'); button.type = 'button'; button.disabled = openingBranch;
        button.append(node('span', '⌂', 'tc-branch-icon'));
        const label = node('span', null, 'tc-branch-label');
        label.append(node('strong', branch.branchName));
        label.append(node('small', active ? 'Tiếp tục trò chuyện' : 'Gửi tin tư vấn'));
        button.append(label);
        button.append(node('span', unread ? String(unread) : '›', unread ? 'tc-unread' : 'tc-branch-arrow'));
        button.addEventListener('click', async () => {
            if (openingBranch) return;
            openingBranch = true; error(''); el('branch').value = String(branch.branchId); renderBranches();
            try {
                if (current?.branchId === branch.branchId && current.status === 'OPEN') {
                    setView('conversation'); heading(); await loadMessages();
                } else await openSession();
            } catch (e) { error(e.message); }
            finally { openingBranch = false; renderBranches(); }
        });
        list.append(button);
    }
}
const error = message => { el('error').textContent = message || ''; el('error').hidden = !message; };
function busy() {
    const otherStaff = inbox && current?.staffId && current.staffId !== config.userId && config.role !== 'ADMIN';
    const ready = current && current.status === 'OPEN' && !otherStaff;
    el('input').disabled = !ready || sending; el('send').disabled = !ready || sending;
    el('send').textContent = sending ? 'Đang gửi…' : pending ? 'Gửi lại' : 'Gửi';
    el('close').hidden = !ready; el('claim').hidden = !inbox || !ready || current.staffId === config.userId; el('new').hidden = inbox || root.dataset.view !== 'conversation' || current?.status !== 'CLOSED';
    if (!inbox) { el('new').textContent = 'Tư vấn lại'; el('back').disabled = sending; }
    el('hint').textContent = !current ? 'Chọn cuộc trò chuyện để bắt đầu.' : current.status === 'CLOSED'
        ? 'Phiên đã kết thúc. Khách có thể bắt đầu một phiên tư vấn mới.' : otherStaff ? 'Nhân viên khác đang phụ trách phiên này.'
        : 'Tối đa 2.000 ký tự. Enter để gửi, Shift+Enter để xuống dòng.';
}
async function api(path, method = 'GET', body) {
    const headers = {Accept:'application/json'};
    if (method !== 'GET') { headers['Content-Type'] = 'application/json'; headers[config.csrfHeader] = config.csrfToken; }
    const response = await fetch('/chat/api' + path, {method, headers, credentials:'same-origin', body:body === undefined ? undefined : JSON.stringify(body)});
    if (response.status === 401 || response.redirected) throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    if (!response.ok) { const data = await response.json().catch(() => ({})); throw new Error(data.message || (data.errors && Object.values(data.errors)[0]) || 'Không thực hiện được thao tác. Vui lòng thử lại.'); }
    const text = await response.text(); return text ? JSON.parse(text) : null;
}
function node(tag, text, cls) {
    const result = document.createElement(tag); if (text != null) result.textContent = text; if (cls) result.className = cls; return result;
}
const time = value => value ? new Intl.DateTimeFormat('vi-VN', {day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'}).format(new Date(value)) : '';
const sameEntry = (a, b) => !!a && !!b && (inbox
    ? a.customerId === b.customerId && a.branchId === b.branchId
    : a.sessionId === b.sessionId);
async function sessionPage(number) {
    const result = await api((inbox ? '/conversations' : '/sessions') + '?page=' + number
        + (inbox ? '&branchId=' + encodeURIComponent(el('branch').value) : ''));
    return inbox ? {...result, sessions:result.conversations} : result;
}
function renderSessions() {
    const list = el('sessions'); list.replaceChildren();
    const shown = inbox ? sessions : sessions.filter(session => session.branchId === Number(el('branch').value));
    if (!shown.length) list.append(node('p', inbox ? 'Chưa có khách cần tư vấn.' : 'Chưa có cuộc trò chuyện.', 'tc-empty'));
    shown.forEach(session => {
        const button = node('button', null, 'tc-session' + (sameEntry(current, session) ? ' is-selected' : '')); button.type = 'button';
        if (session.unreadCount) button.append(node('span', session.unreadCount, 'tc-unread'));
        button.append(node('strong', inbox ? session.customerName : session.branchName));
        button.append(node('small', (session.status === 'CLOSED' ? 'Đã kết thúc' : session.staffName ? 'Tư vấn: ' + session.staffName : 'Chờ tiếp nhận') + (inbox ? '' : ' · #' + session.sessionId)));
        button.append(node('small', time(session.lastMessageAt))); button.addEventListener('click', () => choose(session).catch(e => error(e.message))); list.append(button);
    });
    el('moreSessions').hidden = !moreSessions;
}
function heading() {
    el('title').textContent = current ? (inbox ? current.customerName + ' · ' + current.branchName : current.branchName) : 'Chọn cuộc trò chuyện';
    el('subtitle').textContent = current ? (inbox ? '' : '#' + current.sessionId + ' · ') + (current.status === 'CLOSED' ? 'Đã kết thúc' : current.staffName ? 'Nhân viên: ' + current.staffName : 'Đang chờ tiếp nhận') : 'Cuộc trò chuyện riêng với chi nhánh.'; busy();
}
function renderMessages(bottom = true, prepend = false) {
    const list = el('messages'), oldHeight = list.scrollHeight, oldTop = list.scrollTop; list.replaceChildren();
    const sorted = [...history.values()].sort((a,b) => a.messageId - b.messageId);
    if (!sorted.length) list.append(node('p', 'Chưa có tin nhắn. Hãy gửi nội dung cần tư vấn.', 'tc-empty'));
    let previousSession, previousDay;
    sorted.forEach(message => {
        const day = message.sentAt?.slice(0, 10);
        if (message.sessionId !== previousSession || day !== previousDay) {
            const divider = node('div', time(message.sentAt), 'tc-time-divider');
            divider.setAttribute('role', 'separator'); list.append(divider);
        }
        previousSession = message.sessionId; previousDay = day;
        const article = node('article', null, 'tc-message' + (message.senderId === config.userId ? ' is-self' : ''));
        article.append(node('small', message.senderName + (message.senderRole === 'CUSTOMER' ? '' : ' · Tư vấn viên')));
        // Never insert untrusted chat content or names as HTML.
        article.append(node('p', message.content)); article.append(node('time', time(message.sentAt))); list.append(article);
    });
    el('older').hidden = !hasOlder; list.scrollTop = bottom ? list.scrollHeight : prepend ? oldTop + list.scrollHeight - oldHeight : oldTop;
}
async function markRead(id) {
    if (!conversationVisible() || !nearBottom() || current?.sessionId !== id || !history.size) return;
    // History may span closed sessions. Each read cursor must belong to its own session.
    const markers = new Map(); let changed = false;
    for (const message of history.values()) markers.set(message.sessionId, Math.max(markers.get(message.sessionId) || 0, message.messageId));
    await Promise.all([...markers].map(async ([sessionId, messageId]) => {
        if ((readThrough.get(sessionId) || 0) >= messageId) return;
        await api(`/sessions/${sessionId}/read`, 'POST', {messageId});
        readThrough.set(sessionId, Math.max(readThrough.get(sessionId) || 0, messageId));
        changed = true;
        if (!inbox) { const item = sessions.find(s => s.sessionId === sessionId); if (item) item.unreadCount = 0; }
    }));
    if (inbox && changed) await refresh();
    else { renderSessions(); renderBranches(); }
}
async function loadMessages(older = false, expected = version) {
    const id = current?.sessionId; if (!id || (older && (loadingOlder || !hasOlder || !beforeId))) return;
    if (older) loadingOlder = true;
    try {
        let more, batches = 0;
        do {
            const incremental = !older && lastSyncedId !== null;
            const query = older && beforeId ? '?beforeId=' + beforeId : incremental ? '?afterId=' + lastSyncedId : '';
            const result = await api(`/sessions/${id}/conversation/messages` + query);
            if (version !== expected || current?.sessionId !== id) return;
            const follow = nearBottom();
            result.messages.forEach(m => history.set(m.messageId, m));
            if (!incremental) { beforeId = result.nextBeforeId; hasOlder = result.hasMore; }
            if (!older) lastSyncedId = result.nextAfterId ?? lastSyncedId ?? 0;
            renderMessages(!older && follow, older);
            more = incremental && result.hasMore;
        } while (more && ++batches < 10);
        await markRead(id);
    } finally { if (older) loadingOlder = false; }
}
async function choose(session) {
    if (sending) return;
    setView('conversation');
    if (sameEntry(current, session)) { current = session; heading(); renderSessions(); await loadMessages(); return; }
    const expected = ++version; current = session; history = new Map(); beforeId = null; lastSyncedId = null; hasOlder = false; pending = null;
    el('input').value = ''; error(''); heading(); renderSessions(); renderMessages();
    try { await loadMessages(false, expected); } catch (e) { if (expected === version) error(e.message); }
}
async function refresh(reset = false) {
    if (!config || stopped) return;
    const branch = el('branch').value;
    if (inbox && !branch) { sessions = []; renderSessions(); return; }
    const result = await sessionPage(0);
    if (el('branch').value !== branch) return;
    const previous = reset ? [] : sessions.filter(s => !result.sessions.some(f => sameEntry(f, s)));
    sessions = [...result.sessions, ...previous];
    if (reset || page === 0) { page = 0; moreSessions = result.hasMore; }
    if (current) {
        const selectedId = current.sessionId;
        const found = result.sessions.find(s => sameEntry(s, current));
        if (found) current = found;
        else {
            try {
                const detail = await api('/sessions/' + selectedId + (inbox ? '/conversation' : ''));
                if (current?.sessionId === selectedId) {
                    current = detail;
                    if (inbox) {
                        const index = sessions.findIndex(s => sameEntry(s, detail));
                        if (index >= 0) sessions[index] = detail;
                    }
                }
            } catch (e) {
                if (current?.sessionId === selectedId) { ++version; current = null; history.clear(); renderMessages(); error(e.message); }
            }
        }
        heading();
    }
    renderSessions(); renderBranches();
}
async function update() {
    if (!visible() || refreshing || !config) return;
    refreshing = true; try { await refresh(); if (conversationVisible()) await loadMessages(); } finally { refreshing = false; }
}
async function openSession() {
    const branchId = Number(el('branch').value); if (!branchId) { error('Vui lòng chọn chi nhánh đang hoạt động.'); return; }
    el('new').disabled = true;
    try { const session = await api('/sessions', 'POST', {branchId}); await refresh(true); await choose(session); }
    catch (e) { error(e.message); } finally { el('new').disabled = false; }
}
async function send(event) {
    event?.preventDefault(); if (sending || !current || current.status !== 'OPEN') return;
    const content = el('input').value.trim(); if (!content) return;
    if (!pending || pending.content !== content || pending.sessionId !== current.sessionId) pending = {sessionId:current.sessionId, content,
        clientMessageId:globalThis.crypto?.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2)};
    const attempt = pending, expected = version; sending = true; error(''); busy();
    try {
        const message = await api('/messages', 'POST', attempt);
        if (version === expected) { history.set(message.messageId, message); el('input').value = ''; pending = null; renderMessages(); }
        await refresh();
    } catch (e) { error(e.message + ' Nội dung vẫn được giữ; bấm Gửi lại khi kết nối ổn định.'); }
    finally { sending = false; busy(); if (!el('input').disabled) el('input').focus(); }
}
async function action(name) {
    if (!current || sending) return; const id = current.sessionId; el(name).disabled = true;
    try { const result = await api(`/sessions/${id}/${name}`, 'POST'); if (current?.sessionId === id) { current = result; heading(); } error(''); await refresh(); }
    catch (e) { error(e.message); } finally { el(name).disabled = false; }
}
function connect() {
    if (stopped || !window.SockJS || !window.Stomp) { el('connection').textContent = 'Tự cập nhật mỗi 15 giây'; return; }
    connection = Stomp.over(new SockJS('/ws')); connection.debug = null; connection.heartbeat.outgoing = 10000; connection.heartbeat.incoming = 10000;
    connection.connect({[config.csrfHeader]:config.csrfToken}, () => {
        if (stopped) { connection.disconnect(); return; } el('connection').textContent = '● Đã kết nối';
        connection.subscribe('/user/queue/chat', frame => {
            try { const event = JSON.parse(frame.body); if (event.message && conversationVisible() && current?.sessionId === event.sessionId) { const follow = nearBottom(); history.set(event.message.messageId, event.message); renderMessages(follow); } }
            catch (_) { /* HTTP history recovers any missed event. */ }
            update().catch(e => error(e.message));
        });
        connection.subscribe('/user/queue/chat-errors', frame => { try { error(JSON.parse(frame.body).message); } catch (_) { error('Không gửi được tin nhắn.'); } });
        update().catch(e => error(e.message));
    }, () => {
        if (stopped) return; el('connection').textContent = 'Đang kết nối lại · vẫn lưu và tải tin'; clearTimeout(reconnectTimer); reconnectTimer = setTimeout(connect, 5000);
    });
}
async function init() {
    try {
        const response = await fetch('/chat/api/bootstrap', {headers:{Accept:'application/json'}});
        if (!response.ok || response.redirected) throw new Error('Vui lòng đăng nhập bằng tài khoản có quyền chat.'); config = await response.json();
        if (!inbox && config.role !== 'CUSTOMER') return;
        for (const branch of config.branches) { const option = node('option', branch.branchName); option.value = branch.branchId; el('branch').append(option); }
        if (config.branchId) el('branch').value = config.branchId;
        else if (root.dataset.branchId && config.branches.some(b => String(b.branchId) === root.dataset.branchId)) el('branch').value = root.dataset.branchId;
        el('branch').disabled = inbox && config.role !== 'ADMIN'; setView('branches'); renderBranches(); busy(); await refresh(true); connect();
    } catch (e) { error(e.message); el('connection').textContent = 'Chưa kết nối'; el('new').disabled = true; }
}
if (!inbox) {
    setView('branches');
    el('back').addEventListener('click', () => {
        setView('branches'); renderBranches();
    });
}
el('form').addEventListener('submit', send);
el('input').addEventListener('keydown', e => { if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); send(); } });
el('new').addEventListener('click', openSession); el('claim').addEventListener('click', () => action('claim')); el('close').addEventListener('click', () => action('close'));
el('messages').addEventListener('scroll', () => {
    if (!conversationVisible()) return;
    if (el('messages').scrollTop < 60 && hasOlder) loadMessages(true).catch(e => error(e.message));
    else if (nearBottom() && current) markRead(current.sessionId).catch(e => error(e.message));
});
el('older').addEventListener('click', () => loadMessages(true).catch(e => error(e.message))); el('refresh').addEventListener('click', () => update().catch(e => error(e.message)));
el('moreSessions').addEventListener('click', async () => {
    el('moreSessions').disabled = true;
    try { const result = await sessionPage(page + 1);
        result.sessions.forEach(s => { const index = sessions.findIndex(old => sameEntry(old, s)); if (index < 0) sessions.push(s); else sessions[index] = s; }); page = result.page; moreSessions = result.hasMore; renderSessions(); renderBranches();
    } catch (e) { error(e.message); } finally { el('moreSessions').disabled = false; }
});
el('branch').addEventListener('change', () => {
    if (!inbox) { el('hint').textContent = 'Bấm Bắt đầu tư vấn để trò chuyện với chi nhánh vừa chọn.'; return; }
    ++version; current = null; history.clear(); beforeId = null; lastSyncedId = null; hasOlder = false; heading(); renderMessages(); refresh(true).catch(e => error(e.message));
});
document.addEventListener('visibilitychange', () => { if (visible()) update().catch(e => error(e.message)); });
popup?.addEventListener('chat-open', () => { setView('branches'); if (config) update().catch(e => error(e.message)); });
const poll = setInterval(() => { if (visible()) update().catch(e => error(e.message)); }, 15000);
window.addEventListener('beforeunload', () => { stopped = true; clearInterval(poll); clearTimeout(reconnectTimer); if (connection?.connected) connection.disconnect(); });
init();
})();
