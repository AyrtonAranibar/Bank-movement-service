package com.bank.ayrton.movement_service.entity;

//el enum limita los valores que puede tener un campo
public enum MovementType {
    DEPOSIT, //deposito en una cuenta bancaria
    WITHDRAWAL, // Retiro de una cuenta bancaria o producto financiero
    PAYMENT, //pago de una deuda credito o tarjeta
    CONSUMPTION // consumo cargado a una tarjeta de credito.
}