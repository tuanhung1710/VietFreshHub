"use strict";

// Xử lý hiển thị và gửi dữ liệu; service Java kiểm tra ngày, giá, SKU và quy tắc bản nháp.
/** Hiển thị ảnh xem trước khi URL dùng HTTP, HTTPS hoặc đường dẫn ảnh nội bộ. */
function preview(picker) {
    const input = picker.querySelector(".image-url");
    const img = picker.querySelector(".image-preview");
    if (!input || !img) return;
    const visible = /^(https?:\/\/|\/images\/)/.test(input.value.trim());
    if (visible) img.src = input.value.trim();
    else img.removeAttribute("src");
    img.hidden = !visible;
    img.style.display = visible ? "block" : "none";
}

/** Ẩn và vô hiệu hóa dữ liệu nhập kho khi người bán chọn chưa nhập hàng. */
function stock(row) {
    const box = row.querySelector(".no-stock");
    const section = row.querySelector(".initial-stock");
    if (!box || !section) return;
    section.hidden = box.checked;
    // Input bị disabled sẽ không gửi lên máy chủ, tránh gửi dữ liệu lô cũ khi chọn chưa nhập hàng.
    section.querySelectorAll("input").forEach(input => { input.disabled = box.checked; });
    section.querySelectorAll(".initial-quantity, .batch-received-date, .batch-expiry-date").forEach(input => {
        input.required = !box.checked;
    });
}

/** Lấy tên header và token CSRF từ trang để gửi yêu cầu tải ảnh hợp lệ. */
function csrfHeaders() {
    return {[document.querySelector('meta[name="csrf-header"]').content]:
        document.querySelector('meta[name="csrf-token"]').content};
}

// Đồng bộ ảnh, trạng thái nhập kho và tải ảnh lên; khóa ô tải tệp trong lúc chờ phản hồi.
document.addEventListener("change", async event => {
    const input = event.target;
    if (input.matches(".image-url")) preview(input.closest(".image-picker"));
    if (input.matches(".no-stock")) stock(input.closest(".variant-row"));
    if (!input.matches(".image-upload") || !input.files.length) return;
    const picker = input.closest(".image-picker");
    const data = new FormData();
    data.append("file", input.files[0]);
    input.disabled = true;
    try {
        const response = await fetch(document.querySelector('meta[name="image-upload-url"]').content, {
            method: "POST", headers: csrfHeaders(), body: data
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || "Không thể tải ảnh lên.");
        // Sau upload, form lưu URL do máy chủ trả về; ảnh xem trước dùng cùng URL đó.
        picker.querySelector(".image-url").value = result.url;
        preview(picker);
    } catch (error) {
        alert(error instanceof SyntaxError ? "Không thể tải ảnh lên. Vui lòng thử lại." : error.message);
    } finally {
        // Luôn mở lại ô tải và xóa lựa chọn để có thể chọn lại cùng tệp sau một lần thất bại.
        input.disabled = false;
        input.value = "";
    }
});

// Khởi tạo trạng thái đúng cho các dòng đã có dữ liệu khi trang vừa tải.
document.querySelectorAll(".variant-row").forEach(stock);
document.querySelectorAll(".image-picker").forEach(preview);

// Dùng ảnh sản phẩm được chọn làm ảnh cho phân loại và cập nhật phần xem trước.
document.addEventListener("click", event => {
    const button = event.target.closest("button[data-product-image]");
    if (!button) return;
    const picker = button.closest(".image-picker");
    picker.querySelector(".image-url").value = button.dataset.productImage;
    preview(picker);
});
// Ưu tiên xác nhận riêng của nút (ví dụ xóa), sau đó mới dùng xác nhận chung của biểu mẫu.
document.addEventListener("submit", event => {
    const form = event.target.closest("form[data-confirm]");
    const deletesVariant = form?.hasAttribute("data-variant-status")
        && form.elements.namedItem("status")?.value === "DELETED";
    const message = deletesVariant
        ? "Xóa phân loại này? Nếu không còn phân loại mở bán, sản phẩm sẽ tự tạm ẩn."
        : event.submitter?.dataset.confirm || form?.dataset.confirm;
    if (message && !confirm(message)) event.preventDefault();
});
