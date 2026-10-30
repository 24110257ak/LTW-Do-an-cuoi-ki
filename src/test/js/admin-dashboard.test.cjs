const {test} = require('node:test');
const assert = require('node:assert/strict');
const {initStatusChart} = require('../../main/resources/static/js/admin-dashboard.js');

function page() {
    const element = () => ({
        children: [], attrs: {}, style: {}, handlers: {}, textContent: '',
        append(...nodes) { this.children.push(...nodes); },
        replaceChildren(...nodes) { this.children = nodes; },
        setAttribute(key, value) { this.attrs[key] = value; },
        addEventListener(name, handler) { this.handlers[name] = handler; },
        getContext() { return {}; }
    });
    const ids = Object.fromEntries(['orderStatusChart', 'orderStatusLegend', 'orderStatusTotal', 'orderStatusEmpty']
        .map(id => [id, element()]));
    class Chart {
        constructor(context, config) { this.config = config; this.hidden = new Set(); this.updates = 0; }
        toggleDataVisibility(index) { this.hidden.has(index) ? this.hidden.delete(index) : this.hidden.add(index); }
        getDataVisibility(index) { return !this.hidden.has(index); }
        update() { this.updates++; }
    }
    const doc = {getElementById: id => ids[id], createElement: element};
    return {doc, ids, Chart};
}

test('an empty period draws a neutral ring but reports zero orders and no fake tooltip', () => {
    const p = page();
    const chart = initStatusChart(p.doc, p.Chart, {NEW: 0, DELIVERED: 0});
    assert.deepEqual(chart.config.data.datasets[0].data, [1]);
    assert.deepEqual(chart.config.data.datasets[0].backgroundColor, ['#e9ecef']);
    assert.equal(chart.config.options.plugins.tooltip.enabled, false);
    assert.equal(chart.config.options.plugins.legend.display, false);
    assert.equal(p.ids.orderStatusTotal.textContent, '0');
    assert.equal(p.ids.orderStatusEmpty.hidden, false);
    for (const item of p.ids.orderStatusLegend.children) {
        const button = item.children[0];
        assert.equal(button.disabled, true);
        assert.equal(button.children[2].textContent, '0 · 0.0%');
    }
});

test('all ten statuses get separate readable legend entries and distinct colors', () => {
    const p = page();
    const statuses = ['NEW', 'CONFIRMED', 'READY_FOR_PICKUP', 'SHIPPING', 'DELIVERY_FAILED',
        'RETURNING', 'RETURNED', 'DELIVERED', 'CANCELLED', 'RETURNED_REFUND'];
    const chart = initStatusChart(p.doc, p.Chart, Object.fromEntries(statuses.map(key => [key, 1])));
    assert.equal(p.ids.orderStatusLegend.children.length, 10);
    assert.equal(new Set(chart.config.data.datasets[0].backgroundColor).size, 10);
    assert.equal(p.ids.orderStatusTotal.textContent, '10');
    assert.equal(p.ids.orderStatusEmpty.hidden, true);
    assert.equal(chart.config.options.maintainAspectRatio, false);
    for (const item of p.ids.orderStatusLegend.children) {
        assert.equal(item.children[0].children[2].textContent, '1 · 10.0%');
    }
});

test('hover percentages use actual orders and status colors survive reordering', () => {
    const p = page();
    const chart = initStatusChart(p.doc, p.Chart, {NEW: 1, DELIVERED: 3});
    assert.equal(chart.config.options.plugins.tooltip.callbacks.label({label: 'Đã giao', raw: 3}),
        'Đã giao: 3 đơn (75.0%)');
    const colors = chart.config.data.datasets[0].backgroundColor;
    const reordered = initStatusChart(p.doc, p.Chart, {DELIVERED: 3, NEW: 1});
    assert.deepEqual(reordered.config.data.datasets[0].backgroundColor, [colors[1], colors[0]]);
    assert.equal(p.ids.orderStatusLegend.children.length, 2);
});

test('clicking a legend entry toggles its segment and accessible pressed state', () => {
    const p = page();
    const chart = initStatusChart(p.doc, p.Chart, {NEW: 1, DELIVERED: 3});
    const button = p.ids.orderStatusLegend.children[0].children[0];
    assert.equal(button.type, 'button');
    assert.equal(button.attrs['aria-pressed'], 'true');
    button.handlers.click();
    assert.equal(chart.getDataVisibility(0), false);
    assert.equal(button.attrs['aria-pressed'], 'false');
    button.handlers.click();
    assert.equal(chart.getDataVisibility(0), true);
    assert.equal(button.attrs['aria-pressed'], 'true');
    assert.equal(chart.updates, 2);
});

test('missing or invalid counts cannot produce NaN or negative totals', () => {
    const p = page();
    const chart = initStatusChart(p.doc, p.Chart, {NEW: -1, CONFIRMED: 'invalid', DELIVERED: '4'});
    assert.deepEqual(chart.config.data.datasets[0].data, [0, 0, 4]);
    assert.equal(p.ids.orderStatusTotal.textContent, '4');
    const empty = initStatusChart(p.doc, p.Chart, {});
    assert.equal(empty.config.options.plugins.tooltip.enabled, false);
    assert.equal(p.ids.orderStatusLegend.children.length, 0);
});
