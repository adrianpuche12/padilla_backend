package com.padilla.backend.service.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** PRO-072: exporta un Report a Excel (xlsx) o PDF. */
@Component
public class ReportExporter {

    public byte[] toXlsx(Report report) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Reporte");
            CellStyle bold = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);

            int r = 0;
            Row title = sheet.createRow(r++);
            title.createCell(0).setCellValue(report.title());
            title.getCell(0).setCellStyle(bold);
            if (report.subtitle() != null) {
                sheet.createRow(r++).createCell(0).setCellValue(report.subtitle());
            }
            r++;
            Row header = sheet.createRow(r++);
            for (int c = 0; c < report.columns().size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(report.columns().get(c).label());
                cell.setCellStyle(bold);
            }
            for (Map<String, Object> row : report.rows()) {
                Row x = sheet.createRow(r++);
                for (int c = 0; c < report.columns().size(); c++) {
                    Object v = row.get(report.columns().get(c).key());
                    Cell cell = x.createCell(c);
                    if (v instanceof Number n) cell.setCellValue(n.doubleValue());
                    else if (v != null) cell.setCellValue(v.toString());
                }
            }
            for (int c = 0; c < report.columns().size(); c++) sheet.autoSizeColumn(c);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel", e);
        }
    }

    public byte[] toPdf(Report report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(report.columns().size() > 5 ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(0x0f, 0x45, 0x38));
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font cabecera = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        doc.add(new Paragraph(report.title(), titulo));
        if (report.subtitle() != null) doc.add(new Paragraph(report.subtitle(), normal));
        doc.add(new Paragraph(" "));
        PdfPTable table = new PdfPTable(report.columns().size());
        table.setWidthPercentage(100);
        for (Report.Column col : report.columns()) {
            PdfPCell cell = new PdfPCell(new Phrase(col.label(), cabecera));
            cell.setBackgroundColor(new Color(0x1A, 0x6B, 0x5A));
            cell.setPadding(4);
            table.addCell(cell);
        }
        for (Map<String, Object> row : report.rows()) {
            for (Report.Column col : report.columns()) {
                Object v = row.get(col.key());
                PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : v.toString(), normal));
                cell.setPadding(3);
                if (v instanceof Number) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(cell);
            }
        }
        if (report.rows().isEmpty()) {
            PdfPCell vacio = new PdfPCell(new Phrase("Sin datos para los filtros elegidos", normal));
            vacio.setColspan(report.columns().size());
            table.addCell(vacio);
        }
        doc.add(table);
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Generado por Propero", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY)));
        doc.close();
        return out.toByteArray();
    }
}
