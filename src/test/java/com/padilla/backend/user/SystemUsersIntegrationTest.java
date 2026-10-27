package com.padilla.backend.user;

import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-013 / PRO-016: pantalla Usuarios y perfil para todos los roles. */
@AutoConfigureMockMvc
class SystemUsersIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    @Test
    void adminVeLosUsuariosDeSistemaEnSoloLectura() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID otroAdmin = data.user("ADMIN");
        UUID manager = data.user("MANAGER");

        mvc.perform(get("/api/users").param("scope", "system").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(otroAdmin.toString())))
                .andExpect(jsonPath("$[*].id", hasItem(manager.toString())))
                .andExpect(jsonPath("$[?(@.id == '" + admin + "')].canManage").value(false))
                .andExpect(jsonPath("$[?(@.id == '" + otroAdmin + "')].canManage").value(false))
                .andExpect(jsonPath("$[?(@.role == 'TENANT')]").doesNotExist());
    }

    @Test
    void superAdminPuedeGestionarManagersYAdminsPeroNoASiMismo() throws Exception {
        UUID superAdmin = data.user("SUPER_ADMIN");
        UUID manager = data.user("MANAGER");

        mvc.perform(get("/api/users").param("scope", "system").with(as(superAdmin, "SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + manager + "')].canManage").value(true))
                .andExpect(jsonPath("$[?(@.id == '" + superAdmin + "')].canManage").value(false));
    }

    @Test
    void perfilDisponibleParaTodosLosRoles() throws Exception {
        for (String rol : new String[]{"SUPER_ADMIN", "MANAGER", "ADMIN", "OWNER", "TENANT", "PROVIDER"}) {
            mvc.perform(get("/api/user/profile").with(as(UUID.randomUUID(), rol))).andExpect(status().isOk());
        }
    }
}
