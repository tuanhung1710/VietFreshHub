package VietFreshHub.admin.controller;

import VietFreshHub.auth.service.AuthService;
import VietFreshHub.payout.dto.PayoutBatchExport;
import VietFreshHub.payout.service.PayoutExcelService;
import VietFreshHub.payout.service.SellerPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/payouts")
public class AdminPayoutController {

    private final SellerPayoutService sellerPayoutService;
    private final PayoutExcelService payoutExcelService;
    private final AuthService authService;

    @GetMapping
    public String dashboard(@RequestParam(name = "fromDate", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                            @RequestParam(name = "toDate", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                            Model model) {
        model.addAttribute("payoutQueue", sellerPayoutService.getQueue());
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            model.addAttribute("filterError", "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
            model.addAttribute("batches", List.of());
        } else {
            model.addAttribute("batches", sellerPayoutService.getBatchesCreatedBetween(fromDate, toDate));
        }
        model.addAttribute("commissionRate", sellerPayoutService.getCommissionRate()
                .movePointRight(2).stripTrailingZeros().toPlainString());
        return "admin/payouts";
    }

    @PostMapping("/{payoutId}/confirm-cod")
    public String confirmCod(@PathVariable Long payoutId, Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            sellerPayoutService.confirmCodRemittance(payoutId, authService.getCurrentUserId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận tiền COD đã về sàn.");
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
                    "Đã tạo đợt chi trả #" + batchId + ". Bạn có thể tải file Excel trong mục Các đợt chi trả.");
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
