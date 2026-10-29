package VietFreshHub.refund.service;

import VietFreshHub.refund.dto.RefundPeriod;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RefundExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private final RefundRequestService refunds;

    public ExportFile export(String from, String to) throws IOException {
        RefundPeriod period = refunds.getAdminPeriod(from, to);
        if (!period.isCompleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chưa đến mốc kết thúc đã chọn. Vui lòng xuất Excel sau mốc thời gian này.");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Yeu cau hoan tien");
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            CellStyle amountStyle = workbook.createCellStyle();
            amountStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00 \"VND\""));

            writeText(sheet.createRow(0), 0, "Yêu cầu hoàn tiền", textStyle);
            writeText(sheet.createRow(1), 0, "Khoảng thời gian đã chọn (giờ Việt Nam): " + DATE_TIME.format(period.start())
                    + " (bao gồm) đến " + DATE_TIME.format(period.end()) + " (không bao gồm)", textStyle);
            writeText(sheet.createRow(2), 0, "Thời gian xuất (giờ Việt Nam): "
                    + DATE_TIME.format(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))), textStyle);
            XSSFRow countRow = sheet.createRow(3);
            writeText(countRow, 0, "Số yêu cầu", textStyle);
            countRow.createCell(1, CellType.NUMERIC).setCellValue(period.requests().size());
            XSSFRow totalRow = sheet.createRow(4);
            writeText(totalRow, 0, "Tổng số tiền", textStyle);
            writeAmount(totalRow, 1, period.totalAmount(), amountStyle);

            String[] headers = {"Mã yêu cầu hoàn tiền", "Mã đơn hàng", "Ngân hàng nhận tiền", "Số tài khoản",
                    "Tên chủ tài khoản", "Số tiền hoàn (VND)", "Nội dung chuyển khoản"};
            XSSFRow header = sheet.createRow(6);
            for (int column = 0; column < headers.length; column++) {
                writeText(header, column, headers[column], textStyle);
                sheet.setColumnWidth(column, 28 * 256);
            }
            int rowNumber = 7;
            for (RefundPeriod.Row request : period.requests()) {
                XSSFRow row = sheet.createRow(rowNumber++);
                writeText(row, 0, request.refundRequestId().toString(), textStyle);
                writeText(row, 1, request.orderCode(), textStyle);
                writeText(row, 2, request.bankName(), textStyle);
                writeText(row, 3, request.bankAccountNumber(), textStyle);
                writeText(row, 4, request.accountHolderName(), textStyle);
                writeAmount(row, 5, request.refundAmount(), amountStyle);
                writeText(row, 6, request.getTransferDescription(), textStyle);
            }
            sheet.createFreezePane(0, 7);
            workbook.write(output);
            return new ExportFile("Refund_" + FILE_DATE_TIME.format(period.start()) + "_to_"
                    + FILE_DATE_TIME.format(period.end()) + ".xlsx", output.toByteArray());
        }
    }

    private void writeText(XSSFRow row, int column, String value, CellStyle style) {
        XSSFCell cell = row.createCell(column, CellType.STRING);
        cell.setCellStyle(style);
        cell.setCellValue(value);
    }

    private void writeAmount(XSSFRow row, int column, BigDecimal value, CellStyle style) {
        XSSFCell cell = row.createCell(column, CellType.NUMERIC);
        cell.setCellStyle(style);
        cell.getCTCell().setV(value.toPlainString());
    }

    public record ExportFile(String filename, byte[] content) {}
}
