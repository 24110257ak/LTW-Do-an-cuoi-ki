const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/chat.js'),'utf8');
class Element {
    constructor(tag='div') { this.tagName=tag; this.children=[]; this.listeners={}; this.value=''; this.hidden=false; this.disabled=false; this.dataset={}; this.scrollHeight=100; this.scrollTop=0; this.clientHeight=100; }
    append(...nodes) { this.children.push(...nodes); if(this.tagName==='select' && !this.value && nodes[0]) this.value=String(nodes[0].value); }
    replaceChildren(...nodes) { this.children=[...nodes]; }
    set textContent(value) { this.text=String(value); this.children=[]; }
    get textContent() { return this.text || this.children.map(c=>c.textContent).join(''); }
    set innerHTML(value) { throw new Error('Untrusted HTML insertion is forbidden'); }
    setAttribute(name,value) { this[name]=value; }
    addEventListener(name,fn) { (this.listeners[name] ||= []).push(fn); }
    async emit(name,event={}) { for(const fn of this.listeners[name]||[]) await fn({preventDefault(){},...event}); }
    dispatchEvent(event) { this.emit(event.type); }
    focus() {}
}
const settle = async () => { for(let i=0;i<10;i++) await new Promise(setImmediate); };
function setup({inbox=false,role='CUSTOMER',anonymous=false,initialSessions=[],initialMessages=[]}={}) {
    const names=['branch','new','connection','error','sessions','moreSessions','title','subtitle','claim','close','older','messages','form','input','send','hint','refresh','branchPicker','branchList','navigation','layout','sessionPanel','conversation','back'];
    const elements=Object.fromEntries(names.map(n=>[n,new Element(n==='branch'?'select':'div')]));
    const root=new Element(); root.dataset={inbox:String(inbox),branchId:'1'};
    root.querySelector=s=>elements[s.match(/"(.+)"/)[1]];
    const popup=new Element(), toggle=new Element();
    const document=new Element(); document.hidden=false;
    document.getElementById=id=>id==='techChatPopup'?popup:null;
    document.querySelector=s=>anonymous?null:root;
    document.querySelectorAll=()=>[toggle]; document.createElement=tag=>new Element(tag);
    let sessions=initialSessions.map(s=>({...s})), messages=initialMessages.map(m=>({...m})), failNext=false, poll;
    const calls=[];
    const response=(body,status=200)=>{const data=JSON.stringify(body);return {ok:status>=200&&status<300,status,redirected:false,json:async()=>JSON.parse(data),text:async()=>body===null?'':data};};
    const contacts=(branchId)=>{
        const groups=new Map();
        for(const session of sessions.filter(s=>s.branchId===branchId)) {
            if(!messages.some(m=>sessions.some(s=>s.sessionId===m.sessionId&&s.customerId===session.customerId&&s.branchId===branchId))) continue;
            const group=groups.get(session.customerId)||[]; group.push(session); groups.set(session.customerId,group);
        }
        return [...groups.values()].map(group=>{
            const representative=[...group].sort((a,b)=>(b.status==='OPEN')-(a.status==='OPEN')||b.sessionId-a.sessionId)[0];
            return {...representative,unreadCount:group.reduce((sum,s)=>sum+s.unreadCount,0),
                lastMessageAt:group.map(s=>s.lastMessageAt).filter(Boolean).sort().at(-1)};
        }).sort((a,b)=>(b.lastMessageAt||'').localeCompare(a.lastMessageAt||'')||b.customerId-a.customerId);
    };
    const fetch=async(url,options={})=>{
        const body=options.body?JSON.parse(options.body):null; calls.push({url,...options,body});
        if(url.endsWith('/bootstrap')) return response({userId:role==='CUSTOMER'?1:2,fullName:'Test',role,branchId:role==='STAFF'?1:null,branches:role==='STAFF'?[{branchId:1,branchName:'Chi nhánh A'}]:[{branchId:1,branchName:'Chi nhánh A'},{branchId:2,branchName:'Chi nhánh B'}],csrfHeader:'X-CSRF-TOKEN',csrfToken:'masked-token'});
        if(url.includes('/conversations?page=')) {
            const params=new URL(url,'http://test').searchParams, page=Number(params.get('page'));
            const all=contacts(Number(params.get('branchId')));
            return response({conversations:all.slice(page*30,(page+1)*30),hasMore:all.length>(page+1)*30,page});
        }
        if(url.includes('/sessions?page=')) return response({sessions,hasMore:false,page:0});
        if(url.endsWith('/sessions') && options.method==='POST') {
            let session=sessions.find(s=>s.branchId===body.branchId && s.status==='OPEN');
            if(!session) { session={sessionId:Math.max(body.branchId+4,...sessions.map(s=>s.sessionId+1)),customerId:1,customerName:'Customer',branchId:body.branchId,branchName:body.branchId===1?'Chi nhánh A':'Chi nhánh B',status:'OPEN',staffId:null,unreadCount:0}; sessions.push(session); }
            return response(session);
        }
        if(url.endsWith('/messages') && options.method==='POST') {
            if(failNext) { failNext=false; return response({message:'Mất kết nối'},503); }
            const message={...body,messageId:Math.max(9,...messages.map(m=>m.messageId))+1,senderId:role==='CUSTOMER'?1:2,senderName:'Test',senderRole:role,sentAt:'2026-10-27T10:00:00'}; messages.push(message); return response(message);
        }
        const parts=url.match(/\/sessions\/(\d+)(.*)/);
        if(parts) {
            const id=Number(parts[1]), session=sessions.find(s=>s.sessionId===id);
            if(!session) return response({message:'Not found'},404);
            if(parts[2].startsWith('/conversation/messages')) {
                const params=new URL(url,'http://test').searchParams;
                const before=params.has('beforeId')?Number(params.get('beforeId')):null;
                const after=params.has('afterId')?Number(params.get('afterId')):null;
                let selected=messages.filter(m=>{
                    const owner=sessions.find(s=>s.sessionId===m.sessionId);
                    return owner?.customerId===session.customerId && owner?.branchId===session.branchId
                        && (before===null || m.messageId<before) && (after===null || m.messageId>after);
                }).sort((a,b)=>after===null?b.messageId-a.messageId:a.messageId-b.messageId);
                const hasMore=selected.length>50; selected=selected.slice(0,50); if(after===null) selected.reverse();
                return response({messages:selected,hasMore,nextBeforeId:selected[0]?.messageId??null,nextAfterId:selected.at(-1)?.messageId??after});
            }
            if(parts[2]==='/conversation') return response(contacts(session.branchId).find(c=>c.customerId===session.customerId));
            if(parts[2]==='/read') {
                if(!messages.some(m=>m.sessionId===id&&m.messageId===body.messageId)) return response({message:'Wrong session read cursor'},400);
                session.unreadCount=0; return response(null);
            }
            if(parts[2]==='') return response(session);
        }
        throw new Error('Unexpected request: '+url);
    };
    const context={document,fetch,console,Intl,Date,Map,Event,URL,crypto:{randomUUID:()=> 'client-test-id'},setInterval:fn=>{poll=fn;return 1;},clearInterval(){},setTimeout,clearTimeout};
    context.window=context; context.globalThis=context; context.addEventListener=()=>{};
    vm.runInNewContext(source,context);
    return {elements,calls,popup,toggle,root,tick:()=>poll?.(),failNext:()=>{failNext=true;},snapshot:()=>({initialSessions:sessions,initialMessages:messages})};
}
test('anonymous visitor opens login prompt without authenticated API calls',async()=>{
    const app=setup({anonymous:true}); await settle(); assert.equal(app.calls.length,0);
    app.popup.hidden=true; await app.toggle.emit('click'); assert.equal(app.popup.hidden,false);
});
test('customer opens real session, sends XSS text safely and includes CSRF',async()=>{
    const app=setup(); await settle(); await app.elements.branchList.children[0].emit('click'); await settle();
    app.elements.input.value='<img src=x onerror=alert(1)>'; await app.elements.form.emit('submit'); await settle();
    const sent=app.calls.find(c=>c.url.endsWith('/messages')&&c.method==='POST');
    assert.equal(sent.body.sessionId,5); assert.equal(sent.body.senderId,undefined); assert.equal(sent.headers['X-CSRF-TOKEN'],'masked-token');
    assert.equal(app.elements.messages.children.find(n=>n.tagName==='article').children[1].textContent,'<img src=x onerror=alert(1)>');
    assert.equal(app.elements.input.value,'');
});
test('failed send preserves draft and retries same idempotency key',async()=>{
    const app=setup(); await settle(); await app.elements.branchList.children[0].emit('click'); await settle(); app.failNext();
    app.elements.input.value='Cần tư vấn'; await app.elements.form.emit('submit'); await settle();
    assert.equal(app.elements.input.value,'Cần tư vấn'); assert.equal(app.elements.send.textContent,'Gửi lại');
    await app.elements.form.emit('submit'); await settle();
    const attempts=app.calls.filter(c=>c.url.endsWith('/messages')&&c.method==='POST');
    assert.equal(attempts.length,2); assert.equal(attempts[0].body.clientMessageId,attempts[1].body.clientMessageId); assert.equal(app.elements.input.value,'');
});
test('staff branch selector is disabled and cannot start a customer session',async()=>{
    const app=setup({inbox:true,role:'STAFF'}); await settle();
    assert.equal(app.elements.branch.disabled,true); assert.equal(app.elements.new.hidden,true);
    assert.ok(app.calls.some(c=>c.url.includes('branchId=1')));
});

