import { $, money, discountLabel } from './ui.js';
import { quantityWithinStock } from './purchase.js';

const form = $('productPurchase');
const quantity = $('quantity');
const photo = $('mainPhoto');
const fallbackPhoto = '/images/product/product-placeholder.svg';

function loginUrl() {
    const selected = form.querySelector('input[name="variantId"]:checked');
    const target = '/product-detail/' + Number(selected.value) + '?quantity=' + quantity.value;
    return '/login?returnTo=' + encodeURIComponent(target);
}

function update(changeVariant = false) {
    const selected = form.querySelector('input[name="variantId"]:checked');
    const stock = Number(selected.dataset.stock);
    quantity.value = quantityWithinStock(quantity.value, stock);
    quantity.max = stock;
    $('currentPrice').textContent = money(selected.dataset.price);
    const percent = selected.dataset.discountPercent;
    const discounted = percent != null && percent !== '';
    $('compareAtPrice').hidden = !discounted;
    $('discountPercent').hidden = !discounted;
    $('compareAtPrice').textContent = discounted ? money(selected.dataset.compareAtPrice) : '';
    $('discountPercent').textContent = discounted ? discountLabel(percent) : '';
    $('stock').textContent = `${stock} phần có sẵn`;
    $('variantNote').textContent = `Đang chọn ${selected.dataset.name} · Tạm tính: ${money(Number(selected.dataset.price) * Number(quantity.value))}`;
    $('minus').disabled = Number(quantity.value) <= 1;
    $('plus').disabled = Number(quantity.value) >= stock;
    form.querySelectorAll('input[name="variantId"]').forEach(input => input.closest('label').classList.toggle('selected', input.checked));
    if (changeVariant && selected.dataset.image) photo.src = selected.dataset.image;
    document.querySelectorAll('[data-customer-login]').forEach(link => link.href = loginUrl());
}

document.querySelectorAll('img').forEach(image => {
    const fallback = () => {
        if (image.hasAttribute('data-shop-background')) image.hidden = true;
        else if (image.id === 'storeLogo') { image.hidden = true; $('storeInitial').hidden = false; }
        else if (!image.src.endsWith(fallbackPhoto)) image.src = fallbackPhoto;
    };
    image.addEventListener('error', fallback);
    if (image.complete && !image.naturalWidth) fallback();
});
form.addEventListener('change', event => {
    if (event.target.name === 'variantId') {
        quantity.value = 1;
        history.replaceState({}, '', '/product-detail/' + Number(event.target.value));
        update(true);
    } else if (event.target === quantity) update();
});
$('minus').addEventListener('click', () => { quantity.value = Number(quantity.value) - 1; update(); });
$('plus').addEventListener('click', () => { quantity.value = Number(quantity.value) + 1; update(); });
form.addEventListener('submit', event => {
    if (document.body.dataset.customer !== 'true') { event.preventDefault(); location.assign(loginUrl()); }
});
document.addEventListener('click', event => {
    const thumb = event.target.closest('[data-photo]');
    if (thumb) {
        photo.src = thumb.dataset.photo;
        document.querySelectorAll('[data-photo]').forEach(button => button.classList.toggle('active', button === thumb));
    }
    const link = event.target.closest('a[data-auth-link]');
    if (link) {
        const url = new URL(link.href, location.origin);
        url.searchParams.set('returnTo', location.pathname + location.search);
        link.href = url.pathname + url.search;
    }
});
const initialQuantity = new URLSearchParams(location.search).get('quantity');
if (initialQuantity != null) quantity.value = initialQuantity;
update();
