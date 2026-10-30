(function (root) {
    'use strict';
    function init(document, fetchLimits) {
        const form = document.getElementById('addToCartForm');
        if (!form) return;
        const quantity = document.getElementById('inputQuantity');
        const minus = document.getElementById('btnQtyMinus');
        const plus = document.getElementById('btnQtyPlus');
        const add = document.getElementById('btnAddToCart');
        const buy = document.getElementById('btnBuyNow');
        const hint = document.getElementById('stockLimitHint');
        const max = Number(form.dataset.maxQuantity || 10);
        let inCart = Number(form.dataset.inCart || 0), loading = false, failed = false, version = 0;
        const selected = () => form.querySelector('input[name="variantId"]:checked');
        function paint() {
            let count = Number(quantity.value);
            if (!Number.isInteger(count)) count = 1;
            count = Math.max(1, Math.min(max, count));
            quantity.value = String(count);
            const remaining = Math.max(0, max - inCart), hasVariant = !!selected();
            minus.disabled = count <= 1; plus.disabled = count >= max;
            add.disabled = !hasVariant || loading || failed || count > remaining;
            buy.disabled = !hasVariant;
            hint.textContent = loading ? 'Đang kiểm tra số lượng trong giỏ…' : failed ? 'Chưa cập nhật được giỏ hàng. Vui lòng tải lại trang để thêm vào giỏ.'
                : `Giỏ đã có ${inCart} sản phẩm này; bạn có thể thêm tối đa ${remaining} (cộng các phiên bản, giới hạn ${max}). Mua ngay tạo đơn riêng.`;
            hint.className = 'small ' + (failed || count > remaining ? 'text-danger' : 'text-muted');
        }
        async function refresh() {
            const radio = selected(), current = ++version;
            if (!radio || form.dataset.signedIn !== 'true') { paint(); return; }
            loading = true; failed = false; paint();
            try {
                const response = await fetchLimits(form.dataset.limitUrl + '?variantId=' + encodeURIComponent(radio.value), {headers: {'Accept': 'application/json'}, cache: 'no-store'});
                if (!response.ok || response.redirected) throw new Error('limits');
                const result = await response.json();
                if (current !== version) return;
                if (result.inCartQuantity == null || !Number.isInteger(Number(result.inCartQuantity)) || Number(result.inCartQuantity) < 0) throw new Error('limits');
                inCart = Number(result.inCartQuantity);
            } catch (_) { if (current === version) failed = true; }
            finally { if (current === version) { loading = false; paint(); } }
        }
        minus.addEventListener('click', () => { quantity.value = Number(quantity.value) - 1; paint(); });
        plus.addEventListener('click', () => { quantity.value = Number(quantity.value) + 1; paint(); });
        quantity.addEventListener('input', paint);
        form.addEventListener('change', event => { if (event.target.name === 'variantId') refresh(); });
        form.addEventListener('submit', event => {
            paint();
            if (event.submitter === buy ? buy.disabled : add.disabled) { event.preventDefault(); }
        });
        paint(); refresh();
    }
    if (typeof module !== 'undefined' && module.exports) module.exports = {init};
    if (root.document) init(root.document, root.fetch.bind(root));
})(typeof window !== 'undefined' ? window : globalThis);
