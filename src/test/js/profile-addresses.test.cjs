const {test} = require('node:test');
const assert = require('node:assert/strict');
const {init} = require('../../main/resources/static/js/profile-addresses.js');
function page() {
    const nodes = Object.fromEntries(['deleteAddressModal','deleteAddressForm','deleteAddressId','deleteAddressMessage','deleteDefaultWarning','confirmDeleteAddress']
        .map(id => [id, {value: '', handlers: {}, addEventListener(name, handler) {this.handlers[name] = handler;}}]));
    init({getElementById: id => nodes[id]});
    return nodes;
}
test('delete confirmation copies only the clicked ID, uses plain text and warns for default address', () => {
    const p = page();
    assert.equal(p.confirmDeleteAddress.disabled, true);
    p.deleteAddressModal.handlers['show.bs.modal']({relatedTarget: {dataset: {addressId: '12', receiverName: '<img src=x>', addressDefault: 'true'}}});
    assert.equal(p.deleteAddressId.value, '12');
    assert.ok(p.deleteAddressMessage.textContent.includes('<img src=x>'));
    assert.equal(p.deleteDefaultWarning.hidden, false);
    assert.equal(p.confirmDeleteAddress.disabled, false);
    p.deleteAddressModal.handlers['hidden.bs.modal']();
    assert.equal(p.deleteAddressId.value, '');
    assert.equal(p.confirmDeleteAddress.disabled, true);
});
test('invalid confirmation and repeated submits are blocked', () => {
    const p = page();
    let prevented = 0;
    p.deleteAddressModal.handlers['show.bs.modal']({relatedTarget: {dataset: {addressId: '../other'}}});
    p.deleteAddressForm.handlers.submit({preventDefault() {prevented++;}});
    assert.equal(prevented, 1);
    p.deleteAddressModal.handlers['show.bs.modal']({relatedTarget: {dataset: {addressId: '8', addressDefault: 'false'}}});
    p.deleteAddressForm.handlers.submit({preventDefault() {prevented++;}});
    assert.equal(p.confirmDeleteAddress.disabled, true);
    p.deleteAddressForm.handlers.submit({preventDefault() {prevented++;}});
    assert.equal(prevented, 2);
});
