package dev.haja.buckpal.account.adapter.out.persistence;

import dev.haja.buckpal.account.application.port.out.AccountLock;
import dev.haja.buckpal.account.domain.Account;
import org.springframework.stereotype.Component;

@Component
public class NoOpsAccountLock implements AccountLock {
    @Override
    public void lockAccount(Account.AccountId accountId) {
        // 아무것도 하지 않음
    }

    @Override
    public void releaseAccount(Account.AccountId accountId) {
        // 아무것도 하지 않음
    }
}
