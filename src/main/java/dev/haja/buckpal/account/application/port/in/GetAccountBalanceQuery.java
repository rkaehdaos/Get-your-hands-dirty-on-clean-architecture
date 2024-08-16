package dev.haja.buckpal.account.application.port.in;

import dev.haja.buckpal.account.domain.Account.AccountId;
import dev.haja.buckpal.account.domain.Money;

public interface GetAccountBalanceQuery {
    Money getAccountBalance(AccountId accountId);
}