import { request, parameters } from './api.js';
import { $, html, notice } from './render.js';
import { openProduct, openInformation, closeModal, addProduct, refreshCartCount } from './product.js';
import { shopProductsPath, shopProductCard } from './shop-catalog.js';

const shopId = Number(document.body.dataset.shopId);
const state = { tab: 'home', categoryId: null, q: '', sort: 'popular', page: 1, pageSize: null };
let options;
let revision = 0;

const handle = action => Promise.resolve().then(action).catch(error => notice(error.message));
const empty = '<p class="empty-products">Shop chưa có sản phẩm phù hợp đang được bán.</p>';

function saveLocation(replace = false) {
    const query = state.tab === 'home' ? '' : '?' + parameters(state) + '&tab=all';
    history[replace ? 'replaceState' : 'pushState']({}, '', '/shops/' + shopId + query);
}

function readLocation() {
    const query = new URLSearchParams(location.search);
    state.q = (query.get('keyword') || '').slice(0, 100);
    state.categoryId = Number(query.get('categoryId')) || null;
    state.sort = ['popular', 'latest', 'sales', 'asc', 'desc'].includes(query.get('sort')) ? query.get('sort') : 'popular';
    state.page = Math.min(10000, Math.max(1, Math.floor(Number(query.get('page')) || 1)));
    state.pageSize = options.pageSizes.includes(Number(query.get('pageSize'))) ? Number(query.get('pageSize')) : options.defaultPageSize;
    state.tab = query.get('tab') === 'all' || ['categoryId', 'keyword', 'sort', 'page', 'pageSize'].some(key => query.has(key)) ? 'all' : 'home';
}

function syncControls() {
    $('q').value = state.q;
    $('size').value = state.pageSize;
    $('priceSort').value = ['asc', 'desc'].includes(state.sort) ? state.sort : 'popular';
    document.querySelectorAll('[data-sort]').forEach(button => button.classList.toggle('active', state.sort === button.dataset.sort));
    document.querySelectorAll('[data-shop-category]').forEach(button => {
        const selected = state.tab === 'all' && (Number(button.dataset.shopCategory) || null) === state.categoryId;
        button.classList.toggle('active', selected);
        if (selected) button.setAttribute('aria-current', 'page');
        else button.removeAttribute('aria-current');
    });
}

function home(push = true) {
    revision++;
    closeModal();
    Object.assign(state, { tab: 'home', categoryId: null, q: '', sort: 'popular', page: 1 });
    $('shopHome').classList.remove('hidden');
    $('catalog').classList.add('hidden');
    $('notice').classList.add('hide');
    syncControls();
    if (push) saveLocation();
}

async function catalog(push = true) {
    const current = ++revision;
    closeModal();
    state.tab = 'all';
    $('shopHome').classList.add('hidden');
    $('catalog').classList.remove('hidden');
    $('notice').classList.add('hide');
    syncControls();
    if (push) saveLocation();
    $('results').innerHTML = '';
    $('results').setAttribute('aria-busy', 'true');
    $('prev').disabled = $('next').disabled = true;
    try {
        const page = await request(shopProductsPath(shopId, state));
        if (current !== revision) return;
        state.page = page.page;
        $('results').innerHTML = page.items.map(product => shopProductCard(product)).join('') || empty;
        $('matchCount').textContent = `${page.total} sản phẩm`;
        $('pageLabel').textContent = `Trang ${page.page} / ${page.pages}`;
        $('totalLabel').textContent = `${page.total} sản phẩm`;
        $('prev').disabled = page.page <= 1;
        $('next').disabled = page.page >= page.pages;
        saveLocation(true);
    } catch (error) {
        if (current !== revision) return;
        $('matchCount').textContent = 'Chưa tải được sản phẩm';
        $('totalLabel').textContent = '';
        notice(error.message);
    } finally {
        if (current === revision) $('results').removeAttribute('aria-busy');
    }
}

