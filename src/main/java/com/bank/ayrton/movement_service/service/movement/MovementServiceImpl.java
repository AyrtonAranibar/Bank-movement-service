package com.bank.ayrton.movement_service.service.movement;

import com.bank.ayrton.movement_service.api.movement.MovementRepository;
import com.bank.ayrton.movement_service.api.movement.MovementService;
import com.bank.ayrton.movement_service.dto.ClientDto;
import com.bank.ayrton.movement_service.dto.ProductDto;
import com.bank.ayrton.movement_service.entity.Movement;
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

    @Override
    public Flux<Movement> findAll() {
        return repository.findAll();
    }

    @Override
    public Mono<Movement> findById(String id) {
        return repository.findById(id);
    }

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

                                    //cliente empresarial no puede tener cuentas de ahorro ni plazo fijo
                                    if (client.getType().equalsIgnoreCase("empresarial") &&
                                            (product.getSubtype().equalsIgnoreCase("SAVINGS") ||
                                                    product.getSubtype().equalsIgnoreCase("FIXED_TERM"))) {
                                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un cliente empresarial no puede tener cuentas de ahorro ni de plazo fijo."));
                                    }

                                    //cliente personal no puede tener más de una cuenta por tipo (ahorro, corriente, plazo fijo)
                                    if (client.getType().equalsIgnoreCase("personal") &&
                                            (product.getSubtype().equalsIgnoreCase("SAVINGS") ||
                                                    product.getSubtype().equalsIgnoreCase("CURRENT_ACCOUNT") ||
                                                    product.getSubtype().equalsIgnoreCase("FIXED_TERM"))) {
                                        return repository.findByClientId(client.getId())
                                                .flatMap(existingMovement ->
                                                        productWebClient.get()
                                                                .uri("/api/v1/product/{id}", existingMovement.getProductId())
                                                                .retrieve()
                                                                .bodyToMono(ProductDto.class)
                                                )
                                                .filter(existingProduct -> existingProduct.getSubtype().equalsIgnoreCase(product.getSubtype()))
                                                .hasElements()
                                                .flatMap(alreadyExists -> {
                                                    if (alreadyExists) {
                                                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente personal ya tiene una cuenta de tipo " + product.getSubtype()));
                                                    }
                                                    return validarMovimiento(movement, product);
                                                });
                                    }

                                    return validarMovimiento(movement, product);
                                })
                )
                .onErrorResume(e -> Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage())));
    }

    private Mono<Movement> validarMovimiento(Movement movement, ProductDto product) {

        if (product.getSubtype().equalsIgnoreCase("FIXED_TERM") && movement.getType().name().equalsIgnoreCase("withdrawal")) {
            int today = LocalDate.now().getDayOfMonth();
            if (product.getAllowedMovementDay() != null && product.getAllowedMovementDay() != today) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Movimiento no permitido: solo se puede hacer el día permitido."));
            }
        }

        //si es una cuenta de ahorros y aun no supero el limite de movimientos
        if (product.getSubtype().equalsIgnoreCase("SAVINGS") && product.getMonthlyMovementLimit() != null) {
            // obtiene la fecha del primer dia del mes para contar los movimientos desde esa fecha
            Date startOfMonth = Date.from(LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            return repository.findByProductIdAndDateAfter(movement.getProductId(), startOfMonth)
                    .count()
                    .flatMap(count -> {
                        if (count >= product.getMonthlyMovementLimit()) {
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se superó el límite de movimientos mensuales para cuenta de ahorro."));
                        }
                        return repository.save(movement);
                    });
        }

        //si el producto es un credito y se intenta hacer un retiro
        if ((product.getSubtype().contains("CREDIT") || product.getSubtype().equalsIgnoreCase("CREDIT_CARD"))
                && movement.getType().name().equalsIgnoreCase("withdrawal")) {
            // se valida que el monto no exceda el límite de crédito disponible
            if (product.getCreditLimit() != null && movement.getAmount() > product.getCreditLimit()) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto excede el límite de crédito."));
            }
        }

        return repository.save(movement);
    }

    @Override
    public Mono<Movement> update(String id, Movement movement) {
        return repository.findById(id)
                .flatMap(existing -> {
                    movement.setId(id);
                    return repository.save(movement);
                });
    }

    @Override
    public Mono<Void> delete(String id) {
        return repository.deleteById(id);
    }
}

