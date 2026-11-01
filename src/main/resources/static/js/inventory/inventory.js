"use strict";

const vfBatchActionModal = document.getElementById("vfBatchActionModal");
const vfBatchActionForm = document.getElementById("vfBatchActionForm");
const vfBatchActionReason = document.getElementById("vfBatchActionReason");

// Hiển thị giá vốn có dấu phẩy hàng nghìn; gửi chuỗi số gốc để BigDecimal không mất độ chính xác.
document.querySelectorAll("input[data-vf-money]").forEach(input => {
  const raw = document.createElement("input");
  raw.type = "hidden";
  raw.name = input.name;
  raw.disabled = input.disabled;
  input.after(raw);
  input.removeAttribute("name");
  input.type = "text";
  input.inputMode = "decimal";
  input.removeAttribute("min");
  input.removeAttribute("step");

  function synchronize(settled = false) {
    const value = input.value.replaceAll(",", "").trim();
    const caret = input.selectionStart;
    const logicalCaret = caret == null ? null : input.value.slice(0, caret).replaceAll(",", "").length;
    input.setCustomValidity("");
    if (value === "") {
      raw.value = "";
      input.value = "";
      return true;
    }
    if (!/^(?:\d+(?:\.\d{0,2})?|\.\d{1,2})$/.test(value)) {
      raw.value = "";
      input.setCustomValidity("Vui lòng nhập số tiền không âm, tối đa 2 chữ số thập phân.");
      return false;
    }
    const parts = value.split(".");
    const integer = (parts[0] || "0").replace(/^0+(?=\d)/, "");
    if (integer.length > 16) {
      raw.value = "";
      input.setCustomValidity("Số tiền không được vượt quá 16 chữ số phần nguyên.");
      return false;
    }
    const fraction = parts[1];
    raw.value = integer + (fraction ? "." + fraction : "");
    let display = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    if (fraction !== undefined && !(settled && /^0*$/.test(fraction))) display += "." + fraction;
    input.value = display;
    if (document.activeElement === input && logicalCaret != null && !settled) {
      let position = 0;
      let characters = 0;
      while (position < display.length && characters < logicalCaret) {
        if (display[position] !== ",") characters++;
        position++;
      }
      input.setSelectionRange(position, position);
    }
    return true;
  }

  synchronize(true);
  raw.defaultValue = raw.value;
  input.addEventListener("input", () => synchronize());
  input.addEventListener("blur", () => synchronize(true));
  input.form?.addEventListener("submit", event => {
    raw.disabled = input.disabled;
    if (!synchronize(true)) {
      event.preventDefault();
      input.reportValidity();
    }
  }, true);
  input.form?.addEventListener("reset", () => window.setTimeout(() => synchronize(true), 0));
});

if (vfBatchActionForm) vfBatchActionForm.dataset.confirm = "";

// Hiện xác nhận cho các biểu mẫu có data-confirm trước khi gửi.
document.addEventListener("submit", event => {
  const form = event.target.closest("form[data-confirm]");
  if (!form) return;
  if(form.dataset.confirm && !window.confirm(form.dataset.confirm)) event.preventDefault();
});

// Chuẩn bị hộp nhập lý do dùng chung cho thao tác khóa, mở khóa lô hoặc dừng giảm giá.
document.addEventListener("click", event => {
  const trigger = event.target.closest("[data-vf-batch-action]");
  if (!trigger || !vfBatchActionModal || !vfBatchActionForm || !vfBatchActionReason) return;

  const action = trigger.dataset.vfBatchAction;
  if (action !== "block" && action !== "cancel-markdown") return;
  const actionUrl = trigger.dataset.vfActionUrl;
  if (!actionUrl || !window.bootstrap?.Modal) return;
  event.preventDefault();

  const isCancellation = action === "cancel-markdown";
  const blocked = trigger.dataset.vfBlocked === "true";
  const actionLabel = isCancellation ? "Dừng giảm giá" : (blocked ? "Khóa lô" : "Mở khóa lô");
  const batchLabel = "lô #" + trigger.dataset.vfBatchId;
  const title = document.getElementById("vfBatchActionTitle");
  const description = document.getElementById("vfBatchActionDescription");
  const blockedInput = document.getElementById("vfBatchActionBlocked");
  const submit = document.getElementById("vfBatchActionSubmit");

  // Modal được tái sử dụng cho nhiều lô, nên xóa dữ liệu và trạng thái lỗi của lần mở trước.
  vfBatchActionForm.reset();
  vfBatchActionForm.classList.remove("was-validated");
  vfBatchActionForm.action = actionUrl;
  vfBatchActionReason.value = "";
  vfBatchActionReason.setCustomValidity("");

  if (title) title.textContent = isCancellation ? "Dừng giảm giá cho " + batchLabel : actionLabel + " #" + trigger.dataset.vfBatchId;
  if (description) description.textContent = isCancellation
    ? "Giá giảm sẽ không còn áp dụng cho lô hàng này."
    : (blocked ? "Lô hàng sẽ tạm ngừng bán. Số lượng đã giữ cho đơn hàng vẫn được giữ nguyên." : "Lô hàng còn hạn có thể tiếp tục bán. Số lượng đã giữ cho đơn hàng vẫn được giữ nguyên.");
  if (submit) submit.textContent = actionLabel;
  if (blockedInput) {
    // Dừng giảm giá không dùng tham số blocked; disabled để trường này không được gửi lên máy chủ.
    blockedInput.value = String(blocked);
    blockedInput.disabled = isCancellation;
    blockedInput.hidden = isCancellation;
  }

  // Hộp thoại đã hiển thị thao tác, lý do và nút gửi nên không hỏi xác nhận thêm lần nữa.
  vfBatchActionForm.dataset.confirm = "";

  window.bootstrap.Modal.getOrCreateInstance(vfBatchActionModal).show(trigger);
});

