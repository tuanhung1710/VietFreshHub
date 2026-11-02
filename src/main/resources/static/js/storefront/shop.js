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
    state.tab = query.get('tab') === 'all' ? 'all' : 'home';
}

function syncControls() {

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
    try {
        const page = await request(shopProductsPath(shopId, state));
        if (current !== revision) return;
        state.page = page.page;
        $('results').innerHTML = page.items.map(product => shopProductCard(product)).join('') || empty;
        $('matchCount').textContent = `${page.total} sản phẩm`;
        saveLocation(true);
    } catch (error) {
        if (current !== revision) return;
        $('matchCount').textContent = 'Chưa tải được sản phẩm';
        notice(error.message);
    } finally {
        if (current === revision) $('results').removeAttribute('aria-busy');
    }
}

async function loadHome() {
    const sectionSize = options.pageSizes.find(size => size >= 5) || options.defaultPageSize;
    try {
        const suggestions = await request(shopProductsPath(shopId, { page: 1, pageSize: sectionSize, sort: 'popular' }));
        $('productCount').textContent = `${suggestions.total} sản phẩm`;
        $('recommended').innerHTML = suggestions.items.slice(0, 5).map(product => shopProductCard(product)).join('') || empty;
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
    if (data.shopAll !== undefined) return catalog();
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
    readLocation();
    document.addEventListener('click', event => {
        handle(() => clicked(event));
    });
    window.addEventListener('popstate', () => { readLocation(); handle(() => state.tab === 'home' ? home(false) : catalog(false)); });
    const initialCatalog = state.tab === 'home' ? home(false) : catalog(false);
    await Promise.all([loadHome(), initialCatalog, refreshCartCount()]);
}

handle(start);
