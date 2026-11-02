import { request, parameters } from './api.js';
import { $, html, card, shopCard, pager, notice, show } from './render.js';
import { openProduct, openInformation, closeModal, addProduct, refreshCartCount } from './product.js';

let options;
let view = 'home';
let revision = 0;
let extended = false;
const homeState = { page: 1, pageSize: null };
const state = { categoryId: null, q: '', places: [], shopIds: [], min: '', max: '', minRating: 0, sort: 'popular', page: 1, pageSize: null };
const category = id => options.categories.find(category => category.id === Number(id));
const shop = id => options.shops.find(shop => shop.id === Number(id));

function saveLocation(replace = false, shopId) {
    let url = '/home';
    if (view === 'catalog') url += '?view=catalog&' + parameters(state);
    if (view === 'home') url += `?homePage=${homeState.page}&homePageSize=${homeState.pageSize}`;
    if (view === 'shopspage') url = '/shops';
    if (view === 'shopdetail') url = `/shops/${shopId}`;
    history[replace ? 'replaceState' : 'pushState']({}, '', url);
}

function activate(nextView) {
    revision++;
    closeModal();
    if (view !== nextView) show(nextView);
    else $('notice').classList.add('hide');
    view = nextView;
    return revision;
}

function renderLocations() {
    const places = [...new Set(options.shops.map(shop => shop.place).filter(Boolean))];
    $('locations').innerHTML = places.filter((_, index) => extended || index < 4).map(place =>
        `<label><input type="checkbox" data-place="${html(place)}" ${state.places.includes(place) ? 'checked' : ''}>${html(place)}</label>`).join('');
    $('moreLocations').textContent = extended ? 'Thu gọn' : 'Xem thêm';
}

function syncFilters() {
    document.querySelectorAll('[data-shopfilter]').forEach(input => input.checked = state.shopIds.includes(Number(input.dataset.shopfilter)));
    document.querySelectorAll('input[name="rating"]').forEach(input => input.checked = Number(input.value) === state.minRating);
    $('min').value = state.min;
    $('max').value = state.max;
    $('search').value = state.q;
    $('sort').value = ['asc', 'desc'].includes(state.sort) ? state.sort : 'none';
    document.querySelectorAll('[data-sort]').forEach(button => button.classList.toggle('active', button.dataset.sort === state.sort));
    document.querySelectorAll('.filtercategory,#headcats [data-cat]').forEach(button =>
        button.classList.toggle('active', Number(button.dataset.cat) === Number(state.categoryId)));
    renderLocations();
}

function renderChips() {
    const chips = [state.q && 'Tìm: ' + state.q, category(state.categoryId)?.name, ...state.places,
        ...state.shopIds.map(id => shop(id)?.name), state.minRating && 'Từ ' + state.minRating + ' sao',
        (Number(state.min) > 0 || state.max !== '') && 'Đã lọc giá'].filter(Boolean);
    $('chips').innerHTML = chips.map(chip => `<span class="chip">${html(chip)}</span>`).join('');
}

function renderNavigation() {
    $('headcats').innerHTML = '<button data-all>Tất cả sản phẩm</button>' + options.categories.map(category =>
        `<button data-cat="${category.id}">${html(category.name)}</button>`).join('');
    $('footerCategories').innerHTML = '<h3>Danh mục trái cây</h3>' + options.categories.slice(0, 4).map(category =>
        `<button data-cat="${category.id}">${html(category.name)}</button>`).join('') + '<button data-all>Xem tất cả</button>';
}

