package com.padilla.backend.controller;


import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.dto.client.ClientDTO;
import com.padilla.backend.dto.client.CreateClientRequest;
import com.padilla.backend.dto.client.UpdateClientRequest;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.service.client.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<ClientDTO>> getClients(
            @RequestParam(required = false) Role role) {
        List<ClientDTO> clients = (role != null)
                ? clientService.findByRole(role)
                : clientService.findAll();
        return ResponseEntity.ok(clients);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ClientDTO> getClient(@PathVariable UUID id) {
        return clientService.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ClientDTO> createClient(@Valid @RequestBody CreateClientRequest request) {
        ClientDTO created = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ClientDTO> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {
        ClientDTO updated = clientService.updateClient(id, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deactivateClient(@PathVariable UUID id) {
        clientService.deactivateClient(id);
        return ResponseEntity.ok().build();
    }

    /**
     * PRO-033 (decision D5): DELETE es baja logica (antes borraba de la base y de Keycloak).
     * El borrado definitivo queda en DELETE /{id}/permanent, solo SUPER_ADMIN.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id) {
        clientService.deactivateClient(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteClientPermanently(@PathVariable UUID id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ClientDTO> reactivateClient(@PathVariable UUID id) {
        ClientDTO reactivated = clientService.reactivateClient(id);
        return ResponseEntity.ok(reactivated);
    }


}
