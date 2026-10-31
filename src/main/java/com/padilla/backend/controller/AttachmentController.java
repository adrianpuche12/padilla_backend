package com.padilla.backend.controller;

import com.padilla.backend.service.storage.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PRO-046 / PRO-050: adjuntos. Tambien en /api/tickets/{id}/attachments para las fotos de un ticket. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT', 'PROVIDER')")
public class AttachmentController {

    private final AttachmentService attachments;

    @GetMapping("/api/attachments")
    public List<Map<String, Object>> list(@RequestParam String entityType, @RequestParam UUID entityId) {
        return attachments.list(entityType, entityId);
    }

    @PostMapping(value = "/api/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam String entityType, @RequestParam UUID entityId,
                                                      @RequestPart("file") MultipartFile file,
                                                      @RequestParam(required = false) String descripcion,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vence) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachments.upload(entityType, entityId, file, descripcion, vence));
    }

    @GetMapping("/api/tickets/{ticketId}/attachments")
    public List<Map<String, Object>> listTicket(@PathVariable UUID ticketId) {
        return attachments.list("TICKET", ticketId);
    }

    @PostMapping(value = "/api/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadTicket(@PathVariable UUID ticketId, @RequestPart("file") MultipartFile file,
                                                            @RequestParam(required = false) String descripcion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachments.upload("TICKET", ticketId, file, descripcion, null));
    }

    /** PRO-120: con Range responde 206 por partes (la app reproduce videos sin bajarlos enteros). */
    @GetMapping("/api/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        AttachmentService.Download d = attachments.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(d.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(d.resource());
    }

    @DeleteMapping("/api/attachments/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        attachments.delete(id);
        return ResponseEntity.noContent().build();
    }
}
