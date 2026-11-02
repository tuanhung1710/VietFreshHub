/* Product Detail: polling chỉ hỗ trợ hiển thị. Backend vẫn xác thực khi thêm giỏ/đặt hàng. */
(() => {
    const root = document.querySelector('[data-product-stock]');
    if (!root) return;

    const variantList = root.querySelector('[data-variant-list]');
    const variantLinks = new Map();
    const price = root.querySelector('[data-variant-price]');
    const image = root.querySelector('[data-product-image]');
    const form = root.querySelector('[data-detail-cart-form]');
    const quantityInput = form ? form.querySelector('[name="quantity"]') : null;
    const addButton = root.querySelector('[data-add-button]');
    const stockStatus = root.querySelector('[data-stock-status]');
    const warning = root.querySelector('[data-stock-warning]');
    const networkStatus = root.querySelector('[data-stock-network]');
    const priceNotice = root.querySelector('[data-price-notice]');
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
        variantLinks.set(link.dataset.variantId, link);
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
        if (element.textContent !== message) element.textContent = message;
        element.hidden = !message;
    }

    function renderSelection() {
        const variant = variants.get(selectedId);
        const canBuy = !productUnavailable && variant && variant.purchasable;
        const availableStock = variant ? variant.availableStock : 0;
        if (stockStatus) {
            const message = productUnavailable || !variant
                ? 'Phân loại này hiện không còn được bán. Vui lòng chọn phân loại khác hoặc quay lại sau.'
                : variant.availabilityMessage;
            if (stockStatus.textContent !== message) stockStatus.textContent = message;
            stockStatus.className = canBuy ? 'muted' : 'item-error';
        }
        if (variant) {
            const formattedPrice = money.format(variant.price);
            if (price.textContent !== formattedPrice) price.textContent = formattedPrice;
            if (variant.imageUrl && image.getAttribute('src') !== variant.imageUrl) image.src = variant.imageUrl;
        }
        for (const link of variantLinks.values()) {
            const selected = link.dataset.variantId === selectedId;
            link.classList.toggle('selected', selected);
            link.setAttribute('aria-current', String(selected));
            link.setAttribute('aria-disabled', String(submitting));
            const option = variants.get(link.dataset.variantId);
            const label = option && option.purchasable && !productUnavailable ? '' : ' · Chưa khả dụng';
            const labelElement = link.querySelector('[data-variant-label]');
            if (labelElement.textContent !== label) labelElement.textContent = label;
        }
        if (!quantityInput) return;
        form.querySelector('[name="variantId"]').value = selectedId;
        quantityInput.max = String(availableStock);
        quantityInput.readOnly = submitting;
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
            let link = variantLinks.get(id);
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
                variantLinks.set(id, link);
            }
            const name = link.querySelector('[data-variant-name]');
            if (name.textContent !== variant.variantName) name.textContent = variant.variantName;
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
                showMessage(priceNotice, 'Giá phân loại vừa thay đổi. Vui lòng kiểm tra giá mới trước khi thêm giỏ.');
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
        showMessage(priceNotice, '');
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
        form.addEventListener('submit', event => {
            // Cho trình duyệt POST form MVC. Không disable input vì nó phải được gửi lên server.
            if (submitting || addButton.disabled || !form.reportValidity()) {
                event.preventDefault();
                return;
            }
            submitting = true;
            cancelRefresh();
            form.setAttribute('aria-busy', 'true');
            renderSelection();
        });
    }
    document.addEventListener('visibilitychange', () => {
        cancelRefresh();
        if (!document.hidden) refreshStock();
    });
    window.addEventListener('pagehide', () => { stopped = true; cancelRefresh(); });
    window.addEventListener('pageshow', event => {
        if (event.persisted) {
            stopped = false;
            submitting = false;
            if (form) form.removeAttribute('aria-busy');
            renderSelection();
            refreshStock();
        }
    });
    renderSelection();
    refreshStock();
})();
