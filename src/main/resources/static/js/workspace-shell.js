(() => {
    const header = document.querySelector('[data-workspace-header]');
    const sidebar = document.getElementById('vfoSidebar');
    const toggle = document.querySelector('[data-workspace-sidebar-toggle]');
    if (!header || !sidebar || !toggle) return;

    const footer = document.querySelector('footer.vfh-footer');
    const workspace = document.querySelector('.vfo-workspace, .vf-workspace');
    const body = document.body;
    const desktop = window.matchMedia('(min-width: 992px)');
    let collapsed = false;
    let scrollFrame = null;
    let sidebarTop = '';
    let sidebarBottom = '';

    body.classList.add('vfo-shell');

    function updateToggle(expanded) {
        const label = expanded ? 'Ẩn thanh điều hướng' : 'Hiện thanh điều hướng';
        toggle.setAttribute('aria-expanded', String(expanded));
        toggle.setAttribute('aria-label', label);
        toggle.title = label;
    }

    function updateSidebarBounds() {
        scrollFrame = null;
        const top = `${Math.max(0, header.getBoundingClientRect().bottom)}px`;
        if (top !== sidebarTop) {
            body.style.setProperty('--vfo-sidebar-top', top);
            sidebarTop = top;
        }
        const viewportHeight = document.documentElement.clientHeight;
        const bottom = `${desktop.matches && footer
            ? Math.min(viewportHeight, Math.max(0, viewportHeight - footer.getBoundingClientRect().top))
            : 0}px`;
        if (bottom !== sidebarBottom) {
            body.style.setProperty('--vfo-sidebar-bottom', bottom);
            sidebarBottom = bottom;
        }
    }

    function scheduleSidebarBounds() {
        if (scrollFrame === null) scrollFrame = window.requestAnimationFrame(updateSidebarBounds);
    }

    function updateLayout() {
        const hidden = desktop.matches && collapsed;
        if (hidden && sidebar.contains(document.activeElement)) toggle.focus();
        body.classList.toggle('vfo-sidebar-collapsed', hidden);
        sidebar.inert = hidden;
        if (hidden) sidebar.setAttribute('aria-hidden', 'true');
        else sidebar.removeAttribute('aria-hidden');
        if (desktop.matches) toggle.removeAttribute('data-bs-toggle');
        else toggle.setAttribute('data-bs-toggle', 'offcanvas');
        updateToggle(desktop.matches ? !collapsed : sidebar.classList.contains('show'));
        updateSidebarBounds();
    }

    toggle.addEventListener('click', event => {
        if (!desktop.matches) return;
        event.preventDefault();
        event.stopPropagation();
        collapsed = !collapsed;
        updateLayout();
    });

    sidebar.addEventListener('show.bs.offcanvas', () => updateToggle(true));
    sidebar.addEventListener('hide.bs.offcanvas', () => {
        updateToggle(desktop.matches && !collapsed);
    });
    sidebar.addEventListener('hidden.bs.offcanvas', updateLayout);

    desktop.addEventListener('change', () => {
        if (desktop.matches) window.bootstrap?.Offcanvas.getInstance(sidebar)?.hide();
        updateLayout();
    });
    window.addEventListener('scroll', scheduleSidebarBounds, { passive: true });
    window.addEventListener('resize', scheduleSidebarBounds);
    const layoutObserver = new ResizeObserver(scheduleSidebarBounds);
    layoutObserver.observe(header);
    if (workspace) layoutObserver.observe(workspace);
    if (footer) layoutObserver.observe(footer);
    updateLayout();
})();
