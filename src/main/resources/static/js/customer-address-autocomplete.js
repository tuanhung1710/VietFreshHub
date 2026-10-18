(() => {
    document.querySelectorAll('[data-customer-address-autocomplete]').forEach(form => {
        const searchInput = form.querySelector('[data-address-search]');
        const refIdInput = form.querySelector('[data-address-ref-id]');
        const originalText = form.dataset.addressOriginalText;
        const suggestions = form.querySelector('[data-address-suggestions]');
        const status = form.querySelector('[data-address-status]');
        const saveButton = form.querySelector('[data-address-save]');
        let searchTimer;
        let searchVersion = 0;
        let submitting = false;
        let selectedText = refIdInput.value ? searchInput.value : '';

        function hasValidLocation() {
            const selected = refIdInput.value.trim() && searchInput.value === selectedText;
            const unchanged = originalText !== undefined && searchInput.value === originalText && !refIdInput.value.trim();
            return selected || unchanged;
        }

        function updateSelection() {
            saveButton.disabled = submitting || !hasValidLocation();
        }

        function selectSuggestion(candidate) {
            clearTimeout(searchTimer);
            searchVersion++;
            searchInput.value = candidate.display;
            selectedText = candidate.display;
            refIdInput.value = candidate.refId;
            suggestions.replaceChildren();
            status.textContent = '';
            updateSelection();
        }

        async function loadSuggestions(query, version) {
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
                    ? ''
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
            selectedText = '';
            suggestions.replaceChildren();
            updateSelection();
            const query = searchInput.value.trim();
            if (hasValidLocation() || Array.from(query).length < 2) {
                status.textContent = '';
                return;
            }
            status.textContent = 'Đang tìm gợi ý địa chỉ...';
            searchTimer = setTimeout(() => loadSuggestions(query, version), 300);
        });

        form.addEventListener('submit', event => {
            if (submitting) {
                event.preventDefault();
                return;
            }
            if (!hasValidLocation()) {
                event.preventDefault();
                refIdInput.value = '';
                updateSelection();
                status.textContent = 'Vui lòng chọn địa chỉ từ danh sách gợi ý.';
                return;
            }
            if (form.checkValidity()) {
                submitting = true;
                updateSelection();
            }
        }, true);

        window.addEventListener('pageshow', event => {
            if (event.persisted) {
                submitting = false;
                updateSelection();
            }
        });

        updateSelection();
    });
})();
