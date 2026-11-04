"use strict";

(() => {
    const getElement = id => document.getElementById(id);
    const placeholder = "/images/product/product-placeholder.svg";

    /** Thay ảnh lỗi hoặc hiển thị chữ cái đại diện của shop. */
    function fallbackImage(image) {
        if (image.hasAttribute("data-shop-background")) {
            image.hidden = true;
        } else if (image.id === "shopLogo") {
            image.hidden = true;
            getElement("shopInitial").hidden = false;
        } else if (image.hasAttribute("data-product-image") && !image.src.endsWith(placeholder)) {
            image.src = placeholder;
        }
    }

    document.addEventListener("error", event => {
        if (event.target.tagName === "IMG") fallbackImage(event.target);
    }, true);
    document.querySelectorAll("img").forEach(image => {
        if (image.complete && !image.naturalWidth) fallbackImage(image);
    });

    // Tìm kiếm, bộ lọc, sắp xếp và phân trang dùng form GET/link trong template.
    document.addEventListener("click", event => {
        const information = event.target.closest("[data-footer-info]");
        if (information) {
            getElement("informationTitle").textContent = information.dataset.footerInfo;
            getElement("informationDialog").showModal();
        }
        const authLink = event.target.closest("a[data-auth-link]");
        if (authLink) {
            const url = new URL(authLink.href, location.origin);
            url.searchParams.set("returnTo", location.pathname + location.search);
            authLink.href = url.pathname + url.search;
        }
    });
})();
