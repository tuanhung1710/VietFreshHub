"use strict";
(() => {
    function toIso(value) {
        if (!value.trim()) return "";
        const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value.trim())
            || /^(\d{2})(\d{2})(\d{4})$/.exec(value.trim());
        if (!match) return null;
        const [, day, month, year] = match;
        const date = new Date(0);
        date.setUTCHours(0, 0, 0, 0);
        date.setUTCFullYear(Number(year), Number(month) - 1, Number(day));
        if (Number(year) < 1 || date.getUTCFullYear() !== Number(year)
            || date.getUTCMonth() + 1 !== Number(month) || date.getUTCDate() !== Number(day)) return null;
        return year + "-" + month + "-" + day;
    }
    function displayDate(value) {
        return /^(\d{4})-(\d{2})-(\d{2})$/.test(value) ? value.slice(8) + "/" + value.slice(5, 7) + "/" + value.slice(0, 4) : "";
    }
    if (typeof module !== "undefined") module.exports = {toIso, displayDate};
    if (typeof document === "undefined") return;

    const controls = new WeakMap();
    const valueProperty = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value");
    let nextId = 0;
    function enhance(native) {
        if (controls.has(native)) return;
        let wrapper = native.closest(".vf-date-control");
        if (!wrapper) {
            wrapper = document.createElement("span");
            wrapper.className = "vf-date-control" + (native.classList.contains("mt-1") ? " mt-1" : "");
            native.before(wrapper);
            wrapper.append(native);
        } else wrapper.querySelectorAll(".vf-date-display, .vf-date-picker").forEach(item => item.remove());
        const labels = [...new Set([...native.labels, native.closest("label")].filter(Boolean))];
        const text = document.createElement("input");
        text.type = "text";
        text.className = "form-control vf-date-display";
        text.placeholder = "dd/mm/yyyy";
        text.inputMode = "numeric";
        text.autocomplete = "off";
        text.maxLength = 10;
        if (native.getAttribute("aria-label")) text.setAttribute("aria-label", native.getAttribute("aria-label"));
        if (native.getAttribute("aria-describedby")) text.setAttribute("aria-describedby", native.getAttribute("aria-describedby"));
        const picker = document.createElement("button");
        picker.type = "button";
        picker.className = "vf-date-picker";
        picker.setAttribute("aria-label", "Mở lịch chọn ngày");
        picker.innerHTML = '<svg class="vf-icon" aria-hidden="true" viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4m10-4v4M3 11h18"/></svg>';
        native.classList.add("vf-date-native");
        native.tabIndex = -1;
        native.setAttribute("aria-hidden", "true");
        if (!native.id) native.id = "vf-date-" + (++nextId);
        wrapper.append(text, picker);
        function sync() {
            text.id = native.id + "-display";
            labels.forEach(label => { label.htmlFor = text.id; });
            text.value = displayDate(valueProperty.get.call(native));
            text.disabled = native.disabled;
            text.readOnly = native.readOnly;
            text.required = native.required;
            picker.disabled = native.disabled || native.readOnly;
            let error = "";
            if (native.value && native.min && native.value < native.min) error = "Chọn ngày từ " + displayDate(native.min) + " trở đi.";
            if (native.value && native.max && native.value > native.max) error = "Chọn ngày không sau " + displayDate(native.max) + ".";
            text.setCustomValidity(error);
        }
        controls.set(native, sync);
        Object.defineProperty(native, "value", {
            configurable:true,
            get() { return valueProperty.get.call(this); },
            set(value) { valueProperty.set.call(this, value); sync(); }
        });
        function commit(type) {
            const value = toIso(text.value);
            if (value === null) {
                text.setCustomValidity("Nhập ngày hợp lệ theo định dạng dd/mm/yyyy.");
                return;
            }
            text.setCustomValidity("");
            valueProperty.set.call(native, value);
            native.dispatchEvent(new Event(type, {bubbles:true}));
        }
        text.addEventListener("input", event => {event.stopPropagation();commit("input");});
        text.addEventListener("change", event => {event.stopPropagation();commit("change");});
        native.addEventListener("input", sync);
        native.addEventListener("change", sync);
        native.addEventListener("invalid", event => {event.preventDefault();sync();text.focus();text.reportValidity();});
        picker.addEventListener("click", event => {
            event.preventDefault();
            if (native.showPicker) native.showPicker();
            else {native.focus();native.click();}
        });
        new MutationObserver(sync).observe(native, {attributes:true, attributeFilter:["min","max","required","disabled","readonly","id"]});
        sync();
    }
    function refresh() {document.querySelectorAll('input[type="date"]').forEach(enhance);}
    new MutationObserver(refresh).observe(document.body, {childList:true, subtree:true});
    document.addEventListener("reset", () => requestAnimationFrame(() => {
        document.querySelectorAll('input[type="date"]').forEach(input => controls.get(input)?.());
    }));
    refresh();
})();
