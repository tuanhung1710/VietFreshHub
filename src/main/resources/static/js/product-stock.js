/* Product Detail: polling chỉ hỗ trợ hiển thị. Backend vẫn xác thực khi thêm giỏ/đặt hàng. */
(() => {
    const root = document.querySelector('[data-product-stock]');
    if (!root) return;

    const variantList = root.querySelector('[data-variant-list]');
    const form = root.querySelector('[data-detail-cart-form]');
    const quantityInput = form ? form.querySelector('[name="quantity"]') : null;
    const addButton = root.querySelector('[data-add-button]');
    const stockStatus = root.querySelector('[data-stock-status]');
    const warning = root.querySelector('[data-stock-warning]');
    const networkStatus = root.querySelector('[data-stock-network]');
    const feedback = root.querySelector('[data-cart-feedback]');
    const quantities = new Map();
    let variants = new Map();
    let selectedId = root.dataset.selectedId;
    let productUnavailable = false;
    let submitting = false;
    let pollTimer;
    let activeRequest;
    let requestNumber = 0;
    let failureCount = 0;
    let stopped = false;
    const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });

    // Dùng HTML server trả về làm dữ liệu ban đầu, không cần nhúng JSON vào trang.
    for (const link of variantList.querySelectorAll('[data-variant-id]')) {
        variants.set(link.dataset.variantId, {
            variantId: link.dataset.variantId,
            variantName: link.dataset.name,
            price: Number(link.dataset.price),
            imageUrl: link.dataset.image,
            availableStock: Number(link.dataset.stock),
            purchasable: link.dataset.purchasable === 'true',
            availabilityMessage: link.dataset.message
        });
    }
    if (quantityInput) quantities.set(selectedId, quantityInput.value);

    function showMessage(element, message) {
        if (!element) return;
        element.textContent = message;
        element.hidden = !message;
    }

    function renderSelection() {
        const variant = variants.get(selectedId);
        const canBuy = !productUnavailable && variant && variant.purchasable;
        const availableStock = variant ? variant.availableStock : 0;
        if (stockStatus) {
            stockStatus.textContent = productUnavailable || !variant
                ? 'Phân loại này hiện không còn được bán. Vui lòng chọn phân loại khác hoặc quay lại sau.'
                : variant.availabilityMessage;
            stockStatus.className = canBuy ? 'muted' : 'item-error';
        }
        if (variant) {
            root.querySelector('[data-variant-price]').textContent = money.format(variant.price);
            if (variant.imageUrl) root.querySelector('[data-product-image]').src = variant.imageUrl;
        }
        for (const link of variantList.querySelectorAll('[data-variant-id]')) {
            const selected = link.dataset.variantId === selectedId;
            link.classList.toggle('selected', selected);
            link.setAttribute('aria-current', String(selected));
            link.setAttribute('aria-disabled', String(submitting));
            const option = variants.get(link.dataset.variantId);
            const label = option && option.purchasable && !productUnavailable ? '' : ' · Chưa khả dụng';
            link.querySelector('[data-variant-label]').textContent = label;
        }
        if (!quantityInput) return;
        form.querySelector('[name="variantId"]').value = selectedId;
        quantityInput.max = String(availableStock);
        quantityInput.disabled = submitting;
        const quantity = Number(quantityInput.value);
        const validQuantity = Number.isSafeInteger(quantity) && quantity >= 1 && quantity <= availableStock;
        addButton.disabled = submitting || !canBuy || !validQuantity;
        for (const button of root.querySelectorAll('[data-stock-step]')) {
            const decrease = Number(button.dataset.stockStep) < 0;
            button.disabled = submitting || !canBuy || (decrease ? quantity <= 1 : quantity >= availableStock);
        }
        if (canBuy && quantity > availableStock) {
            showMessage(warning, 'Tồn kho vừa thay đổi. Phân loại này hiện chỉ còn ' + availableStock
                + ' sản phẩm. Bạn đang chọn ' + quantity + '. Vui lòng điều chỉnh số lượng để tiếp tục.');
        } else {
            showMessage(warning, '');
        }
    }

    function updateOptions() {
        for (const [id, variant] of variants) {
            let link = [...variantList.querySelectorAll('[data-variant-id]')].find(item => item.dataset.variantId === id);
            if (!link) {
                link = document.createElement('a');
                link.className = 'variant-option';
                link.dataset.variantId = id;
                link.href = root.dataset.detailBase + id;
                const name = document.createElement('span');
                name.setAttribute('data-variant-name', '');
                const label = document.createElement('span');
                label.setAttribute('data-variant-label', '');
                link.append(name, label);
                variantList.append(link);
            }
            link.querySelector('[data-variant-name]').textContent = variant.variantName;
        }
    }

    function cancelRefresh() {
        clearTimeout(pollTimer);
        requestNumber++;
        if (activeRequest) activeRequest.abort();
        activeRequest = null;
    }

    function scheduleRefresh() {
        if (document.hidden || stopped || submitting) return;
        // Khi mất mạng: 10s, 20s rồi tối đa 30s; khi bình thường: 5s.
        const delay = failureCount ? Math.min(30000, 5000 * (2 ** failureCount)) : 5000;
        pollTimer = setTimeout(refreshStock, delay);
    }

    async function refreshStock() {
        cancelRefresh();
        if (document.hidden || stopped || submitting) return;
        const number = requestNumber;
        const controller = new AbortController();
        activeRequest = controller;
        const timeout = setTimeout(() => controller.abort(), 8000);
        try {
            const response = await fetch(root.dataset.stockUrl, {
                cache: 'no-store', credentials: 'same-origin', signal: controller.signal,
                headers: { Accept: 'application/json' }
            });
            if (number !== requestNumber) return;
            if (response.status === 404) {
                productUnavailable = true;
                showMessage(networkStatus, 'Sản phẩm hiện không khả dụng.');
                renderSelection();
                return;
            }
            if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) {
                throw new Error('Không thể cập nhật tồn kho');
            }
            const snapshot = await response.json();
            if (number !== requestNumber) return;
            if (String(snapshot.productId) !== root.dataset.productId || !Array.isArray(snapshot.variants)) {
                throw new Error('Dữ liệu không hợp lệ');
            }
            const previous = variants.get(selectedId);
            const updatedVariants = new Map();
            for (const variant of snapshot.variants) updatedVariants.set(String(variant.variantId), variant);
            variants = updatedVariants;
            productUnavailable = false;
            failureCount = 0;
            updateOptions();
            renderSelection();
            showMessage(networkStatus, '');
            const current = variants.get(selectedId);
            if (previous && current && Number(previous.price) !== Number(current.price)) {
                showMessage(feedback, 'Giá phân loại vừa thay đổi. Vui lòng kiểm tra giá mới trước khi thêm giỏ.');
            }
        } catch (error) {
            if (number !== requestNumber) return;
            failureCount++;
            showMessage(networkStatus, 'Chưa cập nhật được tồn kho. Bạn có thể thử lại; hệ thống vẫn kiểm tra khi thêm giỏ.');
        } finally {
            clearTimeout(timeout);
            if (number === requestNumber) {
                activeRequest = null;
                scheduleRefresh();
            }
        }
    }

    function selectVariant(id, updateHistory) {
        if (submitting || id === selectedId) return;
        cancelRefresh();
        if (quantityInput) quantities.set(selectedId, quantityInput.value);
        selectedId = id;
        if (quantityInput) quantityInput.value = quantities.get(id) || '1';
        showMessage(feedback, '');
        renderSelection();
        if (updateHistory) history.pushState(null, '', root.dataset.detailBase + id);
        refreshStock();
    }

    variantList.addEventListener('click', event => {
        const link = event.target.closest('[data-variant-id]');
        if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
        event.preventDefault();
        selectVariant(link.dataset.variantId, true);
    });
    window.addEventListener('popstate', () => {
        const id = location.pathname.split('/').filter(Boolean).pop();
        if (variants.has(id)) selectVariant(id, false);
        else location.reload();
    });
    if (quantityInput) {
        quantityInput.addEventListener('input', () => {
            quantities.set(selectedId, quantityInput.value);
            renderSelection();
        });
        for (const button of root.querySelectorAll('[data-stock-step]')) {
            button.addEventListener('click', () => {
                const variant = variants.get(selectedId);
                if (!variant || !variant.purchasable || submitting) return;
                const quantity = Number(quantityInput.value);
                const step = Number(button.dataset.stockStep);
                let nextQuantity = Number.isSafeInteger(quantity) ? quantity + step : 1;
                // Khách chủ động bấm giảm: đưa số vượt kho về giới hạn hợp lệ trong một lần.
                if (step < 0 && quantity > variant.availableStock) nextQuantity = variant.availableStock;
                quantityInput.value = String(Math.max(1, Math.min(variant.availableStock, nextQuantity)));
                quantities.set(selectedId, quantityInput.value);
                renderSelection();
            });
        }
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (submitting || addButton.disabled || !form.reportValidity()) return;
            const body = new URLSearchParams(new FormData(form));
            submitting = true;
            cancelRefresh();
            form.setAttribute('aria-busy', 'true');
            renderSelection();
            const controller = new AbortController();
            const timeout = setTimeout(() => controller.abort(), 10000);
            try {
                const response = await fetch(form.dataset.apiUrl, {
                    method: 'POST', body, credentials: 'same-origin', signal: controller.signal,
                    headers: { Accept: 'application/json' }
                });
                if (!response.headers.get('content-type')?.includes('application/json')) {
                    throw new Error('Phiên đăng nhập có thể đã hết hạn. Vui lòng tải lại trang để kiểm tra.');
                }
                const result = await response.json();
                if (!response.ok && response.status !== 409 && response.status !== 400) {
                    throw new Error('Chưa xác nhận được kết quả thêm giỏ. Hãy kiểm tra giỏ trước khi thử lại.');
                }
                showMessage(feedback, result.message);
                feedback.className = response.ok ? 'notice success' : 'item-error';
                // Dùng ngay số lượng server vừa xác thực, kể cả lần refresh tiếp theo bị mất mạng.
                const variant = variants.get(selectedId);
                if (variant && result.code === 'INSUFFICIENT_STOCK' && Number.isInteger(result.availableStock)) {
                    variant.availableStock = Math.max(0, result.availableStock);
                    variant.purchasable = variant.purchasable && variant.availableStock > 0;
                    variant.availabilityMessage = variant.availableStock > 0
                        ? 'Còn ' + variant.availableStock + ' sản phẩm có thể mua.' : 'Tạm hết hàng.';
                }
                if (variant && result.code === 'ITEM_UNAVAILABLE') {
                    variant.purchasable = false;
                    variant.availabilityMessage = result.message;
                }
                if (response.ok && result.totalItems != null) {
                    document.querySelectorAll('[data-cart-badge]').forEach(badge => { badge.textContent = result.totalItems; });
                }
            } catch (error) {
                // Không tự gửi lại POST: request có thể đã được server xử lý.
                showMessage(feedback, error.name === 'AbortError'
                    ? 'Chưa xác nhận được kết quả thêm giỏ. Hãy kiểm tra giỏ trước khi thử lại.'
                    : error.name === 'TypeError'
                        ? 'Mất kết nối. Hãy kiểm tra giỏ trước khi thử lại để tránh thêm trùng số lượng.'
                        : error.message || 'Chưa xác nhận được kết quả thêm giỏ. Hãy kiểm tra giỏ trước khi thử lại.');
                feedback.className = 'item-error';
            } finally {
                clearTimeout(timeout);
                submitting = false;
                form.removeAttribute('aria-busy');
                renderSelection();
                refreshStock();
            }
        });
    }
    document.addEventListener('visibilitychange', () => {
        cancelRefresh();
        if (!document.hidden) refreshStock();
    });
    window.addEventListener('pagehide', () => { stopped = true; cancelRefresh(); });
    window.addEventListener('pageshow', event => {
        if (event.persisted) { stopped = false; refreshStock(); }
    });
    renderSelection();
    refreshStock();
})();
