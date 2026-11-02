import { $, notice } from './render.js';
import { refreshCartCount } from './product.js';
const photo = $('mainPhoto');
const fallbackPhoto = '/images/product/product-placeholder.svg';
document.querySelectorAll('img').forEach(image => {
    const fallback = () => {
        if (image.hasAttribute('data-shop-background')) image.hidden = true;
        else if (image.id === 'storeLogo') { image.hidden = true; $('storeInitial').hidden = false; }
        else if (!image.src.endsWith(fallbackPhoto)) image.src = fallbackPhoto;
    };
    image.addEventListener('error', fallback);
    if (image.complete && !image.naturalWidth) fallback();
});
document.addEventListener('click', event => {
    const thumb = event.target.closest('[data-photo]');
    if (thumb) {
        photo.src = thumb.dataset.photo;
        document.querySelectorAll('[data-photo]').forEach(button => button.classList.toggle('active', button === thumb));
    }
});
refreshCartCount().catch(error => notice(error.message));
