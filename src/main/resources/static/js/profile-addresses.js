(function (root) {
    'use strict';
    function init(document) {
        const modal = document.getElementById('deleteAddressModal');
        if (!modal) return;
        const form = document.getElementById('deleteAddressForm');
        const id = document.getElementById('deleteAddressId');
        const message = document.getElementById('deleteAddressMessage');
        const warning = document.getElementById('deleteDefaultWarning');
        const confirm = document.getElementById('confirmDeleteAddress');
        let submitting = false;
        function reset() { id.value = ''; confirm.disabled = true; warning.hidden = true; submitting = false; }
        reset();
        modal.addEventListener('show.bs.modal', event => {
            reset();
            const data = event.relatedTarget?.dataset;
            if (!data || !/^[1-9]\d*$/.test(data.addressId || '')) return;
            id.value = data.addressId;
            message.textContent = 'Xóa địa chỉ nhận hàng của ' + (data.receiverName || 'bạn') + '? Thao tác này không thể hoàn tác.';
            warning.hidden = data.addressDefault !== 'true';
            confirm.disabled = false;
        });
        modal.addEventListener('hidden.bs.modal', reset);
        form.addEventListener('submit', event => {
            if (submitting || !/^[1-9]\d*$/.test(id.value || '')) event.preventDefault();
            else { submitting = true; confirm.disabled = true; }
        });
    }
    if (typeof module !== 'undefined' && module.exports) module.exports = {init};
    if (root.document) init(root.document);
})(typeof window !== 'undefined' ? window : globalThis);
