"use strict";

document.getElementById("printBatchLabel").addEventListener("click", () => window.print());
const copyBatchCode = document.getElementById("copyBatchCode");
copyBatchCode.addEventListener("click", async () => {
    try {
        await navigator.clipboard.writeText(copyBatchCode.dataset.code);
        copyBatchCode.textContent = "Đã sao chép";
    } catch {
        copyBatchCode.textContent = "Vui lòng sao chép mã lô thủ công";
    }
});
