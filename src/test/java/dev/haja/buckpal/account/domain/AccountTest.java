package dev.haja.buckpal.account.domain;

import dev.haja.buckpal.account.domain.Account.AccountId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.haja.buckpal.common.AccountTestData.defaultAccount;
import static dev.haja.buckpal.common.ActivityTestData.defaultActivity;
import static org.assertj.core.api.Assertions.assertThat;

class AccountTest {

    private Account testAccount = null;

    @BeforeEach
    void setUp() {
        AccountId accountId = new AccountId(1L);
        testAccount = defaultAccount()
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
    }

    @Test
    void calculatesBalanceTest() {
        assertThat(testAccount.calculateBalance()).isEqualTo(Money.of(1555L));
    }

    @Test
    void withdrawalSucceedsTest() {
        boolean withdrawSuccess = testAccount.withdraw(Money.of(555L), new AccountId(99L));
        assertThat(withdrawSuccess).isTrue();
        assertThat(testAccount.getActivityWindow().getActivities()).hasSize(3);
        assertThat(testAccount.calculateBalance()).isEqualTo(Money.of(1000L));
    }

    @Test
    void withdrawalFailure() {
        boolean withdrawSuccess = testAccount.withdraw(Money.of(1556L), new AccountId(99L));
        assertThat(withdrawSuccess).isFalse();
        assertThat(testAccount.getActivityWindow().getActivities()).hasSize(2);
        assertThat(testAccount.calculateBalance()).isEqualTo(Money.of(1555L));
    }

    @Test
    void depositSuccessTest() {
        boolean depositSuccess = testAccount.deposit(Money.of(445L), new AccountId(99L));
        assertThat(depositSuccess).isTrue();
        assertThat(testAccount.getActivityWindow().getActivities()).hasSize(3);
        assertThat(testAccount.calculateBalance()).isEqualTo(Money.of(2000L));
    }
}