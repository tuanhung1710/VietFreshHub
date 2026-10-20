"use strict";

(function () {
    const millisecondsPerDay = 24 * 60 * 60 * 1000;
    const minimumYear = 1;
    const maximumYear = 9999;

    function readDate(value) {
        const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
        if (!parts) {
            return null;
        }

        const year = Number(parts[1]);
        const month = Number(parts[2]);
        const day = Number(parts[3]);
        if (year < minimumYear || year > maximumYear) {
            return null;
        }

        // setUTCFullYear giữ đúng cả các năm 0001–0099.
        const date = new Date(0);
        date.setUTCHours(0, 0, 0, 0);
        date.setUTCFullYear(year, month - 1, day);
        if (date.getUTCFullYear() !== year
                || date.getUTCMonth() !== month - 1
                || date.getUTCDate() !== day) {
            return null;
        }
        return date;
    }

    function formatDate(date) {
        const year = String(date.getUTCFullYear()).padStart(4, "0");
        const month = String(date.getUTCMonth() + 1).padStart(2, "0");
        const day = String(date.getUTCDate()).padStart(2, "0");
        return year + "-" + month + "-" + day;
    }

    function dateFields(wrapper) {
        const received = wrapper.querySelector(".batch-received-date");
        const days = wrapper.querySelector(".batch-shelf-life-days");
        const expiry = wrapper.querySelector(".batch-expiry-date");
        if (!received || !days || !expiry) {
            return null;
        }
        return {received, days, expiry};
    }

    function hasInvalidNumberInput(input) {
        return input.validity && input.validity.badInput;
    }

    function updateExpiry(fields) {
        const rawDays = fields.days.value.trim();
        const received = readDate(fields.received.value);
        fields.days.setCustomValidity("");
        if (rawDays === "" && !hasInvalidNumberInput(fields.days)) {
            return;
        }

        const days = Number(rawDays);
        if (hasInvalidNumberInput(fields.days)
                || !Number.isFinite(days)
                || !Number.isInteger(days)
                || days < 0) {
            fields.days.setCustomValidity("Số ngày bảo quản phải là số nguyên từ 0 trở lên.");
            if (received) {
                fields.expiry.value = "";
            }
            return;
        }
        if (!received) {
            return;
        }

        const expiry = new Date(received.getTime() + days * millisecondsPerDay);
        if (!Number.isFinite(expiry.getTime())
                || expiry.getUTCFullYear() < minimumYear
                || expiry.getUTCFullYear() > maximumYear) {
            fields.days.setCustomValidity(
                "Số ngày bảo quản quá lớn. Vui lòng nhập lại."
            );
            fields.expiry.value = "";
            return;
        }
        fields.expiry.value = formatDate(expiry);
    }

    function updateDays(fields) {
        const received = readDate(fields.received.value);
        const expiry = readDate(fields.expiry.value);
        fields.days.setCustomValidity("");
        if (!received || !expiry || expiry.getTime() < received.getTime()) {
            fields.days.value = "";
            return;
        }
        fields.days.value = String((expiry.getTime() - received.getTime()) / millisecondsPerDay);
    }

    function handleDateInput(event) {
        const input = event.target;
        if (!input || !input.matches(
            ".batch-received-date, .batch-shelf-life-days, .batch-expiry-date"
        )) {
            return;
        }
        const wrapper = input.closest(".batch-dates");
        if (!wrapper) {
            return;
        }
        const fields = dateFields(wrapper);
        if (!fields) {
            return;
        }
        if (input.matches(".batch-shelf-life-days")) {
            updateExpiry(fields);
            return;
        }
        if (input.matches(".batch-expiry-date")) {
            updateDays(fields);
            return;
        }
        if (fields.days.value.trim() !== "" || hasInvalidNumberInput(fields.days)) {
            updateExpiry(fields);
            return;
        }
        updateDays(fields);
    }

    // Delegation áp dụng cho cả các phân loại được thêm sau khi trang đã tải.
    document.addEventListener("input", handleDateInput);
    document.addEventListener("change", handleDateInput);
    for (const wrapper of document.querySelectorAll(".batch-dates")) {
        const fields = dateFields(wrapper);
        if (fields) {
            updateDays(fields);
        }
    }
})();
