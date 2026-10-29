package VietFreshHub.refund.controller;

import VietFreshHub.refund.service.RefundExportService;
import VietFreshHub.refund.service.RefundRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/refund-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private final RefundRequestService refunds;
    private final RefundExportService exports;

    @GetMapping
    public String list(@RequestParam(required = false) String from,
                       @RequestParam(required = false) String to, Model model) {
        model.addAttribute("period", refunds.getAdminPeriod(from, to));
        return "admin/refund-requests";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String from, @RequestParam String to) throws IOException {
        RefundExportService.ExportFile file = exports.export(from, to);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }
}
