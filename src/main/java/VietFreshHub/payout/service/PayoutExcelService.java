package VietFreshHub.payout.service;

import VietFreshHub.payout.dto.PayoutBatchExport;
import VietFreshHub.payout.dto.PayoutBatchExportItem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PayoutExcelService {

    public byte[] createWorkbook(PayoutBatchExport batch) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            CellStyle amountStyle = workbook.createCellStyle();
            amountStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            writeShopSummary(workbook, headerStyle, amountStyle, batch);
            writeOrderDetails(workbook, headerStyle, amountStyle, batch.items());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo file Excel payout.", exception);
        }
    }

    private void writeShopSummary(Workbook workbook, CellStyle headerStyle, CellStyle amountStyle,
                                  PayoutBatchExport batch) {
        Sheet sheet = workbook.createSheet("Chuyen tien theo shop");
        String[] headers = {"Shop ID", "Cửa hàng", "Ngân hàng", "Mã ngân hàng", "Số tài khoản",
                "Chủ tài khoản", "Số tiền chuyển", "Số đơn", "Trạng thái", "Mã giao dịch"};
        header(sheet, headerStyle, headers);
        Map<Long, List<PayoutBatchExportItem>> grouped = batch.items().stream().collect(Collectors.groupingBy(
                PayoutBatchExportItem::shopId, LinkedHashMap::new, Collectors.toList()));
        int rowIndex = 1;
        for (Map.Entry<Long, List<PayoutBatchExportItem>> entry : grouped.entrySet()) {
            List<PayoutBatchExportItem> shopItems = entry.getValue();
            PayoutBatchExportItem first = shopItems.getFirst();
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(first.shopName());
            row.createCell(2).setCellValue(first.bankName());
            row.createCell(3).setCellValue(first.bankCode());
            row.createCell(4).setCellValue(first.accountNumber());
            row.createCell(5).setCellValue(first.accountHolder());
            amount(row.createCell(6), shopItems.stream().map(PayoutBatchExportItem::transferAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add), amountStyle);
            row.createCell(7).setCellValue(shopItems.size());
            row.createCell(8).setCellValue(aggregateStatus(shopItems));
            row.createCell(9).setCellValue(shopItems.stream().map(PayoutBatchExportItem::transferReference)
                    .filter(value -> value != null && !value.isBlank()).findFirst().orElse(""));
        }
        Row totalRow = sheet.createRow(rowIndex);
        CellStyle totalLabelStyle = workbook.createCellStyle();
        Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalLabelStyle.setFont(totalFont);
        Cell totalLabel = totalRow.createCell(5);
        totalLabel.setCellValue("TỔNG CỘNG");
        totalLabel.setCellStyle(totalLabelStyle);
        amount(totalRow.createCell(6), batch.totalAmount(), amountStyle);
        totalRow.createCell(7).setCellValue(batch.items().size());
        autoSize(sheet, headers.length);
    }

    private void writeOrderDetails(Workbook workbook, CellStyle headerStyle, CellStyle amountStyle,
                                   List<PayoutBatchExportItem> items) {
        Sheet sheet = workbook.createSheet("Chi tiet don hang");
        String[] headers = {"Shop ID", "Cửa hàng", "ID đơn shop", "Mã đơn chính", "Tổng tiền hàng", "Coupon shop tài trợ",
                "Tiền hàng sau coupon", "Hoa hồng sàn", "Tiền chuyển shop", "Trạng thái", "Mã giao dịch"};
        header(sheet, headerStyle, headers);
        int rowIndex = 1;
        for (PayoutBatchExportItem item : items) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item.shopId());
            row.createCell(1).setCellValue(item.shopName());
            row.createCell(2).setCellValue(item.shopOrderId());
            row.createCell(3).setCellValue(item.orderCode());
            amount(row.createCell(4), item.subtotalAmount(), amountStyle);
            amount(row.createCell(5), item.discountAmount(), amountStyle);
            amount(row.createCell(6), item.grossAmount(), amountStyle);
            amount(row.createCell(7), item.commissionAmount(), amountStyle);
            amount(row.createCell(8), item.transferAmount(), amountStyle);
            row.createCell(9).setCellValue(item.status());
            row.createCell(10).setCellValue(item.transferReference() == null ? "" : item.transferReference());
        }
        autoSize(sheet, headers.length);
    }

    private void header(Sheet sheet, CellStyle style, String[] labels) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < labels.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(labels[index]);
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, labels.length - 1));
    }

    private void amount(Cell cell, BigDecimal value, CellStyle style) {
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        cell.setCellStyle(style);
    }

    private String aggregateStatus(List<PayoutBatchExportItem> items) {
        long paid = items.stream().filter(item -> "PAID".equals(item.status())).count();
        if (paid == items.size()) return "PAID";
        if (paid == 0 && items.stream().allMatch(item -> "FAILED".equals(item.status()))) return "FAILED";
        if (items.stream().allMatch(item -> "PENDING".equals(item.status()))) return "PENDING";
        return "PARTIAL";
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int column = 0; column < columnCount; column++) sheet.autoSizeColumn(column);
    }
}
