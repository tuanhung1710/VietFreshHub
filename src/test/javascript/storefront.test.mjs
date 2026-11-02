import { test } from 'node:test';
import assert from 'node:assert/strict';
import { card, shopCard, pager } from '../../main/resources/static/js/storefront/render.js';
import { parameters } from '../../main/resources/static/js/storefront/api.js';

test('database and search text cannot become markup in product or shop cards', () => {
    const payload = '<img src=x onerror="alert(1)">';
    const product = { id: 1, name: payload, emoji: '🍎', shopName: payload, place: payload, price: 50000, rating: 4.8, sales: 2 };
    assert.equal((card(product).match(/<img\b/g) || []).length, 1);
    assert.ok(!card(product).includes(payload));
    assert.ok(card(product).includes('&lt;img'));
    assert.ok(!shopCard({ id:1, name:payload, description:payload, place:payload }).includes('<img'));
});

test('pagination uses supplied common options and keeps home controls separate', () => {
    const data = { page: 1, pages: 3, pageSize: 15, total: 31 };
    const home = pager(data, [15, 30], true), category = pager(data, [15, 30]);
    assert.ok(home.includes('homePageSize'));
    assert.ok(home.includes('data-homeprev'));
    assert.ok(category.includes('data-prev'));
    assert.ok(!category.includes('data-homeprev'));
    assert.ok(home.includes('value="15" selected'));
    assert.ok(!home.includes('value="10"'));
});

test('filters preserve multiple shops, Unicode locations, and zero prices', () => {
    const params = parameters({ page:2, pageSize:20, q:'Xoài & Nho', shopIds:[1,2], places:['Hà Nội','Hồ Chí Minh'], min:0, max:50000, minRating:4 });
    assert.deepEqual(params.getAll('shopIds'), ['1','2']);
    assert.deepEqual(params.getAll('places'), ['Hà Nội','Hồ Chí Minh']);
    assert.equal(params.get('keyword'), 'Xoài & Nho');
    assert.equal(params.get('minPrice'), '0');
    assert.ok(!parameters({page:1,pageSize:10}).has('minPrice'));
});
