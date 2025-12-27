package com.weblab.rplace.weblab.rplace.core.utilities.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, this::newBucket);
    }

    private Bucket newBucket(String key) {

        Bandwidth limit = Bandwidth.classic(1, Refill.intervally(1, Duration.ofSeconds(3)));

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}