package com.padilla.backend.service.auth;

import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** PRO-110: alta en Keycloak sin huerfanos y errores de validacion como 400 (Keycloak simulado). */
@DisplayName("KeycloakAdminService - alta de usuario")
class KeycloakAdminServiceTest {

    private static final String KC = "http://kc";
    private static final String USER_ID = "11111111-2222-3333-4444-555555555555";

    private KeycloakAdminService service;
    private MockRestServiceServer kc;

    @BeforeEach
    void setUp() {
        service = new KeycloakAdminService();
        ReflectionTestUtils.setField(service, "serverUrl", KC);
        ReflectionTestUtils.setField(service, "realm", "padilla");
        ReflectionTestUtils.setField(service, "adminUsername", "tecnico");
        ReflectionTestUtils.setField(service, "adminPassword", "x");
        ReflectionTestUtils.setField(service, "kcClientId", "padilla_frontend");
        kc = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();
        kc.expect(requestTo(KC + "/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"t\"}", MediaType.APPLICATION_JSON));
    }

    private void expectUserCreated() {
        HttpHeaders h = new HttpHeaders();
        h.setLocation(URI.create(KC + "/admin/realms/padilla/users/" + USER_ID));
        kc.expect(requestTo(KC + "/admin/realms/padilla/users")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).headers(h));
        kc.expect(requestTo(KC + "/admin/realms/padilla/clients?clientId=padilla_frontend"))
                .andRespond(withSuccess("[{\"id\":\"cid\"}]", MediaType.APPLICATION_JSON));
    }

    @Test
    void alta_completa_asigna_el_client_role() {
        expectUserCreated();
        kc.expect(requestTo(KC + "/admin/realms/padilla/clients/cid/roles/TENANT"))
                .andRespond(withSuccess("{\"id\":\"r\",\"name\":\"TENANT\"}", MediaType.APPLICATION_JSON));
        kc.expect(requestTo(KC + "/admin/realms/padilla/users/" + USER_ID + "/role-mappings/clients/cid"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(service.createUser("Ana Pérez", "ana@example.com", Role.TENANT)).isEqualTo(USER_ID);
        kc.verify();
    }

    @Test
    void si_falta_el_rol_borra_el_usuario_creado_y_lo_explica() {
        expectUserCreated();
        kc.expect(requestTo(KC + "/admin/realms/padilla/clients/cid/roles/TENANT"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        kc.expect(requestTo(KC + "/admin/realms/padilla/users/" + USER_ID)).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatThrownBy(() -> service.createUser("Ana Pérez", "ana@example.com", Role.TENANT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no tiene configurado el rol TENANT");
        kc.verify(); // el DELETE compensatorio se hizo
    }

    @Test
    void nombre_invalido_es_un_400_claro() {
        kc.expect(requestTo(KC + "/admin/realms/padilla/users")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"field\":\"firstName\",\"errorMessage\":\"error-person-name-invalid-character\"}"));

        assertThatThrownBy(() -> service.createUser("Ana (prueba)", "ana@example.com", Role.TENANT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("caracteres no permitidos");
        kc.verify();
    }

    @Test
    void mensajes_de_validacion() {
        assertThat(KeycloakAdminService.badRequestMessage("{\"errorMessage\":\"error-invalid-email\"}")).isEqualTo("El email no es válido");
        assertThat(KeycloakAdminService.badRequestMessage(null)).isEqualTo("El sistema de acceso rechazó los datos del usuario");
    }
}