test('opening bubble shows branches; selecting a branch shows only that conversation',async()=>{
    const app=setup(); await settle();
    assert.equal(app.root.dataset.view,'branches'); assert.equal(app.elements.layout.hidden,true);
    assert.equal(app.elements.branchPicker.hidden,false); assert.equal(app.elements.branchList.children.length,2);
    assert.equal(app.calls.filter(c=>c.method==='POST').length,0);
    await app.elements.branchList.children[1].emit('click'); await settle();
    assert.equal(app.root.dataset.view,'conversation'); assert.equal(app.elements.branchPicker.hidden,true);
    assert.equal(app.elements.sessionPanel.hidden,true); assert.equal(app.elements.conversation.hidden,false);
    assert.equal(app.calls.find(c=>c.url.endsWith('/sessions')&&c.method==='POST').body.branchId,2);
});
test('back to branch list and reopening same branch preserves the draft and session',async()=>{
    const app=setup(); await settle(); await app.elements.branchList.children[0].emit('click'); await settle();
    app.elements.input.value='Bản nháp chưa gửi';
    await app.elements.back.emit('click'); assert.equal(app.root.dataset.view,'branches');
    await app.elements.branchList.children[0].emit('click'); await settle();
    assert.equal(app.root.dataset.view,'conversation'); assert.equal(app.elements.input.value,'Bản nháp chưa gửi');
    assert.equal(app.calls.filter(c=>c.url.endsWith('/sessions')&&c.method==='POST').length,1);
    await app.toggle.emit('click'); await app.toggle.emit('click'); await settle();
    assert.equal(app.root.dataset.view,'branches'); assert.equal(app.elements.layout.hidden,true);
});
test('viewing the branch list does not mark the hidden conversation as read',async()=>{
    const app=setup(); await settle(); await app.elements.branchList.children[0].emit('click');
    app.elements.input.value='Xin chào'; await app.elements.form.emit('submit'); await settle();
    await app.elements.back.emit('click');
    const reads=app.calls.filter(c=>c.url.endsWith('/read')).length;
    app.tick(); await settle();
    assert.equal(app.calls.filter(c=>c.url.endsWith('/read')).length,reads);
});

