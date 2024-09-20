package dev.haja.buckpal.account.application.port.out;

import dev.haja.buckpal.account.domain.Account;

import java.time.LocalDateTime;

import static dev.haja.buckpal.account.domain.Account.AccountId;

public interface LoadAccountPort {
    Account loadAccount(
            AccountId accountId,
            LocalDateTime baselineDate);
}