package VietFreshHub.admin.controller;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.payout.dto.PayoutBatchExport;
import VietFreshHub.payout.service.PayoutExcelService;
import VietFreshHub.payout.service.SellerPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/payouts")
public class AdminPayoutController {

    private final SellerPayoutService sellerPayoutService;
    private final PayoutExcelService payoutExcelService;
    private final AuthService authService;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("payoutQueue", sellerPayoutService.getQueue());
        model.addAttribute("accounts", sellerPayoutService.getAccounts());
        model.addAttribute("batches", sellerPayoutService.getRecentBatches());
        model.addAttribute("commissionRate", sellerPayoutService.getCommissionRate()
                .movePointRight(2).stripTrailingZeros().toPlainString());
        return "admin/payouts";
    }

    @PostMapping("/accounts/{shopId}/verification")
    public String setAccountVerified(@PathVariable Long shopId,
                                     @RequestParam boolean verified,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            sellerPayoutService.setAccountVerified(shopId, verified,
                    authService.getCurrentUserId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", verified
                    ? "Đã xác minh tài khoản payout của shop."
                    : "Đã thu hồi xác minh tài khoản payout của shop.");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getReason());
        }
        return "redirect:/admin/payouts";
    }

    @PostMapping("/{payoutId}/confirm-cod")
    public String confirmCod(@PathVariable Long payoutId, Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            sellerPayoutService.confirmCodRemittance(payoutId, authService.getCurrentUserId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận sàn nhận được khoản COD từ shipper.");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getReason());
        }
        return "redirect:/admin/payouts";
    }

    @PostMapping("/batches/manual")
    public String createManualBatch(@RequestParam(name = "payoutIds", required = false) List<Long> payoutIds,
                                    RedirectAttributes redirectAttributes) {
        try {
            Long batchId = sellerPayoutService.createManualBatch(payoutIds);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo lô payout thủ công #" + batchId + ". Tải file Excel trong mục Các đợt payout.");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getReason());
        }
        return "redirect:/admin/payouts";
    }

    @PostMapping("/batches/{batchId}/shops/{shopId}/result")
    public String recordTransferResult(@PathVariable Long batchId,
                                       @PathVariable Long shopId,
                                       @RequestParam String result,
                                       @RequestParam(required = false) String reference,
                                       @RequestParam(required = false) String failureReason,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        try {
            boolean successful;
            if ("PAID".equals(result)) successful = true;
            else if ("FAILED".equals(result)) successful = false;
            else throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Kết quả chuyển khoản không hợp lệ.");
            sellerPayoutService.recordTransferResult(batchId, shopId, successful, reference, failureReason,
                    authService.getCurrentUserId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi nhận kết quả chuyển khoản.");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getReason());
        }
        return "redirect:/admin/payouts";
    }

    @GetMapping("/batches/{batchId}/export.xlsx")
    public ResponseEntity<byte[]> export(@PathVariable Long batchId) {
        PayoutBatchExport batch = sellerPayoutService.getBatchExport(batchId);
        byte[] workbook = payoutExcelService.createWorkbook(batch);
        String filename = "payout-batch-" + batch.batchId() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .body(workbook);
    }
}
