package com.bank.ayrton.movement_service.config;

import com.bank.ayrton.movement_service.api.movement.MovementService;
import com.bank.ayrton.movement_service.dto.BootcoinTransactionEvent;
import com.bank.ayrton.movement_service.dto.BootcoinWalletDto;
import com.bank.ayrton.movement_service.dto.TransferMethod;
import com.bank.ayrton.movement_service.dto.YankiMovementEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class KafkaMovementListener {

    private final WebClient bootcoinWebClient;
    private final MovementService movementService;

    public KafkaMovementListener(WebClient bootcoinWebClient, MovementService movementService) {
        this.bootcoinWebClient = bootcoinWebClient;
        this.movementService = movementService;
    }

    @KafkaListener(topics = "yanki-transactions", groupId = "movement-group")
    public void listen(YankiMovementEvent event) {
        log.info("Evento Yanki recibido: de {} a {} por {}", event.getFromCard(), event.getToCard(), event.getAmount());

        movementService.transfer(event.getFromCard(), event.getToCard(), event.getAmount())
                .doOnSuccess(v -> log.info("Transferencia Yanki procesada correctamente"))
                .doOnError(err -> log.error("Error procesando transferencia Yanki: {}", err.getMessage()))
                .subscribe();
    }

    @KafkaListener(topics = "bootcoin.yanki.transfer", groupId = "movement-group")
    public void handleBootcoinYankiTransfer(BootcoinTransactionEvent event) {
        log.info("Recibiendo transferencia Bootcoin (YANKI): {}", event);
        processBootcoinTransfer(event);
    }

    @KafkaListener(topics = "bootcoin.account.transfer", groupId = "movement-group")
    public void handleBootcoinAccountTransfer(BootcoinTransactionEvent event) {
        log.info("Recibiendo transferencia Bootcoin (ACCOUNT): {}", event);
        processBootcoinTransfer(event);
    }

    private void processBootcoinTransfer(BootcoinTransactionEvent event) {
        Mono.zip(
                        getWalletById(event.getBuyerWalletId()),
                        getWalletById(event.getSellerWalletId())
                ).flatMap(tuple -> {
                    BootcoinWalletDto buyer = tuple.getT1();
                    BootcoinWalletDto seller = tuple.getT2();

                    String fromProductId;
                    String toProductId;

                    if (event.getTransferMethod() == TransferMethod.ACCOUNT) {
                        fromProductId = buyer.getAssociatedAccountId();
                        toProductId = seller.getAssociatedAccountId();
                    } else {
                        fromProductId = buyer.getAssociatedYankiWalletId();
                        toProductId = seller.getAssociatedYankiWalletId();
                    }

                    if (fromProductId == null || toProductId == null) {
                        return Mono.error(new RuntimeException("Uno de los productos asociados es null"));
                    }

                    log.info("Bootcoin transferencia - de {} a {} por {}", fromProductId, toProductId, event.getAmount());

                    return movementService.transfer(fromProductId, toProductId, event.getAmount());
                }).doOnSuccess(r -> log.info("Transferencia de Bootcoin completada exitosamente"))
                .doOnError(error -> log.error("Error en transferencia Bootcoin: {}", error.getMessage()))
                .subscribe();
    }

    private Mono<BootcoinWalletDto> getWalletById(String walletId) {
        return bootcoinWebClient.get()
                .uri("/wallets/{id}", walletId)
                .retrieve()
                .bodyToMono(BootcoinWalletDto.class);
    }
}