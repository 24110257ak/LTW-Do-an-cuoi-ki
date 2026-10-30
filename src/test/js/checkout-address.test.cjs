const {test} = require('node:test');
const assert = require('node:assert/strict');
const {init} = require('../../main/resources/static/js/checkout-address.js');

function page(saved = ['TP. Hồ Chí Minh', 'Quận 1', 'Phường Bến Nghé']) {
    const notifications = [];
    function select(id, value) {
        return {id, options: value ? [{value, dataset: {}}] : [], _value: value, handlers: {},
            get value() { return this._value; }, set value(value) { this._value = this.options.some(o => o.value === value) ? value : ''; },
            get selectedIndex() { return this.options.findIndex(o => o.value === this.value); },
            replaceChildren() { this.options = []; this._value = ''; },
            appendChild(option) { this.options.push(option); },
            addEventListener(name, handler) { (this.handlers[name] ||= []).push(handler); },
            dispatchEvent(event) { notifications.push(this.id); (this.handlers[event.type] || []).forEach(fn => fn(event)); },
            async choose(value) { this.value = value; await Promise.all((this.handlers.change || []).map(fn => fn())); }
        };
    }
    const nodes = Object.fromEntries(['province', 'district', 'ward'].map((id, i) => [id, select(id, saved[i])]));
    nodes.addressStatus = {textContent: ''};
    nodes.retryAddress = {hidden: true, addEventListener(name, fn) { this[name] = fn; }};
    return {nodes, notifications, select, doc: {getElementById: id => nodes[id], createElement: () => ({dataset: {}})}};
}
const data = {
    '?depth=1': [{name: 'Thành phố Hồ Chí Minh', code: 79}, {name: 'Thành phố Hà Nội', code: 1}],
    'p/79?depth=2': {districts: [{name: 'Quận 1', code: 760}, {name: 'Quận 3', code: 770}, {name: 'Thành phố Thủ Đức', code: 769}]},
    'p/1?depth=2': {districts: [{name: 'Quận Ba Đình', code: 1}]},
    'd/760?depth=2': {wards: [{name: 'Phường Bến Nghé', code: 26734}]},
    'd/770?depth=2': {wards: [{name: 'Phường Võ Thị Sáu', code: 27123}]},
    'd/769?depth=2': {wards: [{name: 'Phường Linh Trung', code: 26809}]},
    'd/1?depth=2': {wards: [{name: 'Phường Phúc Xá', code: 1}]}
};
const response = value => ({ok: true, json: async () => value});
const key = url => url.split('/api/v1/')[1];
const fetchData = async url => response(data[key(url)]);

test('restores saved address, normalizes TP aliases, and notifies backend pricing listeners', async () => {
    const p = page(); const urls = [];
    await init(p.doc, async url => { urls.push(url); return fetchData(url); }).ready;
    assert.equal(p.nodes.province.value, 'Thành phố Hồ Chí Minh');
    assert.equal(p.nodes.district.value, 'Quận 1');
    assert.equal(p.nodes.ward.value, 'Phường Bến Nghé');
    assert.equal(p.nodes.province.options[p.nodes.province.selectedIndex].dataset.code, '79');
    assert.deepEqual(p.notifications, ['province', 'district', 'ward']);
    assert.equal(urls.length, 3);
    assert.ok(urls.every(url => url.startsWith('https://provinces.open-api.vn/api/v1/')));
});

test('province and district changes clear descendants and submit names rather than numeric codes', async () => {
    const p = page(); await init(p.doc, fetchData).ready;
    const change = p.nodes.province.choose('Thành phố Hà Nội');
    assert.equal(p.nodes.district.value, ''); assert.equal(p.nodes.ward.value, '');
    await change;
    assert.equal(p.nodes.district.options[1].value, 'Quận Ba Đình');
    await p.nodes.district.choose('Quận Ba Đình');
    assert.equal(p.nodes.ward.options[1].value, 'Phường Phúc Xá');
    p.nodes.ward.value = 'Phường Phúc Xá';
    await p.nodes.province.choose('');
    assert.equal(p.nodes.district.value, ''); assert.equal(p.nodes.ward.value, '');
});

test('older province response cannot overwrite districts for a newer province selection', async () => {
    const p = page(['', '', '']); let resolveOld;
    await init(p.doc, url => key(url) === 'p/79?depth=2'
        ? new Promise(resolve => { resolveOld = resolve; }) : fetchData(url)).ready;
    const oldChange = p.nodes.province.choose('Thành phố Hồ Chí Minh');
    await p.nodes.province.choose('Thành phố Hà Nội');
    resolveOld(response(data['p/79?depth=2'])); await oldChange;
    assert.equal(p.nodes.province.value, 'Thành phố Hà Nội');
    assert.equal(p.nodes.district.options[1].value, 'Quận Ba Đình');
    assert.equal(p.nodes.district.options.length, 2);
});

test('network failure preserves saved address and retry refetches failed requests', async () => {
    const p = page(); let fail = true;
    const app = init(p.doc, async url => { if (fail) throw new TypeError('Offline'); return fetchData(url); });
    await app.ready;
    assert.equal(p.nodes.province.value, 'TP. Hồ Chí Minh');
    assert.equal(p.nodes.ward.value, 'Phường Bến Nghé');
    assert.equal(p.nodes.retryAddress.hidden, false);
    assert.match(p.nodes.addressStatus.textContent, /tải lại/);
    fail = false; await p.nodes.retryAddress.click();
    assert.equal(p.nodes.province.value, 'Thành phố Hồ Chí Minh');
    assert.equal(p.nodes.ward.value, 'Phường Bến Nghé');
    assert.equal(p.nodes.retryAddress.hidden, true);
});

