document.querySelectorAll('[data-review-report-reason]').forEach((select) => {
    const form = select.closest('form');
    const description = form.querySelector('[data-review-report-description]');
    const input = description.querySelector('textarea');
    const sync = () => {
        const required = select.value === 'OTHER';
        description.hidden = !required;
        input.required = required;
        if (!required) input.value = '';
    };
    select.addEventListener('change', sync);
    sync();
});
