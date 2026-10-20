document.addEventListener('DOMContentLoaded', () => {
    const panel = document.getElementById('vnpay-payment');
    if (!panel) return;
    const form = panel.querySelector('form');
    if (form) form.addEventListener('submit', () => {
        const button = form.querySelector('button');
        button.disabled = true;
        button.textContent = 'Đang mở cổng VNPAY…';
    });
    // Read server state only. Never treat a URL parameter as proof of payment.
    let attempts = 0;
    const poll = async () => {
        if (++attempts > 180 || !['PENDING', 'FAILED'].includes(panel.dataset.status)) return;
        try {
            const response = await fetch(panel.dataset.statusUrl, {
                credentials: 'same-origin', cache: 'no-store', headers: {Accept: 'application/json'}
            });
            if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) return;
            const payment = await response.json();
            panel.dataset.status = payment.status;
            panel.querySelector('[data-payment-message]').textContent = payment.message;
            if (!payment.canPay && form) form.hidden = true;
        } catch (_) { /* Keep the manual status check available after a network error. */ }
        window.setTimeout(poll, 5000);
    };
    window.setTimeout(poll, 5000);
});
window.addEventListener('pageshow', event => {
    if (event.persisted) window.location.reload();
});
