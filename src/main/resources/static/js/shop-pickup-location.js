(() => {
    const form = document.getElementById('pickup-location-form');
    if (!form) return;

    const searchInput = document.getElementById('searchText');
    const refIdInput = document.getElementById('refId');
    const suggestions = document.getElementById('address-suggestions');
    const status = document.getElementById('autocomplete-status');
    const currentAddress = document.getElementById('current-pickup-address');
    const display = document.getElementById('pickup-location-display');
    const editor = document.getElementById('pickup-location-editor');
    const changeButton = document.getElementById('change-pickup-location');
    const saveButton = document.getElementById('save-pickup-location');
    let searchTimer;
    let searchVersion = 0;

    function selectSuggestion(candidate) {
        clearTimeout(searchTimer);
        searchVersion++;
        searchInput.value = candidate.display;
        refIdInput.value = candidate.refId;
        status.textContent = '';
        suggestions.replaceChildren();
        saveButton.disabled = false;
    }

    async function loadSuggestions(query, version) {
        status.textContent = 'Đang tìm gợi ý địa chỉ...';
        try {
            const url = new URL(form.dataset.autocompleteUrl, window.location.origin);
            url.searchParams.set('text', query);
            const response = await fetch(url, {
                headers: { Accept: 'application/json' },
                redirect: 'error'
            });
            if (!response.ok) {
                throw new Error(response.status === 429
                    ? 'Dịch vụ địa chỉ đang giới hạn yêu cầu. Vui lòng thử lại sau.'
                    : 'Không thể tải gợi ý địa chỉ. Vui lòng thử lại sau.');
            }
            const candidates = await response.json();
            if (version !== searchVersion) return;
            if (!Array.isArray(candidates) || candidates.some(candidate =>
                !candidate || typeof candidate.refId !== 'string' || !candidate.refId.trim()
                || typeof candidate.display !== 'string' || !candidate.display.trim())) {
                throw new Error('Dữ liệu gợi ý địa chỉ không hợp lệ. Vui lòng thử lại sau.');
            }
            suggestions.replaceChildren();
            for (const candidate of candidates) {
                const item = document.createElement('li');
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = candidate.display;
                button.addEventListener('click', () => selectSuggestion(candidate));
                item.appendChild(button);
                suggestions.appendChild(item);
            }
            status.textContent = candidates.length
                ? 'Chọn địa chỉ phù hợp từ danh sách gợi ý.'
                : 'Không tìm thấy địa chỉ phù hợp. Vui lòng nhập địa chỉ chi tiết hơn.';
        } catch (error) {
            if (version !== searchVersion) return;
            suggestions.replaceChildren();
            status.textContent = error instanceof TypeError || error instanceof SyntaxError
                ? 'Không thể tải gợi ý địa chỉ. Vui lòng thử lại hoặc đăng nhập lại.'
                : error.message;
        }
    }

    changeButton.addEventListener('click', () => {
        display.hidden = true;
        editor.hidden = false;
        searchInput.value = currentAddress ? currentAddress.textContent.trim() : '';
        searchInput.focus();
        searchInput.select();
    });

    searchInput.addEventListener('input', () => {
        clearTimeout(searchTimer);
        const version = ++searchVersion;
        refIdInput.value = '';
        saveButton.disabled = true;
        suggestions.replaceChildren();
        const query = searchInput.value.trim();
        if (Array.from(query).length < 2) {
            status.textContent = 'Nhập ít nhất 2 ký tự để tìm gợi ý địa chỉ.';
            return;
        }
        status.textContent = 'Đang tìm gợi ý địa chỉ...';
        searchTimer = setTimeout(() => loadSuggestions(query, version), 300);
    });

    form.addEventListener('submit', event => {
        if (!refIdInput.value.trim()) {
            event.preventDefault();
            status.textContent = 'Vui lòng chọn một địa chỉ từ danh sách gợi ý.';
        }
    });
})();
