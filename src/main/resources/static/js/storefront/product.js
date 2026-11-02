import { request } from './api.js';
import { $, html, money, rating, placeName, notice } from './render.js';

let adding = false;
let previousFocus;

export function closeModal() {
    $('modal').replaceChildren();
    previousFocus?.focus();
}

export function openProduct(id, variantId) {
    window.location.assign(variantId ? '/product-detail/' + Number(variantId) : '/product-detail?productId=' + Number(id));
}

export function openInformation(title) {
    closeModal();
    previousFocus = document.activeElement;
    $('modal').innerHTML = `<div class="modal-back"><div class="modal" role="dialog" aria-modal="true" aria-labelledby="infoTitle"><button class="close" data-close>Đóng ✕</button><h2 id="infoTitle">${html(title)}</h2><p>Đây là mục minh họa của VietFresh Hub. Nội dung chính thức sẽ được bổ sung khi triển khai hệ thống.</p></div></div>`;
    $('modal').querySelector('[data-close]').focus();
}

export async function refreshCartCount() {
    try { $('cartCount').textContent = (await request('/cart')).totalItems; }
    catch (error) { if (error.status !== 401 && error.status !== 403) throw error; }
}

export async function addProduct(button) {
    if (adding) return;
    adding = true;
    button.disabled = true;
    try {
        const cart = await request('/cart', { method: 'POST', body: { variantId: Number(button.dataset.add), quantity: 1 } });
        $('cartCount').textContent = cart.totalItems;
        closeModal();
        notice('Đã thêm vào giỏ hàng.');
    } catch (error) {
        if (error.status === 401 || error.status === 403) {
            window.location.assign('/login');
            return;
        }
        closeModal();
        notice(error.message);
    } finally {
        adding = false;
        button.disabled = false;
    }
}

document.addEventListener('keydown', event => {
    const modal = $('modal')?.querySelector('[role="dialog"]');
    if (!modal) return;
    if (event.key === 'Escape') { event.preventDefault(); closeModal(); }
    if (event.key === 'Tab') {
        const buttons = [...modal.querySelectorAll('button:not(:disabled),a[href],input,select')];
        const first = buttons[0], last = buttons.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
});
