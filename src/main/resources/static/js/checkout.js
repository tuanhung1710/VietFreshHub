document.addEventListener('DOMContentLoaded', () => {
    const addressForm = document.querySelector('.address-create-form');
    if (addressForm) addressForm.addEventListener('submit', event => {
        if (event.defaultPrevented || !addressForm.checkValidity()) return;
        const button = addressForm.querySelector('button[type="submit"]');
        button.disabled = true; button.textContent = 'Đang lưu địa chỉ…';
    });
    const form = document.getElementById('checkout-form');
    if (!form) return;
    const button = form.querySelector('[data-confirm-order]');
    let requoting = false;
    form.querySelectorAll('input[name="addressId"]').forEach(address => {
        address.addEventListener('change', () => {
            requoting = true;
            button.disabled = true;
            button.textContent = 'Đang tính phí giao hàng…';
            form.setAttribute('aria-busy', 'true');
            form.querySelectorAll('[data-shipping-amount], [data-grand-total]').forEach(amount => {
                amount.textContent = 'Đang tính lại…';
            });
            form.querySelectorAll('[data-shipping-error]').forEach(error => {
                error.hidden = true;
            });
            const url = new URL(form.action);
            url.searchParams.set('checkoutToken', form.elements.checkoutToken.value);
            url.searchParams.set('addressId', address.value);
            url.searchParams.set('selectionSubmitted', 'true');
            form.querySelectorAll('select[name="couponCodes"]').forEach(coupon => {
                if (coupon.value) url.searchParams.append('couponCodes', coupon.value);
            });
            form.querySelectorAll('input[name="couponCodes"]').forEach(coupon => url.searchParams.append('couponCodes', coupon.value));
            url.searchParams.set('rewardAmountToUse', form.elements.rewardAmountToUse.value);
            window.location.assign(url.toString());
        });
    });
    form.querySelectorAll('[data-coupon-error]').forEach(error => {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'couponCodes';
        input.value = error.dataset.couponError;
        form.append(input);
        error.querySelector('[data-remove-coupon]').addEventListener('click', () => {
            input.remove();
            error.remove();
        });
    });
    form.addEventListener('submit', event => {
        if (!event.submitter?.matches('[data-confirm-order]')) return;
        if (requoting || button.disabled) {
            event.preventDefault();
            return;
        }
        if (!form.checkValidity()) return;
        button.disabled = true;
        button.textContent = 'Đang ghi nhận đơn…';
        form.setAttribute('aria-busy', 'true');
    });
});
// Browser back-forward cache must not leave a valid form permanently disabled.
window.addEventListener('pageshow', event => {
    if (event.persisted) window.location.reload();
});
