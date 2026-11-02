import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { card, discountLabel, money } from '../../main/resources/static/js/storefront/render.js';
import { shopProductCard } from '../../main/resources/static/js/storefront/shop-catalog.js';

test('home, catalog and shop cards show only the sale price and a percentage on the image', () => {
    const product = { id: 1, name: 'Xoài', imageUrl: '/images/xoai-cat.png', emoji: '🥭', price: 65000, compareAtPrice: 100000, discountPercent: 35 };
    for (const render of [card, shopProductCard]) {
        const markup = render(product);
        assert.ok(markup.includes('65.000 VND'));
        assert.ok(markup.includes('src="/images/xoai-cat.png"'));
        assert.ok(!markup.includes('🥭'));
        assert.ok(!markup.includes('<del'));
        assert.ok(!markup.includes('100.000 VND'));
        assert.match(markup, /<div class="(?:p-image|picture)">.*class="discount-badge discount-badge--image">-35%<\/span><\/div>/);
        assert.ok(!render({ ...product, compareAtPrice: null, discountPercent: null }).includes('discount-badge'));
        assert.ok(render({ ...product, price: 99999, discountPercent: 0 }).includes('Giảm dưới 1%'));
        assert.ok(render({ ...product, imageUrl: null }).includes('src="/images/product/product-placeholder.svg"'));
        assert.ok(!render({ ...product, imageUrl: '/image.jpg" onerror="alert(1)' }).includes(' onerror="'));
    }
});

test('switching product variants updates and clears both the old price and percentage', async () => {
    const elements = Object.fromEntries(['quantity', 'mainPhoto', 'currentPrice', 'compareAtPrice', 'discountPercent',
        'stock', 'variantNote', 'minus', 'plus'].map(id => [id, {
        value: '1', dataset: {}, addEventListener() {}, classList: { toggle() {} }
    }]));
    const variants = [
        { value: '3', checked: true, dataset: { price: '65000', compareAtPrice: '100000', discountPercent: '35', stock: '20', name: 'Hộp 1 kg' } },
        { value: '4', checked: false, dataset: { price: '90000', compareAtPrice: '120000', discountPercent: '25', stock: '10', name: 'Hộp 2 kg' } },
        { value: '5', checked: false, dataset: { price: '60000', stock: '5', name: 'Hộp 500 g' } }
    ];
    variants.forEach(variant => variant.closest = () => ({ classList: { toggle() {} } }));
    const listeners = {};
    elements.productPurchase = {
        querySelector: () => variants.find(variant => variant.checked),
        querySelectorAll: () => variants,
        addEventListener: (name, listener) => listeners[name] = listener
    };
    const source = (await readFile(new URL('../../main/resources/static/js/storefront/product-detail.js', import.meta.url), 'utf8'))
        .replace(/^import .*;\r?\n/gm, '');
    vm.runInNewContext(source, {
        $: id => elements[id], money, discountLabel,
        quantityWithinStock: (value, stock) => Math.min(stock, Math.max(1, Number(value))),
        refreshCartCount: async () => {}, notice() {},
        document: { querySelectorAll: () => [], addEventListener() {} },
        location: { search: '' }, URLSearchParams, history: { replaceState() {} }
    });
    assert.equal(elements.discountPercent.textContent, '-35%');
    assert.equal(elements.compareAtPrice.textContent, '100.000 VND');
    assert.equal(elements.compareAtPrice.hidden, false);

    function select(index) {
        variants.forEach((variant, i) => variant.checked = i === index);
        listeners.change({ target: { name: 'variantId', value: variants[index].value } });
    }
    select(1);
    assert.equal(elements.currentPrice.textContent, '90.000 VND');
    assert.equal(elements.compareAtPrice.textContent, '120.000 VND');
    assert.equal(elements.discountPercent.textContent, '-25%');
    select(2);
    assert.equal(elements.currentPrice.textContent, '60.000 VND');
    assert.equal(elements.compareAtPrice.hidden, true);
    assert.equal(elements.discountPercent.hidden, true);
    assert.equal(elements.compareAtPrice.textContent, '');
    assert.equal(elements.discountPercent.textContent, '');
    variants[0].dataset.discountPercent = '0';
    select(0);
    assert.equal(elements.discountPercent.hidden, false);
    assert.equal(elements.discountPercent.textContent, 'Giảm dưới 1%');
});
