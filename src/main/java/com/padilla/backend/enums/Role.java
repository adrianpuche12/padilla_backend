package com.padilla.backend.enums;

/**
 * Roles de Propero en cascada: SUPER_ADMIN(0) > MANAGER(1) > ADMIN(2) > OWNER/TENANT/PROVIDER(3).
 * PRO-005: el nivel vive aca (antes cada service copiaba su propio mapa ROLE_LEVEL).
 */
public enum Role {
    SUPER_ADMIN(0),
    MANAGER(1),
    ADMIN(2),
    OWNER(3),
    TENANT(3),
    PROVIDER(3);

    private final int level;

    Role(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /** SUPER_ADMIN, MANAGER o ADMIN: personal de la inmobiliaria. */
    public boolean isManagement() {
        return level <= ADMIN.level;
    }

    /** Regla de la cascada: solo se gestiona a roles de nivel estrictamente inferior. */
    public boolean canManage(Role target) {
        return level < target.level;
    }
}
