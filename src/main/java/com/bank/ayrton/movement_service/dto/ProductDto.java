package com.bank.ayrton.movement_service.dto;

import com.bank.ayrton.movement_service.entity.ProductSubtype;
import lombok.Data;


// el DTO nos permite manejar el objeto de otro microservicio de forma sencilla
@Data
public class ProductDto {
    private String id;
    private String type; // "ACTIVE" o "PASSIVE"
    private ProductSubtype subtype; // SAVINGS,Ahorro - CURRENT_ACCOUNT,Cuenta corriente -
    // FIXED_TERM, Plazo fijo - PERSONAL_CREDIT,Crédito personal - BUSINESS_CREDIT,Crédito empresarial - CREDIT_CARD,Tarjeta de crédito
    private Double balance;
    private String clientId;
    private Double maintenanceFee;
    private Integer monthlyMovementLimit;
    private Integer allowedMovementDay;
    private Double creditLimit;
}