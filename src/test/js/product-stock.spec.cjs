// Run with Node.js and Playwright on NODE_PATH after ProductStockViewTest writes the real Thymeleaf fixture.
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { test, before, after } = require('node:test');
let browser;
before(async () => { browser = await chromium.launch({ channel: 'chrome', headless: true }); });
after(async () => { if (browser) await browser.close(); });

function snapshot(firstStock = 5, secondStock = 4) {
    return { productId: 1, variants: [
        { variantId: 11, variantName: 'Hộp 1 kg', price: 30000, imageUrl: '/images/fruit-1.jpg',
          availableStock: firstStock, purchasable: firstStock > 0,
          availabilityMessage: firstStock ? `Còn ${firstStock} sản phẩm có thể mua.` : 'Tạm hết hàng.' },
        { variantId: 12, variantName: 'Hộp 2 kg', price: 50000, imageUrl: '/images/fruit-2.jpg',
          availableStock: secondStock, purchasable: secondStock > 0,
          availabilityMessage: secondStock ? `Còn ${secondStock} sản phẩm có thể mua.` : 'Tạm hết hàng.' }
    ] };
}

async function open(options = {}) {
    const context = await browser.newContext({ javaScriptEnabled: options.javaScriptEnabled !== false,
        viewport: options.viewport || { width: 1280, height: 900 } });
    const page = await context.newPage();
    const state = { snapshot: snapshot(), gets: 0, posts: 0, mode: 'ok' };
    if (options.ignoreAbort) {
        await page.addInitScript(() => {
            const nativeFetch = window.fetch;
            window.fetch = (url, options) => nativeFetch(url, String(url).includes('/availability')
                ? { ...options, signal: undefined } : options);
        });
    }
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const staticRoot = path.resolve('src/main/resources/static');
    await page.route('**/*', async route => {
        const request = route.request();
        const url = new URL(request.url());
        if (url.hostname === 'cdn.tailwindcss.com') return route.fulfill({ contentType: 'text/javascript', body: 'window.tailwind = {};' });
        if (url.hostname !== 'stock.test') return route.abort();
        if (url.pathname.includes('/availability')) {
            state.gets++;
            if (options.onGet) return options.onGet(route, state);
            if (state.mode === 'offline') return route.abort();
            if (state.mode === 'removed') return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
            return route.fulfill({ contentType: 'application/json', body: JSON.stringify(state.snapshot) });
        }
        if (url.pathname === '/api/cart/items' || url.pathname === '/cart/add') {
            state.posts++;
            state.postBody = request.postData();
            if (options.onPost) return options.onPost(route, state);
            return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ totalItems: 8, message: 'Đã thêm sản phẩm vào giỏ hàng.' }) });
        }
        if (url.pathname.startsWith('/product-detail/')) {
            state.navigations = (state.navigations || 0) + 1;
            return route.fulfill({ contentType: 'text/html; charset=utf-8', body: fs.readFileSync('target/stock-ux/product-detail.html', 'utf8') });
        }
        const file = path.resolve(staticRoot, '.' + url.pathname);
        if (file.startsWith(staticRoot + path.sep) && fs.existsSync(file) && fs.statSync(file).isFile()) {
            const contentType = file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'image/png';
            return route.fulfill({ contentType, body: fs.readFileSync(file) });
        }
        return route.fulfill({ status: 404, body: '' });
    });
    await page.goto('http://stock.test/product-detail/11');
    if (options.javaScriptEnabled !== false) await page.waitForFunction(() => window.document.querySelector('[data-variant-id="12"] [data-variant-label]').textContent === '');
    return { page, state, errors, close: () => context.close() };
}
async function refresh(page) { await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange'))); }
async function stock(page, count) {
    await page.waitForFunction(count => document.querySelector('[data-stock-status]').textContent.includes(`Còn ${count} sản phẩm`), count);
}

test('A reserves four: B keeps quantity three, sees one, deliberately decreases to one', async () => {
    const { page, state, close, errors } = await open();
    try {
        await page.locator('#quantity').fill('3');
        state.snapshot = snapshot(1); await refresh(page); await stock(page, 1);
        assert.equal(await page.locator('#quantity').inputValue(), '3');
        assert.equal(await page.locator('[data-add-button]').isDisabled(), true);
        assert.match(await page.locator('[data-stock-warning]').textContent(), /Bạn đang chọn 3/);
        await page.locator('[data-stock-step="-1"]').click();
        assert.equal(await page.locator('#quantity').inputValue(), '1');
        assert.equal(await page.locator('[data-add-button]').isEnabled(), true);
        assert.equal(state.navigations, 1); assert.deepEqual(errors, []);
    } finally { await close(); }
});

test('sold out and released stock preserve selected variant and entered quantity', async () => {
    const { page, state, close } = await open();
    try {
        await page.locator('#quantity').fill('2');
        state.snapshot = snapshot(0); await refresh(page);
        await page.waitForFunction(() => document.querySelector('[data-stock-status]').textContent.includes('Tạm hết hàng'));
        assert.equal(await page.locator('[name="variantId"]').inputValue(), '11');
        assert.equal(await page.locator('#quantity').inputValue(), '2');
        assert.equal(await page.locator('[data-add-button]').isDisabled(), true);
        state.snapshot = snapshot(2); await refresh(page); await stock(page, 2);
        assert.equal(await page.locator('[data-add-button]').isEnabled(), true);
    } finally { await close(); }
});

test('switching variants updates price image URL and posted ID without navigation, keeps per-variant quantity', async () => {
    const { page, state, close } = await open();
    try {
        await page.locator('#quantity').fill('3');
        await page.locator('[data-variant-id="12"]').click();
        assert.equal(await page.locator('[name="variantId"]').inputValue(), '12');
        assert.match(await page.locator('[data-variant-price]').textContent(), /50/);
        assert.match(await page.locator('[data-product-image]').getAttribute('src'), /fruit-2/);
        assert.match(page.url(), /\/12$/);
        await page.locator('#quantity').fill('2');
        await page.locator('[data-variant-id="11"]').click();
        assert.equal(await page.locator('#quantity').inputValue(), '3');
        await page.goBack();
        assert.equal(await page.locator('#quantity').inputValue(), '2');
        assert.equal(state.navigations, 1);
    } finally { await close(); }
});

test('old response cannot overwrite a newer variant snapshot even when cancellation is ignored', async () => {
    let delayed;
    const { page, state, close } = await open({ ignoreAbort: true, onGet: async (route, state) => {
        if (state.gets === 2) { delayed = route; return; }
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify(state.snapshot) });
    } });
    try {
        await refresh(page); await page.waitForTimeout(50);
        state.snapshot = snapshot(1, 2);
        await page.locator('[data-variant-id="12"]').click(); await stock(page, 2);
        await delayed.fulfill({ contentType: 'application/json', body: JSON.stringify(snapshot(99, 99)) });
        await page.waitForTimeout(100);
        assert.equal(await page.locator('[name="variantId"]').inputValue(), '12');
        assert.match(await page.locator('[data-stock-status]').textContent(), /Còn 2 sản phẩm/);
    } finally { await close(); }
});

