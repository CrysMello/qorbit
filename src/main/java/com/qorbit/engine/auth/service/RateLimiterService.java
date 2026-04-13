package com.qorbit.engine.auth.service;

import io.github.bucket4j.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting in-memory por IP usando Bucket4j.
 *
 * Limites por endpoint:
 *  - Login:           5 tentativas / 15 minutos por IP
 *  - Registro:       10 tentativas / hora por IP
 *  - Forgot password: 3 tentativas / hora por IP
 *
 * Para produção com múltiplas instâncias, substitua pelo Bucket4j + Redis/Hazelcast.
 */
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, Bucket> loginBuckets    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> forgotBuckets   = new ConcurrentHashMap<>();

    public boolean allowLogin(String ip) {
        return loginBuckets
            .computeIfAbsent(ip, k -> newBucket(5, Duration.ofMinutes(15)))
            .tryConsume(1);
    }

    public boolean allowRegister(String ip) {
        return registerBuckets
            .computeIfAbsent(ip, k -> newBucket(10, Duration.ofHours(1)))
            .tryConsume(1);
    }

    public boolean allowForgotPassword(String ip) {
        return forgotBuckets
            .computeIfAbsent(ip, k -> newBucket(3, Duration.ofHours(1)))
            .tryConsume(1);
    }

    private Bucket newBucket(long capacity, Duration refillPeriod) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(capacity)
            .refillGreedy(capacity, refillPeriod)
            .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
