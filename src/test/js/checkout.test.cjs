const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/checkout.js'), 'utf8');

function setup(valid = true) {
    const handlers = {};
    const confirm = { disabled: false, textContent: 'Xác nhận đặt hàng', matches: selector => selector === '[data-confirm-order]' };
    const preview = { disabled: false, matches: () => false };
    const attributes = {};
    let validationCalls = 0;
    let reloads = 0;
    let focused = false;
    let scrolled = false;
    const details = { open: false };
    const addressButton = { disabled: false, textContent: 'Lưu và dùng địa chỉ này' };
    const addressForm = {
        checkValidity: () => valid,
        querySelector: () => addressButton,
        addEventListener: (name, handler) => { handlers.addressSubmit = handler; }
    };
    const form = {
        checkValidity: () => { validationCalls++; return valid; },
        addEventListener: (name, handler) => { handlers.submit = handler; },
        setAttribute: (name, value) => { attributes[name] = value; }
    };
    const elements = {
        'checkout-form': form,
        'checkout-address-section': {
            querySelector: () => details,
            scrollIntoView: () => { scrolled = true; }
        },
        'recipient-name': { focus: () => { focused = true; } }
    };
    const document = {
        getElementById: id => elements[id],
        querySelector: selector => selector === '.address-create-form' ? addressForm : {
            addEventListener: (name, handler) => { handlers.addressClick = handler; }
        },
        addEventListener: (name, handler) => handler()
    };
    const window = {
        addEventListener: (name, handler) => { handlers.pageshow = handler; },
        location: { reload: () => { reloads++; } }
    };
    vm.runInNewContext(source, { document, window });
    return { handlers, confirm, preview, attributes, details, addressButton,
        state: () => ({ validationCalls, reloads, focused, scrolled }) };
}

test('valid order confirmation locks its submitter and announces order processing', () => {
    const app = setup();
    app.handlers.submit({ submitter: app.confirm });
    assert.equal(app.confirm.disabled, true);
    assert.equal(app.confirm.textContent, 'Đang ghi nhận đơn…');
    assert.equal(app.attributes['aria-busy'], 'true');
});

for (const action of ['coupon', 'points']) {
    for (const valid of [true, false]) {
        test(`${action} preview leaves order state unchanged even when form validity is ${valid}`, () => {
            const app = setup(valid);
            app.handlers.submit({ submitter: app.preview });
            assert.equal(app.confirm.disabled, false);
            assert.equal(app.confirm.textContent, 'Xác nhận đặt hàng');
            assert.equal(app.preview.disabled, false);
            assert.equal(app.attributes['aria-busy'], undefined);
            assert.equal(app.state().validationCalls, 0);
        });
    }
}

test('invalid confirmation does not enter order processing', () => {
    const app = setup(false);
    app.handlers.submit({ submitter: app.confirm });
    assert.equal(app.confirm.disabled, false);
    assert.equal(app.attributes['aria-busy'], undefined);
});

test('submission without a submitter does not imply order confirmation', () => {
    const app = setup();
    app.handlers.submit({ submitter: null });
    assert.equal(app.confirm.disabled, false);
    assert.equal(app.attributes['aria-busy'], undefined);
});

test('address link still opens, scrolls to and focuses the address form', () => {
    const app = setup();
    let prevented = false;
    app.handlers.addressClick({ preventDefault: () => { prevented = true; } });
    assert.equal(prevented, true);
    assert.equal(app.details.open, true);
    assert.equal(app.state().scrolled, true);
    assert.equal(app.state().focused, true);
});

test('address submit keeps its saving state and validity check', () => {
    const valid = setup();
    valid.handlers.addressSubmit();
    assert.equal(valid.addressButton.disabled, true);
    assert.equal(valid.addressButton.textContent, 'Đang lưu địa chỉ…');
    const invalid = setup(false);
    invalid.handlers.addressSubmit();
    assert.equal(invalid.addressButton.disabled, false);
});

test('back/forward cache restoration reloads, ordinary pageshow does not', () => {
    const app = setup();
    app.handlers.pageshow({ persisted: false });
    assert.equal(app.state().reloads, 0);
    app.handlers.pageshow({ persisted: true });
    assert.equal(app.state().reloads, 1);
});
