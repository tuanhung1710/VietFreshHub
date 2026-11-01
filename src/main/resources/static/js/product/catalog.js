"use strict";

(() => {
    // Lưu lựa chọn cho giao diện; service vẫn kiểm tra giới hạn và quyền trước khi đổi trạng thái.
    /** Quản lý sản phẩm đã chọn theo ID, tránh trùng lựa chọn giữa các trang. */
    function createSelection() {
        // Map theo ID giúp một sản phẩm chỉ được chọn một lần dù xuất hiện lại sau khi đổi bộ lọc.
        const items = new Map();
        /** Chuẩn hóa dữ liệu lưu lựa chọn và bỏ bản ghi có ID không hợp lệ. */
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
            // Thêm hoặc bỏ một sản phẩm theo ID sau khi chuẩn hóa dữ liệu.
            select(item, checked = true) {
                const entry = normalize(item);
                if (!entry) return false;
                if (!checked) { items.delete(entry.id); return true; }
                items.set(entry.id, entry);
                return true;
            },
            // Áp dụng chọn hoặc bỏ chọn cho trang hiện tại, giữ lựa chọn ở trang khác.
            selectPage(entries, checked) {
                const valid = entries.map(normalize).filter(Boolean);
                valid.forEach(item => {
                    if (checked) items.set(item.id, item);
                    else items.delete(item.id);
                });
                return true;
            },
            // Cập nhật quyền thao tác từ dữ liệu máy chủ cho những sản phẩm đã được chọn.
            refresh(entries) {
                entries.forEach(item => { if (items.has(String(item.id))) this.select(item); });
            },
            // Khôi phục dữ liệu phiên qua cùng bước chuẩn hóa như khi chọn mới.
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
    // Dùng danh sách ID để kiểm tra lựa chọn chưa đổi từ lúc người dùng xem hộp xác nhận.
    const selectionIds = () => selection.values().map(item => item.id).join(",");
    /** Đọc thông tin và khả năng thao tác của sản phẩm từ ô chọn do máy chủ tạo. */
    function readItem(check) {
        return {id: check.value, name: check.dataset.productName, code: check.dataset.productCode,
            canHide: check.dataset.canHide === "true", canOpen: check.dataset.canOpen === "true",
            canDelete: check.dataset.canDelete === "true"};
    }
    try { selection.restore(JSON.parse(window.sessionStorage.getItem(storageKey) || "[]")); }
    catch (_) { /* Nếu không đọc được bộ nhớ phiên, vẫn dùng được lựa chọn trên trang hiện tại. */ }
    selection.remove((page.dataset.vfCompletedSelection || "").split(","));
    /** Lưu lựa chọn theo phạm vi tài khoản và shop để giữ lại khi đổi trang hoặc bộ lọc. */
    function persist() {
        try {
            if (selection.size) window.sessionStorage.setItem(storageKey, JSON.stringify(selection.values()));
            else window.sessionStorage.removeItem(storageKey);
        } catch (_) { /* Trình duyệt chặn lưu phiên thì vẫn giữ lựa chọn trong bộ nhớ của trang. */ }
    }
    /** Hiển thị hoặc xóa thông báo lỗi của phần chọn sản phẩm. */
    function selectionError(message = "") {
        const error = page.querySelector("[data-vf-selection-error]");
        if (error) { error.textContent = message; error.hidden = !message; }
    }
    /** Đồng bộ ô chọn, tổng số và nút thao tác theo lựa chọn hiện tại, rồi lưu lại phiên. */
    function updateSelection() {
        const checks = checksOnPage();
        // Dữ liệu mới từ máy chủ cập nhật quyền thao tác, còn ID đã chọn ngoài trang vẫn được giữ.
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
            // Ô chọn tất cả chỉ phản ánh các dòng trên trang: bỏ chọn, chọn một phần hoặc chọn hết.
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
        // Xóa luôn cho phép gửi khi đã chọn sản phẩm; máy chủ kiểm tra và trả lỗi ràng buộc.
        for (const button of page.querySelectorAll("[data-vf-bulk-status]")) {
            const capability = button.dataset.vfBulkStatus === "ACTIVE" ? "canOpen" : "canHide";
            button.disabled = selection.size === 0 || (button.dataset.vfBulkStatus !== "DELETED"
                && selection.values().some(item => !item[capability]));
            button.title = button.disabled ? "Các sản phẩm đã chọn phải cùng đủ điều kiện cho thao tác này." : "";
        }
        persist();
    }

    // Chỉ cập nhật lựa chọn khi đổi ô chọn sản phẩm hoặc chọn tất cả trên trang.
    page.addEventListener("change", event => {
        const target = event.target;
        if (!target.matches("[data-vf-select-all], [data-vf-select-product]")) return;
        selectionError();
        target.matches("[data-vf-select-all]")
            ? selection.selectPage(checksOnPage().map(readItem), target.checked)
            : selection.select(readItem(target), target.checked);
        manualMode = false;
        updateSelection();
    });
    // Bật chế độ chọn thủ công hoặc xóa lựa chọn, đồng thời đưa tiêu điểm về nút phù hợp.
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
    /** Chép bộ lọc và trang hiện tại vào biểu mẫu để quay lại đúng danh sách sau thao tác. */
    function syncReturnFilters(form) {
        // Chép điều kiện quay về nhưng giữ CSRF token riêng của biểu mẫu thao tác.
        for (const [name, value] of new FormData(filterForm)) {
            const input = form.elements.namedItem(name);
            if (input && name !== "_csrf") input.value = value;
        }
        const current = page.querySelector("[data-vf-current-page]");
        form.elements.namedItem("page").value = current.dataset.page;
        form.elements.namedItem("size").value = current.dataset.size;
    }
    // Cập nhật bộ lọc trả về ngay trước khi mở hộp thao tác một sản phẩm.
    document.querySelector("#productActionModal").addEventListener("show.bs.modal", () => {
        syncReturnFilters(document.querySelector("#productActionForm"));
    });
    document.querySelector("#variantDeleteModal").addEventListener("show.bs.modal", () => {
        syncReturnFilters(document.querySelector("#variantDeleteForm"));
    });
    document.querySelector("#variantActionModal").addEventListener("show.bs.modal", () => {
        syncReturnFilters(document.querySelector("#variantActionForm"));
    });
    const bulkModal = document.querySelector("#bulkProductActionModal");
    // Liệt kê toàn bộ sản phẩm đã chọn, kể cả ngoài trang, để người dùng kiểm tra trước khi gửi.
    bulkModal.addEventListener("show.bs.modal", event => {
        const button = event.relatedTarget;
        if (!button || button.disabled || selection.size === 0) { event.preventDefault(); return; }
        const status = button.dataset.vfBulkStatus;
        const label = status === "ACTIVE" ? "Mở bán" : status === "DELETED" ? "Xóa sản phẩm" : "Tạm ẩn";
        const form = document.querySelector("#bulkProductActionForm");
        syncReturnFilters(form);
        const offPage = selection.size - checksOnPage().filter(check => selection.has(check.value)).length;
        document.querySelector("#bulkProductActionStatus").value = status;
        document.querySelector("#bulkProductActionTitle").textContent = label + " hàng loạt";
        document.querySelector("#bulkProductActionDescription").textContent =
            (status === "DELETED" ? "Xóa " : label + " ") + selection.size + " sản phẩm trong danh sách dưới."
            + (offPage ? " Bao gồm " + offPage + " sản phẩm đang ngoài trang hoặc không khớp bộ lọc hiện tại." : "")
            + (status === "DELETED" ? " Tồn kho và lịch sử được giữ lại trong Kho hàng; hiện chưa hỗ trợ khôi phục sản phẩm đã xóa. Cần xử lý hết số lượng chờ xuất kho trước khi xóa." : "");
        const review = bulkModal.querySelector("[data-vf-bulk-review-list]");
        const inputs = bulkModal.querySelector("[data-vf-bulk-selection-inputs]");
        review.replaceChildren();
        inputs.replaceChildren();
        // Tạo đồng thời danh sách người dùng xem và các hidden input mà máy chủ sẽ nhận.
        for (const item of selection.values()) {
            const li = document.createElement("li");
            li.textContent = item.code + " · " + item.name;
            review.append(li);
            const input = document.createElement("input");
            input.type = "hidden"; input.name = "productIds"; input.value = item.id;
            inputs.append(input);
        }
        // Chỉ cho gửi đúng tập sản phẩm đã được hiển thị trong hộp xác nhận.
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
            // Giữ lựa chọn nếu gửi thất bại; trang trả về chỉ bỏ các ID được máy chủ xác nhận thành công.
            persist();
            form.querySelector('[type="submit"]').disabled = true;
        });
    });
    // Xóa lựa chọn đã lưu khi đăng xuất để kết thúc trạng thái thao tác của phiên hiện tại.
    document.addEventListener("submit", event => {
        if (event.target.action && new URL(event.target.action, window.location.href).pathname === "/logout") {
            selection.clear();
            persist();
        }
    });
    updateSelection();
})();
