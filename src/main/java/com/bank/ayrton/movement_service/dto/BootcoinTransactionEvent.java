package com.bank.ayrton.movement_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BootcoinTransactionEvent {
    private String transactionId;
    private String buyerWalletId;
    private String sellerWalletId;
    private Double amount;
    private TransferMethod transferMethod; // Enum con YANKI y ACCOUNT
}