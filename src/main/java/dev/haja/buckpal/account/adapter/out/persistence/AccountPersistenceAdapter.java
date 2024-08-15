package dev.haja.buckpal.account.adapter.out.persistence;

import dev.haja.buckpal.account.application.port.out.LoadAccountPort;
import dev.haja.buckpal.account.application.port.out.UpdateAccountStatePort;
import dev.haja.buckpal.account.domain.Account;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@RequiredArgsConstructor
class AccountPersistenceAdapter implements
        LoadAccountPort,
        UpdateAccountStatePort {

    private final SpringDataAccountRepository accountRepository;

    @Override
    public Account loadAccount(
                                Account.AccountId accountId,
                                LocalDateTime baselineDate) {
        return null;
    }

    @Override
    public void updateActivities(Account account) {}
}
