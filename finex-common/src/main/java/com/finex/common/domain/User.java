package com.finex.common.domain;

import java.time.Instant;

/**
 * A user of the exchange. Users own one or more trading accounts.
 *
 * @param id        unique user identifier
 * @param username  display name / login
 * @param email     contact email
 * @param createdAt creation timestamp
 */
public record User(long id, String username, String email, Instant createdAt) {

    public User {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