const storedSession=(id,branchId=1,status='OPEN',customerId=1)=>({sessionId:id,customerId,customerName:'Customer',branchId,branchName:branchId===1?'Chi nhánh A':'Chi nhánh B',status,staffId:null,unreadCount:1});
const storedMessage=(id,sessionId,content='Tin '+id,sentAt='2026-10-27T10:00:00')=>({messageId:id,sessionId,senderId:2,senderName:'Staff',senderRole:'STAFF',content,sentAt});
const articles=app=>app.elements.messages.children.filter(n=>n.tagName==='article');

test('branch opens messages from closed and current sessions together, with timestamps and isolated customer/branch',async()=>{
    const app=setup({initialSessions:[storedSession(4,1,'CLOSED'),storedSession(5),storedSession(6,2),storedSession(7,1,'OPEN',99)],
        initialMessages:[storedMessage(1,4,'Nội dung cũ','2026-10-26T08:00:00'),storedMessage(2,5,'Nội dung mới'),storedMessage(3,6,'Nhánh khác'),storedMessage(4,7,'Khách khác')]});
    await settle(); await app.elements.branchList.children[0].emit('click'); await settle();
    assert.equal(app.root.dataset.view,'conversation'); assert.equal(app.elements.sessionPanel.hidden,true);
    assert.deepEqual(articles(app).map(n=>n.children[1].textContent),['Nội dung cũ','Nội dung mới']);
    assert.equal(app.elements.messages.children.filter(n=>n.className==='tc-time-divider').length,2);
    assert.ok(articles(app).every(n=>n.children.some(child=>child.tagName==='time'&&child.textContent)));
    const reads=app.calls.filter(c=>c.url.endsWith('/read'));
    assert.ok(reads.some(c=>c.url.endsWith('/sessions/4/read')&&c.body.messageId===1));
    assert.ok(reads.some(c=>c.url.endsWith('/sessions/5/read')&&c.body.messageId===2));
    assert.equal(app.elements.error.textContent,'');
    app.elements.input.value='Tiếp tục tư vấn'; await app.elements.form.emit('submit');
    assert.equal(app.calls.find(c=>c.url==='/chat/api/messages'&&c.method==='POST').body.sessionId,5);
    const reopened=setup(app.snapshot()); await settle(); await reopened.elements.branchList.children[0].emit('click'); await settle();
    assert.deepEqual(articles(reopened).map(n=>n.children[1].textContent),['Nội dung cũ','Nội dung mới','Tiếp tục tư vấn']);
});

