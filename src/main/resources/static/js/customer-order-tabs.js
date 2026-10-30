(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else factory().init(root.document);
}(typeof window !== 'undefined' ? window : globalThis, function () {
    'use strict';
    function init(doc) {
        const basic = doc.getElementById('orderStatusBasic');
        const extra = doc.getElementById('orderStatusExtra');
        const toggle = doc.getElementById('orderStatusTabsToggle');
        if (!basic || !extra || !toggle) return;
        toggle.addEventListener('click', function () {
            const showExtra = extra.hidden;
            basic.hidden = showExtra;
            extra.hidden = !showExtra;
            toggle.textContent = showExtra ? '<<' : '>>';
            toggle.title = showExtra ? 'Quay lại các trạng thái ban đầu' : 'Xem thêm trạng thái';
            toggle.setAttribute('aria-label', toggle.title);
        });
    }
    return { init };
}));
