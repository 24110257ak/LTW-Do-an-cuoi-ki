(function (root) {
    'use strict';

    // v1 giữ mô hình Tỉnh -> Quận/Huyện -> Phường/Xã đang dùng trong dữ liệu chi nhánh.
    const API = 'https://provinces.open-api.vn/api/v1/';
    const labels = {province: '-- Chọn Tỉnh/Thành --', district: '-- Chọn Quận/Huyện --', ward: '-- Chọn Phường/Xã --'};

    function normalize(value) {
        return String(value || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd')
            .trim().replace(/^(?:(?:tinh|thanh pho|quan|huyen|phuong|xa|thi xa|thi tran)\s+|tp\.?\s*)/, '')
            .replace(/[.\s]/g, '').replace(/^hcm$/, 'hochiminh');
    }

    function init(document, fetchAddress) {
        const province = document.getElementById('province');
        const district = document.getElementById('district');
        const ward = document.getElementById('ward');
        const status = document.getElementById('addressStatus');
        const retry = document.getElementById('retryAddress');
        const savedAddresses = document.getElementById('savedAddressSelect');
        const savedHint = document.getElementById('savedAddressHint');
        if (!province || !district || !ward || !status || !retry) return;

        let version = 0;
        const cache = new Map();

        function addOption(select, name, code) {
            const option = document.createElement('option');
            option.value = name;
            option.textContent = name || labels[select.id];
            if (code != null) option.dataset.code = String(code);
            select.appendChild(option);
        }

        function fill(select, items, saved) {
            if (!Array.isArray(items) || items.some(item => !item || typeof item.name !== 'string' || item.code == null))
                throw new Error('Invalid address response');
            select.replaceChildren();
            addOption(select, '');
            const matched = saved && items.find(item => normalize(item.name) === normalize(saved));
            items.forEach(item => addOption(select, item.name, item.code));
            // Địa chỉ đã lưu/được trả lại khi validation lỗi không bị mất nếu API thiếu tên cũ.
            if (saved && !matched) addOption(select, saved);
            select.value = matched ? matched.name : (saved || '');
            return matched ? matched.code : null;
        }

        function codeOf(select) {
            const selected = select.options[select.selectedIndex];
            return selected && selected.dataset.code;
        }

        function changed(select) {
            select.dispatchEvent(new Event('change', {bubbles: true}));
        }

        function loading() {
            status.textContent = 'Đang tải danh sách địa chỉ…';
            status.className = 'small text-muted';
            retry.hidden = true;
        }

        function done(current) {
            if (current !== version) return;
            status.textContent = '';
            retry.hidden = true;
        }

        function failed(current) {
            if (current !== version) return;
            status.textContent = 'Chưa tải được danh sách địa chỉ. Vui lòng tải lại; địa chỉ đã lưu được giữ nguyên.';
            status.className = 'small text-danger';
            retry.hidden = false;
        }

        async function request(path) {
            if (!cache.has(path)) {
                const controller = new AbortController();
                const timeout = setTimeout(() => controller.abort(), 10000);
                const pending = (async () => {
                    try {
                        const response = await fetchAddress(API + path, {signal: controller.signal});
                        if (!response.ok) throw new Error('Address API unavailable');
                        return await response.json();
                    } finally {
                        clearTimeout(timeout);
                    }
                })();
                cache.set(path, pending);
                pending.catch(() => cache.delete(path));
            }
            return cache.get(path);
        }

        // Thông báo cho checkout.js sau khi chuẩn hóa tên, không tự tính tiền tại trình duyệt.
        let restoring = false;
        function notify(select) {
            restoring = true;
            try { changed(select); } finally { restoring = false; }
        }

        function markManualAddress() {
            if (restoring || !savedAddresses) return;
            savedAddresses.value = '';
            if (savedHint) savedHint.textContent = 'Bạn đang nhập hoặc chỉnh sửa địa chỉ cho đơn hàng này.';
        }

        async function applySavedAddress() {
            // Vô hiệu hóa phản hồi cũ trước khi điền đồng thời đủ các trường.
            ++version;
            const option = savedAddresses.options[savedAddresses.selectedIndex];
            const saved = savedAddresses.value && option ? option.dataset : {};
            const fields = {receiverName: saved.receiverName, receiverPhone: saved.phone, street: saved.street};
            Object.entries(fields).forEach(([id, value]) => {
                const input = document.getElementById(id);
                if (input) input.value = value || '';
            });
            fill(province, [], saved.province);
            fill(district, [], saved.district);
            fill(ward, [], saved.ward);
            // Báo lại checkout.js ngay để khóa nút đặt hàng và lấy phí giao hàng mới.
            [province, district, ward].forEach(notify);
            if (savedHint) savedHint.textContent = savedAddresses.value
                ? 'Đã điền thông tin từ địa chỉ đã lưu. Chỉnh sửa chỉ áp dụng cho đơn hàng này.'
                : 'Nhập thông tin người nhận và địa chỉ mới bên dưới.';
            await reload();
        }

        async function loadWards(districtCode, savedWard, current) {
            if (!districtCode) return;
            const data = await request('d/' + districtCode + '?depth=2');
            if (current !== version) return;
            fill(ward, data.wards, savedWard);
            notify(ward);
        }

        async function loadDistricts(provinceCode, savedDistrict, savedWard, current) {
            if (!provinceCode) return;
            const data = await request('p/' + provinceCode + '?depth=2');
            if (current !== version) return;
            const districtCode = fill(district, data.districts, savedDistrict);
            notify(district);
            await loadWards(districtCode, savedWard, current);
        }

        async function reload() {
            const current = ++version;
            const saved = [province.value, district.value, ward.value];
            loading();
            try {
                const data = await request('?depth=1');
                if (current !== version) return;
                const provinceCode = fill(province, data, saved[0]);
                notify(province);
                await loadDistricts(provinceCode, saved[1], saved[2], current);
                done(current);
            } catch (error) { failed(current); }
        }

        province.addEventListener('change', async () => {
            if (restoring) return;
            markManualAddress();
            const current = ++version;
            fill(district, []);
            fill(ward, []);
            loading();
            try {
                await loadDistricts(codeOf(province), '', '', current);
                done(current);
            } catch (error) { failed(current); }
        });
        district.addEventListener('change', async () => {
            if (restoring) return;
            markManualAddress();
            const current = ++version;
            fill(ward, []);
            loading();
            try {
                await loadWards(codeOf(district), '', current);
                done(current);
            } catch (error) { failed(current); }
        });
        ward.addEventListener('change', markManualAddress);
        ['receiverName', 'receiverPhone', 'street'].forEach(id => {
            document.getElementById(id)?.addEventListener('input', markManualAddress);
        });
        savedAddresses?.addEventListener('change', applySavedAddress);
        retry.addEventListener('click', reload);
        return {reload, ready: reload()};
    }

    if (typeof module !== 'undefined' && module.exports) module.exports = {init};
    if (root.document) init(root.document, root.fetch.bind(root));
})(typeof window !== 'undefined' ? window : globalThis);
