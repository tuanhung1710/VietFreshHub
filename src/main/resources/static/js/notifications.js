(() => {
    if (window.vietFreshNotifications) {
        return;
    }

    function updateCount(count) {
        if (!Number.isSafeInteger(count) || count < 0) {
            return;
        }
        document.querySelectorAll('[data-notification-bell]').forEach(bell => {
            bell.dataset.unreadCount = String(count);
            bell.setAttribute('aria-label', `Thông báo, ${count} chưa đọc`);
            const badge = bell.querySelector('[data-notification-count]');
            if (badge) {
                badge.textContent = String(count);
                badge.hidden = count === 0;
            }
        });
    }

    function updateFrom(page) {
        const bell = page.querySelector('[data-notification-bell]');
        if (bell) {
            updateCount(Number(bell.dataset.unreadCount));
        }
    }

    window.vietFreshNotifications = {updateFrom};
    const bell = document.querySelector('[data-notification-bell]');
    if (!bell || document.getElementById('delivery-dashboard')) {
        return;
    }

    const refreshInterval = document.getElementById('notification-inbox') ? 1000 : 10000;

    async function refreshNotifications() {
        if (!document.hidden) {
            try {
                const inbox = document.getElementById('notification-inbox');
                const response = await fetch(inbox ? window.location.pathname + window.location.search : bell.dataset.countUrl, {
                    cache: 'no-store',
                    signal: AbortSignal.timeout(5000)
                });
                if (!response.ok || response.redirected) {
                    throw new Error('Không thể cập nhật thông báo.');
                }
                if (inbox) {
                    const page = new DOMParser().parseFromString(await response.text(), 'text/html');
                    const updatedInbox = page.getElementById('notification-inbox');
                    if (!updatedInbox) {
                        throw new Error('Không thể cập nhật thông báo.');
                    }
                    if (!inbox.contains(document.activeElement)) {
                        inbox.replaceWith(updatedInbox);
                    }
                    updateFrom(page);
                } else {
                    const result = await response.json();
                    updateCount(result.unreadCount);
                }
            } catch (error) {
                const status = document.getElementById('notification-refresh-status');
                if (status) {
                    status.textContent = 'Chưa tải được thông báo mới. Hệ thống sẽ thử lại sau 1 giây.';
                }
            }
        }
        window.setTimeout(refreshNotifications, refreshInterval);
    }

    window.setTimeout(refreshNotifications, refreshInterval);
})();
