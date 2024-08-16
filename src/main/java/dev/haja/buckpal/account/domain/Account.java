package dev.haja.buckpal.account.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 일정 금액을 보유하고 있는 계정.
 * {@link Account} 에는 최신 계정 활동 윈도우가 포함된다.
 * 계정의 총 잔액은 윈도우에서 첫 활동 이전에 유효했던 기준 잔액과 활동 값의 합이다.
 *
 * @see Account
 */

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account {
    private final AccountId id;
    private final Money baselineBalance;
    private ActivityWindow activityWindow;

    /**
     * id가 있는 Account 엔티티 생성 팩토리 메서드
     * 영속성 관련 엔티티 생성에 사용
     *
     * @param accountId
     * @param baselineBalance
     * @param activityWindow
     * @return Account 엔티티
     */
    public static Account withId(AccountId accountId, Money baselineBalance, ActivityWindow activityWindow) {
        return new Account(accountId, baselineBalance, activityWindow);
    }

    /**
     * id가 없는 Account 엔티티 생성 팩토리 메서드
     * 영속 되기 전 엔티티 생성에 사용
     *
     * @param baselineBalance
     * @param activityWindow
     * @return Account 엔티티
     */
    public static Account withoutId(Money baselineBalance, ActivityWindow activityWindow) {
        return new Account(null, baselineBalance, activityWindow);
    }


    /**
     * 계정의 총 잔액을 계산한다.
     *
     * @return 계정의 총 잔액
     */
    public Money calculateBalance() {
        return Money.add(
                this.baselineBalance,
                this.activityWindow.calculateBalance(this.id));
    }

    // 송금
    public boolean withdraw(Money money, AccountId targetAccountId) {
        // 비즈니스 규칙을 도메인 엔티티 안에 넣었다.
        if (!mayWithdraw(money)) {
            return false;
        }

        Activity withdrawal = new Activity(this.id,
                this.id,
                targetAccountId,
                LocalDateTime.now(),
                money);
        this.activityWindow.addActivity(withdrawal);
        return true;
    }

    /**
     * 출금 가능 여부를 확인한다.
     *
     * @param money 출금 금액
     * @return 출금 가능 여부
     */
    private boolean mayWithdraw(Money money) {
        return Money.add(
                        this.calculateBalance(),
                        money.negate())
                .isPositiveOrZero();
    }

    /**
     * 입금: 이 계좌에 일정 금액을 입금하려고 시도
     * 성공하면 양수 값의 새 활동을 생성하고 활동 윈도우에 추가
     *
     * @param money
     * @param sourceAccountId
     * @return 입금 성공 여부
     */

    public boolean deposit(Money money, AccountId sourceAccountId) {
        Activity deposit = new Activity(
                this.id,
                sourceAccountId,
                this.id,
                LocalDateTime.now(),
                money);
        this.activityWindow.addActivity(deposit);
        return true;
    }

    @Value
    public static class AccountId {
        Long value;
    }
}