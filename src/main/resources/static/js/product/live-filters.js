"use strict";

// Fetch the existing, server-paginated page and update only its list and summaries.
// The filter inputs stay mounted so typing, selection and Vietnamese composition are preserved.
(() => {
    const page = document.querySelector("[data-vf-live-list]");
    const form = page?.querySelector("form[data-vf-live-filter]");
    const results = page?.querySelector("[data-vf-list-results]");
    if (!form || !results || !window.fetch || !window.AbortController) return;

    const defaultSize = form.elements.namedItem("size")?.value || "15";
    const defaultSort = "updated_desc";
    const status = document.createElement("p");
    status.className = "visually-hidden";
    status.setAttribute("role", "status");
    const error = document.createElement("p");
    error.className = "small text-danger px-3 mb-2";
    error.setAttribute("role", "alert");
    error.hidden = true;
    page.querySelector(".vf-list-controls").append(status, error);

    let timer;
    let controller;
    let generation = 0;
    let composing = false;

    function hideSubmitButtons() {
        page.querySelectorAll("[data-vf-filter-submit]").forEach(button => { button.hidden = button.dataset?.vfKeepSubmit !== "true"; });
    }

    function cancelPending() {
        window.clearTimeout(timer);
        controller?.abort();
        generation++;
        results.setAttribute("aria-busy", "false");
    }

    function updateDateBounds() {
        const from = form.elements.namedItem("createdFrom");
        const to = form.elements.namedItem("createdTo");
        if (from && to) {
            from.max = to.value;
            to.min = from.value;
        }
        const minPrice = form.elements.namedItem("minPrice");
        const maxPrice = form.elements.namedItem("maxPrice");
        if (minPrice && maxPrice) {
            minPrice.max = maxPrice.value || "9999999999999999";
            maxPrice.min = minPrice.value || "0";
        }
    }

    function filterUrl() {
        const url = new URL(form.action, window.location.href);
        for (const [name, value] of new FormData(form)) {
            if (name !== "page" && name !== "_csrf" && value !== "") url.searchParams.set(name, value);
        }
        return url;
    }

    function restoreFilters(url) {
        for (const input of form.elements) {
            if (input.name && input.type !== "submit") {
                if (input.type === "checkbox") input.checked = url.searchParams.get(input.name) === input.value;
                else input.value = url.searchParams.get(input.name) ?? (input.name === "size" ? defaultSize : input.name === "sort" ? defaultSort : "");
            }
        }
        updateDateBounds();
    }

    async function loadResults(url, historyMode = "replace") {
        cancelPending();
        updateDateBounds();
        if (!form.reportValidity()) return;
        const requestGeneration = generation;
        controller = new AbortController();
        results.setAttribute("aria-busy", "true");
        status.textContent = "Đang cập nhật kết quả…";
        error.hidden = true;
        try {
            const response = await window.fetch(url.href, {
                signal: controller.signal,
                credentials: "same-origin",
                cache: "no-store",
                headers: {Accept: "text/html"}
            });
            if (requestGeneration !== generation) return;
            if (response.redirected && new URL(response.url).pathname === "/login") {
                window.location.assign(response.url);
                return;
            }
            if (!response.ok) throw new Error("Request failed");
            const html = await response.text();
            if (requestGeneration !== generation) return;
            const next = new DOMParser().parseFromString(html, "text/html");
            const nextPage = next.querySelector("[data-vf-live-list]");
            const nextResults = nextPage?.querySelector("[data-vf-list-results]");
            if (!nextResults) throw new Error("List not found");

            const expanded = Array.from(results.querySelectorAll(".collapse.show[id]"), item => item.id);
            results.replaceChildren(...nextResults.childNodes);
            for (const id of expanded) {
                const section = document.getElementById(id);
                if (!section || !results.contains(section)) continue;
                section.classList.add("show");
                results.querySelectorAll("[data-bs-target]").forEach(button => {
                    if (button.dataset.bsTarget === "#" + id) {
                        button.classList.remove("collapsed");
                        button.setAttribute("aria-expanded", "true");
                    }
                });
            }
            page.querySelectorAll("[data-vf-live-summary]").forEach(summary => {
                const key = summary.dataset.vfLiveSummary;
                const replacement = nextPage.querySelector('[data-vf-live-summary="' + key + '"]');
                if (replacement) {
                    summary.innerHTML = replacement.innerHTML;
                    summary.hidden = replacement.hidden;
                }
            });
            const clearLink = page.querySelector("[data-vf-clear-filters]");
            const nextClearLink = nextPage.querySelector("[data-vf-clear-filters]");
            if (clearLink && nextClearLink) clearLink.href = nextClearLink.href;
            hideSubmitButtons();
            page.dispatchEvent(new Event("vf:list-updated"));
            if (historyMode !== "none" && url.href !== window.location.href) {
                if (historyMode === "push") window.history.pushState(null, "", url.href);
                else window.history.replaceState(null, "", url.href);
            }
            status.textContent = page.querySelector('[data-vf-live-summary="count"]')?.textContent.trim() || "Đã cập nhật kết quả.";
        } catch (failure) {
            if (failure.name !== "AbortError" && requestGeneration === generation) {
                error.textContent = "Chưa cập nhật được kết quả. Nhấn Enter trong ô tìm kiếm để thử lại.";
                error.hidden = false;
                status.textContent = "";
            }
        } finally {
            if (requestGeneration === generation) results.setAttribute("aria-busy", "false");
        }
    }

    function scheduleSearch() {
        cancelPending();
        if (!composing) timer = window.setTimeout(() => loadResults(filterUrl()), 300);
    }

    form.addEventListener("input", event => {
        if (event.target.name === "q") scheduleSearch();
    });
    form.addEventListener("compositionstart", () => {
        composing = true;
        cancelPending();
    });
    form.addEventListener("compositionend", () => {
        composing = false;
        scheduleSearch();
    });
    form.addEventListener("change", event => {
        if (event.target.name !== "q") loadResults(filterUrl());
    });
    form.addEventListener("submit", event => {
        event.preventDefault();
        loadResults(filterUrl());
    });
    page.addEventListener("change", event => {
        if (event.target.closest(".vf-page-size") && event.target.name === "size") {
            form.elements.namedItem("size").value = event.target.value;
            loadResults(filterUrl());
        }
        if (event.target.dataset?.vfSort !== undefined) {
            form.elements.namedItem("sort").value = event.target.value;
            loadResults(filterUrl());
        }
    });
    page.addEventListener("submit", event => {
        if (event.target.matches(".vf-page-jump")) {
            event.preventDefault();
            if (!event.target.reportValidity()) return;
            const url = filterUrl();
            url.searchParams.set("page", event.target.elements.namedItem("page").value);
            loadResults(url, "push");
            return;
        }
        if (!event.target.matches(".vf-page-size")) return;
        event.preventDefault();
        form.elements.namedItem("size").value = event.target.elements.namedItem("size").value;
        loadResults(filterUrl());
    });
    page.addEventListener("click", event => {
        if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        const link = event.target.closest(".vf-filter-tabs a[href], .vf-pagination a[href], [data-vf-clear-filters]");
        if (!link || !page.contains(link) || link.classList.contains("disabled")) return;
        const url = new URL(link.href, window.location.href);
        if (url.origin !== window.location.origin || url.pathname !== new URL(form.action).pathname) return;
        event.preventDefault();
        if (link.closest(".vf-filter-tabs")) {
            form.elements.namedItem("tab").value = url.searchParams.get("tab") || "";
            const scope = form.elements.namedItem("eligibility");
            if (scope) scope.value = "";
            loadResults(filterUrl(), "push");
        } else if (link.matches("[data-vf-clear-filters]")) {
            restoreFilters(url);
            loadResults(filterUrl());
        } else {
            const current = filterUrl();
            const filtersMatch = Array.from(form.elements).every(input => {
                if (!input.name || input.name === "page" || input.name === "_csrf" || input.type === "submit") return true;
                const linkedValue = url.searchParams.get(input.name) ?? (input.name === "size" ? defaultSize : input.name === "sort" ? defaultSort : "");
                return input.type === "checkbox" ? (linkedValue === input.value) === input.checked : linkedValue === input.value;
            });
            if (filtersMatch && url.searchParams.has("page")) current.searchParams.set("page", url.searchParams.get("page"));
            loadResults(current, "push");
        }
    });
    window.addEventListener("popstate", () => {
        const url = new URL(window.location.href);
        if (url.pathname === new URL(form.action).pathname) {
            restoreFilters(url);
            loadResults(url, "none");
        }
    });
    hideSubmitButtons();
})();
