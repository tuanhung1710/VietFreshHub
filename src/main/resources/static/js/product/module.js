"use strict";
// Use a local placeholder if an external product image is unavailable.
document.addEventListener("error", event => {
  const image = event.target;
  if (image instanceof HTMLImageElement && image.dataset.fallback) {
    const fallback = image.dataset.fallback;
    delete image.dataset.fallback;
    image.src = fallback;
  }
}, true);

async function copySellerSku(value) {
    try {
        if (navigator.clipboard?.writeText) {
            await navigator.clipboard.writeText(value);
            return;
        }
    } catch (_) {
        // Browsers without clipboard permission can still copy selected text.
    }
    const input = document.createElement("textarea");
    input.value = value;
    input.setAttribute("readonly", "");
    input.style.position = "fixed";
    input.style.opacity = "0";
    const previousFocus = document.activeElement;
    document.body.appendChild(input);
    input.select();
    let copied;
    try {
        copied = document.execCommand("copy");
    } finally {
        input.remove();
        previousFocus?.focus();
    }
    if (!copied) {
        throw new Error("Không thể sao chép mã. Hãy chọn mã và sao chép thủ công.");
    }
}

document.addEventListener("click", async event => {
    const button = event.target.closest("[data-vf-copy-sku], [data-vf-copy-code]");
    if (!button) return;
    try {
        const code = button.dataset.vfCopySku || button.dataset.vfCopyCode;
        const label = button.dataset.vfCopySku ? "SKU " : "mã ";
        await copySellerSku(code);
        button.textContent = "Đã sao chép";
        button.setAttribute("aria-label", "Đã sao chép " + label + code);
        window.setTimeout(() => {
            button.textContent = "Sao chép";
            button.setAttribute("aria-label", "Sao chép " + label + code);
        }, 2000);
    } catch (error) {
        alert(error.message);
    }
});

// Reuse one Bootstrap dialog; the POST is still checked by the existing service.
document.querySelector("#productActionModal")?.addEventListener("show.bs.modal", event => {
  const button = event.relatedTarget;
  if (!button) return;
  const status = button.dataset.status;
  document.querySelector("#productActionForm").action = button.dataset.actionUrl;
  document.querySelector("#productActionTitle").textContent = button.dataset.label;
  document.querySelector("#productActionName").textContent = button.dataset.name;
  document.querySelector("#productActionStatus").value = status;
  document.querySelector("#productActionReason").value = "";
  document.querySelector("#productActionDescription").textContent = status === "DELETED"
    ? "Sản phẩm và các phân loại sẽ ngừng kinh doanh. Tồn kho và lịch sử vẫn được giữ lại để theo dõi, xử lý. Hiện chưa hỗ trợ mở bán lại. Cần xử lý hết số lượng chờ xuất kho trước khi ngừng kinh doanh."
    : status === "ACTIVE" ? "Sản phẩm có thể mở bán khi đủ điều kiện kinh doanh và sẽ được Admin hậu kiểm."
    : "Sản phẩm sẽ được tạm ẩn khỏi cửa hàng. Bạn có thể mở bán lại khi sản phẩm đáp ứng điều kiện bán hàng.";
  const submit = document.querySelector("#productActionSubmit");
  submit.textContent = button.dataset.label;
  submit.className = status === "DELETED" ? "btn btn-danger" : "btn btn-success";
});
document.querySelector("#productActionModal")?.addEventListener("shown.bs.modal", () => {
  document.querySelector("#productActionReason").focus();
});

// Standalone histories use GET pagination; live lists handle their own updates.
document.querySelectorAll(".vf-page-size").forEach(form => {
    if (form.closest("[data-vf-live-list]")) return;
    form.querySelector("[data-vf-filter-submit]")?.setAttribute("hidden", "");
    form.querySelector('select[name="size"]')?.addEventListener("change", () => form.requestSubmit());
});

// Collapse filters on small viewports; list-layout.js keeps the sticky rows aligned.
const listPage = document.querySelector(".vf-list-page");
if (listPage) {
    const controls = listPage.querySelector(".vf-list-controls");
    const panel = controls.querySelector(".vf-filter-panel");
    const filterToggle = controls.querySelector("[data-vf-filter-toggle]");
    const compactFilters = window.matchMedia("(max-width: 767px), (max-height: 650px)");
    let filtersOpen = false;
    function updateFilterVisibility() {
        const collapsed = compactFilters.matches && !filtersOpen;
        controls.classList.toggle("vf-compact-filters", compactFilters.matches);
        panel.hidden = collapsed;
        filterToggle.setAttribute("aria-expanded", String(!collapsed));
        if (collapsed && panel.contains(document.activeElement)) filterToggle.focus();
    }
    filterToggle.addEventListener("click", () => {
        filtersOpen = !filtersOpen;
        updateFilterVisibility();
    });
    compactFilters.addEventListener("change", () => {
        filtersOpen = false;
        updateFilterVisibility();
    });
    updateFilterVisibility();
}
