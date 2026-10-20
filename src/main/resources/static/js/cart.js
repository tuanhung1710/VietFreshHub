// Native POST forms remain usable without JavaScript; successful mutations use PRG.
document.addEventListener('DOMContentLoaded', () => {
    // Product detail updates the local quantity; only the add-to-cart button submits.
    document.querySelectorAll('[data-quantity-picker]').forEach(picker => {
        const input = picker.querySelector('[name="quantity"]');
        const buttons = picker.querySelectorAll('[data-step]');
        const min = Number(input.min || 1);
        const max = input.max ? Number(input.max) : Number.MAX_SAFE_INTEGER;
        const sync = () => {
            const value = Number(input.value);
            buttons.forEach(button => {
                button.disabled = Number(button.dataset.step) < 0 ? value <= min : value >= max;
            });
        };
        buttons.forEach(button => button.addEventListener('click', () => {
            const value = Number(input.value);
            const current = Number.isSafeInteger(value) ? value : min;
            input.value = String(Math.max(min, Math.min(max, current + Number(button.dataset.step))));
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
        }));
        input.addEventListener('input', sync);
        input.addEventListener('change', sync);
        sync();
    });
    const selections = [...document.querySelectorAll('.cart-selection-checkbox:not(:disabled)')];
    const selectAll = document.getElementById('select-all-items');
    const selectedButton = document.getElementById('checkout-selected-button');
    if (selectAll && selectedButton) {
        const cartId = document.querySelector('[data-cart-id]').dataset.cartId;
        const key = 'vietfresh-cart-selection-' + cartId;
        try {
            const saved = JSON.parse(sessionStorage.getItem(key));
            if (Array.isArray(saved)) selections.forEach(box => { box.checked = saved.includes(box.value); });
        } catch { /* Selection works without browser storage. */ }
        const money = value => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value) + ' ₫';
        const update = () => {
            const chosen = selections.filter(box => box.checked);
            const subtotal = chosen.reduce((total, box) => total + Number(box.dataset.amount), 0);
            document.getElementById('selected-item-count').textContent = chosen.length;
            document.getElementById('selected-subtotal').textContent = money(subtotal);
            document.getElementById('selected-grand-total').textContent = money(subtotal);
            selectAll.checked = selections.length > 0 && chosen.length === selections.length;
            selectAll.indeterminate = chosen.length > 0 && chosen.length < selections.length;
            selectedButton.disabled = chosen.length === 0;
            try { sessionStorage.setItem(key, JSON.stringify(chosen.map(box => box.value))); } catch {}
        };
        selectAll.addEventListener('change', () => { selections.forEach(box => { box.checked = selectAll.checked; }); update(); });
        selections.forEach(box => box.addEventListener('change', update));
        update();
    }
    document.querySelectorAll('[data-quantity-form]').forEach(form => {
        const input = form.querySelector('[name="quantity"]');
        const original = input.value;
        let submitting = false;
        const submit = () => {
            if (submitting || !input.reportValidity() || input.value === original) return;
            submitting = true;
            form.requestSubmit();
        };
        form.querySelectorAll('[data-step]').forEach(button => {
            button.addEventListener('click', () => {
                // Recover an over-stock quantity with a single decrease, rather than trapping the shopper.
                if (Number(button.dataset.step) < 0 && input.max && Number(input.value) > Number(input.max)) {
                    input.value = input.max;
                    submit();
                    return;
                }
                const next = Number(input.value) + Number(button.dataset.step);
                if (!Number.isSafeInteger(next) || next < 0 || (input.max && next > Number(input.max))) return;
                input.value = String(next);
                submit();
            });
        });
        input.addEventListener('change', submit);
        form.addEventListener('submit', () => {
            form.setAttribute('aria-busy', 'true');
            form.querySelectorAll('button').forEach(button => { button.disabled = true; });
        });
    });
});
