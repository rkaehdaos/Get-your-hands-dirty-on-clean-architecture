package dev.haja.buckpal.account.domain;


import dev.haja.buckpal.account.domain.Account.AccountId;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static dev.haja.buckpal.common.ActivityTestData.defaultActivity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


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
        assertThat(window.calculateBalance(account1)).isEqualTo(Money.of(-500L));
        assertThat(window.calculateBalance(account2)).isEqualTo(Money.of(500L));
    }

    @Test
    void calculatesStartTimestamp() {
        ActivityWindow window = new ActivityWindow(
                defaultActivity().withTimestamp(startDate()).build(),
                defaultActivity().withTimestamp(inBetweenDate()).build(),
                defaultActivity().withTimestamp(endDate()).build());

        Assertions.assertThat(window.getStartTimestamp()).isEqualTo(startDate());
    }
    @Test
    void calculatesEndTimestamp() {
        ActivityWindow window = new ActivityWindow(
                defaultActivity().withTimestamp(startDate()).build(),
                defaultActivity().withTimestamp(inBetweenDate()).build(),
                defaultActivity().withTimestamp(endDate()).build());

        Assertions.assertThat(window.getEndTimestamp()).isEqualTo(endDate());
    }

    private LocalDateTime startDate() {
        return LocalDateTime.of(2019, 8, 3, 0, 0);
    }

    private LocalDateTime inBetweenDate() {
        return LocalDateTime.of(2019, 8, 4, 0, 0);
    }

    private LocalDateTime endDate() {
        return LocalDateTime.of(2019, 8, 5, 0, 0);
    }

    @Test
    void getStartTimestamp_ShouldThrowIllegalStateException_WhenNoActivities() {
        ActivityWindow emptyWindow = new ActivityWindow(Collections.emptyList());

        assertThatThrownBy(emptyWindow::getStartTimestamp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("활동 윈도우에 활동이 없습니다.");
    }

    @Test
    void getEndTimestamp_ShouldThrowIllegalStateException_WhenNoActivities() {
        ActivityWindow emptyWindow = new ActivityWindow(Collections.emptyList());

        assertThatThrownBy(emptyWindow::getEndTimestamp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("활동 윈도우에 활동이 없습니다.");
    }

}