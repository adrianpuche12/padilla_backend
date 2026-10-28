package com.padilla.backend.enums;

/**
 * Categorias de ticket. PRO-041: se suman ALBANILERIA y JARDINERIA, y cada categoria sabe que
 * especialidad de proveedor la resuelve (antes no habia mapeo, contradiccion C2 de la spec).
 */
public enum TicketCategory {
    PLOMERIA(ProviderSpecialty.PLOMERO),
    ELECTRICIDAD(ProviderSpecialty.ELECTRICISTA),
    PINTURA(ProviderSpecialty.PINTOR),
    CERRAJERIA(ProviderSpecialty.CERRAJERO),
    GAS(ProviderSpecialty.GASISTA),
    ALBANILERIA(ProviderSpecialty.ALBANIL),
    JARDINERIA(ProviderSpecialty.JARDINERO),
    OTRO(null);

    private final ProviderSpecialty specialty;

    TicketCategory(ProviderSpecialty specialty) {
        this.specialty = specialty;
    }

    /** Especialidad que resuelve esta categoria; null = cualquier proveedor (OTRO). */
    public ProviderSpecialty specialty() {
        return specialty;
    }
}
