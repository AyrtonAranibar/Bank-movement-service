package com.bank.ayrton.movement_service.api.movement;

import com.bank.ayrton.movement_service.entity.Movement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MovementService {
    Flux<Movement> findAll();
    Mono<Movement> findById(String id);
    Mono<Movement> save(Movement movement);
    Mono<Movement> update(String id, Movement movement);
    Mono<Void> delete(String id);
}
