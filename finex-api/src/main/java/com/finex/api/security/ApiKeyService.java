package com.finex.api.security;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * In-memory API key registry. A real deployment would persist keys, rotate them, and scope
 * them to accounts in a database.
 */
@Service
public class ApiKeyService {

    private final Map<String, ApiKey> keys = new ConcurrentHashMap<>();

    public Optional<ApiKey> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(keys.get(key));
    }

    public ApiKey register(String key, long accountId) {
        ApiKey apiKey = new ApiKey(key, accountId, true);
        keys.put(key, apiKey);
        return apiKey;
    }
}
