package com.bank.ayrton.movement_service;

import com.bank.ayrton.movement_service.api.movement.MovementRepository;
import com.bank.ayrton.movement_service.entity.Movement;
import com.bank.ayrton.movement_service.entity.MovementType;
import com.bank.ayrton.movement_service.service.movement.MovementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@SpringBootTest
public class MovementServiceImplTest {

    private MovementRepository repository;
    private WebClient clientWebClient;
    private WebClient productWebClient;
    private MovementServiceImpl service;

    @BeforeEach
    void setup() {
        repository = mock(MovementRepository.class);
        clientWebClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        productWebClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        service = new MovementServiceImpl(repository, clientWebClient, productWebClient);
    }

    @Test
    void testFindAll() {
        Movement movement = new Movement();
        movement.setId("123");
        when(repository.findAll()).thenReturn(Flux.just(movement));

        StepVerifier.create(service.findAll())
                .expectNextMatches(m -> m.getId().equals("123"))
                .verifyComplete();
    }

    @Test
    void testFindById() {
        Movement movement = new Movement();
        movement.setId("abc");
        when(repository.findById("abc")).thenReturn(Mono.just(movement));

        StepVerifier.create(service.findById("abc"))
                .expectNextMatches(m -> m.getId().equals("abc"))
                .verifyComplete();
    }

    @Test
    void testDelete() {
        when(repository.deleteById("456")).thenReturn(Mono.empty());

        StepVerifier.create(service.delete("456"))
                .verifyComplete();
    }

    @Test
    void testUpdate() {
        Movement oldMovement = new Movement("1", "client1", "product1", MovementType.DEPOSIT, 100.0, LocalDateTime.now());
        Movement newMovement = new Movement(null, "client1", "product1", MovementType.DEPOSIT, 150.0, LocalDateTime.now());

        when(repository.findById("1")).thenReturn(Mono.just(oldMovement));
        when(repository.save(any(Movement.class))).thenReturn(Mono.just(newMovement));

        StepVerifier.create(service.update("1", newMovement))
                .expectNextMatches(m -> m.getAmount() == 150.0)
                .verifyComplete();
    }
}