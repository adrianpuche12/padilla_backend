package com.padilla.backend.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados del ticket (decision D4: los 5 del MVP + CANCELADA).
 * PRO-012: las transiciones validas viven aca; antes se podia pasar de cualquier estado a cualquiera.
 *
 * ABIERTA -> EN_PROCESO | RECHAZADA | CANCELADA
 * EN_PROCESO -> RESUELTA | CANCELADA
 * RESUELTA -> CERRADA | EN_PROCESO (reapertura)
 * CERRADA, RECHAZADA, CANCELADA: finales
 */
public enum TicketStatus {
    ABIERTA,
    EN_PROCESO,
    RESUELTA,
    CERRADA,
    RECHAZADA,
    CANCELADA;

    private static final Map<TicketStatus, Set<TicketStatus>> TRANSICIONES = Map.of(
            ABIERTA, EnumSet.of(EN_PROCESO, RECHAZADA, CANCELADA),
            EN_PROCESO, EnumSet.of(RESUELTA, CANCELADA),
            RESUELTA, EnumSet.of(CERRADA, EN_PROCESO),
            CERRADA, EnumSet.noneOf(TicketStatus.class),
            RECHAZADA, EnumSet.noneOf(TicketStatus.class),
            CANCELADA, EnumSet.noneOf(TicketStatus.class));

    public boolean canTransitionTo(TicketStatus target) {
        return TRANSICIONES.get(this).contains(target);
    }

    public boolean isFinal() {
        return TRANSICIONES.get(this).isEmpty();
    }

    public Set<TicketStatus> nextStates() {
        return TRANSICIONES.get(this);
    }
}
