package com.bank.ayrton.movement_service.dto;

import com.bank.ayrton.movement_service.entity.ProductSubtype;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.List;


// el DTO nos permite manejar el objeto de otro microservicio de forma sencilla
@Data
public class ProductDto {
    @Id
    private String id;
    private String type;    // activo o pasivo
    private ProductSubtype subtype;   // SAVINGS,Ahorro - CURRENT_ACCOUNT,Cuenta corriente -
    // FIXED_TERM, Plazo fijo - PERSONAL_CREDIT,Crédito personal - BUSINESS_CREDIT,Crédito empresarial - CREDIT_CARD,Tarjeta de crédito
    private String clientId;  //id del cliente
    private Double balance = 0.0; //saldo, saldo por defecto 0
    private Double maintenanceFee;         //para cuenta corriente
    private Integer monthlyMovementLimit;  //para cuenta ahorro
    private Integer allowedMovementDay;    //para cuenta plazo fijo
    private Double creditLimit;            //para créditos y tarjetas
    private List<String> holders;              //titulares (empresas)
    private List<String> authorizedSignatories; //firmantes autorizados
    private Integer freeTransactionLimit; // transacciones sin comision
    private Double transactionFee;        // monto de comision
}