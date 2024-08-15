package dev.haja.buckpal.account.application.port.out;

import dev.haja.buckpal.account.domain.Account;

import java.time.LocalDateTime;

public interface LoadAccountPort {
    Account loadAccount(
            Account.AccountId accountId,
            LocalDateTime baselineDate);
}