test('network failure leaves page usable and successful refresh clears the notice', async () => {
    const { page, state, close } = await open();
    try {
        state.mode = 'offline'; await refresh(page);
        await page.locator('[data-stock-network]').waitFor({ state: 'visible' });
        assert.equal(await page.locator('[data-add-button]').isEnabled(), true);
        await page.locator('#quantity').fill('2');
        state.mode = 'ok'; state.snapshot = snapshot(3); await refresh(page); await stock(page, 3);
        assert.equal(await page.locator('[data-stock-network]').isHidden(), true);
        assert.equal(await page.locator('#quantity').inputValue(), '2');
    } finally { await close(); }
});

test('hidden tab stops polling and foreground refreshes immediately', async () => {
    const { page, state, close } = await open();
    try {
        await page.clock.install();
        await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: true }); document.dispatchEvent(new Event('visibilitychange')); });
        const before = state.gets; await page.clock.runFor(16000);
        assert.equal(state.gets, before);
        state.snapshot = snapshot(1);
        await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: false }); document.dispatchEvent(new Event('visibilitychange')); });
        await stock(page, 1); assert.equal(state.gets, before + 1);
    } finally { await close(); }
});

test('server rejects stale add with structured 409; quantity and page are retained', async () => {
    const { page, state, close } = await open({ onPost: route => route.fulfill({ status: 409,
        contentType: 'application/json', body: JSON.stringify({ code: 'INSUFFICIENT_STOCK', availableStock: 1, message: 'Hộp 1 kg: chỉ còn 1 sản phẩm.' }) }) });
    try {
        await page.locator('#quantity').fill('3');
        state.snapshot = snapshot(1);
        await page.locator('[data-add-button]').click();
        await page.waitForFunction(() => document.querySelector('[data-cart-feedback]').textContent.includes('chỉ còn 1'));
        await stock(page, 1);
        assert.equal(await page.locator('#quantity').inputValue(), '3');
        assert.equal(state.posts, 1); assert.equal(state.navigations, 1);
        assert.match(state.postBody, /variantId=11/);
    } finally { await close(); }
});

