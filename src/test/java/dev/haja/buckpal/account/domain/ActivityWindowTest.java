package dev.haja.buckpal.account.domain;


import dev.haja.buckpal.account.domain.Account.AccountId;
import org.junit.jupiter.api.Test;

import static dev.haja.buckpal.common.ActivityTestData.defaultActivity;


class ActivityWindowTest {

    @Test
    void calculatesBalanceTest() {
        AccountId account1 = new AccountId(1L);
        AccountId account2 = new AccountId(2L);

        ActivityWindow window = new ActivityWindow(
                defaultActivity()
                        .withSourceAccount(account1)
                        .withTargetAccount(account2)
                        .withMoney(Money.of(999L)).build(),
                defaultActivity()
                        .withSourceAccount(account1)
                        .withTargetAccount(account2)
                        .withMoney(Money.of(1L)).build(),
                defaultActivity()
                        .withSourceAccount(account2)
                        .withTargetAccount(account1)
                        .withMoney(Money.of(500)).build()
        );
//        assertThat(window.calculateBalance(account1)).isEqualTo(Money.of(-500L));
//        assertThat(window.calculateBalance(account2)).isEqualTo(Money.of(500L));
    }
}