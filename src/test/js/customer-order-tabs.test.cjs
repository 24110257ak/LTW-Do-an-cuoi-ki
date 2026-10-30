const {test} = require('node:test');
const assert = require('node:assert/strict');
const {init} = require('../../main/resources/static/js/customer-order-tabs.js');

function page(extraSelected = false) {
    const basic = {hidden: extraSelected};
    const extra = {hidden: !extraSelected};
    const toggle = {textContent: extraSelected ? '<<' : '>>', attrs: {},
        addEventListener(name, handler) { this[name] = handler; },
        setAttribute(name, value) { this.attrs[name] = value; }};
    const nodes = {orderStatusBasic: basic, orderStatusExtra: extra, orderStatusTabsToggle: toggle};
    init({getElementById: id => nodes[id]});
    return {basic, extra, toggle};
}

test('>> shows the new customer status tabs and << restores the original tabs', () => {
    const p = page();
    p.toggle.click();
    assert.equal(p.basic.hidden, true);
    assert.equal(p.extra.hidden, false);
    assert.equal(p.toggle.textContent, '<<');
    assert.match(p.toggle.attrs['aria-label'], /Quay lại/);
    p.toggle.click();
    assert.equal(p.basic.hidden, false);
    assert.equal(p.extra.hidden, true);
    assert.equal(p.toggle.textContent, '>>');
    assert.match(p.toggle.attrs['aria-label'], /Xem thêm/);
});

test('a page opened on a new status can navigate back and forward consistently', () => {
    const p = page(true);
    p.toggle.click();
    assert.equal(p.basic.hidden, false);
    assert.equal(p.extra.hidden, true);
    assert.equal(p.toggle.textContent, '>>');
    p.toggle.click();
    assert.equal(p.extra.hidden, false);
    assert.equal(p.toggle.textContent, '<<');
});

test('initialization is safe on pages without customer order tabs', () => {
    assert.doesNotThrow(() => init({getElementById: () => null}));
});
