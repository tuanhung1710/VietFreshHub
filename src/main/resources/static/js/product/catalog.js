"use strict";

(() => {
    // Selection is independent of the rendered page. The existing service still
    // checks ownership, capabilities and the 50-product transaction limit.
    function createSelection(maximum = 50) {
        const items = new Map();
        function normalize(item) {
            if (!item || !/^[1-9]\d{0,18}$/.test(String(item.id))) return null;
            return {
                id: String(item.id), name: String(item.name || "").slice(0, 250),
                code: String(item.code || "").slice(0, 40),
                canHide: item.canHide === true, canOpen: item.canOpen === true,
                canDelete: item.canDelete === true
            };
        }
        return {
            get size() { return items.size; },
            has(id) { return items.has(String(id)); },
            values() { return Array.from(items.values()); },
            clear() { items.clear(); },
            remove(ids) { ids.forEach(id => items.delete(String(id))); },
            select(item, checked = true) {
                const entry = normalize(item);
                if (!entry) return false;
                if (!checked) { items.delete(entry.id); return true; }
                if (!items.has(entry.id) && items.size >= maximum) return false;
                items.set(entry.id, entry);
                return true;
            },
            selectPage(entries, checked) {
                const valid = entries.map(normalize).filter(Boolean);
                const newIds = new Set(valid.filter(item => !items.has(item.id)).map(item => item.id));
                if (checked && items.size + newIds.size > maximum) return false;
                valid.forEach(item => {
                    if (checked) items.set(item.id, item);
                    else items.delete(item.id);
                });
                return true;
            },
            refresh(entries) {
                entries.forEach(item => { if (items.has(String(item.id))) this.select(item); });
            },
            restore(entries) {
                if (Array.isArray(entries)) entries.forEach(item => this.select(item));
            }
        };
    }
    if (typeof module !== "undefined" && module.exports) module.exports = {createSelection};
    if (typeof document === "undefined") return;

    const page = document.querySelector(".vf-product-catalog");
    if (!page) return;
    const filterForm = page.querySelector("[data-vf-live-filter]");
    const selection = createSelection();
    const storageKey = "vf:product-selection:v1:" + page.dataset.vfSelectionScope;
    let manualMode = false;
    let reviewedIds = "";
    const checksOnPage = () => Array.from(page.querySelectorAll("[data-vf-select-product]"));
    const selectionIds = () => selection.values().map(item => item.id).join(",");
    function readItem(check) {
        return {id: check.value, name: check.dataset.productName, code: check.dataset.productCode,
            canHide: check.dataset.canHide === "true", canOpen: check.dataset.canOpen === "true",
            canDelete: check.dataset.canDelete === "true"};
    }
    try { selection.restore(JSON.parse(window.sessionStorage.getItem(storageKey) || "[]")); }
    catch (_) { /* Pagination still keeps its in-memory selection if storage is unavailable. */ }
    selection.remove((page.dataset.vfCompletedSelection || "").split(","));
    function persist() {
        try {
            if (selection.size) window.sessionStorage.setItem(storageKey, JSON.stringify(selection.values()));
            else window.sessionStorage.removeItem(storageKey);
        } catch (_) { /* Keep the current page usable with storage disabled. */ }
    }
    function selectionError(message = "") {
        const error = page.querySelector("[data-vf-selection-error]");
        if (error) { error.textContent = message; error.hidden = !message; }
    }
    function updateAdvancedIndicator() {
        const active = ["createdFrom", "createdTo", "minPrice", "maxPrice"]
            .some(name => Boolean(filterForm.elements.namedItem(name)?.value));
        filterForm.querySelector('[data-bs-target="#productAdvancedFilters"]').classList.toggle("vf-filter-used", active);
    }
    function updateSelection() {
        const checks = checksOnPage();
        selection.refresh(checks.map(readItem));
        let visibleSelected = 0;
        checks.forEach(check => {
            check.checked = selection.has(check.value);
            if (check.checked) visibleSelected++;
            check.closest("tr").classList.toggle("vf-catalog-selected", check.checked);
        });
        const mode = selection.size > 0 || manualMode;
        page.classList.add("vf-selection-enhanced");
        page.classList.toggle("vf-selection-active", mode);
        const all = page.querySelector("[data-vf-select-all]");
        if (all) {
            all.checked = checks.length > 0 && visibleSelected === checks.length;
            all.indeterminate = visibleSelected > 0 && visibleSelected < checks.length;
            all.disabled = checks.length === 0;
        }
        const start = page.querySelector("[data-vf-start-selection]");
        start.hidden = mode || checks.length === 0;
        start.setAttribute("aria-pressed", String(mode));
        page.querySelector("[data-vf-selection-panel]").hidden = !mode;
        const count = page.querySelector("[data-vf-selection-count]");
        count.hidden = selection.size === 0;
        const offPage = selection.size - visibleSelected;
        count.textContent = selection.size ? "Đã chọn " + selection.size + " sản phẩm"
            + (offPage ? " · " + visibleSelected + " trên trang này · " + offPage + " ngoài trang" : "") : "";
        page.querySelector("[data-vf-selection-help]").hidden = !mode || selection.size > 0;
        page.querySelector("[data-vf-exit-selection]").hidden = !mode || selection.size > 0;
        page.querySelector("[data-vf-bulk-toolbar]").hidden = selection.size === 0;
        for (const button of page.querySelectorAll("[data-vf-bulk-status]")) {
            const capability = button.dataset.vfBulkStatus === "ACTIVE" ? "canOpen"
                : button.dataset.vfBulkStatus === "DELETED" ? "canDelete" : "canHide";
            button.disabled = selection.size === 0 || selection.values().some(item => !item[capability]);
            button.title = button.disabled ? "Các sản phẩm đã chọn phải cùng đủ điều kiện cho thao tác này." : "";
        }
        persist();
    }

    page.addEventListener("change", event => {
        const target = event.target;
        if (!target.matches("[data-vf-select-all], [data-vf-select-product]")) return;
        selectionError();
        const accepted = target.matches("[data-vf-select-all]")
            ? selection.selectPage(checksOnPage().map(readItem), target.checked)
            : selection.select(readItem(target), target.checked);
        manualMode = false;
        if (!accepted) selectionError("Tối đa 50 sản phẩm mỗi lần thao tác. Bỏ bớt lựa chọn trước khi chọn thêm hoặc chọn cả trang.");
        updateSelection();
    });
    page.addEventListener("click", event => {
        if (event.target.closest("[data-vf-start-selection]")) {
            manualMode = true;
            selectionError();
            updateSelection();
            page.querySelector("[data-vf-select-all]")?.focus({preventScroll: true});
        } else if (event.target.closest("[data-vf-clear-selection], [data-vf-exit-selection]")) {
            selection.clear();
            manualMode = false;
            selectionError();
            updateSelection();
            const start = page.querySelector("[data-vf-start-selection]");
            (start.hidden ? filterForm.elements.namedItem("q") : start)?.focus({preventScroll: true});
        }
    });
    page.addEventListener("vf:list-updated", () => {
        // Restore visible checks from the full selection, including off-page items.
        selectionError();
        updateSelection();
        updateAdvancedIndicator();
    });

    function syncReturnFilters(form) {
        for (const [name, value] of new FormData(filterForm)) {
            const input = form.elements.namedItem(name);
            if (input && name !== "_csrf") input.value = value;
        }
        const current = page.querySelector("[data-vf-current-page]");
        form.elements.namedItem("page").value = current.dataset.page;
        form.elements.namedItem("size").value = current.dataset.size;
    }
    document.querySelector("#productActionModal").addEventListener("show.bs.modal", () => {
        syncReturnFilters(document.querySelector("#productActionForm"));
    });
    const bulkModal = document.querySelector("#bulkProductActionModal");
    bulkModal.addEventListener("show.bs.modal", event => {
        const button = event.relatedTarget;
        if (!button || button.disabled || selection.size === 0) { event.preventDefault(); return; }
        const status = button.dataset.vfBulkStatus;
        const label = status === "ACTIVE" ? "Mở bán" : status === "DELETED" ? "Ngừng kinh doanh" : "Tạm ẩn";
        const form = document.querySelector("#bulkProductActionForm");
        syncReturnFilters(form);
        const offPage = selection.size - checksOnPage().filter(check => selection.has(check.value)).length;
        document.querySelector("#bulkProductActionStatus").value = status;
        document.querySelector("#bulkProductActionTitle").textContent = label + " hàng loạt";
        document.querySelector("#bulkProductActionDescription").textContent =
            (label + " ") + selection.size + " sản phẩm trong danh sách dưới."
            + (offPage ? " Bao gồm " + offPage + " sản phẩm đang ngoài trang hoặc không khớp bộ lọc hiện tại." : "")
            + (status === "DELETED" ? " Tồn kho và lịch sử được giữ lại; hiện chưa hỗ trợ mở bán lại. Cần xử lý hết số lượng chờ xuất kho trước khi ngừng kinh doanh." : "");
        const review = bulkModal.querySelector("[data-vf-bulk-review-list]");
        const inputs = bulkModal.querySelector("[data-vf-bulk-selection-inputs]");
        review.replaceChildren();
        inputs.replaceChildren();
        for (const item of selection.values()) {
            const li = document.createElement("li");
            li.textContent = item.code + " · " + item.name;
            review.append(li);
            const input = document.createElement("input");
            input.type = "hidden"; input.name = "productIds"; input.value = item.id;
            inputs.append(input);
        }
        reviewedIds = selectionIds();
        const submit = document.querySelector("#bulkProductActionSubmit");
        submit.textContent = label;
        submit.className = status === "DELETED" ? "btn btn-danger" : "btn btn-success";
        submit.disabled = false;
        document.querySelector("#bulkProductActionReason").value = "";
    });
    bulkModal.addEventListener("shown.bs.modal", () => document.querySelector("#bulkProductActionReason").focus());
    bulkModal.addEventListener("hidden.bs.modal", () => { reviewedIds = ""; });
    document.querySelectorAll("[data-vf-catalog-action]").forEach(form => {
        form.addEventListener("submit", event => {
            if (form.id === "bulkProductActionForm" && (!selection.size || reviewedIds !== selectionIds())) {
                event.preventDefault();
                return;
            }
            // Keep selection through a failed POST; the success acknowledgement
            // removes only the completed IDs on the redirected page.
            persist();
            form.querySelector('[type="submit"]').disabled = true;
        });
    });
    document.addEventListener("submit", event => {
        if (event.target.action && new URL(event.target.action, window.location.href).pathname === "/logout") {
            selection.clear();
            persist();
        }
    });
    updateSelection();
    updateAdvancedIndicator();
})();
