package com.padilla.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SellerSummaryDTO {
    private Integer id;
    private String fullname;
    private Integer latestAssignee;
}