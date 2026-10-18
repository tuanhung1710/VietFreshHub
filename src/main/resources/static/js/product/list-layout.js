"use strict";

// Move the original header so columns, horizontal scrolling and selection stay
// aligned. Filters return on upward scrolling without replacing the live form.
(() => {
    const page = document.querySelector(".vf-list-page");
    if (!page) return;

    const topbar = document.querySelector(".vf-topbar");
    const tabs = page.querySelector(".vf-catalog-status-tabs");
    const controls = page.querySelector(".vf-list-controls");
    let table;
    let toolbar;
    let frame;
    let controlsOffset = 0;
    let lastScrollY = window.scrollY;
    let directionTravel = 0;
    let revealFilters = false;

    function update() {
        frame = null;
        const delta = window.scrollY - lastScrollY;
        lastScrollY = window.scrollY;
        if (delta) {
            directionTravel = Math.sign(delta) === Math.sign(directionTravel) ? directionTravel + delta : delta;
            if (Math.abs(directionTravel) >= 8) revealFilters = directionTravel < 0;
        }
        if (window.scrollY <= 0) revealFilters = false;

        const top = Math.max(0, topbar?.getBoundingClientRect().bottom || 0);
        const tabsHeight = tabs?.getBoundingClientRect().height || 0;
        const stackTop = top + tabsHeight;
        const controlsBounds = controls?.getBoundingClientRect();
        const naturalControlsTop = controlsBounds ? controlsBounds.top - controlsOffset : 0;
        const controlsHeight = controlsBounds?.height || 0;
        const toolbarHeight = toolbar?.getBoundingClientRect().height || 0;
        const head = table?.tHead;
        const bounds = table?.getBoundingClientRect();
        const headHeight = head?.getBoundingClientRect().height || 0;
        const keepFilters = revealFilters || controls?.contains(document.activeElement);
        const maxOffset = Math.max(0, (bounds?.bottom ?? page.getBoundingClientRect().bottom)
            - naturalControlsTop - controlsHeight - toolbarHeight - headHeight);
        controlsOffset = keepFilters ? Math.max(0, Math.min(stackTop - naturalControlsTop, maxOffset)) : 0;
        if (controls) {
            controls.style.transform = controlsOffset ? "translateY(" + controlsOffset + "px)" : "";
            controls.classList.toggle("vf-filters-pinned", controlsOffset > 0);
        }
        const visibleControlsHeight = controlsOffset > 0
            ? Math.max(0, naturalControlsTop + controlsOffset + controlsHeight - stackTop) : 0;
        page.style.setProperty("--vf-list-top", top + "px");
        page.style.setProperty("--vf-tabs-height", tabsHeight + "px");
        page.style.setProperty("--vf-controls-height", visibleControlsHeight + "px");

        if (!head) return;
        // Product tabs and its toolbar stay visible in both scroll directions.
        const headerTop = Math.max(stackTop + visibleControlsHeight, toolbar?.getBoundingClientRect().bottom || 0);
        const distance = Math.max(0, Math.min(headerTop - bounds.top, bounds.height - headHeight));
        head.style.transform = distance ? "translateY(" + distance + "px)" : "";
        head.classList.toggle("vf-table-head-pinned", distance > 0);
    }

    function schedule() {
        if (frame == null) frame = window.requestAnimationFrame(update);
    }

    const observer = new ResizeObserver(schedule);
    function refresh() {
        observer.disconnect();
        table = page.querySelector(".vf-catalog-scroll > table, .vf-inventory-table-wrap > table");
        toolbar = page.querySelector(".vf-catalog-toolbar");
        [topbar, tabs, controls, toolbar, table, table?.tHead].filter(Boolean).forEach(element => observer.observe(element));
        schedule();
    }

    window.addEventListener("scroll", schedule, {passive: true});
    window.addEventListener("resize", schedule, {passive: true});
    controls?.addEventListener("focusin", schedule);
    controls?.addEventListener("focusout", schedule);
    page.addEventListener("vf:list-updated", refresh);
    refresh();
})();
