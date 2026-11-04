"use strict";

document.addEventListener("focusout", event => {
    const field = event.target;
    if (!(field instanceof HTMLInputElement) || !field.hasAttribute("data-vf-money")) return;

    const value = field.value.trim();
    if (!/^[+-]?(?:(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d+)?|\.\d+)$/.test(value)) return;

    const [whole, fraction = ""] = value.replaceAll(",", "").split(".");
    const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    const decimals = fraction.replace(/0+$/, "");
    field.value = (grouped || "0") + (decimals ? "." + decimals : "");
});
