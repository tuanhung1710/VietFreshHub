"use strict";

(() => {
    const slider = document.querySelector("[data-home-slider]");
    if (!slider) return;

    const track = slider.querySelector("[data-home-slider-track]");
    if (!track) return;

    const slides = [...track.querySelectorAll("[data-home-slide]")];
    const dots = [...slider.querySelectorAll("[data-home-slider-dot]")];
    if (slides.length < 2) return;

    const status = slider.querySelector("[data-home-slider-status]");
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const autoplayDelay = 2000;
    let current = 0;
    let timer;
    let drag;
    let motion;

    function schedule() {
        window.clearTimeout(timer);
        const activeElement = document.activeElement;
        const keyboardFocus = slider.contains(activeElement) && activeElement.matches(":focus-visible");
        if (reducedMotion.matches || keyboardFocus || drag || motion || document.hidden) return;
        timer = window.setTimeout(() => show(current + 1), autoplayDelay);
    }

    function wrap(index) {
        return (index + slides.length) % slides.length;
    }

    function translate(offset) {
        return `translate3d(${offset}%, 0, 0)`;
    }

    function placeSlides(next = current, direction = 0) {
        slides[current].style.removeProperty("transform");
        if (next !== current) slides[next].style.transform = translate(direction * 100);
        slides.forEach((slide, index) => { slide.hidden = index !== current && index !== next; });
    }

    function resetTrack() {
        placeSlides();
        track.style.removeProperty("transform");
        motion?.cancel();
        motion = undefined;
    }

    function settle(next, from, to, announce = false) {
        if (next !== current) {
            dots[current]?.removeAttribute("aria-current");
            current = next;
            dots[current]?.setAttribute("aria-current", "true");
            if (announce) status.textContent = `Ảnh nền ${current + 1} / ${slides.length}`;
        }

        if (from === to || reducedMotion.matches) {
            resetTrack();
            schedule();
            return;
        }

        const transition = track.animate({ transform: [translate(from), translate(to)] }, {
            duration: 160 + Math.abs(to - from) * 1.8,
            easing: "cubic-bezier(.2,.7,.25,1)",
            fill: "forwards"
        });
        motion = transition;
        transition.onfinish = () => {
            if (motion !== transition) return;
            resetTrack();
            schedule();
        };
    }

    function show(index, announce = false) {
        const next = wrap(index);
        if (drag || next === current) return;
        if (motion) resetTrack();

        const direction = index > current ? 1 : -1;
        placeSlides(next, direction);
        settle(next, 0, -direction * 100, announce);
    }

    dots.forEach((dot, index) => dot.addEventListener("click", () => show(index, true)));

    slider.addEventListener("focusin", schedule);
    slider.addEventListener("focusout", () => queueMicrotask(schedule));
    document.addEventListener("visibilitychange", schedule);
    reducedMotion.addEventListener("change", () => {
        if (reducedMotion.matches && motion) resetTrack();
        schedule();
    });
    slider.addEventListener("keydown", event => {
        if (event.altKey || event.ctrlKey || event.metaKey) return;
        if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
        event.preventDefault();
        show(current + (event.key === "ArrowRight" ? 1 : -1), true);
    });

    slider.addEventListener("pointerdown", event => {
        if (drag || motion || !event.isPrimary || event.button !== 0 || event.target.closest("a,button")) return;
        const { left, right, width } = track.getBoundingClientRect();
        const edgeWidth = Math.min(72, width * .15);
        let edge = 0;
        if (event.clientX - left <= edgeWidth) edge = -1;
        else if (right - event.clientX <= edgeWidth) edge = 1;

        drag = {
            id: event.pointerId,
            x: event.clientX,
            y: event.clientY,
            width,
            edge,
            startedAt: event.timeStamp,
            offset: 0
        };
        slider.setPointerCapture(event.pointerId);
        slider.classList.add("is-dragging");
        schedule();
    });

    function moveDrag(event) {
        const dx = event.clientX - drag.x;
        const dy = event.clientY - drag.y;
        if (!drag.axis && Math.max(Math.abs(dx), Math.abs(dy)) >= 6) {
            drag.axis = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
        }
        if (drag.axis !== "x") return;

        const direction = dx < 0 ? 1 : -1;
        if (direction !== drag.direction) {
            drag.direction = direction;
            placeSlides(wrap(current + direction), direction);
        }
        drag.offset = Math.max(-100, Math.min(100, dx / drag.width * 100));
        track.style.transform = translate(drag.offset);
    }

    function endDrag(event) {
        if (drag?.id !== event.pointerId) return;
        const released = event.type === "pointerup";
        if (released) moveDrag(event);

        const { axis, edge, direction, offset, width, startedAt } = drag;
        drag = undefined;
        slider.classList.remove("is-dragging");

        if (released && !axis && edge) {
            show(current + edge, true);
            return;
        }

        const distance = Math.abs(offset) * width / 100;
        const quickFlick = distance >= 28 && event.timeStamp - startedAt < 250;
        if (released && (distance >= Math.min(width * .2, 120) || quickFlick)) {
            settle(wrap(current + direction), offset, -direction * 100, true);
        } else {
            settle(current, offset, 0);
        }
    }

    slider.addEventListener("pointermove", event => {
        if (drag?.id === event.pointerId) moveDrag(event);
    });
    slider.addEventListener("pointerup", endDrag);
    slider.addEventListener("pointercancel", endDrag);
    slider.addEventListener("lostpointercapture", endDrag);

    slider.querySelector("[data-home-slider-controls]").hidden = false;
    schedule();
})();
