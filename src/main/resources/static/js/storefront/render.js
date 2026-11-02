export const $ = id => document.getElementById(id);
export const html = value => String(value ?? '').replace(/[&<>"']/g, character =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character]);
export const money = value => Number(value).toLocaleString('vi-VN') + ' VND';
export const rating = value => String(Math.round(Number(value || 0) * 10) / 10);
export const placeName = place => place || 'Chưa cập nhật';

export function card(product) {
    return `<button class="p-card" data-product="${Number(product.id)}"><div class="p-image">${html(product.emoji)}</div><div class="p-body"><div class="p-name">${html(product.name)}</div><div class="p-shop">${html(product.shopName)} · ${html(placeName(product.place))}</div><div class="p-price">${money(product.price)}</div><div class="p-sub"><span>★ ${rating(product.rating)}/5</span><span>Đã bán ${Number(product.sales)}</span></div></div></button>`;
}

export function shopCard(shop) {
    return `<button class="shop" data-shopview="${Number(shop.id)}"><h3>${html(shop.name)}</h3><p>${html(shop.description)}</p><p>Gửi từ: ${html(placeName(shop.place))}</p><p><u>Xem gian hàng</u></p></button>`;
}

export function pager(page, sizes, home = false) {
    const previous = home ? 'data-homeprev' : 'data-prev';
    const next = home ? 'data-homenext' : 'data-next';
    const select = home ? 'homePageSize' : 'pageSize';
    const apply = home ? 'applyHomePageSize' : 'applyPageSize';
    return `<div class="pager-left">
        <button ${previous} aria-label="Trang trước" ${page.page <= 1 ? 'disabled' : ''}>‹</button>
        <div class="pager-info"><strong>Trang ${page.page} / ${page.pages}</strong><small>${page.total} sản phẩm</small></div>
        <button ${next} aria-label="Trang tiếp" ${page.page >= page.pages ? 'disabled' : ''}>›</button>
    </div><div class="pager-right">
        <label for="${select}">Sản phẩm mỗi trang</label>
        <select id="${select}" aria-label="${home ? 'Số sản phẩm mỗi trang ở Home' : 'Số sản phẩm mỗi trang'}">
            ${sizes.map(size => `<option value="${size}" ${size === page.pageSize ? 'selected' : ''}>${size}</option>`).join('')}
        </select><button id="${apply}">Áp dụng</button>
    </div>`;
}

export function notice(message) {
    $('notice').textContent = message;
    $('notice').classList.remove('hide');
    $('notice').setAttribute('role', 'status');
}

export function show(view) {
    for (const id of ['home', 'catalog', 'shopspage', 'shopdetail']) $(id).classList.toggle('hide', id !== view);
    $('headcats')?.classList.toggle('hide', view === 'catalog');
    $('notice').classList.add('hide');
    $('modal').replaceChildren();
    window.scrollTo(0, 0);
}
