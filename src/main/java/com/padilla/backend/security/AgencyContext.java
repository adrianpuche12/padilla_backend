package com.padilla.backend.security;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * PRO-021: agencia (inmobiliaria) del request en curso. La fija AgencyInterceptor y la usa
 * AgencyAwareJpaTransactionManager para ejecutar SET LOCAL app.current_agency en cada transaccion.
 */
public final class AgencyContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private AgencyContext() {
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Agencia del request; 403 si no hay (por ejemplo, usuario sin inmobiliaria). */
    public static UUID require() {
        return current().orElseThrow(() ->
                new com.padilla.backend.exception.RbacException("El usuario no pertenece a ninguna inmobiliaria"));
    }

    public static void set(UUID agencyId) {
        CURRENT.set(agencyId);
    }

    public static void clear() {
        CURRENT.remove();
        BYPASS.remove();
    }

    public static boolean isBypass() {
        return BYPASS.get();
    }

    /** Ejecuta con una agencia fijada (jobs programados, onboarding) y restaura la anterior. */
    public static <T> T callAs(UUID agencyId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.set(agencyId);
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    /**
     * Marca el hilo como "sin filtro de agencia". Solo tiene efecto en transacciones que EMPIEZAN
     * dentro del bloque: usar RlsBypass, que abre una transaccion nueva.
     * Ejecuta sin filtro de agencia (app.rls_bypass). Solo para operaciones de plataforma:
     * resolver la agencia de un usuario, alta de inmobiliarias, jobs que recorren todas las agencias.
     */
    public static <T> T callBypassingRls(Supplier<T> action) {
        boolean previous = BYPASS.get();
        BYPASS.set(true);
        try {
            return action.get();
        } finally {
            BYPASS.set(previous);
        }
    }
}
