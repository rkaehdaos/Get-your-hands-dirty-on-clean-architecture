package dev.haja.buckpal.account.application.port.out;

import dev.haja.buckpal.account.domain.Account;

public interface UpdateAccountStatePort {
    void updateActivities(Account account);
}
