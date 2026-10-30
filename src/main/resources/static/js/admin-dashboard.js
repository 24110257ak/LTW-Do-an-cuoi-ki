(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.TechChainDashboard = factory();
}(typeof window !== 'undefined' ? window : globalThis, function () {
    'use strict';

    // Key colors by status so filtering/reordering never changes their meaning.
    const statuses = {
        NEW: ['Mới', '#eab308'],
        CONFIRMED: ['Đã xác nhận', '#06b6d4'],
        READY_FOR_PICKUP: ['Chờ nhận tại cửa hàng', '#8b5cf6'],
        SHIPPING: ['Đang giao', '#3b82f6'],
        DELIVERY_FAILED: ['Giao thất bại', '#f97316'],
        RETURNING: ['Đang hoàn', '#6366f1'],
        RETURNED: ['Đã nhận hoàn', '#14b8a6'],
        DELIVERED: ['Đã giao', '#22c55e'],
        CANCELLED: ['Đã hủy', '#ef4444'],
        RETURNED_REFUND: ['Trả hàng/Hoàn tiền', '#64748b']
    };

    function initStatusChart(doc, ChartCtor, counts) {
        const canvas = doc.getElementById('orderStatusChart');
        const legend = doc.getElementById('orderStatusLegend');
        if (!canvas || !legend) return null;
        const keys = Object.keys(counts || {});
        const values = keys.map(key => {
            const count = Number(counts[key]);
            return Number.isFinite(count) && count > 0 ? Math.floor(count) : 0;
        });
        const names = keys.map(key => statuses[key]?.[0] || key);
        const colors = keys.map(key => statuses[key]?.[1] || '#94a3b8');
        const total = values.reduce((sum, value) => sum + value, 0);
        const formatCount = value => value.toLocaleString('vi-VN');
        const percentage = value => (total ? value * 100 / total : 0).toFixed(1) + '%';
        doc.getElementById('orderStatusTotal').textContent = formatCount(total);
        doc.getElementById('orderStatusEmpty').hidden = total > 0;

        const chart = new ChartCtor(canvas.getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: total ? names : ['Chưa có đơn hàng'],
                datasets: [{
                    // A neutral placeholder keeps the chart visible for an empty period.
                    // This is a drawing value only; the totals and legend still show zero.
                    data: total ? values : [1],
                    backgroundColor: total ? colors : ['#e9ecef'],
                    borderColor: '#ffffff',
                    borderWidth: total ? 2 : 0,
                    hoverOffset: total ? 5 : 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '72%',
                layout: { padding: 8 },
                events: total ? ['mousemove', 'mouseout', 'click', 'touchstart', 'touchmove'] : [],
                plugins: {
                    // The HTML legend has its own space; it cannot shrink the doughnut.
                    legend: { display: false },
                    tooltip: {
                        enabled: total > 0,
                        callbacks: { label: context => context.label + ': ' +
                            formatCount(Number(context.raw) || 0) + ' đơn (' + percentage(Number(context.raw) || 0) + ')' }
                    }
                }
            }
        });

        legend.replaceChildren();
        keys.forEach((key, index) => {
            const item = doc.createElement('li');
            const button = doc.createElement('button');
            button.type = 'button';
            button.disabled = !total;
            button.setAttribute('aria-pressed', 'true');
            button.title = 'Ẩn/hiện trạng thái ' + names[index];
            const swatch = doc.createElement('span');
            swatch.className = 'order-status-swatch';
            swatch.style.backgroundColor = colors[index];
            swatch.setAttribute('aria-hidden', 'true');
            const label = doc.createElement('span');
            label.className = 'order-status-label';
            label.textContent = names[index];
            const value = doc.createElement('span');
            value.className = 'order-status-value';
            value.textContent = formatCount(values[index]) + ' · ' + percentage(values[index]);
            button.append(swatch, label, value);
            button.addEventListener('click', () => {
                chart.toggleDataVisibility(index);
                button.setAttribute('aria-pressed', String(chart.getDataVisibility(index)));
                chart.update();
            });
            item.append(button);
            legend.append(item);
        });
        return chart;
    }

    return { initStatusChart };
}));
