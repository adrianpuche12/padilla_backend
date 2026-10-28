package com.padilla.backend.controller;

import com.padilla.backend.entity.User;
import com.padilla.backend.enums.NotificationType;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.service.notification.NotificationService;
import com.padilla.backend.service.notification.NotificationService.Notice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** PRO-064: comunicados de la inmobiliaria a un grupo de roles (broadcast). */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
public class AnnouncementController {

    private static final Set<Role> DESTINATARIOS_VALIDOS = Set.of(Role.ADMIN, Role.MANAGER, Role.OWNER, Role.TENANT, Role.PROVIDER);

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final NotificationService notifications;

    @Value("${app.db.schema}")
    private String schema;

    public record AnnouncementRequest(@NotBlank @Size(max = 200) String titulo,
                                      @NotBlank @Size(max = 4000) String mensaje,
                                      @NotEmpty List<Role> roles) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> send(@Valid @RequestBody AnnouncementRequest req) {
        if (!DESTINATARIOS_VALIDOS.containsAll(req.roles())) {
            throw new BadRequestException("Roles destinatarios posibles: " + DESTINATARIOS_VALIDOS);
        }
        List<UUID> destinatarios = userRepository.findByRoleIn(req.roles()).stream()
                .filter(User::isActive).map(User::getId).toList();
        UUID id = UUID.randomUUID();
        String roles = req.roles().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        jdbc.update("INSERT INTO " + schema + ".announcements (id, titulo, mensaje, roles, destinatarios, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, req.titulo().trim(), req.mensaje().trim(), roles, destinatarios.size(), CurrentUser.id());
        notifications.notify(destinatarios, Notice.of(NotificationType.COMUNICADO, req.titulo().trim(), req.mensaje().trim(),
                "COMUNICADO", id));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "destinatarios", destinatarios.size()));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history() {
        return jdbc.queryForList("SELECT id, titulo, mensaje, roles, destinatarios, created_by AS \"createdBy\", "
                + "created_at AS \"createdAt\" FROM " + schema + ".announcements ORDER BY created_at DESC LIMIT 200");
    }
}
