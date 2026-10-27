package com.padilla.backend.exception;

import com.padilla.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-006: los errores salen con status correcto y cuerpo {status, error, message, path, timestamp}. */
@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static JwtRequestPostProcessor como(String rol) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("email", UUID.randomUUID() + "@propero.test"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void recursoInexistenteDa404ConMensaje() throws Exception {
        mvc.perform(get("/api/tickets/" + UUID.randomUUID()).with(como("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").exists());
    }

    @Test
    void rolSinPermisoDa403() throws Exception {
        mvc.perform(get("/api/users").with(como("TENANT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("No tenes permiso para esta accion"));
    }

    @Test
    void cuerpoMalformadoDa400() throws Exception {
        mvc.perform(post("/api/tickets").with(como("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void sinTokenDa401() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
    }
}
