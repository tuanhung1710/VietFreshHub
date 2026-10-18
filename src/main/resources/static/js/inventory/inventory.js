"use strict";

const vfBatchActionModal = document.getElementById("vfBatchActionModal");
const vfBatchActionForm = document.getElementById("vfBatchActionForm");
const vfBatchActionReason = document.getElementById("vfBatchActionReason");

if (vfBatchActionForm) vfBatchActionForm.dataset.confirm = "";

document.addEventListener("submit", event => {
  const form = event.target.closest("form[data-confirm]");
  if (!form) return;
  if(form.dataset.confirm && !window.confirm(form.dataset.confirm)) event.preventDefault();
});

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
    blockedInput.value = String(blocked);
    blockedInput.disabled = isCancellation;
    blockedInput.hidden = isCancellation;
  }

  // The dialog already shows the operation, required reason and explicit submit button.
  vfBatchActionForm.dataset.confirm = "";

  window.bootstrap.Modal.getOrCreateInstance(vfBatchActionModal).show(trigger);
});

if (vfBatchActionModal && vfBatchActionReason) {
  vfBatchActionModal.addEventListener("shown.bs.modal", () => vfBatchActionReason.focus());
}
