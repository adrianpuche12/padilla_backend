package com.padilla.backend.service.dashboard;

import com.padilla.backend.enums.Role;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PRO-070 (Widget Registry): cada widget del dashboard es un @Component que implementa esta interfaz.
 * El desarrollador construye capacidades; el usuario elige que ver. WidgetRegistryService los
 * descubre y sincroniza la tabla dashboard_widgets al arrancar.
 *
 * fetchData corre dentro de una transaccion con la agencia fijada (RLS) y con el usuario del request.
 */
public interface DashboardWidget {

    Metadata metadata();

    Object fetchData(UUID agencyId, Map<String, String> params);

    record Metadata(String code, String title, String description, String category, int categoryOrder,
                    int widgetOrder, Set<Role> allowedRoles, Set<Role> enabledByDefaultFor) {}
}