// Đưa tiêu điểm vào ô lý do khi hộp thoại mở xong để người dùng nhập ngay.
if (vfBatchActionModal && vfBatchActionReason) {
  vfBatchActionModal.addEventListener("shown.bs.modal", () => vfBatchActionReason.focus());
}

function setAllocationHistoryExpanded(button, expanded) {
  const history = document.getElementById(button.getAttribute("aria-controls"));
  if (!history?.matches("[data-vf-allocation-history]")) return;
  history.hidden = !expanded;
  button.setAttribute("aria-expanded", String(expanded));
  button.classList.toggle("collapsed", !expanded);
  const label = expanded ? "Thu gọn" : "Xem chi tiết";
  const text = button.querySelector("[data-vf-allocation-toggle-label]");
  if (text) text.textContent = label;
  button.setAttribute("aria-label", label + " " + button.dataset.vfOrderCode);
}

document.addEventListener("click", event => {
  const button = event.target.closest("[data-vf-allocation-toggle]");
  if (!button) return;
  setAllocationHistoryExpanded(button, button.getAttribute("aria-expanded") !== "true");
});

// Quay lại đúng danh sách, giữ các phân loại/lô đã mở và vị trí cuộn trước khi rời màn.
(() => {
  const main = document.querySelector(".vf-inventory-main");
  if (!main) return;
  const panelSelector = '[id^="inventory-product-"].collapse, [id^="inventory-details-"].collapse';
  const scrollSelector = ".vf-inventory-table-wrap, .vf-batch-table-wrap";
  const storageKey = () => "vf.inventory.navigation:" + location.pathname + location.search;
  const currentUrl = () => location.pathname + location.search + location.hash;
  const scrollKey = element => element.closest('[id^="inventory-details-"]')?.id || "inventory-products";

  function savePosition() {
    const panels = [...main.querySelectorAll(panelSelector)];
    const horizontal = [...main.querySelectorAll(scrollSelector)].map(element => ({
      id: scrollKey(element), left: element.scrollLeft
    }));
    try {
      sessionStorage.setItem(storageKey(), JSON.stringify({
        expanded: panels.filter(panel => panel.classList.contains("show")).map(panel => panel.id),
        expandedAllocationHistory: [...main.querySelectorAll("[data-vf-allocation-history]:not([hidden])")].map(row => row.id),
        top: window.scrollY,
        horizontal
      }));
    } catch (_) {
      // Liên kết vẫn giữ màn nguồn khi trình duyệt không cho dùng bộ nhớ phiên.
    }
  }

  function prepareLink(link) {
    const target = new URL(link.href, location.href);
    if (target.origin !== location.origin) return;
    target.searchParams.set("returnTo", currentUrl());
    link.href = target.href;
  }

  main.querySelectorAll("a[data-vf-inventory-link]").forEach(prepareLink);
  // Xử lý ủy quyền cả các liên kết được thay mới sau khi lọc danh sách.
  ["pointerdown", "click", "focusin"].forEach(type => {
    document.addEventListener(type, event => {
      const link = event.target.closest("a[data-vf-inventory-link]");
      if (!link) return;
      prepareLink(link);
      if (type !== "focusin") savePosition();
    }, true);
  });

  document.addEventListener("submit", event => {
    const form = event.target.closest("form[data-vf-inventory-origin]");
    if (!form) return;
    const input = form.querySelector('input[name="returnTo"]');
    if (input) input.value = currentUrl();
    savePosition();
  }, true);

  let saved;
  try {
    saved = JSON.parse(sessionStorage.getItem(storageKey()) || "null");
    sessionStorage.removeItem(storageKey());
  } catch (_) {
    return;
  }
  if (!saved || !Array.isArray(saved.expanded)) return;

  main.querySelectorAll(panelSelector).forEach(panel => {
    panel.classList.toggle("show", saved.expanded.includes(panel.id));
  });
  main.querySelectorAll('[data-bs-toggle="collapse"]').forEach(button => {
    const selector = button.getAttribute("data-bs-target");
    if (!selector?.startsWith("#")) return;
    const panel = document.getElementById(selector.substring(1));
    if (!panel?.matches(panelSelector)) return;
    const expanded = panel.classList.contains("show");
    button.setAttribute("aria-expanded", String(expanded));
    button.classList.toggle("collapsed", !expanded);
  });
  main.querySelectorAll("[data-vf-allocation-toggle]").forEach(button => {
    setAllocationHistoryExpanded(button, saved.expandedAllocationHistory?.includes(button.getAttribute("aria-controls")) === true);
  });

  function restoreScroll() {
    window.requestAnimationFrame(() => {
      main.querySelectorAll(scrollSelector).forEach(element => {
        const position = saved.horizontal?.find(value => value.id === scrollKey(element));
        if (position) element.scrollLeft = position.left;
      });
      if (Number.isFinite(saved.top)) window.scrollTo({ top: saved.top, behavior: "instant" });
    });
  }
  restoreScroll();
  if (document.readyState !== "complete") window.addEventListener("load", restoreScroll, { once: true });
})();
