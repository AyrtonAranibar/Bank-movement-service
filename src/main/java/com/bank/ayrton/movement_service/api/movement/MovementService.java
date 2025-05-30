package com.bank.ayrton.movement_service.api.movement;

import com.bank.ayrton.movement_service.dto.ThirdPartyPaymentRequest;
import com.bank.ayrton.movement_service.entity.Movement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface MovementService {
    Flux<Movement> findAll();
    Mono<Movement> findById(String id);
    Mono<Movement> save(Movement movement);
    Mono<Movement> update(String id, Movement movement);
    Mono<Void> delete(String id);
    Flux<Movement> findByClientId(String clientId);
    Mono<Void> transfer(String fromProductId, String toProductId, Double amount);
    Flux<Movement> getMovementsByProductAndDateRange(String productId, LocalDate from, LocalDate to);
    Mono<Void> payThirdParty(ThirdPartyPaymentRequest request);
}
