"use strict";
(() => {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");
    function fit() {
        document.querySelectorAll("select[data-vf-fit-select]").forEach(select => {
            const css = getComputedStyle(select);
            context.font = css.font || css.fontSize + " " + css.fontFamily;
            const text = select.selectedOptions[0]?.textContent.trim() || "";
            const label = select.labels[0]?.textContent.trim() || "";
            const space = parseFloat(css.paddingLeft) + parseFloat(css.paddingRight) + 14;
            const width = Math.min(Number(select.dataset.vfFitSelect), Math.max(112,
                context.measureText(text).width + space, context.measureText(label).width + 12));
            select.style.setProperty("--vf-select-width", Math.ceil(width) + "px");
            select.title = text;
        });
    }
    document.addEventListener("change", fit);
    document.addEventListener("vf:list-updated", fit, true);
    window.addEventListener("resize", fit, {passive:true});
    document.fonts.ready.then(fit);
    fit();
})();
