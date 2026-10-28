package com.padilla.backend.controller;

import com.padilla.backend.service.ticket.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PRO-053: presupuestos de un ticket. */
@RestController
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgets;

    public record BudgetRequest(BigDecimal monto, String detalle) {}

    public record DecisionRequest(String motivo) {}

    @PostMapping("/api/tickets/{ticketId}/budgets")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Map<String, Object>> submit(@PathVariable UUID ticketId, @RequestBody BudgetRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(budgets.submit(ticketId, req.monto(), req.detalle()));
    }

    @GetMapping("/api/tickets/{ticketId}/budgets")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'PROVIDER')")
    public List<Map<String, Object>> list(@PathVariable UUID ticketId) {
        return budgets.list(ticketId);
    }

    @PatchMapping("/api/budgets/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER')")
    public Map<String, Object> approve(@PathVariable UUID id) {
        return budgets.decide(id, true, null);
    }

    @PatchMapping("/api/budgets/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER')")
    public Map<String, Object> reject(@PathVariable UUID id, @RequestBody DecisionRequest req) {
        return budgets.decide(id, false, req.motivo());
    }
}
