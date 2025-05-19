package com.bank.ayrton.movement_service.controller;

import com.bank.ayrton.movement_service.api.movement.MovementService;
import com.bank.ayrton.movement_service.entity.Movement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/movement")
@RequiredArgsConstructor
public class MovementController {

    private final MovementService service;

    @GetMapping
    public Flux<Movement> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Movement>> findById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Movement> save(@RequestBody Movement movement) {
        return service.save(movement);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Movement>> update(@PathVariable String id, @RequestBody Movement movement) {
        return service.update(id, movement)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
        return service.delete(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @GetMapping("/client/{clientId}")
    public Flux<Movement> findByClientId(@PathVariable String clientId){
        return service.findByClientId(clientId);
    }
}