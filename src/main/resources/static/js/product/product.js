"use strict";

// Presentation and transport only. Dates, prices, SKUs and draft rules belong to Java services.
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

function stock(row) {
    const box = row.querySelector(".no-stock");
    const section = row.querySelector(".initial-stock");
    if (!box || !section) return;
    section.hidden = box.checked;
    section.querySelectorAll("input").forEach(input => { input.disabled = box.checked; });
}

function csrfHeaders() {
    return {[document.querySelector('meta[name="csrf-header"]').content]:
        document.querySelector('meta[name="csrf-token"]').content};
}

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
        picker.querySelector(".image-url").value = result.url;
        preview(picker);
    } catch (error) {
        alert(error instanceof SyntaxError ? "Không thể tải ảnh lên. Vui lòng thử lại." : error.message);
    } finally {
        input.disabled = false;
        input.value = "";
    }
});

document.querySelectorAll(".variant-row").forEach(stock);
document.querySelectorAll(".image-picker").forEach(preview);
document.addEventListener("submit", event => {
    const form = event.target.closest("form[data-confirm]");
    if (form?.dataset.confirm && !confirm(form.dataset.confirm)) event.preventDefault();
});
