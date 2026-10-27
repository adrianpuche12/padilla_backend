package com.padilla.backend.enums;

import com.padilla.backend.exception.BadRequestException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * PRO-014 (decision D8): estados de una reserva y transiciones validas.
 * Antes el estado era texto libre.
 *
 * PENDIENTE -> APROBADA | RECHAZADA | CANCELADA
 * APROBADA  -> CANCELADA (y puede generar un contrato, PRO-082)
 * RECHAZADA, CANCELADA: finales
 */
public enum ReservaStatus {
    PENDIENTE,
    APROBADA,
    RECHAZADA,
    CANCELADA;

    private static final Map<ReservaStatus, Set<ReservaStatus>> TRANSICIONES = Map.of(
            PENDIENTE, EnumSet.of(APROBADA, RECHAZADA, CANCELADA),
            APROBADA, EnumSet.of(CANCELADA),
            RECHAZADA, EnumSet.noneOf(ReservaStatus.class),
            CANCELADA, EnumSet.noneOf(ReservaStatus.class));

    public boolean canTransitionTo(ReservaStatus target) {
        return TRANSICIONES.get(this).contains(target);
    }

    public Set<ReservaStatus> nextStates() {
        return TRANSICIONES.get(this);
    }

    /** Convierte texto a estado con un mensaje claro (400) si no existe. */
    public static ReservaStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("El estado de la reserva es obligatorio");
        }
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Estado de reserva invalido: " + value + ". Valores posibles: " + Arrays.toString(values())));
    }
}