test('double submit sends one POST and successful add updates actual badge', async () => {
    let pending;
    const { page, state, close } = await open({ onPost: route => { pending = route; } });
    try {
        await page.evaluate(() => { const form = document.querySelector('[data-detail-cart-form]'); form.requestSubmit(); form.requestSubmit(); });
        await page.waitForTimeout(100);
        assert.equal(state.posts, 1);
        assert.equal(await page.locator('#quantity').isDisabled(), true);
        await pending.fulfill({ contentType: 'application/json', body: JSON.stringify({ totalItems: 8, message: 'Đã thêm sản phẩm vào giỏ hàng.' }) });
        await page.waitForFunction(() => document.querySelector('[data-cart-badge]').textContent === '8');
        assert.equal(await page.locator('#quantity').isEnabled(), true);
    } finally { await close(); }
});

test('failed POST is never automatically replayed and reports an uncertain result', async () => {
    const { page, state, close } = await open({ onPost: route => route.abort() });
    try {
        await page.locator('[data-add-button]').click();
        await page.waitForFunction(() => document.querySelector('[data-cart-feedback]').textContent.includes('kiểm tra giỏ'));
        await refresh(page); await page.waitForTimeout(100);
        assert.equal(state.posts, 1);
        assert.equal(await page.locator('#quantity').isEnabled(), true);
    } finally { await close(); }
});

test('product removed blocks purchase without changing selection', async () => {
    const { page, state, close } = await open();
    try {
        state.mode = 'removed'; await refresh(page);
        await page.waitForFunction(() => document.querySelector('[data-stock-status]').textContent.includes('không còn được bán'));
        assert.equal(await page.locator('[data-add-button]').isDisabled(), true);
        assert.equal(await page.locator('[name="variantId"]').inputValue(), '11');
    } finally { await close(); }
});

test('price changes are announced and API names are rendered as text', async () => {
    const { page, state, close } = await open();
    try {
        state.snapshot.variants[0].price = 31000;
        state.snapshot.variants[0].variantName = '<img onerror="alert(1)">';
        await refresh(page);
        await page.waitForFunction(() => document.querySelector('[data-cart-feedback]').textContent.includes('Giá phân loại vừa thay đổi'));
        assert.equal(await page.locator('[data-variant-id="11"] img').count(), 0);
        assert.match(await page.locator('[data-variant-price]').textContent(), /31/);
    } finally { await close(); }
});

test('no JavaScript retains native add form and variant navigation', async () => {
    const { page, state, close } = await open({ javaScriptEnabled: false });
    try {
        assert.equal(await page.locator('[data-variant-id="12"]').getAttribute('href'), '/product-detail/12');
        await page.locator('[data-add-button]').click();
        await page.waitForURL('**/cart/add');
        assert.equal(state.posts, 1); assert.match(state.postBody, /variantId=11/);
    } finally { await close(); }
});

test('mobile stock controls remain inside viewport', async () => {
    const { page, close } = await open({ viewport: { width: 390, height: 844 } });
    try {
        const bounds = await page.locator('[data-detail-cart-form]').boundingBox();
        assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= 391);
        await page.screenshot({ path: 'target/stock-ux/product-detail-mobile.png', fullPage: true });
    } finally { await close(); }
});

test('409 stock is applied immediately even if the following refresh loses network', async () => {
    const { page, state, close } = await open({ onPost: route => route.fulfill({ status: 409,
        contentType: 'application/json', body: JSON.stringify({ code: 'INSUFFICIENT_STOCK', availableStock: 1, message: 'Chỉ còn 1 sản phẩm.' }) }) });
    try {
        await page.locator('#quantity').fill('3');
        state.mode = 'offline';
        await page.locator('[data-add-button]').click(); await stock(page, 1);
        assert.equal(await page.locator('#quantity').inputValue(), '3');
        assert.equal(await page.locator('[data-add-button]').isDisabled(), true);
        assert.equal(state.posts, 1);
    } finally { await close(); }
});

test('visible tab polls after five seconds and failures use backoff', async () => {
    const { page, state, close } = await open();
    try {
        await page.clock.install();
        state.snapshot = snapshot(1);
        await page.clock.runFor(5100); await stock(page, 1);
        state.mode = 'offline'; await refresh(page);
        await page.locator('[data-stock-network]').waitFor({ state: 'visible' });
        const afterFailure = state.gets;
        await page.clock.runFor(9000); assert.equal(state.gets, afterFailure);
        await page.clock.runFor(1200);
        await page.waitForFunction(() => !document.querySelector('[data-stock-network]').hidden);
        assert.equal(state.gets, afterFailure + 1);
    } finally { await close(); }
});
