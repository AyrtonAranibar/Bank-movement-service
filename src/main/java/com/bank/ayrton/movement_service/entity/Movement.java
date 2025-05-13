package com.bank.ayrton.movement_service.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "movements")
@NoArgsConstructor
@AllArgsConstructor
public class Movement {
    @Id
    private String id;

    private String clientId;      // ID del cliente que hace el movimiento
    private String productId;     // ID del producto afectado
    private MovementType type;    // DEPOSIT, WITHDRAW, PAYMENT, CONSUMPTION
    private Double amount;        // Monto del movimiento
    private LocalDateTime date;   // Fecha del movimiento
}