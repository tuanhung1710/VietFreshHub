"use strict";

// Đưa thông báo lỗi vào vùng nhìn thấy khi quay lại sau thao tác bị từ chối.
document.querySelector("[data-vf-error]")?.focus();

// Dùng ảnh thay thế nội bộ nếu ảnh sản phẩm tải lỗi; xóa cờ fallback để tránh lặp vô hạn.
document.addEventListener("error", event => {
  const image = event.target;
  if (image instanceof HTMLImageElement && image.dataset.fallback) {
    const fallback = image.dataset.fallback;
    delete image.dataset.fallback;
    image.src = fallback;
  }
}, true);

/** Sao chép SKU hoặc mã nghiệp vụ; dùng vùng chọn tạm nếu Clipboard API không dùng được. */
async function copySellerSku(value) {
    try {
        if (navigator.clipboard?.writeText) {
            await navigator.clipboard.writeText(value);
            return;
        }
    } catch (_) {
        // Nếu trình duyệt không cho dùng Clipboard API, thử sao chép qua vùng văn bản được chọn.
    }
    // Cách dự phòng cần một ô văn bản tạm được chọn, kể cả khi ô đó không hiển thị cho người dùng.
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
        // Dọn ô tạm và trả tiêu điểm về nút cũ cả khi lệnh sao chép thất bại.
        input.remove();
        previousFocus?.focus();
    }
    if (!copied) {
        throw new Error("Không thể sao chép mã. Hãy chọn mã và sao chép thủ công.");
    }
}

// Sao chép mã và tạm đổi nhãn nút để người dùng biết thao tác đã thành công.
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

// Dùng chung hộp xác nhận cho các thao tác sản phẩm; service kiểm tra lại khi nhận yêu cầu.
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
    ? "Sản phẩm và các phân loại sẽ được xóa khỏi danh sách quản lý. Tồn kho và lịch sử vẫn được giữ lại trong Kho hàng để theo dõi, xử lý. Hiện chưa hỗ trợ khôi phục sản phẩm đã xóa. Cần xử lý hết số lượng chờ xuất kho trước khi xóa."
    : status === "ACTIVE" ? "Sản phẩm có thể mở bán khi đủ điều kiện kinh doanh."
    : "Sản phẩm sẽ được tạm ẩn khỏi cửa hàng. Bạn có thể mở bán lại khi sản phẩm đáp ứng điều kiện bán hàng.";
  const submit = document.querySelector("#productActionSubmit");
  submit.textContent = button.dataset.label;
  submit.className = status === "DELETED" ? "btn btn-danger" : "btn btn-success";
});
document.querySelector("#productActionModal")?.addEventListener("shown.bs.modal", () => {
  document.querySelector("#productActionReason").focus();
});

// Xác nhận xóa phân loại từ trang chi tiết sản phẩm.
document.querySelector("#variantDeleteModal")?.addEventListener("show.bs.modal", event => {
    const button = event.relatedTarget;
    if (!button || button.disabled || !button.dataset.actionUrl) {
        event.preventDefault();
        return;
    }
    const form = document.querySelector("#variantDeleteForm");
    form.reset();
    form.action = button.dataset.actionUrl;
    document.querySelector("#variantDeleteName").textContent = button.dataset.name;
});
document.querySelector("#variantDeleteModal")?.addEventListener("shown.bs.modal", () => {
    document.querySelector("#variantDeleteReason").focus();
});

// Gửi lại biểu mẫu GET khi đổi kích thước trang hoặc cách sắp xếp; máy chủ xử lý danh sách.
document.querySelectorAll(".vf-page-size").forEach(form => {
    form.querySelector('select[name="size"]')?.addEventListener("change", () => form.requestSubmit());
});
document.querySelector("[data-vf-sort]")?.addEventListener("change", event => event.target.form.requestSubmit());
