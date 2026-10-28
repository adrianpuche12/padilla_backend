package com.padilla.backend.enums;

/**
 * PRO-025: planes de Propero y sus limites (Vision Estrategica §6: Starter / Pro / Scale).
 * null = sin limite. Los valores son iniciales y se ajustan aca.
 */
public enum Plan {
    STARTER(150, 2),
    PRO(2000, 10),
    SCALE(null, null);

    private final Integer maxProperties;
    private final Integer maxAdmins;

    Plan(Integer maxProperties, Integer maxAdmins) {
        this.maxProperties = maxProperties;
        this.maxAdmins = maxAdmins;
    }

    public Integer maxProperties() {
        return maxProperties;
    }

    public Integer maxAdmins() {
        return maxAdmins;
    }
}
