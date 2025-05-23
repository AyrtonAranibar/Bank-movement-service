package com.bank.ayrton.movement_service.service.movement;

import com.bank.ayrton.movement_service.api.movement.MovementRepository;
import com.bank.ayrton.movement_service.api.movement.MovementService;
import com.bank.ayrton.movement_service.dto.ClientDto;
import com.bank.ayrton.movement_service.dto.ProductDto;
import com.bank.ayrton.movement_service.entity.Movement;
import com.bank.ayrton.movement_service.entity.MovementType;
import com.bank.ayrton.movement_service.entity.ProductSubtype;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

//Simple Logging Facade for Java sirve para registrar logs
@Slf4j
@Service
@RequiredArgsConstructor
public class MovementServiceImpl implements MovementService {

    private final MovementRepository repository;
    private final WebClient clientWebClient;
    private final WebClient productWebClient;

    // Lista los movimientos
    @Override
    public Flux<Movement> findAll() {
        log.info("Obteniendo todos los movimientos");
        return repository.findAll();
    }

    // Busca por ID
    @Override
    public Mono<Movement> findById(String id) {
        log.info("Buscando movimiento con ID: {}", id);
        return repository.findById(id);
    }

    //registra un nuevo movimiento y hace validaciones
    @Override
    public Mono<Movement> save(Movement movement) {
        log.info("Registrando nuevo movimiento: {}", movement);
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
                                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de cliente no valido para tarjeta de crrdito"));
                                    }

                                    return validarMovimiento(movement, product);
                                })
                )
                .onErrorResume(ResponseStatusException.class, error -> {
                    log.error("Error esperado: {}", error.getReason());
                    return Mono.error(error);
                })
                .onErrorResume(error -> {
                    log.error("Error inesperado: {}", error.getMessage());
                    return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado"));
                });
    }

    private Mono<Movement> validarMovimiento(Movement movement, ProductDto product) {
        log.info("Validando movimiento para producto: {}", product.getId());
        //plazo fijo solo permite retiro en un día específico del mes
        if (product.getSubtype() == ProductSubtype.FIXED_TERM && movement.getType() == MovementType.WITHDRAWAL) {
            int today = LocalDate.now().getDayOfMonth();
            if (product.getAllowedMovementDay() != null && product.getAllowedMovementDay() != today) {
                log.warn("Retiro no permitido: hoy es día {}, permitido solo el día {}", today, product.getAllowedMovementDay());
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Movimiento no permitido: solo se puede hacer el día permitido."));
            }
        }

        Date startOfMonth = Date.from(LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        return repository.findByProductIdAndDateAfter(product.getId(), startOfMonth)
                .count()
                .flatMap(movementCount -> {
                    log.info("Cantidad de movimientos este mes: {}", movementCount);

                    // Aplica comisión si se excede el límite de transacciones gratuitas
                    if (product.getFreeTransactionLimit() != null && product.getTransactionFee() != null &&
                            movementCount >= product.getFreeTransactionLimit()) {
                        log.info("Aplicando comisión de {} por exceder el límite de {} transacciones gratuitas",
                                product.getTransactionFee(), product.getFreeTransactionLimit());
                        movement.setAmount(movement.getAmount() + product.getTransactionFee());
                    }

                    // no debe exceder el límite de crédito disponible
                    if ((product.getSubtype() == ProductSubtype.PERSONAL_CREDIT ||
                            product.getSubtype() == ProductSubtype.BUSINESS_CREDIT ||
                            product.getSubtype() == ProductSubtype.CREDIT_CARD) &&
                            movement.getType() == MovementType.WITHDRAWAL) {
                        if (product.getCreditLimit() != null && movement.getAmount() > product.getCreditLimit()) {
                            log.warn("Retiro excede el límite de crédito. Monto: {}, Límite: {}", movement.getAmount(), product.getCreditLimit());
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto excede el límite de crédito."));
                        }
                    }

                    return actualizarBalanceYGuardar(product, movement);
                });
    }

    //ctualiza el saldo del producto dependiendo del tipo de movimiento
    private Mono<Movement> actualizarBalanceYGuardar(ProductDto product, Movement movement) {
        double currentBalance = product.getBalance() != null ? product.getBalance() : 0.0;

        if (movement.getType() == MovementType.DEPOSIT) {
            product.setBalance(currentBalance + movement.getAmount());
        } else if (movement.getType() == MovementType.WITHDRAWAL) {
            product.setBalance(currentBalance - movement.getAmount());
        }
        log.info("Actualizando balance del producto {} nuevo saldo: {}", product.getId(), product.getBalance());

        return productWebClient.put()
                .uri("/api/v1/product/{id}", product.getId())
                .bodyValue(product)
                .retrieve()
                .bodyToMono(ProductDto.class)
                .then(repository.save(movement));
    }

    // Realiza una transferencia entre productos
    @Override
    public Mono<Void> transfer(String fromProductId, String toProductId, Double amount) {
        log.info("Iniciando transferencia de {} de {} a {}", amount, fromProductId, toProductId);

        // Obtener producto origen y destino
        return productWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/product/{id}").build(fromProductId))
                .retrieve()
                .bodyToMono(ProductDto.class)
                .zipWith(productWebClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/product/{id}").build(toProductId))
                        .retrieve()
                        .bodyToMono(ProductDto.class))
                .flatMap(tuple -> {
                    ProductDto from = tuple.getT1();
                    ProductDto to = tuple.getT2();

                    if (from.getBalance() == null || from.getBalance() < amount) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente en cuenta origen"));
                    }

                    from.setBalance(from.getBalance() - amount);
                    to.setBalance((to.getBalance() != null ? to.getBalance() : 0.0) + amount);

                    Movement withdrawal = new Movement(null, from.getClientId(), from.getId(), MovementType.WITHDRAWAL, amount, LocalDateTime.now());
                    Movement deposit = new Movement(null, to.getClientId(), to.getId(), MovementType.DEPOSIT, amount, LocalDateTime.now());

                    Mono<ProductDto> updateFrom = productWebClient.put()
                            .uri(uriBuilder -> uriBuilder.path("/api/v1/product/{id}").build(from.getId()))
                            .bodyValue(from)
                            .retrieve()
                            .bodyToMono(ProductDto.class);

                    Mono<ProductDto> updateTo = productWebClient.put()
                            .uri(uriBuilder -> uriBuilder.path("/api/v1/product/{id}").build(to.getId()))
                            .bodyValue(to)
                            .retrieve()
                            .bodyToMono(ProductDto.class);

                    return Mono.when(updateFrom, updateTo)
                            .then(repository.saveAll(List.of(withdrawal, deposit)).then());
                });
    }

    // Actualiza un movimiento existente por ID
    @Override
    public Mono<Movement> update(String id, Movement movement) {
        log.info("Actualizando movimiento con ID: {}", id);
        return repository.findById(id)
                .flatMap(existing -> {
                    movement.setId(id);
                    return repository.save(movement);
                });
    }

    // Elimina un movimiento por ID
    @Override
    public Mono<Void> delete(String id) {
        log.info("Eliminando movimiento con ID: {}", id);
        return repository.deleteById(id);
    }

    @Override
    public Flux<Movement> findByClientId(String clientId) {
        log.info("Buscando movimientos por clientId: {}", clientId);
        return repository.findByClientId(clientId);
    }
}

