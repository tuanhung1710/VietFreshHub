"use strict";

function preview(picker) {
    const input = picker.querySelector(".image-url");
    const img = picker.querySelector(".image-preview");
    if (!input || !img) {
        return;
    }
    const value = input.value.trim();
    if (/^(https?:\/\/|\/images\/)/.test(value)) {
        img.src = value;
        img.style.display = "block";
    } else {
        img.removeAttribute("src");
        img.style.display = "none";
    }
}

function stock(row) {
    const box = row.querySelector(".no-stock");
    const section = row.querySelector(".initial-stock");
    if (!box || !section) {
        return;
    }
    section.hidden = box.checked;
    for (const input of section.querySelectorAll("input")) {
        input.disabled = box.checked;
        input.required = !box.checked && !input.readOnly
            && !input.matches(".batch-shelf-life-days")
            && !input.name.endsWith(".costPrice") && input.name !== "costPrice";
    }
}

document.addEventListener("change", async event => {
    const input = event.target;
    if (input.matches(".image-url")) {
        preview(input.closest(".image-picker"));
    }
    if (input.matches(".no-stock")) {
        stock(input.closest(".variant-row"));
    }
    if (!input.matches(".image-upload") || !input.files.length) {
        return;
    }
    const picker = input.closest(".image-picker");
    const file = input.files[0];
    if (file.size > 1024 * 1024) {
        alert("Chọn ảnh JPG hoặc PNG có dung lượng tối đa 1 MB.");
        input.value = "";
        return;
    }
    const data = new FormData();
    data.append("file", file);
    input.disabled = true;
    try {
        const token = document.querySelector('meta[name="csrf-token"]').content;
        const header = document.querySelector('meta[name="csrf-header"]').content;
        const response = await fetch(document.querySelector('meta[name="image-upload-url"]').content, {
            method: "POST", headers: {[header]: token}, body: data
        });
        if (!response.ok) {
            const text = await response.text();
            let message = "Không thể tải ảnh lên. Kiểm tra trạng thái đăng nhập và dung lượng ảnh rồi thử lại.";
            try {
                message = JSON.parse(text).message || message;
            } catch (_) {
                // Keep the default message when the response is not JSON.
            }
            throw new Error(message);
        }
        const result = await response.json();
        picker.querySelector(".image-url").value = result.url;
        preview(picker);
    } catch (error) {
        alert(error.message);
    } finally {
        input.disabled = false;
        input.value = "";
    }
});

document.addEventListener("click", event => {
    if (event.target.matches(".copy-image")) {
        const image = document.querySelector(".product-image");
        const picker = event.target.closest(".image-picker");
        if (image && image.value) {
            picker.querySelector(".image-url").value = image.value;
            preview(picker);
        } else {
            alert("Chọn ảnh sản phẩm trước khi dùng cho phân loại.");
        }
    }
    if (event.target.matches(".remove-variant")) {
        const rows = document.querySelector("#variant-rows");
        if (rows.children.length === 1) {
            alert("Sản phẩm cần ít nhất một phân loại.");
            return;
        }
        event.target.closest(".variant-row").remove();
        renumber();
    }
});

function renumber() {
    let index = 0;
    for (const row of document.querySelectorAll("#variant-rows > .variant-row")) {
        row.dataset.index = index;
        row.querySelector("h3").textContent = "Phân loại " + (index + 1);
        for (const input of row.querySelectorAll("[name]")) {
            input.name = input.name.replace(/variants\[\d+\]/g, "variants[" + index + "]");
        }
        for (const element of row.querySelectorAll("[id]")) {
            element.id = element.id.replace(/variants\d+/g, "variants" + index);
        }
        index++;
    }
}

document.querySelector("#add-variant")?.addEventListener("click", () => {
    const rows = document.querySelector("#variant-rows");
    if (rows.children.length >= 20) {
        alert("Mỗi sản phẩm có tối đa 20 phân loại.");
        return;
    }
    const row = rows.firstElementChild.cloneNode(true);
    for (const input of row.querySelectorAll("input")) {
        if (input.type === "checkbox") {
            input.checked = false;
        } else if (input.type !== "hidden") {
            input.value = "";
        }
        input.disabled = false;
    }
    for (const img of row.querySelectorAll("img")) {
        img.removeAttribute("src");
        img.style.display = "none";
    }
    row.querySelector("input[name$='.variantName']").value = "";
    row.querySelector("input[name$='.lowStockThreshold']").value = "5";
    rows.appendChild(row);
    renumber();
    stock(row);
});

for (const row of document.querySelectorAll(".variant-row")) {
    stock(row);
}
for (const picker of document.querySelectorAll(".image-picker")) {
    preview(picker);
}
document.addEventListener("submit", event => {
    const form = event.target.closest("form[data-confirm]");
    if (form?.dataset.confirm && !confirm(form.dataset.confirm)) {
        event.preventDefault();
    }
});