async function loadHome() {
    const sectionSize = options.pageSizes.find(size => size >= 5) || options.defaultPageSize;
    try {
        const [suggestions, best] = await Promise.all([
            request(shopProductsPath(shopId, { page: 1, pageSize: sectionSize, sort: 'popular' })),
            request(shopProductsPath(shopId, { page: 1, pageSize: sectionSize, sort: 'sales' }))
        ]);
        $('productCount').textContent = `${suggestions.total} sản phẩm`;
        $('recommended').innerHTML = suggestions.items.slice(0, 5).map(product => shopProductCard(product)).join('') || empty;
        $('topProducts').innerHTML = best.items.slice(0, 5).map((product, index) => shopProductCard(product, index + 1)).join('') || empty;
    } catch (error) {
        $('productCount').textContent = 'Chưa tải được';
        $('recommended').innerHTML = $('topProducts').innerHTML = '<p class="empty-products">Chưa tải được sản phẩm. Vui lòng tải lại trang.</p>';
        throw error;
    } finally {
        $('recommended').removeAttribute('aria-busy');
        $('topProducts').removeAttribute('aria-busy');
    }
}

function clicked(event) {
    const button = event.target.closest('button');
    if (!button || button.disabled) return;
    const data = button.dataset;
    if (data.close !== undefined) return closeModal();
    if (data.add !== undefined) return addProduct(button);
    if (data.product !== undefined) return openProduct(Number(data.product));
    if (data.shopview !== undefined) return location.assign('/shops/' + Number(data.shopview));
    if (data.footerInfo !== undefined) return openInformation(data.footerInfo);
    if (data.shopAll !== undefined || data.shopCategory !== undefined) {
        state.categoryId = Number(data.shopCategory) || null;
        state.page = 1;
        return catalog();
    }
    if (data.sort !== undefined) { state.sort = data.sort; state.page = 1; return catalog(); }
    if (button.id === 'prev' || button.id === 'next') { state.page += button.id === 'prev' ? -1 : 1; return catalog(); }
    if (button.id === 'apply') { state.pageSize = Number($('size').value); state.page = 1; return catalog(); }
}

async function start() {
    document.querySelectorAll('[data-shop-background]').forEach(image => {
        image.addEventListener('error', () => image.hidden = true);
        if (image.complete && !image.naturalWidth) image.hidden = true;
    });
    const logo = $('shopLogo');
    if (logo) {
        const fallback = () => { logo.hidden = true; $('shopInitial').hidden = false; };
        logo.addEventListener('error', fallback);
        if (logo.complete && !logo.naturalWidth) fallback();
    }
    options = await request('/options');
    $('topnav').innerHTML = '<button data-shop-category="">Tất cả sản phẩm</button>' + options.categories.map(category =>
        `<button data-shop-category="${Number(category.id)}">${html(category.name)}</button>`).join('');
    $('footerCategories').innerHTML = options.categories.slice(0, 3).map(category =>
        `<p><button class="textlink" data-shop-category="${Number(category.id)}">${html(category.name)}</button></p>`).join('');
    $('size').innerHTML = options.pageSizes.map(size => `<option value="${size}">${size}</option>`).join('');
    readLocation();
    document.addEventListener('click', event => {
        const authLink = event.target.closest('a[data-auth-link]');
        if (authLink) {
            const url = new URL(authLink.href, location.origin);
            url.searchParams.set('returnTo', location.pathname + location.search);
            authLink.href = url.pathname + url.search;
            return;
        }
        handle(() => clicked(event));
    });
    $('shopSearch').addEventListener('submit', event => {
        event.preventDefault();
        state.q = $('q').value.trim();
        state.page = 1;
        handle(() => catalog());
    });
    $('priceSort').addEventListener('change', () => { state.sort = $('priceSort').value; state.page = 1; handle(() => catalog()); });
    window.addEventListener('popstate', () => { readLocation(); handle(() => state.tab === 'home' ? home(false) : catalog(false)); });
    const initialCatalog = state.tab === 'home' ? home(false) : catalog(false);
    await Promise.all([loadHome(), initialCatalog, refreshCartCount()]);
}

handle(start);
