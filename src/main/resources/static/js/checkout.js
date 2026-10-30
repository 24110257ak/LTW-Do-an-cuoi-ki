(function (root) {
    'use strict';
    function init(document, fetchQuote) {
        const form = document.getElementById('checkoutForm');
        if (!form) return;
        const button = document.getElementById('placeOrderButton');
        const message = document.getElementById('pricingMessage');
        const fields = ['subtotal', 'shippingFee', 'discountAmount', 'totalAmount'];
        const nodes = fields.map(key => document.getElementById({subtotal: 'displaySubtotal', shippingFee: 'displayShippingFee', discountAmount: 'displayDiscount', totalAmount: 'displayTotal'}[key]));
        let version = 0, timer, valid = false;
        const money = new Intl.NumberFormat('vi-VN', {style: 'currency', currency: 'VND'});
        function view() {
            const pickup = document.getElementById('typePickup').checked;
            document.getElementById('pickupSection').classList.toggle('d-none', !pickup);
            document.getElementById('deliverySection').classList.toggle('d-none', pickup);
            // Required inputs must be restored when changing back from pickup.
            ['receiverName', 'receiverPhone', 'province', 'district', 'ward', 'street'].forEach(id => {
                const input = document.getElementById(id);
                if (input) input.required = !pickup;
            });
            document.getElementById('pickupBranchId').required = pickup;
        }
        async function refresh(current) {
            const params = new URLSearchParams();
            const selected = new FormData(form);
            ['orderType', 'paymentMethod', 'province', 'district', 'pickupBranchId', 'providerId', 'couponCode', 'buyNowVariantId', 'buyNowQuantity'].forEach(key => {
                const value = selected.get(key);
                if (value !== null && value !== '') params.set(key, value);
            });
            try {
                const response = await fetchQuote(form.dataset.pricingUrl + '?' + params, {headers: {'Accept': 'application/json'}, cache: 'no-store'});
                if (current !== version) return;
                if (response.redirected || response.status === 401 || response.status === 403) throw new Error('Vui lòng đăng nhập lại để tiếp tục đặt hàng.');
                const result = await response.json();
                if (current !== version) return;
                if (!response.ok) throw new Error(result.message || 'Không tính được số tiền. Vui lòng kiểm tra lựa chọn của bạn.');
                const values = fields.map(key => Number(result[key]));
                if (values.some(value => !Number.isFinite(value) || value < 0) || fields.some(key => result[key] == null))
                    throw new Error('Không nhận được bảng giá hợp lệ. Vui lòng thử lại.');
                nodes.forEach((node, i) => { node.textContent = (i === 2 ? '− ' : '') + money.format(values[i]); });
                valid = true;
                button.disabled = false;
                message.textContent = 'Đã cập nhật số tiền theo lựa chọn của bạn.';
                message.className = 'small text-success mb-3';
            } catch (error) {
                if (current !== version) return;
                valid = false;
                button.disabled = true;
                nodes.forEach(node => { node.textContent = '—'; });
                message.textContent = error instanceof SyntaxError || error instanceof TypeError ? 'Không nhận được bảng giá. Vui lòng thử lại hoặc đăng nhập lại.' : (error.message || 'Không kết nối được. Vui lòng thử lại.');
                message.className = 'small text-danger mb-3';
            }
        }
        function schedule(immediate) {
            clearTimeout(timer);
            valid = false;
            button.disabled = true;
            nodes.forEach(node => { node.textContent = '—'; });
            message.textContent = 'Đang cập nhật số tiền…';
            message.className = 'small text-muted mb-3';
            view();
            const current = ++version;
            timer = setTimeout(() => refresh(current), immediate ? 0 : 250);
        }
        form.addEventListener('input', event => {
            if (['province', 'district', 'ward', 'street', 'couponCode'].includes(event.target.name)) schedule(false);
        });
        form.addEventListener('change', event => {
            if (['orderType', 'paymentMethod', 'pickupBranchId', 'providerId', 'province', 'district', 'couponCode'].includes(event.target.name)) schedule(false);
        });
        document.getElementById('btnApplyCoupon').addEventListener('click', () => schedule(true));
        form.addEventListener('submit', event => {
            if (!valid) { event.preventDefault(); schedule(true); }
            else { valid = false; button.disabled = true; }
        });
        schedule(true);
    }
    if (typeof module !== 'undefined' && module.exports) module.exports = {init};
    if (root.document) init(root.document, root.fetch.bind(root));
})(typeof window !== 'undefined' ? window : globalThis);
