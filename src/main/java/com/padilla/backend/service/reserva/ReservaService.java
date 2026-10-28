package com.padilla.backend.service.reserva;


import com.padilla.backend.enums.ReservaStatus;
import com.padilla.backend.exception.ConflictException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.security.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padilla.backend.dto.reserva.CreateReservaRequest;
import com.padilla.backend.entity.Reserva;
import com.padilla.backend.entity.ReservaGarante;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.ReservaGaranteRepository;
import com.padilla.backend.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private final com.padilla.backend.service.audit.AuditService auditService;

    private final ReservaRepository reservaRepository;
    private final ReservaGaranteRepository reservaGaranteRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    public List<Reserva> findAll(String status) {
        Role callerRole = CurrentUser.role();
        if (isAdmin(callerRole)) {
            if (status != null) return reservaRepository.findByStatusOrderByCreatedAtDesc(ReservaStatus.parse(status).name());
            return reservaRepository.findAllByOrderByCreatedAtDesc();
        }
        UUID callerId = CurrentUser.id();
        return reservaRepository.findByOperadorIdOrderByCreatedAtDesc(callerId);
    }

    public Optional<Reserva> findById(UUID id) {
        Role callerRole = CurrentUser.role();
        Optional<Reserva> reserva = reservaRepository.findById(id);
        if (isAdmin(callerRole)) return reserva;
        UUID callerId = CurrentUser.id();
        return reserva.filter(r -> callerId.equals(r.getOperadorId()));
    }

    public List<ReservaGarante> findGarantes(UUID reservaId) {
        findById(reservaId).orElseThrow(() -> new NotFoundException("Reserva no encontrada: " + reservaId));
        return reservaGaranteRepository.findByReservaId(reservaId);
    }

    @Transactional
    public Reserva create(CreateReservaRequest req) {
        Role callerRole = CurrentUser.role();
        if (!isAdmin(callerRole)) {
            throw new RbacException("Only admin roles can create reservas");
        }

        Reserva reserva = new Reserva();
        reserva.setNombre(req.getNombre());
        reserva.setApellido(req.getApellido());
        reserva.setApellidoMaterno(req.getApellidoMaterno());
        reserva.setDni(req.getDni());
        reserva.setCuil(req.getCuil());
        reserva.setFechaNacimiento(req.getFechaNacimiento());
        reserva.setDomicilioActual(req.getDomicilioActual());
        reserva.setTelefono(req.getTelefono());
        reserva.setEmail(req.getEmail());
        reserva.setCondicionFiscal(req.getCondicionFiscal());
        reserva.setProfesion(req.getProfesion());
        reserva.setPropiedad(req.getPropiedad());
        reserva.setPropertyId(req.getPropertyId());
        reserva.setMonto(req.getMonto());
        reserva.setMontoInformes(req.getMontoInformes());
        reserva.setTipoGarantia(req.getTipoGarantia());
        reserva.setOperadorId(CurrentUser.id());
        reserva.setCantidadPersonas(req.getCantidadPersonas());
        reserva.setTieneCoinquilino(req.getTieneCoinquilino() != null ? req.getTieneCoinquilino() : false);
        reserva.setCoinquilinoNombre(req.getCoinquilinoNombre());
        reserva.setTipoInmueble(req.getTipoInmueble());
        reserva.setAsesor(req.getAsesor());
        reserva.setFechaRecibo(req.getFechaRecibo());
        reserva.setMedioPago(req.getMedioPago());
        reserva.setObservaciones(req.getObservaciones());
        reserva.setApoderadoLocativa(req.getApoderadoLocativa());
        reserva.setLocadorNombre(req.getLocadorNombre());
        reserva.setLocadorApellido(req.getLocadorApellido());
        reserva.setLocadorDni(req.getLocadorDni());
        reserva.setLocadorDomicilio(req.getLocadorDomicilio());
        reserva.setStatus(ReservaStatus.PENDIENTE.name());

        Reserva saved = reservaRepository.save(reserva);

        if (req.getGarantes() != null) {
            for (CreateReservaRequest.GaranteInput g : req.getGarantes()) {
                ReservaGarante garante = new ReservaGarante();
                garante.setReservaId(saved.getId());
                garante.setNombre(g.getNombre());
                garante.setDni(g.getDni());
                garante.setCuil(g.getCuil());
                garante.setDomicilio(g.getDomicilio());
                garante.setDescripcionBien(g.getDescripcionBien());
                garante.setApellidoMaterno(g.getApellidoMaterno());
                garante.setFechaNacimiento(g.getFechaNacimiento());
                reservaGaranteRepository.save(garante);
            }
        }

        return saved;
    }

    @Transactional
    /** PRO-014: estado validado contra ReservaStatus y sus transiciones (antes texto libre). */
    public Reserva updateStatus(UUID id, String newStatus) {
        validateIsAdmin();
        ReservaStatus target = ReservaStatus.parse(newStatus);
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada: " + id));
        ReservaStatus current = ReservaStatus.parse(reserva.getStatus());
        if (current == target) {
            return reserva;
        }
        if (!current.canTransitionTo(target)) {
            throw new ConflictException("No se puede pasar una reserva de " + current + " a " + target
                    + ". Estados posibles: " + current.nextStates());
        }
        auditService.record("RESERVA_ESTADO", "RESERVA", reserva.getId(), current + " -> " + target);
        reserva.setStatus(target.name());
        return reservaRepository.save(reserva);
    }

    @Transactional
    public void delete(UUID id) {
        validateIsAdmin();
        reservaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada: " + id));
        reservaGaranteRepository.deleteByReservaId(id);
        reservaRepository.deleteById(id);
    }

    /**
     * Calls OpenAI Vision API to extract DNI data from a base64-encoded image.
     * Returns a map of extracted fields: nombre, apellido, dni, cuil, fechaNacimiento, domicilio.
     */
    public Map<String, String> readDniWithAI(String base64Image, String mimeType) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            throw new RuntimeException("OpenAI API key not configured");
        }

        String prompt = """
                Eres un asistente que extrae datos de DNIs argentinos.
                Analiza la imagen del DNI y devuelve un JSON con exactamente estas claves:
                nombre, apellido, dni, cuil, fecha_nacimiento (formato YYYY-MM-DD), domicilio.
                Si no encuentras algún dato, usa null. Solo devuelve el JSON, sin texto adicional.
                """;

        Map<String, Object> imageUrl = Map.of(
                "url", "data:" + mimeType + ";base64," + base64Image
        );
        Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrl);
        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-4o");
        body.put("max_tokens", 300);
        body.put("messages", List.of(message));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/chat/completions",
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").get(0).path("message").path("content").asText();

            // Strip markdown code fences if present
            content = content.replaceAll("(?s)```(?:json)?\\s*", "").replaceAll("```", "").trim();

            JsonNode parsed = objectMapper.readTree(content);
            Map<String, String> result = new LinkedHashMap<>();
            parsed.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText()));
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Error calling OpenAI Vision: " + e.getMessage(), e);
        }
    }

    // --- RBAC helpers ---

    private boolean isAdmin(Role role) {
        return role.level() <= Role.ADMIN.level();
    }

    private void validateIsAdmin() {
        if (!isAdmin(CurrentUser.role())) {
            throw new RbacException("Only admin roles can perform this action");
        }
    }


}
