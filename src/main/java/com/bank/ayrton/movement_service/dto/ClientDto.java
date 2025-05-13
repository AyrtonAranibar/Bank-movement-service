package com.bank.ayrton.movement_service.dto;

import lombok.Data;

// el DTO nos permite manejar el objeto de otro microservicio de forma sencilla
@Data
public class ClientDto {
    private String id;
    private String name;
    private String dni;
    private String type; //personal,empresarial
}
