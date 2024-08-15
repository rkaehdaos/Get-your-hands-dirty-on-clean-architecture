package dev.haja.buckpal.account.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * 일정 금액을 보유하고 있는 계정.
 * {@link Account} 에는 최신 계정 활동 윈도우가 포함된다.
 * 계정의 총 잔액은 윈도우에서 첫 활동 이전에 유효했던 기준 잔액과 활동 값의 합이다.
 * @see Account
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account {
    private final AccountId id;

    @Value
    public static class AccountId {
        Long value;
    }
}