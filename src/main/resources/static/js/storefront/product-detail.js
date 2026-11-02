import { $, money, notice } from './render.js';
import { refreshCartCount } from './product.js';

const form = $('productPurchase');
const photo = $('mainPhoto');
const fallbackPhoto = '/images/product/product-placeholder.svg';

function update(changeVariant = false) {
    const selected = form.querySelector('input[name="variantId"]:checked');
    const stock = Number(selected.dataset.stock);
    $('currentPrice').textContent = money(selected.dataset.price);
    $('stock').textContent = `${stock} phần có sẵn`;
    $('variantNote').textContent = `Đang chọn ${selected.dataset.name} · Tạm tính: ${money(selected.dataset.price)}`;
    form.querySelectorAll('input[name="variantId"]').forEach(input => input.closest('label').classList.toggle('selected', input.checked));
    if (changeVariant && selected.dataset.image) photo.src = selected.dataset.image;
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
        history.replaceState({}, '', '/product-detail/' + Number(event.target.value));
        update(true);
    }
});
document.addEventListener('click', event => {
    const thumb = event.target.closest('[data-photo]');
    if (thumb) {
        photo.src = thumb.dataset.photo;
        document.querySelectorAll('[data-photo]').forEach(button => button.classList.toggle('active', button === thumb));
    }
});
update();
refreshCartCount().catch(error => notice(error.message));
