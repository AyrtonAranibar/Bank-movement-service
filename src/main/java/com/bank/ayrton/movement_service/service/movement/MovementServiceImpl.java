package com.bank.ayrton.movement_service.service.movement;

import com.bank.ayrton.movement_service.api.movement.MovementRepository;
import com.bank.ayrton.movement_service.api.movement.MovementService;
import com.bank.ayrton.movement_service.dto.ClientDto;
import com.bank.ayrton.movement_service.dto.ProductDto;
import com.bank.ayrton.movement_service.entity.Movement;
import com.bank.ayrton.movement_service.entity.MovementType;
import com.bank.ayrton.movement_service.entity.ProductSubtype;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class MovementServiceImpl implements MovementService {

    private final MovementRepository repository;
    private final WebClient clientWebClient;
    private final WebClient productWebClient;

    // Lista los movimientos
    @Override
    public Flux<Movement> findAll() {
        return repository.findAll();
    }

    // Busca por ID
    @Override
    public Mono<Movement> findById(String id) {
        return repository.findById(id);
    }

    //registra un nuevo movimiento y hace validaciones
    @Override
    public Mono<Movement> save(Movement movement) {
        return clientWebClient.get()
                .uri("/api/v1/client/{id}", movement.getClientId())
                .retrieve()
                .bodyToMono(ClientDto.class)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")))
                .flatMap(client ->
                        productWebClient.get()
                                .uri("/api/v1/product/{id}", movement.getProductId())
                                .retrieve()
                                .bodyToMono(ProductDto.class)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")))
                                .flatMap(product -> {
                                    // Validación: el cliente debe tener tipo válido para tarjeta de crédito
                                    if (product.getSubtype() == ProductSubtype.CREDIT_CARD &&
                                            !"personal".equalsIgnoreCase(client.getType()) &&
                                            !"empresarial".equalsIgnoreCase(client.getType())) {
                                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de cliente no válido para tarjeta de crédito."));
                                    }

                                    return validarMovimiento(movement, product);
                                })
                )
                .onErrorResume(ResponseStatusException.class, Mono::error)
                .onErrorResume(e -> Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado: " + e.getMessage())));
    }


    private Mono<Movement> validarMovimiento(Movement movement, ProductDto product) {

        //plazo fijo solo permite retiro en un día específico del mes
        if (product.getSubtype() == ProductSubtype.FIXED_TERM && movement.getType() == MovementType.WITHDRAWAL) {
            int today = LocalDate.now().getDayOfMonth();
            if (product.getAllowedMovementDay() != null && product.getAllowedMovementDay() != today) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Movimiento no permitido: solo se puede hacer el día permitido."));
            }
        }

        //cuenta de ahorro con limite de movimientos mensuales
        if (product.getSubtype() == ProductSubtype.SAVINGS && product.getMonthlyMovementLimit() != null) {
            Date startOfMonth = Date.from(LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            return repository.findByProductIdAndDateAfter(movement.getProductId(), startOfMonth)
                    .count()
                    .flatMap(count -> {
                        if (count >= product.getMonthlyMovementLimit()) {
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se superó el límite de movimientos mensuales para cuenta de ahorro."));
                        }
                        return actualizarBalanceYGuardar(product, movement);
                    });
        }

        //no debe exceder el límite de credito disponible
        if ((product.getSubtype() == ProductSubtype.PERSONAL_CREDIT ||
                product.getSubtype() == ProductSubtype.BUSINESS_CREDIT ||
                product.getSubtype() == ProductSubtype.CREDIT_CARD) &&
                movement.getType() == MovementType.WITHDRAWAL) {
            if (product.getCreditLimit() != null && movement.getAmount() > product.getCreditLimit()) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto excede el límite de crédito."));
            }
        }

        //se actualiza el balance y se guarda el movimiento
        return actualizarBalanceYGuardar(product, movement);
    }

    //ctualiza el saldo del producto dependiendo del tipo de movimiento
    private Mono<Movement> actualizarBalanceYGuardar(ProductDto product, Movement movement) {
        double currentBalance = product.getBalance() != null ? product.getBalance() : 0.0;

        if (movement.getType() == MovementType.DEPOSIT) {
            product.setBalance(currentBalance + movement.getAmount());
        } else if (movement.getType() == MovementType.WITHDRAWAL) {
            product.setBalance(currentBalance - movement.getAmount());
        }

        return productWebClient.put()
                .uri("/api/v1/product/{id}", product.getId())
                .bodyValue(product)
                .retrieve()
                .bodyToMono(ProductDto.class)
                .then(repository.save(movement));
    }

    // Actualiza un movimiento existente por ID
    @Override
    public Mono<Movement> update(String id, Movement movement) {
        return repository.findById(id)
                .flatMap(existing -> {
                    movement.setId(id);
                    return repository.save(movement);
                });
    }

    // Elimina un movimiento por ID
    @Override
    public Mono<Void> delete(String id) {
        return repository.deleteById(id);
    }
}
