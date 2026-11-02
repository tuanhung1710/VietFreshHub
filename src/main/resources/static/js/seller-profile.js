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

    const form = document.querySelector('[data-profile-form]');
    if (!form) return;
    const message = form.querySelector('[data-image-message]');
    const objectUrls = new Map();

    function preview(image, source) {
        if (source) image.src = source;
        else image.removeAttribute('src');
        image.hidden = !source;
        if (image.nextElementSibling) image.nextElementSibling.hidden = Boolean(source);
    }

    form.querySelectorAll('[data-preview-target]').forEach(input => {
        input.addEventListener('change', () => {
            const image = document.getElementById(input.dataset.previewTarget);
            const urlInput = form.querySelector(`[data-url-preview="${image.id}"]`);
            const previous = objectUrls.get(input.id);
            if (previous) URL.revokeObjectURL(previous);
            objectUrls.delete(input.id);
            message.hidden = true;
            input.setCustomValidity('');
            const file = input.files[0];
            if (!file) {
                preview(image, urlInput.value.trim());
                return;
            }
            if (file.size > 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type)) {
                const error = 'Vui lòng chọn ảnh JPG hoặc PNG có dung lượng tối đa 1 MB.';
                message.textContent = error;
                message.hidden = false;
                input.setCustomValidity(error);
                preview(image, urlInput.value.trim());
                input.reportValidity();
                return;
            }
            const source = URL.createObjectURL(file);
            objectUrls.set(input.id, source);
            preview(image, source);
        });
    });

    form.querySelectorAll('[data-url-preview]').forEach(input => {
        let previewTimer;
        const updatePreview = () => {
            clearTimeout(previewTimer);
            const fileInput = form.querySelector(`[data-preview-target="${input.dataset.urlPreview}"]`);
            if (!fileInput.files.length) preview(document.getElementById(input.dataset.urlPreview), input.value.trim());
        };
        input.addEventListener('change', updatePreview);
        input.addEventListener('input', () => {
            clearTimeout(previewTimer);
            if (!input.value.trim()) updatePreview();
            else previewTimer = setTimeout(updatePreview, 250);
        });
    });

    window.addEventListener('pagehide', () => objectUrls.forEach(source => URL.revokeObjectURL(source)));
})();
