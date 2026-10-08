document.addEventListener('DOMContentLoaded', () => {
    const addressSection = document.getElementById('checkout-address-section');
    const addressLink = document.querySelector('.address-add-link');
    if (addressSection && addressLink) addressLink.addEventListener('click', event => {
        event.preventDefault();
        addressSection.querySelector('details').open = true;
        addressSection.scrollIntoView({ behavior: 'auto', block: 'start' });
        document.getElementById('recipient-name').focus({ preventScroll: true });
    });
    const addressForm = document.querySelector('.address-create-form');
    if (addressForm) addressForm.addEventListener('submit', () => {
        if (!addressForm.checkValidity()) return;
        const button = addressForm.querySelector('button[type="submit"]');
        button.disabled = true; button.textContent = 'Đang lưu địa chỉ…';
    });
    const form = document.getElementById('checkout-form');
    if (!form) return;
    form.addEventListener('submit', () => {
        if (!form.checkValidity()) return;
        const button = form.querySelector('[data-confirm-order]');
        button.disabled = true;
        button.textContent = 'Đang ghi nhận đơn…';
        form.setAttribute('aria-busy', 'true');
    });
});
// Browser back-forward cache must not leave a valid form permanently disabled.
window.addEventListener('pageshow', event => {
    if (event.persisted) window.location.reload();
});
