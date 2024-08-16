package dev.haja.buckpal.account.application.service;

import dev.haja.buckpal.account.application.port.in.GetAccountBalanceQuery;
import dev.haja.buckpal.account.application.port.out.LoadAccountPort;
import dev.haja.buckpal.account.domain.Account.AccountId;
import dev.haja.buckpal.account.domain.Money;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class GetAccountBalanceService implements GetAccountBalanceQuery {

    private final LoadAccountPort loadAccountPort;

    @Override
    public Money getAccountBalance(AccountId accountId) {
        return loadAccountPort.loadAccount(accountId, LocalDateTime.now())
                .calculateBalance();
    }
}
