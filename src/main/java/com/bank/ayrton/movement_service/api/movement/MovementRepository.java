package com.bank.ayrton.movement_service.api.movement;

import com.bank.ayrton.movement_service.entity.Movement;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.util.Date;

public interface MovementRepository extends ReactiveMongoRepository<Movement, String> {

    //ReactiveMongoRepository usa query derivation para crear consultas mongo db de forma automatica dependiendo del nombre de la funcion
    Flux<Movement> findByProductId(String productId);
    Flux<Movement> findByClientId(String clientId);
    Flux<Movement> findByProductIdAndDateAfter(String productId, Date date);
}