function renderOptions() {
    renderNavigation();
    $('homecategories').innerHTML = options.categories.map(category =>
        `<button class="cat" data-cat="${category.id}"><span class="catIcon">${html(category.emoji)}</span>${html(category.name)}</button>`).join('');
    $('filterCategories').innerHTML = '<button class="filtercategory" data-cat="">Tất cả danh mục</button>' + options.categories.map(category =>
        `<button class="filtercategory" data-cat="${category.id}">${html(category.name)}</button>`).join('');
    $('filterShops').innerHTML = options.shops.map(shop =>
        `<label><input type="checkbox" data-shopfilter="${shop.id}">${html(shop.name)}</label>`).join('');
    $('ratings').innerHTML = [5, 4, 3, 2, 1].map(stars =>
        `<label><input name="rating" type="radio" value="${stars}">${'★'.repeat(stars)}${'☆'.repeat(5 - stars)} (${stars} sao trở lên)</label>`).join('');
    $('homeshops').innerHTML = options.shops.slice(0, 4).map(shopCard).join('');
    $('shoplist').innerHTML = options.shops.map(shopCard).join('');
    syncFilters();
}

async function home(push = true) {
    const current = activate('home');
    if (push) saveLocation();
    const data = await request('/home?' + parameters(homeState));
    if (current !== revision) return;
    homeState.page = data.products.page;
    $('homeproducts').innerHTML = data.products.items.map(card).join('');
    $('topproducts').innerHTML = data.topProducts.map(card).join('');
    $('homePagination').innerHTML = pager(data.products, options.pageSizes, true);
    saveLocation(true);
}

async function catalog(push = true) {
    const current = activate('catalog');
    syncFilters();
    renderChips();
    const title = category(state.categoryId)?.name || 'Tất cả sản phẩm';
    $('catTitle').textContent = title;
    $('breadcrumb').textContent = title;
    if (push) saveLocation();
    $('results').setAttribute('aria-busy', 'true');
    try {
        const data = await request('/products?' + parameters(state));
        if (current !== revision) return;
        state.page = data.page;
        $('count').textContent = data.total;
        $('results').innerHTML = data.items.map(card).join('') || '<p>Không tìm thấy sản phẩm phù hợp.</p>';
        $('pagination').innerHTML = pager(data, options.pageSizes);
        saveLocation(true);
    } finally { if (current === revision) $('results').removeAttribute('aria-busy'); }
}

function shops(push = true) {
    activate('shopspage');
    if (push) saveLocation();
}

function openShop(id) {
    location.assign('/shops/' + Number(id));
}

function readLocation() {
    const params = new URLSearchParams(location.search);
    state.categoryId = Number(params.get('categoryId')) || null;
    state.q = params.get('keyword') || '';
    state.places = params.getAll('places');
    state.shopIds = params.getAll('shopIds').flatMap(value => value.split(',')).map(Number).filter(id => id > 0);
    if (params.get('shopId')) state.shopIds = [Number(params.get('shopId'))];
    state.min = params.get('minPrice') || '';
    state.max = params.get('maxPrice') || '';
    state.minRating = Number(params.get('minRating')) || 0;
    const aliases = { newest: 'latest', best_selling: 'sales', price_asc: 'asc', price_desc: 'desc' };
    state.sort = aliases[params.get('sort')] || params.get('sort') || 'popular';
    state.page = Math.max(1, Number(params.get('page')) || 1);
    state.pageSize = Number(params.get('pageSize')) || options.defaultPageSize;
    homeState.page = Math.max(1, Number(params.get('homePage')) || homeState.page);
    homeState.pageSize = Number(params.get('homePageSize')) || homeState.pageSize || options.defaultPageSize;
    return params;
}

async function route() {
    const params = readLocation();
    const shopMatch = location.pathname.match(/^\/shops\/(\d+)$/);
    if (shopMatch) return openShop(Number(shopMatch[1]), false);
    if (location.pathname === '/shops') return shops(false);
    if (params.get('view') === 'catalog' || ['categoryId', 'keyword', 'shopId', 'shopIds', 'minPrice', 'maxPrice', 'minRating', 'sort', 'pageSize', 'page'].some(key => params.has(key))) return catalog(false);
    return home(false);
}