test('branch with only closed sessions opens a new session and shows its previous messages immediately',async()=>{
    const app=setup({initialSessions:[storedSession(4,1,'CLOSED')],initialMessages:[storedMessage(1,4,'Tư vấn hôm trước')]});
    await settle(); await app.elements.branchList.children[0].emit('click'); await settle();
    assert.deepEqual(articles(app).map(n=>n.children[1].textContent),['Tư vấn hôm trước']);
    assert.equal(app.elements.input.disabled,false);
    app.elements.input.value='Câu hỏi mới'; await app.elements.form.emit('submit');
    assert.equal(app.calls.find(c=>c.url==='/chat/api/messages'&&c.method==='POST').body.sessionId,5);
});

test('scrolling up loads older messages across sessions without opening a history screen',async()=>{
    const app=setup({initialSessions:[storedSession(4,1,'CLOSED'),storedSession(5)],
        initialMessages:Array.from({length:62},(_,i)=>storedMessage(i+1,i<30?4:5))});
    await settle(); await app.elements.branchList.children[0].emit('click'); await settle();
    assert.equal(articles(app).length,50); assert.equal(app.elements.older.hidden,false);
    app.elements.messages.scrollTop=0; await app.elements.messages.emit('scroll'); await settle();
    assert.equal(articles(app).length,62); assert.equal(app.elements.older.hidden,true);
    assert.equal(app.root.dataset.view,'conversation');
    assert.ok(app.calls.some(c=>c.url.includes('/conversation/messages?beforeId=13')));
    assert.equal(articles(app)[0].children[1].textContent,'Tin 1');
});

test('staff keeps the inbox layout and shows one contact per sender with all consultation messages',async()=>{
    const app=setup({inbox:true,role:'STAFF',initialSessions:[
        {...storedSession(4,1,'CLOSED'),customerName:'Khách A'}, {...storedSession(5),customerName:'Khách A'},
        {...storedSession(6,1,'OPEN',99),customerName:'Khách B'}, {...storedSession(7,2),customerName:'Nhánh khác'},
        {...storedSession(8,1,'OPEN',100),customerName:'Chưa nhắn'}],
        initialMessages:[storedMessage(1,4,'Cũ của A','2026-10-26T08:00:00'),storedMessage(2,5,'Mới của A'),storedMessage(3,6,'Của B'),storedMessage(4,7,'Nhánh khác')]});
    await settle();
    assert.equal(app.elements.sessions.children.length,2);
    const contactA=app.elements.sessions.children.find(n=>n.children.some(c=>c.tagName==='strong'&&c.textContent==='Khách A'));
    assert.ok(contactA); assert.ok(!contactA.textContent.includes('#'));
    await contactA.emit('click'); await settle();
    assert.equal(app.elements.sessionPanel.hidden,false); assert.equal(app.elements.conversation.hidden,false);
    assert.deepEqual(articles(app).map(n=>n.children[1].textContent),['Cũ của A','Mới của A']);
    assert.equal(app.elements.messages.children.filter(n=>n.className==='tc-time-divider').length,2);
    assert.equal(app.elements.input.disabled,false);
    app.elements.input.value='Staff trả lời'; await app.elements.form.emit('submit'); await settle();
    assert.equal(app.calls.find(c=>c.url==='/chat/api/messages'&&c.method==='POST').body.sessionId,5);
    assert.equal(app.elements.sessions.children.length,2);
    assert.equal(app.elements.branch.disabled,true); assert.equal(app.elements.error.textContent,'');
});

