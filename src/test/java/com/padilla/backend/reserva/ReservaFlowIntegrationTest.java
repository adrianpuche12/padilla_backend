package com.padilla.backend.reserva;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padilla.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-014: guardar reserva sin 500 y estados validados. */
@AutoConfigureMockMvc
class ReservaFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private static final String RESERVA = """
            {"nombre":"Ana","apellido":"Perez","dni":"30111222","propiedad":"Calle Falsa 123",
             "monto":150000,"garantes":[{"nombre":"Juan Garante","dni":"20111222"}]}""";

    private UUID crear(UUID admin) throws Exception {
        String r = mvc.perform(post("/api/reservas").with(as(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(RESERVA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(r);
        return UUID.fromString(node.get("id").asText());
    }

    private void estado(UUID reserva, UUID admin, String estado, int esperado) throws Exception {
        mvc.perform(patch("/api/reservas/" + reserva + "/status").with(as(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + estado + "\"}"))
                .andExpect(status().is(esperado));
    }

    @Test
    void adminSoloDeKeycloakGuardaReservaSin500() throws Exception {
        crear(UUID.randomUUID());
    }

    @Test
    void transicionesDeEstado() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID reserva = crear(admin);
        estado(reserva, admin, "CUALQUIERA", 400);
        estado(reserva, admin, "aprobada", 200);
        estado(reserva, admin, "RECHAZADA", 409);
        estado(reserva, admin, "CANCELADA", 200);
        estado(reserva, admin, "APROBADA", 409);
    }
}
