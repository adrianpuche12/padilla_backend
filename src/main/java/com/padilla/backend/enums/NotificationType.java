package com.padilla.backend.enums;

/** PRO-060: tipos de notificacion (ARQ 4.1). */
public enum NotificationType {
    // Tickets
    TICKET_CREADO,
    TICKET_ASIGNADO,
    TICKET_EN_PROGRESO,
    TICKET_COMPLETADO,
    TICKET_CERRADO,
    TICKET_RECHAZADO,
    TICKET_CANCELADO,
    TICKET_ESCALADO,
    TICKET_COMENTARIO,
    // Presupuestos
    PRESUPUESTO_RECIBIDO,
    PRESUPUESTO_APROBADO,
    PRESUPUESTO_RECHAZADO,
    // Otros
    CONTRATO_POR_VENCER,
    CONTRATO_VENCIDO,
    FEEDBACK_RECIBIDO,
    FEEDBACK_NEGATIVO,
    SLA_INCUMPLIDO,
    RESUMEN_DIARIO,
    RESUMEN_SEMANAL,
    COMUNICADO,
    SISTEMA_ALERTA;

    /** Tipos que no se pueden silenciar (ARQ 4.1, preferencias globales). */
    public boolean isMandatory() {
        return this == TICKET_RECHAZADO || this == CONTRATO_POR_VENCER || this == SLA_INCUMPLIDO
                || this == TICKET_ESCALADO || this == SISTEMA_ALERTA;
    }
}
