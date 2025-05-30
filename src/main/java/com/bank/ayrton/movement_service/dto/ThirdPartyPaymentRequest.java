package com.bank.ayrton.movement_service.dto;

import lombok.Data;

@Data
public class ThirdPartyPaymentRequest {
    private String fromProductId;
    private String toProductId;
    private Double amount;
}