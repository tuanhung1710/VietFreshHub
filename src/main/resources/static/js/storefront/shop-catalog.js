import { parameters } from './api.js';
import { html, money, rating } from './render.js';

export function shopProductsPath(shopId, state) {
    const query = parameters(state);
    // The page's shop is fixed even when a visitor edits its search/filter URL.
    query.delete('shopIds');
    query.set('shopId', Number(shopId));
    return '/products?' + query;
}

export function shopProductCard(product) {
    return `<button class="product" data-product="${Number(product.id)}">
        <div class="picture">${html(product.emoji)}</div>
        <div class="productbody"><div class="productname">${html(product.name)}</div>
        <div class="muted">${html(product.category)}</div><div class="price">${money(product.price)}</div>
        <div class="meta"><span>★ ${rating(product.rating)}/5</span><span>Đã bán ${Number(product.sales)}</span></div></div>
    </button>`;
}
