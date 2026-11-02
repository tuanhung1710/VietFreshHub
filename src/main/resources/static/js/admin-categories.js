(() => {
    const input = document.getElementById('imageFile');
    const dropZone = document.getElementById('imageDropZone');
    const preview = document.getElementById('selectedImagePreview');
    const previewImage = document.getElementById('selectedImage');
    const previewName = document.getElementById('selectedImageName');
    const error = document.getElementById('imageFileError');
    const removeImageInput = document.getElementById('removeImage');
    const removeSelectedButton = document.querySelector('[data-remove-selected-image]');
    const removeCurrentButton = document.querySelector('[data-remove-current-image]');
    const currentPreview = document.getElementById('currentImagePreview');

    if (!input || !dropZone || !preview || !previewImage || !previewName || !error || !removeImageInput) {
        return;
    }

    const maxFileSize = 2 * 1024 * 1024;
    const allowedTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);
    let previewUrl = null;

    function showFile(file) {
        error.hidden = true;
        error.textContent = '';
        preview.hidden = true;
        previewImage.removeAttribute('src');
        previewName.textContent = '';

        if (previewUrl) {
            URL.revokeObjectURL(previewUrl);
            previewUrl = null;
        }
        if (!file) {
            return;
        }

        if (!allowedTypes.has(file.type)) {
            error.textContent = 'Chỉ chấp nhận ảnh JPG, PNG hoặc WebP.';
            error.hidden = false;
            return;
        }
        if (file.size > maxFileSize) {
            error.textContent = 'Ảnh danh mục không được vượt quá 2 MB.';
            error.hidden = false;
            return;
        }

        previewUrl = URL.createObjectURL(file);
        previewImage.src = previewUrl;
        previewName.textContent = file.name;
        preview.hidden = false;
    }

    input.addEventListener('change', () => showFile(input.files[0]));

    if (removeSelectedButton) {
        removeSelectedButton.addEventListener('click', () => {
            input.value = '';
            showFile(null);
        });
    }

    if (removeCurrentButton && currentPreview) {
        removeCurrentButton.addEventListener('click', () => {
            removeImageInput.value = 'true';
            currentPreview.hidden = true;
        });
    }

    dropZone.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            input.click();
        }
    });

    ['dragenter', 'dragover'].forEach(type => {
        dropZone.addEventListener(type, event => {
            event.preventDefault();
            dropZone.classList.add('is-dragging');
        });
    });

    ['dragleave', 'drop'].forEach(type => {
        dropZone.addEventListener(type, event => {
            event.preventDefault();
            dropZone.classList.remove('is-dragging');
        });
    });

    dropZone.addEventListener('drop', event => {
        const file = event.dataTransfer?.files?.[0];
        if (!file) {
            return;
        }
        const transfer = new DataTransfer();
        transfer.items.add(file);
        input.files = transfer.files;
        showFile(file);
    });
})();
