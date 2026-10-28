package com.padilla.backend.controller;

import com.padilla.backend.service.listing.Listings;
import java.util.Set;
import org.springframework.data.domain.Sort;
import com.padilla.backend.util.Paging;
import com.padilla.backend.dto.PageResponse;

import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.dto.reserva.CreateReservaRequest;
import com.padilla.backend.dto.reserva.ReservaDTO;
import com.padilla.backend.dto.reserva.ReservaGaranteDTO;
import com.padilla.backend.entity.Reserva;
import com.padilla.backend.entity.ReservaGarante;
import com.padilla.backend.service.reserva.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final com.padilla.backend.service.reserva.ReservaDocumentService documentService;

    private final com.padilla.backend.repository.ReservaRepository reservaRepository;

    private final ReservaService reservaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<?> getReservas(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q) {
        // PRO-040: con ?page= devuelve una pagina (server-side); sin page, la lista completa como antes
        if (page != null) {
            String st = status == null ? null : com.padilla.backend.enums.ReservaStatus.parse(status).name();
            return ResponseEntity.ok(PageResponse.of(reservaRepository.findAll(Listings.reservas(st, q),
                    Paging.request(page, size, sort, Set.of("createdAt", "apellido", "monto"), Sort.by(Sort.Direction.DESC, "createdAt"))),
                    r -> toDTO(r, List.of())));
        }
        List<Reserva> reservas = reservaService.findAll(status);
        return ResponseEntity.ok(reservas.stream().map(r -> toDTO(r, List.of())).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<ReservaDTO> getReserva(@PathVariable UUID id) {
        return reservaService.findById(id)
                .map(r -> {
                    List<ReservaGarante> garantes = reservaService.findGarantes(id);
                    return ResponseEntity.ok(toDTO(r, garantes));
                })
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ReservaDTO> createReserva(@Valid @RequestBody CreateReservaRequest request) {
        Reserva created = reservaService.create(request);
        List<ReservaGarante> garantes = reservaService.findGarantes(created.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(created, garantes));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ReservaDTO> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        Reserva updated = reservaService.updateStatus(id, newStatus);
        return ResponseEntity.ok(toDTO(updated, List.of()));
    }

    /** PRO-082: datos para los documentos FC1/FC10/CTR (reserva + agencia). */
    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER')")
    public java.util.Map<String, Object> documents(@PathVariable UUID id) {
        return documentService.documentData(id);
    }

    /** PRO-083: documento en PDF generado en el servidor (fc1, fc10, ctr). */
    @GetMapping("/{id}/pdf/{doc}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, @PathVariable String doc) {
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.inline().filename(doc.toUpperCase() + "-" + id + ".pdf").build().toString())
                .body(documentService.pdf(id, doc));
    }

    /** PRO-082: contrato precargado desde una reserva APROBADA. */
    @PostMapping("/{id}/contract")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> contract(@PathVariable UUID id,
            @RequestBody com.padilla.backend.service.reserva.ReservaDocumentService.ContractFromReserva req) {
        var c = documentService.createContract(id, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(java.util.Map.of("contractId", c.getId(), "status", c.getStatus()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteReserva(@PathVariable UUID id) {
        reservaService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-dni")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> readDni(@RequestBody Map<String, String> body) {
        String base64 = body.get("base64");
        String mimeType = body.getOrDefault("mimeType", "image/jpeg");
        if (base64 == null || base64.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, String> result = reservaService.readDniWithAI(base64, mimeType);
        return ResponseEntity.ok(result);
    }

    private ReservaDTO toDTO(Reserva r, List<ReservaGarante> garantes) {
        List<ReservaGaranteDTO> garanteDTOs = garantes.stream()
                .map(g -> new ReservaGaranteDTO(
                        g.getId(), g.getReservaId(), g.getNombre(),
                        g.getDni(), g.getCuil(), g.getDomicilio(), g.getDescripcionBien(),
                        g.getApellidoMaterno(), g.getFechaNacimiento()
                ))
                .collect(Collectors.toList());

        return new ReservaDTO(
                r.getId(),
                r.getNombre(),
                r.getApellido(),
                r.getApellidoMaterno(),
                r.getDni(),
                r.getCuil(),
                r.getFechaNacimiento(),
                r.getDomicilioActual(),
                r.getTelefono(),
                r.getEmail(),
                r.getCondicionFiscal(),
                r.getProfesion(),
                r.getPropiedad(),
                r.getPropertyId(),
                r.getMonto(),
                r.getMontoInformes(),
                r.getTipoGarantia(),
                r.getOperadorId(),
                r.getStatus(),
                r.getCantidadPersonas(),
                r.getTieneCoinquilino(),
                r.getCoinquilinoNombre(),
                r.getTipoInmueble(),
                r.getAsesor(),
                r.getFechaRecibo(),
                r.getMedioPago(),
                r.getObservaciones(),
                r.getApoderadoLocativa(),
                r.getLocadorNombre(),
                r.getLocadorApellido(),
                r.getLocadorDni(),
                r.getLocadorDomicilio(),
                r.getCreatedAt(),
                garanteDTOs
        );
    }
}
