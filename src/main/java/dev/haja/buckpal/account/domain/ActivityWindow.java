package dev.haja.buckpal.account.domain;

import dev.haja.buckpal.account.domain.Account.AccountId;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 계정 활동 윈도우.
 */
public class ActivityWindow {

    /**
     * 활동 목록.
     */
    private final List<Activity> activities;

    public ActivityWindow(@NonNull List<Activity> activities) {
        this.activities = activities;
    }

    public ActivityWindow(@NonNull Activity... activities) {
        this.activities = new ArrayList<>(Arrays.asList(activities));
    }

    /**
     * 활동 윈도우의 활동을 기반으로 계정의 총 잔액을 계산한다.
     *
     * @param accountId 계정 ID
     * @return 계정의 총 잔액
     */
    public Money calculateBalance(AccountId accountId) {
        Money depositBalance = activities.stream()
                .filter(activity -> activity.getTargetAccountId().equals(accountId))
                .map(Activity::getMoney)
                .reduce(Money.ZERO, Money::add);
        Money withdrawalBalance = activities.stream()
                .filter(activity -> activity.getSourceAccountId().equals(accountId))
                .map(Activity::getMoney)
                .reduce(Money.ZERO, Money::add);
        return Money.add(depositBalance, withdrawalBalance.negate());
    }

    public void addActivity(Activity activity) {
        this.activities.add(activity);
    }

    public List<Activity> getActivities() {
        return Collections.unmodifiableList(this.activities);
    }

    /**
     * 활동 윈도우의 첫 번째 활동의 타임스탬프를 반환한다.
     *
     * @return 첫 번째 활동의 타임스탬프
     */
    public LocalDateTime getStartTimestamp() {
        return activities.stream()
                .min(Comparator.comparing(Activity::getTimestamp))
                .orElseThrow(() -> new IllegalStateException("활동 윈도우에 활동이 없습니다."))
                .getTimestamp();
    }


    /**
     * 활동 윈도우의 마지막 활동의 타임스탬프를 반환한다.
     *
     * @return 마지막 활동의 타임스탬프
     */
    public LocalDateTime getEndTimestamp() {
        return activities.stream()
                .max(Comparator.comparing(Activity::getTimestamp))
                .orElseThrow(() -> new IllegalStateException("활동 윈도우에 활동이 없습니다."))
                .getTimestamp();
    }
}
