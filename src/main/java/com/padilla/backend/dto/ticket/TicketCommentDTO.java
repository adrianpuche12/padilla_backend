package com.padilla.backend.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketCommentDTO {
    private UUID id;
    private UUID ticketId;
    private UUID authorId;

    @NotBlank(message = "El contenido es obligatorio")
    private String content;

    private boolean internal;
    private OffsetDateTime createdAt;
}
