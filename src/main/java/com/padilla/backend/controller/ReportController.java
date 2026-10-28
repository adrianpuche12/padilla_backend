package com.padilla.backend.controller;

import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.service.report.Report;
import com.padilla.backend.service.report.ReportExporter;
import com.padilla.backend.service.report.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** PRO-072: reportes en JSON, Excel (format=xlsx) o PDF (format=pdf). */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER')")
public class ReportController {

    private final ReportService reports;
    private final ReportExporter exporter;

    @GetMapping("/api/reports/{tipo}")
    public ResponseEntity<?> report(@PathVariable String tipo,
                                    @RequestParam(defaultValue = "json") String format,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) UUID propertyId,
                                    @RequestParam(required = false) UUID ownerId,
                                    @RequestParam(required = false) UUID providerId,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) BigDecimal minCost,
                                    @RequestParam(required = false) BigDecimal maxCost,
                                    @RequestParam(required = false) Integer minRating,
                                    @RequestParam(required = false) Integer year) {
        Report r = reports.build(tipo, new ReportService.Filters(from, to, propertyId, ownerId, providerId, category,
                minCost, maxCost, minRating, year));
        String archivo = "propero-" + tipo + "-" + LocalDate.now();
        return switch (format.toLowerCase()) {
            case "json" -> ResponseEntity.ok(r);
            case "xlsx" -> file(exporter.toXlsx(r), archivo + ".xlsx",
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            case "pdf" -> file(exporter.toPdf(r), archivo + ".pdf", MediaType.APPLICATION_PDF);
            default -> throw new BadRequestException("Formato invalido: " + format + " (json, xlsx o pdf)");
        };
    }

    private static ResponseEntity<byte[]> file(byte[] content, String name, MediaType type) {
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(content);
    }
}
