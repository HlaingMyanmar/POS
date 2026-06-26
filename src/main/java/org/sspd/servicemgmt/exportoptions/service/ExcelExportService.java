package org.sspd.servicemgmt.exportoptions.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.service.BookingService;
import org.sspd.servicemgmt.serviceoptions.dto.ServiceItemDTO;
import org.sspd.servicemgmt.serviceoptions.service.ServiceItemService;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final BookingService bookingService;
    private final ServiceItemService serviceItemService;

    public byte[] exportBookings() {
        List<BookingDTO> data = bookingService.findAll("", "", "", 0, Integer.MAX_VALUE).getContent();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Bookings");

            CellStyle headerStyle = createHeaderStyle(wb);
            String[] headers = {"No", "Invoice No", "Customer", "Staff",
                "Appointment Date", "Total Amount", "Status", "Remark"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            CellStyle numStyle = wb.createCellStyle();
            numStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            int rowNum = 1;
            for (BookingDTO b : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rowNum - 1);
                row.createCell(1).setCellValue(b.getInvoiceNo());
                row.createCell(2).setCellValue(b.getCustomerName());
                row.createCell(3).setCellValue(b.getStaffName() != null ? b.getStaffName() : "");
                row.createCell(4).setCellValue(b.getAppointmentDate() != null ? b.getAppointmentDate() : "");
                Cell amtCell = row.createCell(5);
                amtCell.setCellValue(b.getTotalAmount() != null ? b.getTotalAmount().doubleValue() : 0);
                amtCell.setCellStyle(numStyle);
                row.createCell(6).setCellValue(b.getStatus() != null ? b.getStatus().name() : "");
                row.createCell(7).setCellValue(b.getRemark() != null ? b.getRemark() : "");
            }

            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
            return toBytes(wb);
        } catch (Exception e) {
            throw new RuntimeException("Excel export failed", e);
        }
    }

    public byte[] exportServices() {
        List<ServiceItemDTO> data = serviceItemService.findAll();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Services");

            CellStyle headerStyle = createHeaderStyle(wb);
            String[] headers = {"No", "Code", "Service Name", "Type", "Price", "Active"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            CellStyle numStyle = wb.createCellStyle();
            numStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            int rowNum = 1;
            for (ServiceItemDTO s : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rowNum - 1);
                row.createCell(1).setCellValue(s.getCode());
                row.createCell(2).setCellValue(s.getItem());
                row.createCell(3).setCellValue(s.getServiceTypeName());
                Cell priceCell = row.createCell(4);
                priceCell.setCellValue(s.getPrice() != null ? s.getPrice().doubleValue() : 0);
                priceCell.setCellStyle(numStyle);
                row.createCell(5).setCellValue(s.isActive() ? "Active" : "Inactive");
            }

            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
            return toBytes(wb);
        } catch (Exception e) {
            throw new RuntimeException("Excel export failed", e);
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private byte[] toBytes(XSSFWorkbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
