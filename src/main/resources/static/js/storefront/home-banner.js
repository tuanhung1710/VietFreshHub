"use strict";

(() => {
    const slider = document.querySelector("[data-home-slider]");
    if (!slider) return;

    const slides = [...slider.querySelectorAll("[data-home-slide]")];
    const dots = [...slider.querySelectorAll("[data-home-slider-dot]")];
    if (slides.length < 2) return;

    const toggle = slider.querySelector("[data-home-slider-toggle]");
    const status = slider.querySelector("[data-home-slider-status]");
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    let current = 0;
    let paused = reducedMotion.matches;
    let hovering = false;
    let focused = false;
    let timer;
    let touchStart;

    function schedule() {
        window.clearTimeout(timer);
        if (paused || hovering || focused || document.hidden) return;
        timer = window.setTimeout(() => show(current + 1), 6000);
    }

    function show(index, announce = false) {
        if (slides[current].contains(document.activeElement)) slider.focus({ preventScroll: true });
        current = (index + slides.length) % slides.length;
        slides.forEach((slide, position) => { slide.hidden = position !== current; });
        dots.forEach((dot, position) => {
            if (position === current) dot.setAttribute("aria-current", "true");
            else dot.removeAttribute("aria-current");
        });
        if (announce) status.textContent = `Banner ${current + 1} / ${slides.length}`;
        schedule();
    }

    function updateToggle() {
        toggle.textContent = paused ? "Tự chuyển" : "Tạm dừng";
        toggle.setAttribute("aria-label", paused ? "Bật tự chuyển banner" : "Tạm dừng tự chuyển banner");
    }

    dots.forEach((dot, index) => dot.addEventListener("click", () => show(index, true)));
    slider.querySelector("[data-home-slider-prev]").addEventListener("click", () => show(current - 1, true));
    slider.querySelector("[data-home-slider-next]").addEventListener("click", () => show(current + 1, true));
    toggle.addEventListener("click", () => {
        paused = !paused;
        updateToggle();
        schedule();
    });

    // Không tự đổi nội dung khi người dùng đang đọc hoặc dùng bàn phím trong banner.
    slider.addEventListener("pointerenter", event => {
        if (event.pointerType !== "mouse") return;
        hovering = true;
        schedule();
    });
    slider.addEventListener("pointerleave", () => { hovering = false; schedule(); });
    slider.addEventListener("focusin", () => { focused = true; schedule(); });
    slider.addEventListener("focusout", event => {
        if (slider.contains(event.relatedTarget)) return;
        focused = false;
        schedule();
    });
    document.addEventListener("visibilitychange", schedule);
    reducedMotion.addEventListener("change", event => {
        if (event.matches) paused = true;
        updateToggle();
        schedule();
    });
    slider.addEventListener("keydown", event => {
        if (event.altKey || event.ctrlKey || event.metaKey) return;
        if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
        event.preventDefault();
        show(current + (event.key === "ArrowRight" ? 1 : -1), true);
    });

    slider.addEventListener("pointerdown", event => {
        touchStart = undefined;
        if (event.pointerType === "touch" && !event.target.closest("a,button")) {
            touchStart = { x: event.clientX, y: event.clientY };
        }
    });
    slider.addEventListener("pointerup", event => {
        if (!touchStart) return;
        const dx = event.clientX - touchStart.x;
        const dy = event.clientY - touchStart.y;
        touchStart = undefined;
        if (Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy) * 1.3) {
            show(current + (dx < 0 ? 1 : -1), true);
        }
    });
    slider.addEventListener("pointercancel", () => { touchStart = undefined; });

    slider.querySelector("[data-home-slider-controls]").hidden = false;
    updateToggle();
    schedule();
})();
