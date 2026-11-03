export const $ = id => document.getElementById(id);
export const html = value => String(value ?? '').replace(/[&<>"']/g, character =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character]);
export const money = value => Number(value).toLocaleString('vi-VN') + ' VND';
export const rating = value => String(Math.round(Number(value || 0) * 10) / 10);
export const placeName = place => place || 'Chưa cập nhật';
export const discountLabel = percent => Number(percent) === 0 ? 'Giảm dưới 1%' : `-${Number(percent)}%`;
const fallbackImage = '/images/product/product-placeholder.svg';

export function productImage(product) {
    const source = product.imageUrl?.trim() || fallbackImage;
    return `<img data-product-image src="${html(source)}" alt="${html(product.name)}" loading="lazy">`;
}

export function fallbackProductImage(event) {
    const image = event.target;
    if (image.tagName === 'IMG' && image.hasAttribute('data-product-image') && image.getAttribute('src') !== fallbackImage) {
        image.src = fallbackImage;
    }
}

export function discountBadge(product) {
    if (product.discountPercent == null) return '';
    return `<span class="discount-badge discount-badge--image">${html(discountLabel(product.discountPercent))}</span>`;
}

export function card(product) {
    return `<button class="p-card" data-product="${Number(product.id)}"><div class="p-image">${productImage(product)}${discountBadge(product)}</div><div class="p-body"><div class="p-name">${html(product.name)}</div><div class="p-shop">${html(product.shopName)} · ${html(placeName(product.place))}</div><div class="p-price">${money(product.price)}</div><div class="p-sub"><span>★ ${rating(product.rating)}/5</span><span>Đã bán ${Number(product.sales)}</span></div></div></button>`;
}

export function shopCard(shop) {
    return `<button class="shop" data-shopview="${Number(shop.id)}"><h3>${html(shop.name)}</h3><p>${html(shop.description)}</p><p>Gửi từ: ${html(placeName(shop.place))}</p><p><u>Xem gian hàng</u></p></button>`;
}

export function pager(page, sizes, home = false) {
    const previous = home ? 'data-homeprev' : 'data-prev';
    const next = home ? 'data-homenext' : 'data-next';
    const select = home ? 'homePageSize' : 'pageSize';
    const apply = home ? 'applyHomePageSize' : 'applyPageSize';
    const arrow = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 5 7 7-7 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
    return `<div class="customer-pager" role="navigation" aria-label="Phân trang sản phẩm">
        <button class="customer-page-button customer-page-previous" ${previous} aria-label="Trang trước" title="Trang trước" ${page.page <= 1 ? 'disabled' : ''}>${arrow}</button>
        <span class="customer-page-summary" aria-live="polite"><span>Trang ${page.page} / ${Math.max(1, page.pages)}</span><small>${page.total} sản phẩm</small></span>
        <button class="customer-page-button customer-page-next" ${next} aria-label="Trang sau" title="Trang sau" ${page.page >= page.pages ? 'disabled' : ''}>${arrow}</button>
    </div><div class="customer-page-size">
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
