package com.bank.ayrton.movement_service.config;

import com.bank.ayrton.movement_service.api.movement.MovementService;
import com.bank.ayrton.movement_service.dto.YankiMovementEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMovementListener {

    private final MovementService movementService;

    /**
     * Escucha el tópico donde Yanki publica las transferencias.
     * Cuando llega un evento, delega a MovementService.transfer(...) para aplicar la lógica de negocio
     * y actualizar saldos y movimientos.
     */
    @KafkaListener(topics = "yanki-transactions", groupId = "movement-group")
    public void listen(YankiMovementEvent event) {
        log.info(" Evento Yanki recibido: de {} a {} por {}", event.getFromCard(), event.getToCard(), event.getAmount());

        movementService.transfer(event.getFromCard(), event.getToCard(), event.getAmount())
                .doOnSuccess(v -> log.info("Transferencia Yanki procesada correctamente"))
                .doOnError(err -> log.error(" Error procesando transferencia Yanki: {}", err.getMessage()))
                .subscribe();
    }
}
