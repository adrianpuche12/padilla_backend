package com.padilla.backend.service.storage;

import com.padilla.backend.entity.Ticket;
import com.padilla.backend.entity.User;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.security.AgencyContext;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.service.contract.ContractService;
import com.padilla.backend.service.property.PropertyService;
import com.padilla.backend.service.reserva.ReservaService;
import com.padilla.backend.service.ticket.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PRO-046 / PRO-050: adjuntos de tickets, contratos, propiedades, clientes, proveedores y reservas.
 * Cada adjunto se ve con la misma regla que su entidad (si no ves el ticket, no ves sus fotos).
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    public static final Set<String> ENTITY_TYPES = Set.of("TICKET", "CONTRATO", "PROPIEDAD", "CLIENTE", "PROVEEDOR", "RESERVA");
    public static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "application/pdf");
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private final StorageService storage;
    private final JdbcTemplate jdbc;
    private final TicketService ticketService;
    private final PropertyService propertyService;
    private final ContractService contractService;
    private final ReservaService reservaService;
    private final UserRepository userRepository;

    @Value("${app.db.schema}")
    private String schema;

    public record Download(String nombre, String contentType, long size, InputStream content) {}

    private static final String COLS = "id, entity_type AS \"entityType\", entity_id AS \"entityId\", nombre, "
            + "content_type AS \"contentType\", size_bytes AS \"sizeBytes\", descripcion, vence, "
            + "uploaded_by AS \"uploadedBy\", created_at AS \"createdAt\"";

    @Transactional
    public Map<String, Object> upload(String entityType, UUID entityId, MultipartFile file, String descripcion, LocalDate vence) {
        String type = normalize(entityType);
        requireCanUpload(type, entityId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo esta vacio");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("El archivo supera los 10 MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BadRequestException("Tipo de archivo no permitido (" + contentType + "). Se aceptan imagenes y PDF.");
        }
        UUID id = UUID.randomUUID();
        String key = AgencyContext.require() + "/" + type.toLowerCase(Locale.ROOT) + "/" + entityId + "/" + id;
        try (InputStream in = file.getInputStream()) {
            storage.store(key, in, file.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
        String nombre = sanitize(file.getOriginalFilename());
        jdbc.update("INSERT INTO " + schema + ".attachments (id, entity_type, entity_id, nombre, content_type, size_bytes, "
                        + "storage_key, descripcion, vence, uploaded_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, type, entityId, nombre, contentType, file.getSize(), key, descripcion, vence, CurrentUser.id());
        return jdbc.queryForMap("SELECT " + COLS + " FROM " + schema + ".attachments WHERE id = ?", id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String entityType, UUID entityId) {
        String type = normalize(entityType);
        requireCanView(type, entityId);
        return jdbc.queryForList("SELECT " + COLS + " FROM " + schema + ".attachments WHERE entity_type = ? AND entity_id = ? "
                + "ORDER BY created_at DESC", type, entityId);
    }

    @Transactional(readOnly = true)
    public Download download(UUID id) {
        Map<String, Object> a = find(id);
        requireCanView((String) a.get("entity_type"), (UUID) a.get("entity_id"));
        return new Download((String) a.get("nombre"), (String) a.get("content_type"), ((Number) a.get("size_bytes")).longValue(),
                storage.load((String) a.get("storage_key")));
    }

    @Transactional
    public void delete(UUID id) {
        Map<String, Object> a = find(id);
        boolean propio = CurrentUser.id().equals(a.get("uploaded_by"));
        if (!CurrentUser.isManagement() && !propio) {
            throw new RbacException("Solo la inmobiliaria o quien lo subio puede borrar un adjunto");
        }
        jdbc.update("DELETE FROM " + schema + ".attachments WHERE id = ?", id);
        storage.delete((String) a.get("storage_key"));
    }

    // --- Reglas de acceso ---

    private void requireCanView(String type, UUID entityId) {
        if (!canView(type, entityId)) {
            throw new NotFoundException("No encontrado: " + type.toLowerCase(Locale.ROOT) + " " + entityId);
        }
    }

    private boolean canView(String type, UUID entityId) {
        return switch (type) {
            case "TICKET" -> ticketService.findById(entityId).isPresent();
            case "PROPIEDAD" -> propertyService.findById(entityId).isPresent();
            case "CONTRATO" -> contractService.findById(entityId).isPresent();
            case "RESERVA" -> reservaService.findById(entityId).isPresent();
            case "CLIENTE", "PROVEEDOR" -> userRepository.findById(entityId).isPresent()
                    && (CurrentUser.isManagement() || CurrentUser.id().equals(entityId));
            default -> false;
        };
    }

    private void requireCanUpload(String type, UUID entityId) {
        requireCanView(type, entityId);
        if (CurrentUser.isManagement()) {
            return;
        }
        Role role = CurrentUser.role();
        boolean ok = switch (type) {
            // el inquilino suma fotos a su pedido y el proveedor a su trabajo
            case "TICKET" -> {
                Ticket t = ticketService.findById(entityId).orElseThrow();
                yield (role == Role.TENANT && CurrentUser.id().equals(t.getTenantId()))
                        || (role == Role.PROVIDER && CurrentUser.id().equals(t.getProviderId()));
            }
            // el proveedor carga sus certificados y habilitaciones
            case "PROVEEDOR" -> role == Role.PROVIDER && CurrentUser.id().equals(entityId)
                    && userRepository.findById(entityId).map(User::getRole).orElse(null) == Role.PROVIDER;
            default -> false;
        };
        if (!ok) {
            throw new RbacException("No podes adjuntar archivos aca");
        }
    }

    private Map<String, Object> find(UUID id) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT * FROM " + schema + ".attachments WHERE id = ?", id);
        if (r.isEmpty()) throw new NotFoundException("Adjunto no encontrado: " + id);
        return r.get(0);
    }

    private static String normalize(String entityType) {
        String t = entityType == null ? "" : entityType.toUpperCase(Locale.ROOT);
        if (!ENTITY_TYPES.contains(t)) {
            throw new BadRequestException("Tipo de entidad invalido: " + entityType + ". Valores: " + ENTITY_TYPES);
        }
        return t;
    }

    private static String sanitize(String name) {
        String n = name == null || name.isBlank() ? "archivo" : name;
        n = n.replaceAll("[\\\\/\\r\\n\"]", "_");
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }
}
