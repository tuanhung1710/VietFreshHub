"use strict";

const picker = document.querySelector("[data-inventory-picker]");
const skuQuery = document.getElementById("inventorySkuQuery");
const choices = document.getElementById("inventorySkuChoices");
let timer;
let pendingRequest;

// Tìm trên máy chủ, giữ nút Tìm để sử dụng khi JavaScript không chạy.
skuQuery.addEventListener("input", () => {
    window.clearTimeout(timer);
    pendingRequest?.abort();
    choices.replaceChildren();
    timer = window.setTimeout(async () => {
        const request = new AbortController();
        pendingRequest = request;
        const url = new URL(picker.dataset.optionsUrl, window.location.href);
        url.search = new URLSearchParams(new FormData(picker)).toString();
        try {
            const response = await fetch(url, {signal: request.signal, redirect: "error"});
            if (!response.ok) throw new Error("Không tải được gợi ý SKU");
            const result = await response.text();
            if (!request.signal.aborted) choices.innerHTML = result;
        } catch {
            if (!request.signal.aborted) {
                choices.textContent = "Chưa tải được gợi ý. Bấm Tìm để thử lại.";
            }
        }
    }, 200);
});

skuQuery.addEventListener("keydown", event => {
    if (event.key === "ArrowDown" && choices.querySelector("a")) {
        event.preventDefault();
        choices.querySelector("a").focus();
    }
});

choices.addEventListener("keydown", event => {
    if (event.key === "Escape") {
        skuQuery.focus();
    } else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        const next = event.key === "ArrowDown" ? event.target.nextElementSibling : event.target.previousElementSibling;
        (next?.matches("a") ? next : skuQuery).focus();
    }
});
