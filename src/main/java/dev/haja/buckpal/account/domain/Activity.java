package dev.haja.buckpal.account.domain;

import lombok.NonNull;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * {@link Account} 간의 송금 활동.
 *
 * @see Account
 */
@Value
public class Activity {
    ActivityId id;

    /**
     * 활동의 소유자 {@link Account}의 ID
     */
    @NonNull
    Account.AccountId ownerAccountId;

    /**
     * 인출된 {@link Account}의 ID
     */
    @NonNull
    Account.AccountId sourceAccountId;

    /**
     * 입금된 {@link Account}의 ID
     */
    @NonNull
    Account.AccountId targetAccountId;

    /**
     * 활동의 타임스탬프
     */
    @NonNull
    LocalDateTime timestamp;

    public Activity(
            @NonNull Account.AccountId ownerAccountId,
            @NonNull Account.AccountId sourceAccountId,
            @NonNull Account.AccountId targetAccountId,
            @NonNull LocalDateTime timestamp) {
        this.id = null;
        this.ownerAccountId = ownerAccountId;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.timestamp = timestamp;
    }

    @Value
    public static class ActivityId {
        Long value;
    }
}