async function clicked(event) {
    const button = event.target.closest('button');
    if (!button || button.disabled) return;
    const data = button.dataset;
    if (data.close !== undefined) return closeModal();
    if (data.add !== undefined) return addProduct(button);
    if (data.home !== undefined) return home();
    if (data.all !== undefined) { state.categoryId = null; state.page = 1; return catalog(); }
    if (data.best !== undefined) { state.categoryId = null; state.sort = 'sales'; state.page = 1; return catalog(); }
    if (data.cat !== undefined) { state.categoryId = Number(data.cat) || null; state.page = 1; return catalog(); }
    if (data.shops !== undefined) return shops();
    if (data.shopview !== undefined) return openShop(Number(data.shopview));
    if (data.shopfilterpage !== undefined) { state.categoryId = null; state.shopIds = [Number(data.shopfilterpage)]; state.page = 1; return catalog(); }
    if (data.product !== undefined) return openProduct(Number(data.product));
    if (data.homeprev !== undefined || data.homenext !== undefined) { homeState.page += data.homeprev !== undefined ? -1 : 1; return home(); }
    if (data.prev !== undefined || data.next !== undefined) { state.page += data.prev !== undefined ? -1 : 1; return catalog(); }
    if (data.sort !== undefined) { state.sort = data.sort; state.page = 1; return catalog(); }
    switch (button.id) {
        case 'applyHomePageSize': homeState.pageSize = Number($('homePageSize').value); homeState.page = 1; return home();
        case 'applyPageSize': state.pageSize = Number($('pageSize').value); state.page = 1; return catalog();
        case 'searchGo': state.q = $('search').value.trim(); state.categoryId = null; state.page = 1; return catalog();
        case 'applyPrice': state.min = $('min').value; state.max = $('max').value; state.page = 1; return catalog();
        case 'reset': Object.assign(state, { places: [], shopIds: [], min: '', max: '', minRating: 0, sort: 'popular', page: 1 }); return catalog();
        case 'moreLocations': extended = !extended; return renderLocations();
        case 'toggleFilters': $('sidebar').classList.toggle('open'); return;
    }
    if (data.footerInfo !== undefined) openInformation(data.footerInfo);
}

function changed(event) {
    const input = event.target;
    if (input.dataset.place !== undefined) state.places = [...document.querySelectorAll('[data-place]:checked')].map(input => input.dataset.place);
    else if (input.dataset.shopfilter !== undefined) state.shopIds = [...document.querySelectorAll('[data-shopfilter]:checked')].map(input => Number(input.dataset.shopfilter));
    else if (input.name === 'rating') state.minRating = Number(input.value);
    else if (input.id === 'sort') state.sort = input.value === 'none' ? 'popular' : input.value;
    else return;
    state.page = 1;
    return catalog();
}

const handle = action => Promise.resolve().then(action).catch(error => notice(error.message));

async function start() {
    options = await request('/options');
    if (!$('home')) {
        renderNavigation();
        document.addEventListener('click', event => {
            const button = event.target.closest('button');
            if (!button) return;
            if (button.dataset.close !== undefined) return closeModal();
            if (button.dataset.footerInfo !== undefined) return openInformation(button.dataset.footerInfo);
            if (button.dataset.home !== undefined) location.assign('/home');
            if (button.dataset.shops !== undefined) location.assign('/shops');
            if (button.dataset.all !== undefined) location.assign('/home?view=catalog');
            if (button.dataset.cat !== undefined) location.assign('/home?view=catalog&categoryId=' + Number(button.dataset.cat));
            if (button.id === 'searchGo') location.assign('/home?view=catalog&keyword=' + encodeURIComponent($('search').value));
        });
        $('search').addEventListener('keydown', event => { if (event.key === 'Enter') $('searchGo').click(); });
        await refreshCartCount();
        return;
    }
    homeState.pageSize = state.pageSize = options.defaultPageSize;
    renderOptions();
    document.addEventListener('click', event => handle(() => clicked(event)));
    document.addEventListener('change', event => handle(() => changed(event)));
    $('search').addEventListener('keydown', event => { if (event.key === 'Enter') { event.preventDefault(); $('searchGo').click(); } });
    window.addEventListener('popstate', () => handle(route));
    await route();
    if (document.body.dataset.productId) await openProduct(Number(document.body.dataset.productId), Number(document.body.dataset.variantId) || undefined);
    await refreshCartCount();
}

handle(start);
