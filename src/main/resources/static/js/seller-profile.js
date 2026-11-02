(() => {
    'use strict';

    document.querySelectorAll('[data-image-fallback]').forEach(image => {
        const showFallback = () => {
            image.hidden = true;
            if (image.nextElementSibling) image.nextElementSibling.hidden = false;
        };
        image.addEventListener('error', showFallback);
        if (image.getAttribute('src') && image.complete && image.naturalWidth === 0) showFallback();
    });

})();
