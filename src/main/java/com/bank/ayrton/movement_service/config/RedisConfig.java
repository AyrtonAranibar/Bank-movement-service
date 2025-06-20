package com.bank.ayrton.movement_service.config;

import com.bank.ayrton.movement_service.dto.ClientDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;


@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, ClientDto> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        RedisSerializationContext<String, ClientDto> context = RedisSerializationContext
                .<String, ClientDto>newSerializationContext(new StringRedisSerializer())
                .value(new Jackson2JsonRedisSerializer<>(ClientDto.class))
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}