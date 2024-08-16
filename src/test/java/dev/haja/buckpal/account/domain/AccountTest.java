package dev.haja.buckpal.account.domain;

import dev.haja.buckpal.account.domain.Account.AccountId;
import org.junit.jupiter.api.Test;

import static dev.haja.buckpal.common.AccountTestData.defaultAccount;
import static dev.haja.buckpal.common.ActivityTestData.defaultActivity;

class AccountTest {

    @Test
    void calculateBalanceTest() {
        AccountId accountId = new AccountId(1L);
        Account account = defaultAccount()
                .withAccountId(accountId)
                .withBaselineBalance(Money.of(555L))
                .withActivityWindow(new ActivityWindow(
                        defaultActivity()
                                .withTargetAccount(accountId)
                                .withMoney(Money.of(999L)).build(),
                        defaultActivity()
                                .withTargetAccount(accountId)
                                .withMoney(Money.of(1L)).build()
                ))
                .build();
//        Money money = account.calculateBalance();
//        assertThat(money).isEqualTo(Money.of(1555L));

    }
}