test('staff selected contact follows a reopened consultation while retaining its existing chat and draft',async()=>{
    const app=setup({inbox:true,role:'STAFF',initialSessions:[storedSession(4,1,'CLOSED')],initialMessages:[storedMessage(1,4,'Cuộc trò chuyện trước')]});
    await settle(); await app.elements.sessions.children[0].emit('click'); await settle();
    assert.equal(app.elements.input.disabled,true);
    const state=app.snapshot(); state.initialSessions.push(storedSession(5));
    state.initialMessages.push(storedMessage(2,5,'Khách quay lại'));
    app.elements.input.value='Bản nháp trả lời'; app.tick(); await settle();
    assert.equal(app.elements.sessions.children.length,1);
    assert.deepEqual(articles(app).map(n=>n.children[1].textContent),['Cuộc trò chuyện trước','Khách quay lại']);
    assert.equal(app.elements.input.disabled,false); assert.equal(app.elements.input.value,'Bản nháp trả lời');
    await app.elements.form.emit('submit'); await settle();
    assert.equal(app.calls.find(c=>c.url==='/chat/api/messages'&&c.method==='POST').body.sessionId,5);
    assert.equal(app.elements.error.textContent,'');
});

test('staff loads additional customers without duplicate contacts from repeated sessions',async()=>{
    const initialSessions=[],initialMessages=[];
    for(let i=0;i<31;i++) {
        initialSessions.push(storedSession(100+i*2,1,'CLOSED',1000+i),storedSession(101+i*2,1,'OPEN',1000+i));
        initialMessages.push(storedMessage(i+1,100+i*2));
    }
    const app=setup({inbox:true,role:'STAFF',initialSessions,initialMessages}); await settle();
    assert.equal(app.elements.sessions.children.length,30); assert.equal(app.elements.moreSessions.hidden,false);
    await app.elements.moreSessions.emit('click'); await settle();
    assert.equal(app.elements.sessions.children.length,31); assert.equal(app.elements.moreSessions.hidden,true);
    app.tick(); await settle(); assert.equal(app.elements.sessions.children.length,31);
    assert.ok(app.calls.some(c=>c.url.includes('/conversations?page=1&branchId=1')));
});

test('selected staff contact beyond the first page refreshes its reply target and cached row after reopening',async()=>{
    const initialSessions=[],initialMessages=[];
    for(let i=0;i<31;i++) {
        initialSessions.push(storedSession(100+i,1,i===0?'CLOSED':'OPEN',1000+i));
        initialMessages.push(storedMessage(i+1,100+i));
    }
    const app=setup({inbox:true,role:'STAFF',initialSessions,initialMessages}); await settle();
    await app.elements.moreSessions.emit('click'); await settle();
    await app.elements.sessions.children.at(-1).emit('click'); await settle();
    assert.equal(app.elements.input.disabled,true);
    const state=app.snapshot(); state.initialSessions.push(storedSession(999,1,'OPEN',1000));
    state.initialMessages.push(storedMessage(99,999,'Quay lại từ trang sau'));
    app.tick(); await settle();
    assert.equal(app.elements.input.disabled,false); assert.equal(app.elements.sessions.children.length,31);
    const selected=app.elements.sessions.children.find(n=>n.className.includes('is-selected'));
    assert.ok(selected); assert.ok(!selected.textContent.includes('Đã kết thúc'));
    assert.deepEqual(articles(app).map(n=>n.children[1].textContent),['Tin 1','Quay lại từ trang sau']);
    assert.ok(app.calls.some(c=>c.url==='/chat/api/sessions/100/conversation'));
});
