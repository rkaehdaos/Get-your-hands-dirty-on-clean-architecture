package dev.haja.buckpal.account.application.port.out;

import dev.haja.buckpal.account.domain.Account.AccountId;

public interface AccountLock {

    void lockAccount(AccountId accountId);

    void releaseAccount(AccountId accountId);
}