test('malformed API response preserves selections and offers retry', async () => {
    const p = page(); await init(p.doc, async () => response({error: 'Bad gateway'})).ready;
    assert.equal(p.nodes.province.value, 'TP. Hồ Chí Minh');
    assert.equal(p.nodes.retryAddress.hidden, false);
});

test('legacy address absent from API remains selected instead of silently switching addresses', async () => {
    const p = page(['TP. Hồ Chí Minh', 'Quận cũ', 'Phường cũ']);
    await init(p.doc, fetchData).ready;
    assert.equal(p.nodes.district.value, 'Quận cũ');
    assert.equal(p.nodes.ward.value, 'Phường cũ');
});

test('saved Thu Duc abbreviation matches API full name and loads the correct wards', async () => {
    const p = page(['TP.HCM', 'TP. Thủ Đức', 'Phường Linh Trung']);
    await init(p.doc, fetchData).ready;
    assert.equal(p.nodes.district.value, 'Thành phố Thủ Đức');
    assert.equal(p.nodes.ward.value, 'Phường Linh Trung');
    assert.equal(p.nodes.retryAddress.hidden, true);
});

function savedPage() {
    const p = page();
    const saved = p.select('savedAddressSelect', '');
    saved.options = [{value: '', dataset: {}},
        {value: '1', dataset: {receiverName: 'Người nhận A', phone: '0901000001', province: 'TP. Hồ Chí Minh', district: 'Quận 1', ward: 'Phường Bến Nghé', street: '12 Lê Lợi'}},
        {value: '2', dataset: {receiverName: 'Người nhận B', phone: '0901000002', province: 'Hà Nội', district: 'Quận Ba Đình', ward: 'Phường Phúc Xá', street: '20 Phố Huế'}}];
    saved.value = '1';
    p.nodes.savedAddressSelect = saved;
    p.nodes.savedAddressHint = {textContent: ''};
    for (const id of ['receiverName','receiverPhone','street']) p.nodes[id] = {
        value: '', handlers: {}, addEventListener(name, handler) {this.handlers[name] = handler;}
    };
    return p;
}

test('choosing a saved address fills the recipient, all location levels and notifies pricing', async () => {
    const p = savedPage();
    await init(p.doc, fetchData).ready;
    await p.nodes.savedAddressSelect.choose('2');
    assert.equal(p.nodes.receiverName.value, 'Người nhận B');
    assert.equal(p.nodes.receiverPhone.value, '0901000002');
    assert.equal(p.nodes.street.value, '20 Phố Huế');
    assert.equal(p.nodes.province.value, 'Thành phố Hà Nội');
    assert.equal(p.nodes.district.value, 'Quận Ba Đình');
    assert.equal(p.nodes.ward.value, 'Phường Phúc Xá');
    assert.equal(p.nodes.savedAddressSelect.value, '2');
    assert.ok(p.notifications.filter(name => name === 'province').length >= 2);
});

test('a slow response for an old saved address cannot replace the latest choice', async () => {
    const p = savedPage(); let resolveOld;
    await init(p.doc, url => key(url) === 'p/1?depth=2'
        ? new Promise(resolve => {resolveOld = resolve;}) : fetchData(url)).ready;
    const oldChoice = p.nodes.savedAddressSelect.choose('2');
    // Let reload consume the cached list of provinces and start the old district request.
    await new Promise(resolve => setImmediate(resolve));
    await p.nodes.savedAddressSelect.choose('1');
    resolveOld(response(data['p/1?depth=2'])); await oldChoice;
    assert.equal(p.nodes.receiverName.value, 'Người nhận A');
    assert.equal(p.nodes.province.value, 'Thành phố Hồ Chí Minh');
    assert.equal(p.nodes.district.value, 'Quận 1');
    assert.equal(p.nodes.ward.value, 'Phường Bến Nghé');
    assert.equal(p.nodes.savedAddressSelect.value, '1');
});

test('saved address remains usable when the address API is unavailable', async () => {
    const p = savedPage();
    await init(p.doc, async () => {throw new TypeError('Offline');}).ready;
    await p.nodes.savedAddressSelect.choose('2');
    assert.equal(p.nodes.receiverName.value, 'Người nhận B');
    assert.equal(p.nodes.province.value, 'Hà Nội');
    assert.equal(p.nodes.district.value, 'Quận Ba Đình');
    assert.equal(p.nodes.ward.value, 'Phường Phúc Xá');
    assert.equal(p.nodes.street.value, '20 Phố Huế');
    assert.equal(p.nodes.retryAddress.hidden, false);
});

test('new-address choice clears copied fields; editing a saved address marks it as custom', async () => {
    const p = savedPage(); await init(p.doc, fetchData).ready;
    await p.nodes.savedAddressSelect.choose('2');
    p.nodes.street.value = 'Địa chỉ mới';
    p.nodes.street.handlers.input();
    assert.equal(p.nodes.savedAddressSelect.value, '');
    assert.equal(p.nodes.street.value, 'Địa chỉ mới');
    await p.nodes.savedAddressSelect.choose('1');
    await p.nodes.savedAddressSelect.choose('');
    for (const field of ['receiverName','receiverPhone','province','district','ward','street']) assert.equal(p.nodes[field].value, '');
});
