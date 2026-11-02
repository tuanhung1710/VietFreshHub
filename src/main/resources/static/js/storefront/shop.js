import { $, notice } from './render.js';
import { refreshCartCount } from './product.js';

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

refreshCartCount().catch(error => notice(error.message));
