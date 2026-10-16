(() => {
    const form = document.getElementById('pickup-location-form');
    if (!form) return;

    const searchInput = document.getElementById('searchText');
    const refIdInput = document.getElementById('refId');
    const suggestions = document.getElementById('address-suggestions');
    const status = document.getElementById('autocomplete-status');
    const selectedAddress = document.getElementById('selected-address');
    const saveButton = document.getElementById('save-pickup-location');
    let searchTimer;
    let searchVersion = 0;

    function selectSuggestion(candidate) {
        clearTimeout(searchTimer);
        searchVersion++;
        searchInput.value = candidate.display;
        refIdInput.value = candidate.refId;
        selectedAddress.textContent = 'Địa chỉ đã chọn: ' + candidate.display;
        status.textContent = 'Địa điểm sẽ được xác minh khi lưu.';
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

    searchInput.addEventListener('input', () => {
        clearTimeout(searchTimer);
        const version = ++searchVersion;
        refIdInput.value = '';
        selectedAddress.textContent = 'Chưa chọn địa chỉ.';
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
