package com.finex.common.domain;

import java.time.Instant;

import com.finex.common.domain.enums.AccountStatus;

/**
 * A trading account. Accounts hold balances and positions and are owned by a user.
 *
 * @param id        unique account identifier
 * @param userId    owner user identifier
 * @param status    account lifecycle status
 * @param createdAt creation timestamp
 */
public record Account(long id, long userId, AccountStatus status, Instant createdAt) {

    public Account {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
