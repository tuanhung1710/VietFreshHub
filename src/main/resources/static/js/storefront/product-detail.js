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
refreshCartCount().catch(error => notice(error.message));
