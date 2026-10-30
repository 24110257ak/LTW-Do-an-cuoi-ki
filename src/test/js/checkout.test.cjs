const {test} = require('node:test');
const assert = require('node:assert/strict');
const {init} = require('../../main/resources/static/js/checkout.js');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

function page() {
    const ids = ['checkoutForm', 'placeOrderButton', 'pricingMessage', 'displaySubtotal', 'displayShippingFee', 'displayDiscount', 'displayTotal',
        'typePickup', 'pickupSection', 'deliverySection', 'pickupBranchId', 'receiverName', 'receiverPhone', 'province', 'district', 'ward', 'street', 'btnApplyCoupon'];
    const nodes = Object.fromEntries(ids.map(id => [id, {textContent: '', disabled: false, required: false, checked: false, handlers: {},
        classList: {toggle() {}}, addEventListener(name, fn) { this.handlers[name] = fn; }}]));
    const values = new Map([['orderType', 'DELIVERY'], ['paymentMethod', 'COD'], ['province', 'Hà Nội']]);
    nodes.checkoutForm.dataset = {pricingUrl: '/checkout/pricing'};
    nodes.checkoutForm.values = values;
    const old = global.FormData;
    global.FormData = class { constructor(form) { this.values = new Map(form.values); } get(key) { return this.values.get(key) ?? null; } };
    return {nodes, values, doc: {getElementById: id => nodes[id], querySelectorAll: () => []}, close: () => { global.FormData = old; }};
}
const quote = (total = 1940000) => ({ok: true, json: async () => ({subtotal: 2000000, shippingFee: 40000, discountAmount: 100000, totalAmount: total})});

test('renders all four prices from one backend response and applies coupon', async () => {
    const p = page(); const urls = [];
    try {
        p.values.set('couponCode', 'WELCOME100K');
        init(p.doc, async url => { urls.push(url); return quote(); });
        assert.equal(p.nodes.placeOrderButton.disabled, true);
        await pause(25);
        assert.equal(p.nodes.displayTotal.textContent, new Intl.NumberFormat('vi-VN', {style: 'currency', currency: 'VND'}).format(1940000));
        assert.match(p.nodes.displayDiscount.textContent, /100\.000/);
        assert.equal(p.nodes.placeOrderButton.disabled, false);
        assert.equal(new URL(urls[0], 'https://example.test').searchParams.get('couponCode'), 'WELCOME100K');
        p.nodes.btnApplyCoupon.handlers.click(); await pause(25); assert.equal(urls.length, 2);
    } finally { p.close(); }
});

test('pickup/address/carrier changes invalidate the old amount and fetch a new quote', async () => {
    const p = page(); const urls = [];
    try {
        init(p.doc, async url => { urls.push(url); return quote(1900000); }); await pause(25);
        p.values.set('orderType', 'PICKUP'); p.values.set('pickupBranchId', '2'); p.values.set('providerId', '3');
        p.nodes.typePickup.checked = true;
        p.nodes.checkoutForm.handlers.change({target: {name: 'orderType'}});
        assert.equal(p.nodes.placeOrderButton.disabled, true); assert.equal(p.nodes.displayTotal.textContent, '—');
        assert.equal(p.nodes.province.required, false); assert.equal(p.nodes.pickupBranchId.required, true);
        await pause(280);
        const params = new URL(urls.at(-1), 'https://example.test').searchParams;
        assert.equal(params.get('pickupBranchId'), '2'); assert.equal(params.get('providerId'), '3');
        p.nodes.typePickup.checked = false; p.values.set('orderType', 'DELIVERY'); p.values.set('province', 'TP. Hồ Chí Minh');
        p.nodes.checkoutForm.handlers.input({target: {name: 'province'}});
        assert.equal(p.nodes.province.required, true); await pause(280);
        assert.equal(new URL(urls.at(-1), 'https://example.test').searchParams.get('province'), 'TP. Hồ Chí Minh');
    } finally { p.close(); }
});

test('a delayed old response cannot replace a newer quote', async () => {
    const p = page(); const pending = [];
    try {
        init(p.doc, () => new Promise(resolve => pending.push(resolve))); await pause(25);
        p.nodes.btnApplyCoupon.handlers.click(); await pause(25);
        pending[1](quote(1900000)); await pause(10);
        const latest = p.nodes.displayTotal.textContent;
        pending[0](quote(9999999)); await pause(10);
        assert.equal(p.nodes.displayTotal.textContent, latest);
        assert.equal(p.nodes.placeOrderButton.disabled, false);
    } finally { p.close(); }
});

test('invalid coupon or connection failure clears stale totals and prevents submit', async () => {
    const p = page();
    try {
        init(p.doc, async () => ({ok: false, json: async () => ({message: 'Mã giảm giá đã hết lượt'})})); await pause(25);
        assert.equal(p.nodes.displayTotal.textContent, '—'); assert.equal(p.nodes.placeOrderButton.disabled, true);
        assert.match(p.nodes.pricingMessage.textContent, /hết lượt/);
        let prevented = false;
        p.nodes.checkoutForm.handlers.submit({preventDefault() { prevented = true; }});
        assert.equal(prevented, true); await pause(25);
    } finally { p.close(); }
});

test('buy-now identifiers and VNPay simulation selection reach the pricing backend', async () => {
    const p = page(); const urls = [];
    try {
        p.values.set('buyNowVariantId','42');p.values.set('buyNowQuantity','2');p.values.set('paymentMethod','VNPAY');
        init(p.doc,async url=>{urls.push(url);return quote(4000000);});await pause(25);
        const params=new URL(urls[0],'https://example.test').searchParams;
        assert.equal(params.get('buyNowVariantId'),'42');assert.equal(params.get('buyNowQuantity'),'2');assert.equal(params.get('paymentMethod'),'VNPAY');
        p.values.set('paymentMethod','COD');p.nodes.checkoutForm.handlers.change({target:{name:'paymentMethod'}});
        assert.equal(p.nodes.placeOrderButton.disabled,true);await pause(280);
        assert.equal(new URL(urls.at(-1),'https://example.test').searchParams.get('paymentMethod'),'COD');
    } finally { p.close(); }
});
