package com.padilla.backend.controller;

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

    private final ReservaService reservaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER')")
    public ResponseEntity<List<ReservaDTO>> getReservas(
            @RequestParam(required = false) String status) {
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
                .orElse(ResponseEntity.notFound().build());
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
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Reserva updated = reservaService.updateStatus(id, newStatus);
        return ResponseEntity.ok(toDTO(updated, List.of()));
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
                        g.getDni(), g.getCuil(), g.getDomicilio(), g.getDescripcionBien()
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
                r.getCreatedAt(),
                garanteDTOs
        );
    }
}
